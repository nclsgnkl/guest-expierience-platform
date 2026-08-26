#!/usr/bin/env bash
# gxp-updater.sh — the host-side updater of the Guest Experience Platform
# (update-channel Design §3, REQ-718…724, REQ-727). Runs as root from
# gxp-updater.timer every 5 minutes; applies at most ONE update per run and
# at most one attempt per request.
#
#   0  flock (already running → exit 0)
#   1  EXCHANGE = docker volume inspect <project>_gxp-updates; no desired file → exit 0;
#      the desired file is snapshotted ONCE (symlink / non-regular / > 64 KiB → malformed)
#   2  parse the snapshot (fixed layout, every value regex-checked);
#      requestedAt ≤ shadow.lastRequestedAt → exit 0 (replay guard);
#      outside [applyNotBefore, applyNotBefore + applyWindowSeconds) → exit 0
#   3  base64-decode manifest + signature; OpenSSL Ed25519 verify against the
#      TRUST SET (the updater is the trust root): the shadow's rotated set if
#      one exists, else the PEM file GXP_PUBLIC_KEYS (1..4 keys — key 1
#      active, key 2 dormant; review A-1); any key of the set may verify → else invalid-signature
#   4  parse the VERIFIED manifest → VERSION ARTIFACT SHA (malformed before any
#      docker command); VERSION ≤ shadow.currentVersion → downgrade-refused;
#      an optional `trustedKeys` line (signed, must contain the signer, newer
#      than the shadow's set) ROTATES the set — persisted in the shadow
#   5  state: in-progress (BEFORE any side effect — a crash never re-attempts)
#   6  pg_dump --format=custom into GXP_BACKUP_DIR → backup-failed; prune to GXP_KEEP_BACKUPS
#   7  docker pull <artifactRef>@sha256:<manifest.sha256> — Docker itself enforces the
#      registry manifest digest → pull-failed | artifact-mismatch; the local image ID is recorded
#   8  rewrite GXP_IMAGE= in <compose dir>/.env (temp + mv); compose up app   [restart #1]
#   9  healthy? ready 200 + newest flyway_schema_history row success + not
#      restarting, ≤ GXP_HEALTH_TIMEOUT s, then GXP_HEALTH_HOLD s and once more → applied
#  10  rollback: previous image, compose up app [restart #2], healthy? → rolled-back
#  11  restore: stop app; if `pg_restore --list` can read the dump: DROP SCHEMA
#      public CASCADE + CREATE SCHEMA (objects a succeeded migration created
#      must not survive; review A-6), pg_restore the dump; up, healthy?
#      → rolled-back-with-restore, else rollback-failed (old image left as is)
#
# Every outcome is written to the updater's OWN state file first
# (GXP_STATE_FILE, root 0600 — the only memory of the replay guard and the
# downgrade refusal; review R3-3) and then mirrored to state.json in the
# exchange directory for the app (0644). The app may read or delete the
# mirror; that never changes what the updater does next. Root NEVER opens a
# path inside the app-owned directory for writing: the mirror is built in
# the volume's root-owned parent directory (same filesystem) and rename(2)d
# into place (review R3-1, A-3); the only read there goes through
# snapshot_file (symlinks, non-regular files and > 64 KiB refused).
# The script exits 0 after every outcome — a failed update is a state, not a
# crash. Non-zero exits are reserved for configuration errors found before
# anything was touched.
#
# desired-update.json is written by the app container (uid 185) and is
# treated as HOSTILE input: snapshotted once (≤ 64 KiB, regular file only),
# fixed line layout, every value must match its regex before it reaches a
# command line, version/artifactRef/sha256 are taken from the verified
# manifest only, no eval, arrays for every argument list, `--` before
# positional arguments.
#
# Configuration (EnvironmentFile /etc/gxp/updater.conf, see updater.conf.example):
#   GXP_COMPOSE_DIR      directory holding compose.yaml + .env   (/opt/gxp/backend)
#   GXP_COMPOSE_PROJECT  compose project name                     (gxp)
#   GXP_BACKUP_DIR       pre-update dumps, root 0700              (/var/lib/gxp/backups)
#   GXP_STATE_FILE       the updater's own state, root 0600       (/var/lib/gxp-updater/state.json)
#   GXP_PUBLIC_KEYS      the host copy of the trust set (PEM, 1..4 keys) (/etc/gxp/update-signing-public-keys.pem)
#   GXP_HEALTH_TIMEOUT   seconds to wait for a healthy app        (180)
#   GXP_HEALTH_HOLD      seconds to hold before the re-check      (30)
#   GXP_KEEP_BACKUPS     newest dumps kept                        (5)
#   GXP_DB_USER / GXP_DB_NAME   what compose.yaml gives Postgres  (gxp / gxp)
#   GXP_LOCK_FILE        flock path                               (/run/lock/gxp-updater.lock)
#
# Test seams (environment only, never set by the units; every one of them is
# REFUSED when the script runs under systemd — INVOCATION_ID set):
#   GXP_UPDATER_LIBRARY_ONLY=1   define the functions and return — test/parse.sh
#   GXP_REHEARSAL_AFTER_DUMP     path of a script run right AFTER the dump
#                                (test/rehearse-rollback.sh scenario B plants a
#                                failed flyway row); must be root-owned and not
#                                group/world-writable; ignored unless set
#   GXP_REHEARSAL_LOCAL_IMAGE=1  rehearsal-only: the artifact is a LOCAL image
#                                without a registry digest — no pull by digest,
#                                sha256 is compared against the local image ID
#                                instead. Never in production.
set -euo pipefail
export LC_ALL=C   # bash =~ character classes are locale-dependent; pin them
umask 022

GXP_COMPOSE_DIR="${GXP_COMPOSE_DIR:-/opt/gxp/backend}"
GXP_COMPOSE_PROJECT="${GXP_COMPOSE_PROJECT:-gxp}"
GXP_BACKUP_DIR="${GXP_BACKUP_DIR:-/var/lib/gxp/backups}"
GXP_STATE_FILE="${GXP_STATE_FILE:-/var/lib/gxp-updater/state.json}"
GXP_PUBLIC_KEYS="${GXP_PUBLIC_KEYS:-/etc/gxp/update-signing-public-keys.pem}"
GXP_HEALTH_TIMEOUT="${GXP_HEALTH_TIMEOUT:-180}"
GXP_HEALTH_HOLD="${GXP_HEALTH_HOLD:-30}"
GXP_KEEP_BACKUPS="${GXP_KEEP_BACKUPS:-5}"
GXP_DB_USER="${GXP_DB_USER:-gxp}"
GXP_DB_NAME="${GXP_DB_NAME:-gxp}"
GXP_LOCK_FILE="${GXP_LOCK_FILE:-/run/lock/gxp-updater.lock}"
GXP_REHEARSAL_AFTER_DUMP="${GXP_REHEARSAL_AFTER_DUMP:-}"
GXP_REHEARSAL_LOCAL_IMAGE="${GXP_REHEARSAL_LOCAL_IMAGE:-}"

# ---------------------------------------------------------------- the regex table (Design §2)
readonly RE_VERSION='^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.]{1,32})?$'
readonly RE_ARTIFACT='^[a-z0-9][a-z0-9._-]{0,127}(:[0-9]{1,5})?(/[a-z0-9][a-z0-9._-]{0,127}){0,7}(:[A-Za-z0-9_][A-Za-z0-9._-]{0,127})?(@sha256:[0-9a-f]{64})?$'
readonly RE_SHA256='^[0-9a-f]{64}$'
readonly RE_INSTANT='^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$'
readonly RE_CHANNEL='^[a-z0-9-]{1,32}$'
readonly RE_BACKUP='^pre-update-[0-9A-Za-z.-]+-[0-9]{8}T[0-9]{6}Z\.dump$'
readonly RE_BASE64='^[A-Za-z0-9+/]+={0,2}$'
readonly RE_CODE='^[a-z0-9-]{1,32}$'
readonly RE_INT='^[0-9]{1,9}$'
readonly RE_IDENT='^[a-z0-9][a-z0-9_-]{0,62}$'
# one key TOKEN = base64 of the 44-byte Ed25519 SubjectPublicKeyInfo DER (60 chars); a set = 1..4, comma-separated
readonly RE_KEY_TOKEN='^[A-Za-z0-9+/]{59}=$'
readonly RE_KEY_SET='^[A-Za-z0-9+/]{59}=(,[A-Za-z0-9+/]{59}=){0,3}$'
readonly MAX_KEYS=4

readonly DESIRED_FILE='desired-update.json'
readonly STATE_FILE='state.json'
readonly MAX_EXCHANGE_BYTES=65536   # the app caps its own reads at 64 KiB; root is no laxer (review R3-2)

# ---------------------------------------------------------------- fixed-layout readers
# The exchange files and the manifest are one key per line, two-space indent,
# `  "key": value` with an optional trailing comma; string values never contain
# `"` or `\`. A key must occur exactly once. Readers print the value and return
# 1 when the key is absent, duplicated or not of the expected kind.

# raw_field FILE KEY → the JSON token after the colon, trailing comma stripped
raw_field() {
    local file="$1" key="$2" line hits=0 value=""
    while IFS= read -r line || [[ -n "$line" ]]; do
        if [[ "$line" == "  \"$key\": "* ]]; then
            hits=$((hits + 1))
            value="${line#"  \"$key\": "}"
            value="${value%,}"
        fi
    done < "$file"
    [[ $hits -eq 1 ]] || return 1
    printf '%s' "$value"
}

# string_field FILE KEY → the unquoted string (no `"`, no `\` inside)
string_field() {
    local value
    value="$(raw_field "$1" "$2")" || return 1
    [[ "$value" =~ ^\"([^\"\\]*)\"$ ]] || return 1
    printf '%s' "${BASH_REMATCH[1]}"
}

# optional_string_field FILE KEY → the string, or nothing for the literal null
optional_string_field() {
    local value
    value="$(raw_field "$1" "$2")" || return 1
    if [[ "$value" == "null" ]]; then
        return 0
    fi
    [[ "$value" =~ ^\"([^\"\\]*)\"$ ]] || return 1
    printf '%s' "${BASH_REMATCH[1]}"
}

# number_field FILE KEY → a non-negative integer
number_field() {
    local value
    value="$(raw_field "$1" "$2")" || return 1
    [[ "$value" =~ $RE_INT ]] || return 1
    printf '%s' "$value"
}

# bool_field FILE KEY → true|false
bool_field() {
    local value
    value="$(raw_field "$1" "$2")" || return 1
    [[ "$value" == "true" || "$value" == "false" ]] || return 1
    printf '%s' "$value"
}

# line_count FILE — the file must be exactly the fixed layout, nothing appended
line_count() {
    local n=0 line
    while IFS= read -r line || [[ -n "$line" ]]; do
        n=$((n + 1))
    done < "$1"
    printf '%s' "$n"
}

# checked FILE KEY REGEX → string_field, additionally matched against REGEX
checked() {
    local value
    value="$(string_field "$1" "$2")" || return 1
    [[ "$value" =~ $3 ]] || return 1
    printf '%s' "$value"
}

# to_epoch INSTANT → seconds since the epoch; returns 1 for a value `date` rejects
# (RE_INSTANT admits 2026-13-45T25:61:61Z — a hostile value must end as
# malformed, never as an arithmetic error or a non-zero exit; review R3-4)
to_epoch() {
    date -u -d "$1" +%s 2>/dev/null
}

# snapshot_file SRC DST — copy a HOSTILE file once, so every later read sees
# the same bytes. Returns 1 (and writes nothing) unless SRC is a regular file
# that is not a symlink and holds at most MAX_EXCHANGE_BYTES. The file is
# opened first and then checked THROUGH the descriptor (device:inode equal to
# the directory entry's, regular, size) — a symlink swapped in between the
# test and the open cannot be followed. (review R3-1/R3-2, A-11)
snapshot_file() {
    local src="$1" dst="$2" entry_inode fd_inode fd_type fd_size
    [[ ! -L "$src" && -f "$src" ]] || return 1
    exec 8< "$src" || return 1
    entry_inode="$(stat -c '%d:%i' -- "$src" 2>/dev/null || true)"
    fd_inode="$(stat -L -c '%d:%i' /proc/self/fd/8 2>/dev/null || true)"
    fd_type="$(stat -L -c %F /proc/self/fd/8 2>/dev/null || true)"
    fd_size="$(stat -L -c %s /proc/self/fd/8 2>/dev/null || true)"
    if [[ -z "$entry_inode" || "$entry_inode" != "$fd_inode" || "$fd_type" != "regular file" ]] \
        || [[ ! "$fd_size" =~ $RE_INT ]] || (( fd_size > MAX_EXCHANGE_BYTES )); then
        exec 8<&-
        return 1
    fi
    head -c "$MAX_EXCHANGE_BYTES" <&8 > "$dst"
    exec 8<&-
}

# ---------------------------------------------------------------- the trust set (review A-1)
# Keys travel as TOKENS (base64 of the SPKI DER — one fixed-layout line) and
# are handed to OpenSSL as PEM again. DER is canonical, so token equality is
# key equality.

# token_pem TOKEN → the PEM of that key on stdout
token_pem() {
    printf -- '-----BEGIN PUBLIC KEY-----\n%s\n-----END PUBLIC KEY-----\n' "$1"
}

# key_token PEM-FILE → the token, provided the file holds exactly one Ed25519 public key
key_token() {
    local der
    der="$(openssl pkey -pubin -in "$1" -outform DER 2>/dev/null | base64 -w0)" || return 1
    [[ "$der" =~ $RE_KEY_TOKEN ]] || return 1
    printf '%s' "$der"
}

# pem_tokens PEM-FILE → the comma-separated token set of every PUBLIC KEY block
# in the file (1..MAX_KEYS, no duplicates). Returns 1 for anything else.
pem_tokens() {
    local file="$1" line block="" in_block=0 tmp tokens="" token n=0
    tmp="$(mktemp)"
    while IFS= read -r line || [[ -n "$line" ]]; do
        line="${line%$'\r'}"
        if [[ "$line" == "-----BEGIN PUBLIC KEY-----" ]]; then
            in_block=1; block=""; continue
        fi
        if [[ "$line" == "-----END PUBLIC KEY-----" ]]; then
            [[ $in_block -eq 1 ]] || { rm -f -- "$tmp"; return 1; }
            in_block=0
            printf -- '-----BEGIN PUBLIC KEY-----\n%s-----END PUBLIC KEY-----\n' "$block" > "$tmp"
            token="$(key_token "$tmp")" || { rm -f -- "$tmp"; return 1; }
            [[ ",$tokens," != *",$token,"* ]] || { rm -f -- "$tmp"; return 1; }
            n=$((n + 1))
            tokens="${tokens:+$tokens,}$token"
            continue
        fi
        if [[ $in_block -eq 1 ]]; then
            [[ "$line" =~ ^[A-Za-z0-9+/=]+$ ]] || { rm -f -- "$tmp"; return 1; }
            block="$block$line"$'\n'
        fi
    done < "$file"
    rm -f -- "$tmp"
    [[ $in_block -eq 0 && $n -ge 1 && $n -le $MAX_KEYS ]] || return 1
    printf '%s' "$tokens"
}

# valid_key_set TOKENS → 0 when TOKENS matches RE_KEY_SET, every token parses as an Ed25519 public key and none repeats
valid_key_set() {
    local tokens="$1" token seen="" tmp
    [[ "$tokens" =~ $RE_KEY_SET ]] || return 1
    tmp="$(mktemp)"
    local IFS=,
    for token in $tokens; do
        [[ ",$seen," != *",$token,"* ]] || { rm -f -- "$tmp"; return 1; }
        token_pem "$token" > "$tmp"
        [[ "$(key_token "$tmp" || true)" == "$token" ]] || { rm -f -- "$tmp"; return 1; }
        seen="${seen:+$seen,}$token"
    done
    rm -f -- "$tmp"
}

# verify_any MANIFEST SIG TOKENS → sets SIGNER to the token that verified; 1 when none does
verify_any() {
    local manifest="$1" sig="$2" tokens="$3" token tmp
    SIGNER=""
    tmp="$(mktemp)"
    local IFS=,
    for token in $tokens; do
        token_pem "$token" > "$tmp"
        if openssl pkeyutl -verify -pubin -inkey "$tmp" -rawin -in "$manifest" -sigfile "$sig" >/dev/null 2>&1; then
            SIGNER="$token"
            rm -f -- "$tmp"
            return 0
        fi
    done
    rm -f -- "$tmp"
    return 1
}

# pinned_ref REF SHA → REF pinned to the registry manifest digest SHA
# (`name[:tag]@sha256:SHA`). Returns 1 when REF already carries a DIFFERENT
# digest — the manifest contradicts itself. (review R3-6)
pinned_ref() {
    local ref="$1" sha="$2"
    if [[ "$ref" == *@sha256:* ]]; then
        [[ "${ref##*@sha256:}" == "$sha" ]] || return 1
        printf '%s' "$ref"
        return 0
    fi
    printf '%s@sha256:%s' "$ref" "$sha"
}

# ---------------------------------------------------------------- version_le A B  (D6, never `sort -V`)
# 0 when A ≤ B: the numeric triple decides; on an equal triple the side WITH a
# qualifier is lower (1.0.0-SNAPSHOT < 1.0.0); two qualifiers compare byte-wise.
version_le() {
    local a="$1" b="$2" a_num b_num a_q="" b_q="" i
    a_num="${a%%-*}"
    b_num="${b%%-*}"
    if [[ "$a" == *-* ]]; then a_q="${a#*-}"; fi
    if [[ "$b" == *-* ]]; then b_q="${b#*-}"; fi
    local -a an bn
    IFS=. read -r -a an <<< "$a_num"
    IFS=. read -r -a bn <<< "$b_num"
    for i in 0 1 2; do
        if (( 10#${an[i]} < 10#${bn[i]} )); then return 0; fi
        if (( 10#${an[i]} > 10#${bn[i]} )); then return 1; fi
    done
    if [[ -z "$a_q" && -z "$b_q" ]]; then return 0; fi
    if [[ -n "$a_q" && -z "$b_q" ]]; then return 0; fi
    if [[ -z "$a_q" && -n "$b_q" ]]; then return 1; fi
    [[ "$a_q" < "$b_q" || "$a_q" == "$b_q" ]]
}

# ---------------------------------------------------------------- state
# Globals a run fills in before write_state: EXCHANGE, ATTEMPT_AT, REQUESTED_AT,
# LAST_REQUESTED_AT, ATTEMPTED_VERSION, CURRENT_IMAGE, CURRENT_IMAGE_ID,
# CURRENT_VERSION, PREVIOUS_IMAGE, BACKUP_FILE, TRUSTED_KEYS, TRUSTED_KEYS_VERSION.
json_or_null() {
    if [[ -z "$1" ]]; then printf 'null'; else printf '"%s"' "$1"; fi
}

# state_json OUTCOME ROLLED_BACK MESSAGE NOW WITH_IMAGE_ID → the document on stdout.
# lastRequestedAt is carried forward from the previous state when the current
# request has no usable one (malformed) — the replay guard never forgets (R3-3).
# The updater's own copy additionally records the local image ID (R3-6) and
# the rotated trust set with the version that introduced it (A-1); the
# mirror keeps the app contract's 14-line layout.
state_json() {
    local outcome="$1" rolled_back="$2" message="$3" now="$4" with_image_id="$5"
    printf '{\n'
    printf '  "schemaVersion": 1,\n'
    printf '  "updatedAt": "%s",\n' "$now"
    printf '  "lastAttemptAt": %s,\n' "$(json_or_null "${ATTEMPT_AT:-}")"
    printf '  "lastRequestedAt": %s,\n' "$(json_or_null "${REQUESTED_AT:-${LAST_REQUESTED_AT:-}}")"
    printf '  "attemptedVersion": %s,\n' "$(json_or_null "${ATTEMPTED_VERSION:-}")"
    printf '  "outcome": "%s",\n' "$outcome"
    printf '  "rolledBack": %s,\n' "$rolled_back"
    printf '  "currentImage": "%s",\n' "${CURRENT_IMAGE:-}"
    if [[ "$with_image_id" == "1" ]]; then
        printf '  "currentImageId": %s,\n' "$(json_or_null "${CURRENT_IMAGE_ID:-}")"
        printf '  "trustedKeys": %s,\n' "$(json_or_null "${TRUSTED_KEYS:-}")"
        printf '  "trustedKeysVersion": %s,\n' "$(json_or_null "${TRUSTED_KEYS_VERSION:-}")"
    fi
    printf '  "currentVersion": %s,\n' "$(json_or_null "${CURRENT_VERSION:-}")"
    printf '  "previousImage": %s,\n' "$(json_or_null "${PREVIOUS_IMAGE:-}")"
    printf '  "backupFile": %s,\n' "$(json_or_null "${BACKUP_FILE:-}")"
    printf '  "message": %s\n' "$(json_or_null "$message")"
    printf '}\n'
}

# place_file DIR NAME MODE [STAGE] — stdin → DIR/NAME without ever opening a
# path inside DIR (review A-3): the file is built under STAGE (default DIR;
# for the app-owned exchange dir the caller passes its ROOT-OWNED parent on
# the same filesystem) via mktemp (O_EXCL) + chmod there, then rename(2)d
# onto DIR/NAME with `mv -fT` — a rename replaces the entry (a planted
# symlink included), never follows it, and refuses a directory. STAGE and
# DIR must share a device, or nothing is written (mv would fall back to a
# copy that opens the destination path).
place_file() {
    local dir="$1" name="$2" mode="$3" stage="${4:-$1}" tmp
    [[ "$(stat -c %d -- "$stage" 2>/dev/null || true)" == "$(stat -c %d -- "$dir" 2>/dev/null || echo none)" ]] || return 1
    tmp="$(mktemp -p "$stage" ".$name.XXXXXX")" || return 1
    if ! cat > "$tmp" || ! chmod "$mode" -- "$tmp"; then
        rm -f -- "$tmp"
        return 1
    fi
    if ! mv -fT -- "$tmp" "$dir/$name"; then
        rm -f -- "$tmp"
        return 1
    fi
}

# write_state OUTCOME ROLLED_BACK MESSAGE — the updater's own file first (0600), then the mirror (0644)
write_state() {
    local now
    now="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    mkdir -p -m 0700 -- "$(dirname -- "$GXP_STATE_FILE")"
    state_json "$1" "$2" "$3" "$now" 1 | place_file "$(dirname -- "$GXP_STATE_FILE")" "$(basename -- "$GXP_STATE_FILE")" 0600
    state_json "$1" "$2" "$3" "$now" 0 | place_file "$EXCHANGE" "$STATE_FILE" 0644 "$EXCHANGE_STAGE"
}

log() {
    printf 'gxp-updater: %s\n' "$*"
}

# seed_shadow VERSION IMAGE — install.sh --current-version (review A-9): a shadow
# with outcome `none` and currentVersion VERSION, so the downgrade refusal is
# armed before the first applied update. Never overwrites an existing shadow.
seed_shadow() {
    local version="$1" image="$2" now
    [[ "$version" =~ $RE_VERSION && "$image" =~ $RE_ARTIFACT ]] || return 1
    [[ ! -e "$GXP_STATE_FILE" ]] || return 1
    now="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    mkdir -p -m 0700 -- "$(dirname -- "$GXP_STATE_FILE")"
    CURRENT_VERSION="$version" CURRENT_IMAGE="$image" ATTEMPT_AT="" REQUESTED_AT="" LAST_REQUESTED_AT="" ATTEMPTED_VERSION="" \
        CURRENT_IMAGE_ID="" PREVIOUS_IMAGE="" BACKUP_FILE="" TRUSTED_KEYS="" TRUSTED_KEYS_VERSION="" \
        state_json none false "" "$now" 1 | place_file "$(dirname -- "$GXP_STATE_FILE")" "$(basename -- "$GXP_STATE_FILE")" 0600
}

# finish OUTCOME ROLLED_BACK MESSAGE — the one exit of every attempt
finish() {
    write_state "$1" "$2" "$3"
    log "outcome=$1 version=${ATTEMPTED_VERSION:-none}"
    exit 0
}

if [[ "${GXP_UPDATER_LIBRARY_ONLY:-}" == "1" ]]; then
    return 0 2>/dev/null || exit 0
fi

# ---------------------------------------------------------------- configuration checks (exit non-zero, nothing touched)
fail_config() {
    log "configuration error: $*"
    exit 2
}

[[ $EUID -eq 0 ]] || fail_config "must run as root (the volume mountpoint is root-only)"
[[ "$GXP_COMPOSE_PROJECT" =~ $RE_IDENT ]] || fail_config "GXP_COMPOSE_PROJECT"
[[ "$GXP_HEALTH_TIMEOUT" =~ $RE_INT && "$GXP_HEALTH_HOLD" =~ $RE_INT && "$GXP_KEEP_BACKUPS" =~ $RE_INT ]] || fail_config "timeouts"
[[ "$GXP_DB_USER" =~ $RE_IDENT && "$GXP_DB_NAME" =~ $RE_IDENT ]] || fail_config "database names"
[[ -f "$GXP_COMPOSE_DIR/compose.yaml" ]] || fail_config "no compose.yaml in GXP_COMPOSE_DIR"
[[ -r "$GXP_PUBLIC_KEYS" ]] || fail_config "public key set not readable"
FILE_KEYS="$(pem_tokens "$GXP_PUBLIC_KEYS")" || fail_config "public key set is not 1..$MAX_KEYS distinct Ed25519 PUBLIC KEY blocks"
[[ "$GXP_STATE_FILE" == /* && ! -L "$GXP_STATE_FILE" && ( ! -e "$GXP_STATE_FILE" || -f "$GXP_STATE_FILE" ) ]] || fail_config "GXP_STATE_FILE"
# the test seams are never honoured under systemd (review R3-5)
if [[ -n "$GXP_REHEARSAL_AFTER_DUMP" || -n "$GXP_REHEARSAL_LOCAL_IMAGE" ]]; then
    [[ -z "${INVOCATION_ID:-}" ]] || fail_config "rehearsal seams are refused under systemd"
fi
if [[ -n "$GXP_REHEARSAL_AFTER_DUMP" ]]; then
    [[ ! -L "$GXP_REHEARSAL_AFTER_DUMP" && -f "$GXP_REHEARSAL_AFTER_DUMP" ]] || fail_config "GXP_REHEARSAL_AFTER_DUMP is not a regular file"
    [[ "$(stat -c %u -- "$GXP_REHEARSAL_AFTER_DUMP")" == "0" ]] || fail_config "GXP_REHEARSAL_AFTER_DUMP must be root-owned"
    hook_mode="$(stat -c %a -- "$GXP_REHEARSAL_AFTER_DUMP")"
    [[ ! "${hook_mode: -2:1}" =~ [2367] && ! "${hook_mode: -1}" =~ [2367] ]] || fail_config "GXP_REHEARSAL_AFTER_DUMP must not be group/world-writable"
fi
for tool in docker openssl base64 flock date stat mktemp; do
    command -v -- "$tool" >/dev/null 2>&1 || fail_config "missing tool: $tool"
done

compose() {
    docker compose --project-directory "$GXP_COMPOSE_DIR" --project-name "$GXP_COMPOSE_PROJECT" "$@"
}

# ---------------------------------------------------------------- 0  one run at a time
mkdir -p -- "$(dirname -- "$GXP_LOCK_FILE")"
exec 9> "$GXP_LOCK_FILE"
if ! flock -n 9; then
    exit 0
fi

# ---------------------------------------------------------------- 1  the exchange directory
EXCHANGE="$(docker volume inspect -f '{{.Mountpoint}}' -- "${GXP_COMPOSE_PROJECT}_gxp-updates" 2>/dev/null || true)"
if [[ -z "$EXCHANGE" || -L "$EXCHANGE" || ! -d "$EXCHANGE" ]]; then
    log "volume ${GXP_COMPOSE_PROJECT}_gxp-updates not found — nothing to do"
    exit 0
fi
# the staging directory for the mirror (A-3): the volume's parent, which Docker
# owns as root — a mismatch is a configuration error, never a reason to open
# a path inside the app-owned directory
EXCHANGE_STAGE="$(dirname -- "$EXCHANGE")"
[[ ! -L "$EXCHANGE_STAGE" && -d "$EXCHANGE_STAGE" && "$(stat -c %u -- "$EXCHANGE_STAGE")" == "0" ]] || fail_config "exchange parent directory is not a root-owned directory"
stage_mode="$(stat -c %a -- "$EXCHANGE_STAGE")"
[[ ! "${stage_mode: -2:1}" =~ [2367] && ! "${stage_mode: -1}" =~ [2367] ]] || fail_config "exchange parent directory is group/world-writable"
[[ "$(stat -c %d -- "$EXCHANGE_STAGE")" == "$(stat -c %d -- "$EXCHANGE")" ]] || fail_config "exchange directory is a separate mount — cannot rename into it"
DESIRED="$EXCHANGE/$DESIRED_FILE"
if [[ ! -e "$DESIRED" && ! -L "$DESIRED" ]]; then
    exit 0
fi

WORK="$(mktemp -d)"
trap 'rm -rf -- "$WORK"' EXIT

# what the updater's OWN previous state says (root 0600 — the exchange mirror is
# never read back: the app could have replaced it; review R3-3). Tolerant: an
# unreadable state is "no state".
LAST_REQUESTED_AT=""
CURRENT_IMAGE=""
CURRENT_IMAGE_ID=""
CURRENT_VERSION=""
PREVIOUS_IMAGE=""
TRUSTED_KEYS=""
TRUSTED_KEYS_VERSION=""
if [[ -f "$GXP_STATE_FILE" ]]; then
    LAST_REQUESTED_AT="$(optional_string_field "$GXP_STATE_FILE" lastRequestedAt 2>/dev/null || true)"
    [[ "$LAST_REQUESTED_AT" =~ $RE_INSTANT ]] && to_epoch "$LAST_REQUESTED_AT" >/dev/null || LAST_REQUESTED_AT=""
    CURRENT_VERSION="$(optional_string_field "$GXP_STATE_FILE" currentVersion 2>/dev/null || true)"
    [[ "$CURRENT_VERSION" =~ $RE_VERSION ]] || CURRENT_VERSION=""
    PREVIOUS_IMAGE="$(optional_string_field "$GXP_STATE_FILE" previousImage 2>/dev/null || true)"
    [[ "$PREVIOUS_IMAGE" =~ $RE_ARTIFACT ]] || PREVIOUS_IMAGE=""
    # the rotated trust set (A-1): both lines or neither; an unreadable pair is "no rotation" (defensive — root wrote it)
    TRUSTED_KEYS="$(optional_string_field "$GXP_STATE_FILE" trustedKeys 2>/dev/null || true)"
    TRUSTED_KEYS_VERSION="$(optional_string_field "$GXP_STATE_FILE" trustedKeysVersion 2>/dev/null || true)"
    if [[ -z "$TRUSTED_KEYS" || -z "$TRUSTED_KEYS_VERSION" ]] || ! [[ "$TRUSTED_KEYS_VERSION" =~ $RE_VERSION ]] \
        || ! valid_key_set "$TRUSTED_KEYS"; then
        TRUSTED_KEYS=""
        TRUSTED_KEYS_VERSION=""
    fi
fi
# the effective trust set: a rotation the shadow remembers beats the installed file
# (a retired key stays retired even after install.sh copied an older resource again)
EFFECTIVE_KEYS="${TRUSTED_KEYS:-$FILE_KEYS}"

# ---------------------------------------------------------------- 2  the desired file (hostile), read exactly once
ATTEMPT_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
REQUESTED_AT=""
ATTEMPTED_VERSION=""

# the effective image right now = what a rollback returns to; read before any state is written
CURRENT_IMAGE="$(compose config 2>/dev/null | awk '/^  app:$/ { in_app = 1; next } /^  [a-z]/ { in_app = 0 } in_app && /^    image: / { sub(/^    image: /, ""); gsub(/^["'"'"']|["'"'"']$/, ""); print; exit }' || true)"
[[ "$CURRENT_IMAGE" =~ $RE_ARTIFACT ]] || fail_config "cannot read the app image from compose"

SNAPSHOT="$WORK/desired.json"
if ! snapshot_file "$DESIRED" "$SNAPSHOT"; then
    finish malformed false ""
fi

# the replay guard first, so an already-handled request never rewrites the state
REQUESTED_AT="$(checked "$SNAPSHOT" requestedAt "$RE_INSTANT" || true)"
REQUESTED_EPOCH=""
if [[ -n "$REQUESTED_AT" ]]; then
    REQUESTED_EPOCH="$(to_epoch "$REQUESTED_AT")" || REQUESTED_AT=""
fi
if [[ -n "$REQUESTED_AT" && -n "$LAST_REQUESTED_AT" ]]; then
    LAST_EPOCH="$(to_epoch "$LAST_REQUESTED_AT")" || LAST_EPOCH=0
    if (( REQUESTED_EPOCH <= LAST_EPOCH )); then
        exit 0
    fi
fi

ATTEMPTED_VERSION="$(checked "$SNAPSHOT" version "$RE_VERSION" || true)"
desired_ok=1
[[ "$(line_count "$SNAPSHOT")" == "12" ]] || desired_ok=0
[[ "$(number_field "$SNAPSHOT" schemaVersion || true)" == "1" ]] || desired_ok=0
[[ -n "$REQUESTED_AT" && -n "$ATTEMPTED_VERSION" ]] || desired_ok=0
checked "$SNAPSHOT" artifactRef "$RE_ARTIFACT" >/dev/null || desired_ok=0
checked "$SNAPSHOT" sha256 "$RE_SHA256" >/dev/null || desired_ok=0
REQUESTED_BY="$(checked "$SNAPSHOT" requestedBy "$RE_CODE" || true)"
[[ "$REQUESTED_BY" == "auto" || "$REQUESTED_BY" == "manual" ]] || desired_ok=0
APPLY_NOT_BEFORE="$(checked "$SNAPSHOT" applyNotBefore "$RE_INSTANT" || true)"
NOT_BEFORE_EPOCH=""
if [[ -n "$APPLY_NOT_BEFORE" ]]; then
    NOT_BEFORE_EPOCH="$(to_epoch "$APPLY_NOT_BEFORE")" || desired_ok=0
else
    desired_ok=0
fi
APPLY_WINDOW="$(number_field "$SNAPSHOT" applyWindowSeconds || true)"
[[ -n "$APPLY_WINDOW" && "$APPLY_WINDOW" -ge 1 && "$APPLY_WINDOW" -le 86400 ]] || desired_ok=0
MANIFEST_B64="$(checked "$SNAPSHOT" manifest "$RE_BASE64" || true)"
SIGNATURE_B64="$(checked "$SNAPSHOT" signature "$RE_BASE64" || true)"
[[ -n "$MANIFEST_B64" && -n "$SIGNATURE_B64" ]] || desired_ok=0
if [[ $desired_ok -ne 1 ]]; then
    finish malformed false ""
fi

# the window (REQ-723): outside it the app's reconcile job moves the time
NOW_EPOCH="$(date -u +%s)"
if (( NOW_EPOCH < NOT_BEFORE_EPOCH || NOW_EPOCH >= NOT_BEFORE_EPOCH + APPLY_WINDOW )); then
    exit 0
fi

# ---------------------------------------------------------------- 3  verify with OpenSSL (the trust root)
if ! printf '%s' "$MANIFEST_B64" | base64 -d > "$WORK/manifest.json" 2>/dev/null; then
    finish malformed false ""
fi
if ! printf '%s' "$SIGNATURE_B64" | base64 -d > "$WORK/manifest.sig" 2>/dev/null; then
    finish malformed false ""
fi
if [[ "$(stat -c %s -- "$WORK/manifest.sig")" != "64" ]]; then
    finish invalid-signature false ""
fi
SIGNER=""
if ! verify_any "$WORK/manifest.json" "$WORK/manifest.sig" "$EFFECTIVE_KEYS"; then
    finish invalid-signature false ""
fi

# ---------------------------------------------------------------- 4  the verified manifest is the only source of truth
manifest_ok=1
lines="$(line_count "$WORK/manifest.json")"
[[ "$lines" == "8" || "$lines" == "9" || "$lines" == "10" ]] || manifest_ok=0
[[ "$(number_field "$WORK/manifest.json" schemaVersion || true)" == "1" ]] || manifest_ok=0
checked "$WORK/manifest.json" channel "$RE_CHANNEL" >/dev/null || manifest_ok=0
VERSION="$(checked "$WORK/manifest.json" version "$RE_VERSION" || true)"
ARTIFACT="$(checked "$WORK/manifest.json" artifactRef "$RE_ARTIFACT" || true)"
SHA="$(checked "$WORK/manifest.json" sha256 "$RE_SHA256" || true)"
checked "$WORK/manifest.json" publishedAt "$RE_INSTANT" >/dev/null || manifest_ok=0
# the optional lines: minimumFromVersion, trustedKeys — exactly (lines − 8) of them must be present
optional_lines=0
if raw_field "$WORK/manifest.json" minimumFromVersion >/dev/null 2>&1; then
    optional_lines=$((optional_lines + 1))
    checked "$WORK/manifest.json" minimumFromVersion "$RE_VERSION" >/dev/null || manifest_ok=0
fi
NEW_KEYS=""
if raw_field "$WORK/manifest.json" trustedKeys >/dev/null 2>&1; then
    optional_lines=$((optional_lines + 1))
    NEW_KEYS="$(checked "$WORK/manifest.json" trustedKeys "$RE_KEY_SET" || true)"
    # a set must parse, and must contain the key that signed this very manifest (retiring one's own signer is a mistake)
    [[ -n "$NEW_KEYS" ]] && valid_key_set "$NEW_KEYS" && [[ ",$NEW_KEYS," == *",$SIGNER,"* ]] || manifest_ok=0
fi
[[ "$optional_lines" == "$((lines - 8))" ]] || manifest_ok=0
[[ -n "$VERSION" && -n "$ARTIFACT" && -n "$SHA" ]] || manifest_ok=0
if [[ $manifest_ok -ne 1 ]]; then
    finish malformed false ""
fi
ATTEMPTED_VERSION="$VERSION"
if [[ -n "$CURRENT_VERSION" ]] && version_le "$VERSION" "$CURRENT_VERSION"; then
    finish downgrade-refused false ""
fi
# rotation (A-1): a signed set NEWER than the remembered one replaces it — persisted with the next state write,
# whatever the outcome of the apply (trust is not tied to the swap succeeding); an older set is ignored (replay)
if [[ -n "$NEW_KEYS" ]] && { [[ -z "$TRUSTED_KEYS_VERSION" ]] || ! version_le "$VERSION" "$TRUSTED_KEYS_VERSION"; }; then
    TRUSTED_KEYS="$NEW_KEYS"
    TRUSTED_KEYS_VERSION="$VERSION"
    log "trust set rotated by version=$VERSION keys=$(( $(printf '%s' "$NEW_KEYS" | tr -cd ',' | wc -c) + 1 ))"
fi
# a ref that already carries a digest must carry THIS one
PINNED="$(pinned_ref "$ARTIFACT" "$SHA")" || finish artifact-mismatch false ""

# ---------------------------------------------------------------- 5  in-progress, before any side effect
PREVIOUS_IMAGE="$CURRENT_IMAGE"
BACKUP_FILE=""
write_state in-progress false ""
log "applying version=$VERSION"

# ---------------------------------------------------------------- 6  dump first
mkdir -p -m 0700 -- "$GXP_BACKUP_DIR"
BACKUP_FILE="pre-update-$VERSION-$(date -u +%Y%m%dT%H%M%SZ).dump"
[[ "$BACKUP_FILE" =~ $RE_BACKUP ]] || finish backup-failed false ""
dump_args=(exec -T db pg_dump -U "$GXP_DB_USER" -d "$GXP_DB_NAME" --format=custom)
if ! compose "${dump_args[@]}" > "$GXP_BACKUP_DIR/$BACKUP_FILE" 2>/dev/null || [[ ! -s "$GXP_BACKUP_DIR/$BACKUP_FILE" ]]; then
    rm -f -- "$GXP_BACKUP_DIR/$BACKUP_FILE"
    BACKUP_FILE=""
    finish backup-failed false ""
fi
chmod 0600 -- "$GXP_BACKUP_DIR/$BACKUP_FILE"
# prune to the newest GXP_KEEP_BACKUPS (names are our own, matched by RE_BACKUP)
kept=0
while IFS= read -r old; do
    kept=$((kept + 1))
    if (( kept > GXP_KEEP_BACKUPS )); then
        rm -f -- "$old"
    fi
done < <(find "$GXP_BACKUP_DIR" -maxdepth 1 -type f -name 'pre-update-*.dump' -printf '%T@ %p\n' | sort -rn | cut -d' ' -f2-)
if [[ -n "$GXP_REHEARSAL_AFTER_DUMP" ]]; then
    bash -- "$GXP_REHEARSAL_AFTER_DUMP" || true
fi

# ---------------------------------------------------------------- 7  pull by digest and pin
# Production: the manifest's sha256 is the REGISTRY MANIFEST DIGEST; pulling
# `ref@sha256:<digest>` makes Docker itself refuse anything else, and the
# digest-pinned ref is what .env carries from now on. The local image ID
# (config digest under graphdriver, manifest digest under containerd — the
# store decides) is recorded in the updater's own state, never compared.
if [[ -n "$GXP_REHEARSAL_LOCAL_IMAGE" ]]; then
    # REHEARSAL ONLY (refused under systemd): a local tag has no registry
    # digest, so sha256 is compared against the local image ID instead.
    PINNED="$ARTIFACT"
    docker image inspect -- "$PINNED" >/dev/null 2>&1 || finish pull-failed false ""
    IMAGE_ID="$(docker image inspect -f '{{.Id}}' -- "$PINNED" 2>/dev/null || true)"
    [[ "$IMAGE_ID" == "sha256:$SHA" ]] || finish artifact-mismatch false ""
else
    if ! docker pull -q -- "$PINNED" >/dev/null 2>&1; then
        # offline: the digest-pinned ref may already be known locally
        docker image inspect -- "$PINNED" >/dev/null 2>&1 || finish pull-failed false ""
    fi
    IMAGE_ID="$(docker image inspect -f '{{.Id}}' -- "$PINNED" 2>/dev/null || true)"
    [[ "$IMAGE_ID" =~ ^sha256:[0-9a-f]{64}$ ]] || finish artifact-mismatch false ""
fi
log "image id=$IMAGE_ID"

# ---------------------------------------------------------------- 8  swap
# set_image REF — rewrite/append the GXP_IMAGE= line of the compose .env (temp + mv, mode AND owner kept)
set_image() {
    local ref="$1" env_file="$GXP_COMPOSE_DIR/.env" tmp line
    tmp="$env_file.tmp"
    (
        umask 077
        : > "$tmp"
    )
    if [[ -f "$env_file" ]]; then
        chmod --reference="$env_file" -- "$tmp"
        chown --reference="$env_file" -- "$tmp"
        while IFS= read -r line || [[ -n "$line" ]]; do
            if [[ "$line" != GXP_IMAGE=* ]]; then
                printf '%s\n' "$line" >> "$tmp"
            fi
        done < "$env_file"
    fi
    printf 'GXP_IMAGE=%s\n' "$ref" >> "$tmp"
    mv -f -- "$tmp" "$env_file"
}

start_app() {
    compose up -d --no-build --pull never app >/dev/null 2>&1
}

app_container() {
    compose ps -q app 2>/dev/null | head -n 1
}

# healthy — sets HEALTH_MESSAGE on failure (health-timeout | flyway-failed | container-restarting)
healthy() {
    local deadline=$((SECONDS + GXP_HEALTH_TIMEOUT)) cid row
    while (( SECONDS < deadline )); do
        cid="$(app_container)"
        if [[ -n "$cid" && "$(docker inspect -f '{{.State.Restarting}}' -- "$cid" 2>/dev/null || true)" == "true" ]]; then
            HEALTH_MESSAGE=container-restarting
            return 1
        fi
        row="$(compose exec -T db psql -U "$GXP_DB_USER" -d "$GXP_DB_NAME" -tAc \
            'SELECT success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1' 2>/dev/null || true)"
        if [[ "$row" == "f" ]]; then
            HEALTH_MESSAGE=flyway-failed
            return 1
        fi
        if [[ "$row" == "t" ]] && compose exec -T app curl -fsk https://localhost:8443/api/system/ready >/dev/null 2>&1; then
            return 0
        fi
        sleep 5
    done
    HEALTH_MESSAGE=health-timeout
    return 1
}

# healthy_and_held — the check, the hold, the check again (REQ-721)
healthy_and_held() {
    healthy || return 1
    sleep "$GXP_HEALTH_HOLD"
    healthy
}

HEALTH_MESSAGE=""
set_image "$PINNED"
start_app || true                                                      # restart #1

# ---------------------------------------------------------------- 9  health
if healthy_and_held; then
    CURRENT_IMAGE="$PINNED"
    CURRENT_IMAGE_ID="$IMAGE_ID"
    CURRENT_VERSION="$VERSION"
    finish applied false ""
fi
FAILURE="$HEALTH_MESSAGE"
log "new image unhealthy ($FAILURE), rolling back"

# ---------------------------------------------------------------- 10  rollback to the previous image
set_image "$PREVIOUS_IMAGE"
start_app || true                                                      # restart #2
if healthy_and_held; then
    finish rolled-back true "$FAILURE"
fi
log "previous image unhealthy ($HEALTH_MESSAGE), restoring the dump"

# ---------------------------------------------------------------- 11  restore, then the previous image again
compose stop app >/dev/null 2>&1 || true
# Objects a SUCCEEDED new migration created are not in the dump, so `--clean`
# would leave them behind and the next attempt of a fixed release fails with
# "already exists" (review A-6). Wipe `public` (the whole database: no
# migration creates another schema or an extension) — but only for a dump
# pg_restore can read; an unreadable dump is not one to wipe the schema for.
restore_args=(exec -T db pg_restore -U "$GXP_DB_USER" -d "$GXP_DB_NAME")
if compose exec -T db pg_restore --list < "$GXP_BACKUP_DIR/$BACKUP_FILE" >/dev/null 2>&1; then
    wipe_rc=0
    compose exec -T db psql -U "$GXP_DB_USER" -d "$GXP_DB_NAME" -v ON_ERROR_STOP=1 -q \
        -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public; ALTER SCHEMA public OWNER TO $GXP_DB_USER;" >/dev/null 2>&1 || wipe_rc=$?
    log "schema wipe exit=$wipe_rc"
else
    log "dump not listable, restoring without a schema wipe"
    restore_args+=(--clean --if-exists)
fi
restore_rc=0
compose "${restore_args[@]}" < "$GXP_BACKUP_DIR/$BACKUP_FILE" >/dev/null 2>&1 || restore_rc=$?
log "pg_restore exit=$restore_rc"    # a number only; health decides
start_app || true
if healthy_and_held; then
    finish rolled-back-with-restore true "$FAILURE"
fi
finish rollback-failed true restore-failed
