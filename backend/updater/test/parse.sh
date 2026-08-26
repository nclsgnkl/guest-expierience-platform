#!/usr/bin/env bash
# parse.sh — the updater's readers against the SHARED fixture set
# (update-channel T-15, REQ-715/716/718, D6): the same files
# ExchangeFilesTest asserts from the JVM side. No Docker, no root.
#
#   bash backend/updater/test/parse.sh
set -euo pipefail
export LC_ALL=C

HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
FIXTURES="$HERE/fixtures"
TEST_KEYS="$HERE/../../app/src/test/resources/update"
TEST_PUBLIC_KEY="$TEST_KEYS/test-signing-public-key.pem"
TEST_PUBLIC_KEY_2="$TEST_KEYS/test-signing-public-key-2.pem"
TEST_PUBLIC_SET="$TEST_KEYS/test-signing-public-keys.pem"
PRODUCTION_SET="$HERE/../../app/src/main/resources/update/signing-public-keys.pem"
WORK="$(mktemp -d)"
trap 'rm -rf -- "$WORK"' EXIT

# the readers only — the guard returns before any configuration check
GXP_UPDATER_LIBRARY_ONLY=1
# shellcheck source=../gxp-updater.sh
source "$HERE/../gxp-updater.sh"

failures=0
checks=0
assert_eq() { # LABEL EXPECTED ACTUAL
    checks=$((checks + 1))
    if [[ "$2" != "$3" ]]; then
        printf 'FAIL %s: expected [%s] got [%s]\n' "$1" "$2" "$3"
        failures=$((failures + 1))
    fi
}
assert_ok() { # LABEL COMMAND...
    checks=$((checks + 1))
    if ! "${@:2}" >/dev/null 2>&1; then
        printf 'FAIL %s: expected success\n' "$1"
        failures=$((failures + 1))
    fi
}
assert_fails() { # LABEL COMMAND...
    checks=$((checks + 1))
    if "${@:2}" >/dev/null 2>&1; then
        printf 'FAIL %s: expected failure\n' "$1"
        failures=$((failures + 1))
    fi
}

# ---------------------------------------------------------------- desired-update.json
D="$FIXTURES/desired-update.json"
assert_eq "desired line count" 12 "$(line_count "$D")"
assert_eq "desired schemaVersion" 1 "$(number_field "$D" schemaVersion)"
assert_eq "desired version" "1.0.1" "$(checked "$D" version "$RE_VERSION")"
assert_eq "desired artifactRef" "ghcr.io/example/gxp-backend:1.0.1" "$(checked "$D" artifactRef "$RE_ARTIFACT")"
assert_eq "desired sha256" "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef" "$(checked "$D" sha256 "$RE_SHA256")"
assert_eq "desired requestedAt" "2026-09-01T10:00:00Z" "$(checked "$D" requestedAt "$RE_INSTANT")"
assert_eq "desired requestedBy" "auto" "$(checked "$D" requestedBy "$RE_CODE")"
assert_eq "desired applyNotBefore" "2026-09-02T02:00:00Z" "$(checked "$D" applyNotBefore "$RE_INSTANT")"
assert_eq "desired applyWindowSeconds" 7200 "$(number_field "$D" applyWindowSeconds)"
MANIFEST_B64="$(checked "$D" manifest "$RE_BASE64")"
SIGNATURE_B64="$(checked "$D" signature "$RE_BASE64")"
assert_eq "desired manifest = base64 of fixtures/manifest.json" "$(base64 -w0 < "$FIXTURES/manifest.json")" "$MANIFEST_B64"
assert_eq "desired signature = fixtures/manifest.json.sig" "$(cat "$FIXTURES/manifest.json.sig")" "$SIGNATURE_B64"
assert_fails "desired: absent key" raw_field "$D" nope
assert_fails "desired: a number is not a string" string_field "$D" schemaVersion
assert_fails "desired: a string is not a number" number_field "$D" version

# ---------------------------------------------------------------- the decoded manifest
printf '%s' "$MANIFEST_B64" | base64 -d > "$WORK/manifest.json"
cmp -s -- "$WORK/manifest.json" "$FIXTURES/manifest.json" && decoded=same || decoded=different
assert_eq "decoded manifest byte-equals the fixture" same "$decoded"
M="$WORK/manifest.json"
assert_eq "manifest line count" 8 "$(line_count "$M")"
assert_eq "manifest schemaVersion" 1 "$(number_field "$M" schemaVersion)"
assert_eq "manifest channel" stable "$(checked "$M" channel "$RE_CHANNEL")"
assert_eq "manifest version" "1.0.1" "$(checked "$M" version "$RE_VERSION")"
assert_eq "manifest artifactRef" "ghcr.io/example/gxp-backend:1.0.1" "$(checked "$M" artifactRef "$RE_ARTIFACT")"
assert_eq "manifest sha256 (the registry manifest digest, R3-6)" "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef" "$(checked "$M" sha256 "$RE_SHA256")"
assert_eq "manifest publishedAt" "2026-09-01T10:00:00Z" "$(checked "$M" publishedAt "$RE_INSTANT")"
assert_fails "manifest: no minimumFromVersion in the fixture" raw_field "$M" minimumFromVersion

# ---------------------------------------------------------------- REQ-718: the informational copies are never the source
sed 's|^  "artifactRef": ".*",$|  "artifactRef": "evil",|' "$D" > "$WORK/desired-evil.json"
assert_eq "evil desired still parses its copy" evil "$(string_field "$WORK/desired-evil.json" artifactRef)"
printf '%s' "$(checked "$WORK/desired-evil.json" manifest "$RE_BASE64")" | base64 -d > "$WORK/manifest-from-evil.json"
assert_eq "…but the manifest inside names the real ref" "ghcr.io/example/gxp-backend:1.0.1" \
    "$(checked "$WORK/manifest-from-evil.json" artifactRef "$RE_ARTIFACT")"

# a validly signed manifest with a shell-shaped version is malformed before any command
sed 's|^  "version": "1.0.1",$|  "version": "1.1.0; rm -rf /",|' "$M" > "$WORK/manifest-shell.json"
assert_eq "shell version is still the raw string" "1.1.0; rm -rf /" "$(string_field "$WORK/manifest-shell.json" version)"
assert_fails "shell version fails the version regex (malformed)" checked "$WORK/manifest-shell.json" version "$RE_VERSION"
# a duplicated key is malformed, a quote or backslash inside a value is malformed, an extra line changes the count
{ cat "$M"; printf '  "version": "9.9.9"\n'; } > "$WORK/manifest-dup.json"
assert_fails "duplicated key" raw_field "$WORK/manifest-dup.json" version
sed 's|^  "channel": "stable",$|  "channel": "sta\\"ble",|' "$M" > "$WORK/manifest-quote.json"
assert_fails "quote inside a value" string_field "$WORK/manifest-quote.json" channel
assert_eq "extra line is counted" 9 "$(line_count "$WORK/manifest-dup.json")"

# ---------------------------------------------------------------- state-*.json — every outcome of Design §2 (+ in-progress, + a future schema)
expected_states="none in-progress applied rolled-back rolled-back-with-restore rollback-failed backup-failed pull-failed artifact-mismatch downgrade-refused invalid-signature malformed"
count=0
for outcome in $expected_states; do
    S="$FIXTURES/state-$outcome.json"
    count=$((count + 1))
    [[ -f "$S" ]] || { printf 'FAIL missing fixture %s\n' "$S"; failures=$((failures + 1)); continue; }
    assert_eq "state-$outcome outcome" "$outcome" "$(checked "$S" outcome "$RE_CODE")"
    assert_eq "state-$outcome schemaVersion" 1 "$(number_field "$S" schemaVersion)"
    assert_ok "state-$outcome updatedAt" checked "$S" updatedAt "$RE_INSTANT"
    assert_ok "state-$outcome currentImage" checked "$S" currentImage "$RE_ARTIFACT"
    assert_ok "state-$outcome rolledBack" bool_field "$S" rolledBack
    for optional in lastAttemptAt lastRequestedAt attemptedVersion currentVersion previousImage backupFile message; do
        assert_ok "state-$outcome $optional readable" optional_string_field "$S" "$optional"
    done
done
assert_eq "twelve outcome fixtures" 12 "$count"
assert_eq "state-future-schema still reads the known keys" 2 "$(number_field "$FIXTURES/state-future-schema.json" schemaVersion)"
assert_ok "state-future-schema outcome" checked "$FIXTURES/state-future-schema.json" outcome "$RE_CODE"
assert_eq "state-applied lastRequestedAt" "2026-09-01T10:00:00Z" "$(optional_string_field "$FIXTURES/state-applied.json" lastRequestedAt)"
assert_eq "state-applied currentVersion" "1.0.1" "$(optional_string_field "$FIXTURES/state-applied.json" currentVersion)"
assert_eq "state-applied backupFile" "pre-update-1.0.1-20260902T020004Z.dump" "$(checked "$FIXTURES/state-applied.json" backupFile "$RE_BACKUP")"
assert_eq "state-applied message is null" "" "$(optional_string_field "$FIXTURES/state-applied.json" message)"
assert_eq "state-none lastRequestedAt is null" "" "$(optional_string_field "$FIXTURES/state-none.json" lastRequestedAt)"
assert_eq "state-rolled-back-with-restore message" flyway-failed "$(optional_string_field "$FIXTURES/state-rolled-back-with-restore.json" message)"
assert_eq "state-rolled-back-with-restore rolledBack" true "$(bool_field "$FIXTURES/state-rolled-back-with-restore.json" rolledBack)"

# ---------------------------------------------------------------- write_state round-trips through the same readers
EXCHANGE="$WORK/volume/_data"        # shaped like a docker volume: root-owned parent, app-owned _data
EXCHANGE_STAGE="$WORK/volume"
GXP_STATE_FILE="$WORK/shadow/state.json"
mkdir -p -- "$EXCHANGE"
ATTEMPT_AT="2026-09-02T02:00:04Z" REQUESTED_AT="2026-09-01T10:00:00Z" ATTEMPTED_VERSION="1.0.1"
CURRENT_IMAGE="ghcr.io/example/gxp-backend:1.0.1" CURRENT_VERSION="1.0.1" PREVIOUS_IMAGE="gxp-backend:1.0.0-SNAPSHOT"
BACKUP_FILE="pre-update-1.0.1-20260902T020004Z.dump"
write_state applied false ""
W="$EXCHANGE/state.json"
assert_eq "written state: 14 lines" 14 "$(line_count "$W")"
assert_eq "written state mode 0644" 644 "$(stat -c %a -- "$W")"
assert_eq "no leftover temp file in the exchange dir" "state.json" "$(ls -A -- "$EXCHANGE" | tr '\n' ' ' | sed 's/ $//')"
assert_eq "no leftover temp file in the staging dir either" "_data" "$(ls -A -- "$EXCHANGE_STAGE" | tr '\n' ' ' | sed 's/ $//')"
assert_eq "written state outcome" applied "$(checked "$W" outcome "$RE_CODE")"
assert_eq "written state message null" "" "$(optional_string_field "$W" message)"
assert_eq "written state backupFile" "$BACKUP_FILE" "$(checked "$W" backupFile "$RE_BACKUP")"
sed '/^  "updatedAt": /d' "$W" > "$WORK/written-minus-time.json"
sed '/^  "updatedAt": /d' "$FIXTURES/state-applied.json" > "$WORK/fixture-minus-time.json"
cmp -s -- "$WORK/written-minus-time.json" "$WORK/fixture-minus-time.json" && same=same || same=different
assert_eq "written state byte-equals state-applied.json (except updatedAt)" same "$same"

# ---------------------------------------------------------------- the updater's own state (review R3-3/R3-6): 0600, one extra line, otherwise the mirror
S="$GXP_STATE_FILE"
assert_eq "shadow state mode 0600" 600 "$(stat -c %a -- "$S")"
assert_eq "shadow state dir mode 0700" 700 "$(stat -c %a -- "$WORK/shadow")"
assert_eq "shadow state: 17 lines (A-1: + trustedKeys, trustedKeysVersion)" 17 "$(line_count "$S")"
assert_eq "shadow trustedKeys is null before any rotation" "" "$(optional_string_field "$S" trustedKeys)"
assert_eq "shadow trustedKeysVersion is null before any rotation" "" "$(optional_string_field "$S" trustedKeysVersion)"
assert_eq "shadow currentImageId is null when unknown" "" "$(optional_string_field "$S" currentImageId)"
assert_eq "mirror = shadow minus currentImageId/trustedKeys/trustedKeysVersion" "$(cat "$W")" "$(sed '/^  "currentImageId": \|^  "trustedKeys": \|^  "trustedKeysVersion": /d' "$S")"
CURRENT_IMAGE_ID="sha256:$(printf 'a%.0s' {1..64})"
write_state applied false ""
assert_eq "shadow records the local image ID" "$CURRENT_IMAGE_ID" "$(optional_string_field "$S" currentImageId)"
assert_fails "the mirror never carries it" raw_field "$W" currentImageId
TRUSTED_KEYS="$(pem_tokens "$TEST_PUBLIC_KEY_2")" TRUSTED_KEYS_VERSION="1.0.2"
write_state applied false ""
assert_eq "shadow records the rotated set" "$(pem_tokens "$TEST_PUBLIC_KEY_2")" "$(optional_string_field "$S" trustedKeys)"
assert_eq "shadow records the set's version" "1.0.2" "$(optional_string_field "$S" trustedKeysVersion)"
assert_fails "the mirror never carries the set" raw_field "$W" trustedKeys
assert_eq "mirror still 14 lines" 14 "$(line_count "$W")"
TRUSTED_KEYS="" TRUSTED_KEYS_VERSION=""
# lastRequestedAt is carried forward when the current request has none (malformed)
LAST_REQUESTED_AT="2026-09-01T10:00:00Z" REQUESTED_AT=""
write_state malformed false ""
assert_eq "malformed keeps the previous lastRequestedAt (shadow)" "2026-09-01T10:00:00Z" "$(optional_string_field "$S" lastRequestedAt)"
assert_eq "malformed keeps the previous lastRequestedAt (mirror)" "2026-09-01T10:00:00Z" "$(optional_string_field "$W" lastRequestedAt)"
REQUESTED_AT="2026-09-01T11:00:00Z"
write_state malformed false ""
assert_eq "a newer request replaces it" "2026-09-01T11:00:00Z" "$(optional_string_field "$S" lastRequestedAt)"
LAST_REQUESTED_AT=""

# ---------------------------------------------------------------- review R3-1: root never follows a symlink planted in the exchange dir
CANARY="$WORK/canary"
printf 'untouched\n' > "$CANARY"
chmod 0600 -- "$CANARY"
rm -f -- "$EXCHANGE/state.json"
ln -s -- "$CANARY" "$EXCHANGE/.state.json.tmp"     # the old predictable temp name
ln -s -- "$CANARY" "$EXCHANGE/state.json"          # and the destination itself
write_state applied false ""
assert_eq "canary content untouched (temp-name symlink)" "untouched" "$(cat "$CANARY")"
assert_eq "canary mode untouched" 600 "$(stat -c %a -- "$CANARY")"
assert_eq "planted temp-name symlink still points at the canary" "$CANARY" "$(readlink -- "$EXCHANGE/.state.json.tmp")"
[[ -L "$EXCHANGE/state.json" ]] && dest=symlink || dest=regular
assert_eq "state.json is now a regular file (rename replaced the symlink entry)" regular "$dest"
assert_eq "state.json holds the state, not the canary" applied "$(checked "$EXCHANGE/state.json" outcome "$RE_CODE")"
rm -f -- "$EXCHANGE/.state.json.tmp"

# ---------------------------------------------------------------- review A-3: the race window after mktemp — no path inside DIR is ever opened
# (1) by construction: place_file's body contains no redirection or mktemp into "$dir"
body="$(sed -n '/^place_file() {/,/^}/p' "$HERE/../gxp-updater.sh")"
assert_fails "place_file never mktemps inside \$dir" bash -c 'grep -q "mktemp -p \"\$dir\"" <<< "$1"' _ "$body"
assert_fails "place_file never redirects into \$dir" bash -c 'grep -qE "> *\"?\\\$dir/" <<< "$1"' _ "$body"
assert_ok "place_file renames with -T (a directory at the destination is refused, never entered)" bash -c 'grep -q "mv -fT" <<< "$1"' _ "$body"
# (2) an attacker who LEARNS the temp name: a mktemp wrapper plants a symlink at DIR/<that name> right after the file is created
mktemp() { local f; f="$(command mktemp "$@")"; ln -s -- "$CANARY" "$EXCHANGE/$(basename -- "$f")"; printf '%s' "$f"; }
write_state applied false ""
unset -f mktemp
assert_eq "canary untouched although the temp NAME was known and planted (A-3)" "untouched 600" "$(cat "$CANARY") $(stat -c %a -- "$CANARY")"
assert_eq "state.json holds the state" applied "$(checked "$EXCHANGE/state.json" outcome "$RE_CODE")"
planted="$(find "$EXCHANGE" -maxdepth 1 -type l -name '.state.json.*' | wc -l)"
assert_eq "both planted links (shadow + mirror temp names) are still links in the app's dir — never followed, never written through" 2 "$planted"
find "$EXCHANGE" -maxdepth 1 -type l -name '.state.json.*' -delete
# (3) a directory planted at the destination → refused, nothing inside it
rm -f -- "$EXCHANGE/state.json"; mkdir -- "$EXCHANGE/state.json"
assert_fails "a directory at the destination is refused" write_state applied false ""
assert_eq "…and nothing was moved into it" "" "$(ls -A -- "$EXCHANGE/state.json")"
rmdir -- "$EXCHANGE/state.json"
assert_eq "…and no temp file was left in the staging dir" "_data" "$(ls -A -- "$EXCHANGE_STAGE" | tr '\n' ' ' | sed 's/ $//')"
# (4) STAGE on another device than DIR → nothing written (mv would copy through the destination path)
mkdir -p -- "$WORK/other"
assert_fails "place_file refuses a stage on another filesystem" bash -c 'printf x | place_file "$1" f 0644 /dev/shm' _ "$WORK/other"
[[ -e "$WORK/other/f" ]] && wrote=yes || wrote=no
assert_eq "…nothing written" no "$wrote"

# ---------------------------------------------------------------- review R3-1/R3-2: the desired file is snapshotted, never followed, capped
ln -s -- "$CANARY" "$EXCHANGE/desired-update.json"
assert_fails "snapshot refuses a symlink" snapshot_file "$EXCHANGE/desired-update.json" "$WORK/snap.json"
[[ -e "$WORK/snap.json" ]] && wrote=yes || wrote=no
assert_eq "…and writes nothing" no "$wrote"
rm -f -- "$EXCHANGE/desired-update.json"
mkdir -- "$EXCHANGE/desired-update.json"
assert_fails "snapshot refuses a non-regular file" snapshot_file "$EXCHANGE/desired-update.json" "$WORK/snap.json"
rmdir -- "$EXCHANGE/desired-update.json"
assert_fails "snapshot refuses a missing file" snapshot_file "$EXCHANGE/desired-update.json" "$WORK/snap.json"
head -c 65537 /dev/zero | tr '\0' 'x' > "$EXCHANGE/desired-update.json"
assert_fails "snapshot refuses 64 KiB + 1" snapshot_file "$EXCHANGE/desired-update.json" "$WORK/snap.json"
head -c 65536 /dev/zero | tr '\0' 'x' > "$EXCHANGE/desired-update.json"
assert_ok "snapshot accepts exactly 64 KiB" snapshot_file "$EXCHANGE/desired-update.json" "$WORK/snap.json"
assert_eq "snapshot is byte-identical" "" "$(cmp -- "$EXCHANGE/desired-update.json" "$WORK/snap.json" 2>&1)"
cp -- "$D" "$EXCHANGE/desired-update.json"
assert_ok "snapshot of the fixture" snapshot_file "$EXCHANGE/desired-update.json" "$WORK/snap-fixture.json"
assert_eq "the snapshot parses like the fixture" "1.0.1" "$(checked "$WORK/snap-fixture.json" version "$RE_VERSION")"

# ---------------------------------------------------------------- review R3-4: an instant the regex admits but `date` rejects
assert_ok "2026-13-45T25:61:61Z passes RE_INSTANT (the regex is shape-only)" bash -c '[[ "2026-13-45T25:61:61Z" =~ '"$RE_INSTANT"' ]]'
assert_fails "…but to_epoch rejects it" to_epoch "2026-13-45T25:61:61Z"
assert_eq "to_epoch of a real instant" 1788256800 "$(to_epoch 2026-09-01T10:00:00Z)"

# ---------------------------------------------------------------- review R3-6: the digest-pinned pull reference
assert_eq "pinned_ref appends the digest" "ghcr.io/example/gxp-backend:1.0.1@sha256:$(printf 'b%.0s' {1..64})" \
    "$(pinned_ref ghcr.io/example/gxp-backend:1.0.1 "$(printf 'b%.0s' {1..64})")"
assert_eq "pinned_ref keeps a ref that already carries the same digest" "x@sha256:$(printf 'b%.0s' {1..64})" \
    "$(pinned_ref "x@sha256:$(printf 'b%.0s' {1..64})" "$(printf 'b%.0s' {1..64})")"
assert_fails "pinned_ref refuses a ref carrying a different digest" pinned_ref "x@sha256:$(printf 'c%.0s' {1..64})" "$(printf 'b%.0s' {1..64})"
assert_ok "a registry on a port matches RE_ARTIFACT (T-20c: localhost:5000/…)" bash -c '[[ "localhost:5000/gxp-backend:9.9.9" =~ '"$RE_ARTIFACT"' ]]'
assert_ok "a registry on a port with a digest pin matches RE_ARTIFACT" bash -c '[[ "registry.example.com:5000/gxp/backend:1.0.1@sha256:'"$(printf 'b%.0s' {1..64})"'" =~ '"$RE_ARTIFACT"' ]]'
assert_fails "a double slash does not match RE_ARTIFACT" bash -c '[[ "ghcr.io//gxp-backend:1.0.1" =~ '"$RE_ARTIFACT"' ]]'
assert_fails "a port with letters does not match RE_ARTIFACT" bash -c '[[ "ghcr.io:abc/gxp-backend:1.0.1" =~ '"$RE_ARTIFACT"' ]]'
assert_ok "the pinned ref still matches RE_ARTIFACT" bash -c '[[ "ghcr.io/example/gxp-backend:1.0.1@sha256:'"$(printf 'b%.0s' {1..64})"'" =~ '"$RE_ARTIFACT"' ]]'

# ---------------------------------------------------------------- review A-9: install.sh --current-version seeds the shadow
GXP_STATE_FILE="$WORK/seed/state.json"
assert_ok "seed_shadow writes a fresh shadow" seed_shadow 1.2.3 gxp-backend:1.2.3
assert_eq "seeded shadow mode" 600 "$(stat -c %a -- "$GXP_STATE_FILE")"
assert_eq "seeded shadow dir mode" 700 "$(stat -c %a -- "$WORK/seed")"
assert_eq "seeded outcome" none "$(checked "$GXP_STATE_FILE" outcome "$RE_CODE")"
assert_eq "seeded currentVersion" 1.2.3 "$(optional_string_field "$GXP_STATE_FILE" currentVersion)"
assert_eq "seeded lastRequestedAt null (no request handled yet)" "" "$(optional_string_field "$GXP_STATE_FILE" lastRequestedAt)"
assert_eq "seeded shadow has the full 17-line shadow layout" 17 "$(line_count "$GXP_STATE_FILE")"
assert_fails "seed_shadow never overwrites" seed_shadow 9.9.9 gxp-backend:9
assert_eq "…still 1.2.3" 1.2.3 "$(optional_string_field "$GXP_STATE_FILE" currentVersion)"
assert_fails "seed_shadow refuses a shell-shaped version" bash -c 'GXP_STATE_FILE="$1" seed_shadow "1.2.3; rm -rf /" x' _ "$WORK/seed2/state.json"
assert_ok "1.0.0 ≤ 1.2.3 → a signed 1.0.0 is downgrade-refused against the seed" version_le 1.0.0 1.2.3
GXP_STATE_FILE="$WORK/shadow/state.json"

# ---------------------------------------------------------------- version_le (D6 — the table SemanticVersionTest pins)
assert_ok    "1.0.0-SNAPSHOT <= 1.0.0"  version_le 1.0.0-SNAPSHOT 1.0.0
assert_fails "1.0.0 <= 1.0.0-SNAPSHOT"  version_le 1.0.0 1.0.0-SNAPSHOT
assert_ok    "1.0.0 <= 1.0.1"           version_le 1.0.0 1.0.1
assert_fails "1.0.10 <= 1.0.9"          version_le 1.0.10 1.0.9
assert_ok    "1.0.9 <= 1.0.10"          version_le 1.0.9 1.0.10
assert_ok    "1.0.0-rc1 <= 1.0.0-rc2"   version_le 1.0.0-rc1 1.0.0-rc2
assert_fails "1.0.0-rc2 <= 1.0.0-rc1"   version_le 1.0.0-rc2 1.0.0-rc1
assert_ok    "1.0.0 <= 1.0.0"           version_le 1.0.0 1.0.0
assert_ok    "1.0.1 <= 1.1.0"           version_le 1.0.1 1.1.0
assert_ok    "1.1.0 <= 2.0.0"           version_le 1.1.0 2.0.0
assert_fails "2.0.0 <= 1.99.99"         version_le 2.0.0 1.99.99
assert_ok    "1.0.0-SNAPSHOT <= 1.0.0-rc1 (lexical)" version_le 1.0.0-SNAPSHOT 1.0.0-rc1
assert_ok    "1.0.0-a <= 1.0.0-a"       version_le 1.0.0-a 1.0.0-a
assert_ok    "1.0.0-SNAPSHOT <= 1.0.1-SNAPSHOT" version_le 1.0.0-SNAPSHOT 1.0.1-SNAPSHOT

# ---------------------------------------------------------------- OpenSSL against the test public key (the updater's step 3)
assert_eq ".sig has no trailing newline (review R2-10)" 0 "$(wc -l < "$FIXTURES/manifest.json.sig")"
assert_eq "raw_field reads a last line without newline" "1.0.1" "$(printf '{\n  "version": "1.0.1"' > "$WORK/nonl.json"; string_field "$WORK/nonl.json" version)"
base64 -d < "$FIXTURES/manifest.json.sig" > "$WORK/fixture.sig"
assert_eq "signature is 64 raw bytes" 64 "$(stat -c %s -- "$WORK/fixture.sig")"
assert_ok "OpenSSL verifies the fixture pair" openssl pkeyutl -verify -pubin -inkey "$TEST_PUBLIC_KEY" -rawin \
    -in "$FIXTURES/manifest.json" -sigfile "$WORK/fixture.sig"
sed 's|"version": "1.0.1"|"version": "1.0.2"|' "$FIXTURES/manifest.json" > "$WORK/flipped.json"
assert_fails "OpenSSL rejects a changed byte" openssl pkeyutl -verify -pubin -inkey "$TEST_PUBLIC_KEY" -rawin \
    -in "$WORK/flipped.json" -sigfile "$WORK/fixture.sig"
assert_fails "OpenSSL rejects the manifest under the production set" verify_any "$FIXTURES/manifest.json" "$WORK/fixture.sig" "$(pem_tokens "$PRODUCTION_SET")"

# ---------------------------------------------------------------- review A-1: the two-key trust set and its rotation
T1="$(pem_tokens "$TEST_PUBLIC_KEY")"
T2="$(pem_tokens "$TEST_PUBLIC_KEY_2")"
assert_ok "a token is 60 chars of base64" bash -c '[[ "'"$T1"'" =~ '"$RE_KEY_TOKEN"' ]]'
assert_eq "the test set = key 1, key 2 in file order" "$T1,$T2" "$(pem_tokens "$TEST_PUBLIC_SET")"
assert_eq "a single-key file is a set of one" "$T1" "$(pem_tokens "$TEST_PUBLIC_KEY")"
P="$(pem_tokens "$PRODUCTION_SET")"
assert_eq "the production resource ships two keys" 2 "$(( $(printf '%s' "$P" | tr -cd ',' | wc -c) + 1 ))"
assert_ok "production keys are not the test keys" bash -c '[[ ",'"$P"'," != *",'"$T1"',"* && ",'"$P"'," != *",'"$T2"',"* ]]'
assert_eq "token → PEM → token round-trips" "$T2" "$(token_pem "$T2" > "$WORK/t2.pem"; key_token "$WORK/t2.pem")"
{ cat "$TEST_PUBLIC_KEY"; cat "$TEST_PUBLIC_KEY"; } > "$WORK/dup.pem"
assert_fails "pem_tokens refuses a duplicated key" pem_tokens "$WORK/dup.pem"
{ for i in 1 2 3 4 5; do cat "$TEST_PUBLIC_KEY" | sed "s/^MCow/MCow/"; done; } > "$WORK/five.pem"
assert_fails "pem_tokens refuses an empty/garbage file" pem_tokens "$FIXTURES/manifest.json"
assert_fails "pem_tokens refuses a private key file" pem_tokens "$TEST_KEYS/test-signing-private-key.pem"
assert_ok "valid_key_set accepts the test set" valid_key_set "$T1,$T2"
assert_fails "valid_key_set refuses a duplicated token" valid_key_set "$T1,$T1"
assert_fails "valid_key_set refuses shape-valid non-key base64" valid_key_set "$(printf 'A%.0s' {1..59})="
assert_fails "valid_key_set refuses five tokens" valid_key_set "$T1,$T2,$T1,$T2,$T1"
assert_fails "valid_key_set refuses an empty set" valid_key_set ""
# either key verifies, and the signer is reported
assert_ok "fixture (signed by key 1) verifies under the set" verify_any "$FIXTURES/manifest.json" "$WORK/fixture.sig" "$T1,$T2"
verify_any "$FIXTURES/manifest.json" "$WORK/fixture.sig" "$T1,$T2" && assert_eq "…signer = key 1" "$T1" "$SIGNER"
base64 -d < "$FIXTURES/manifest-rotation.json.sig" > "$WORK/rotation.sig"
assert_ok "rotation fixture (signed by key 2) verifies under the set" verify_any "$FIXTURES/manifest-rotation.json" "$WORK/rotation.sig" "$T1,$T2"
verify_any "$FIXTURES/manifest-rotation.json" "$WORK/rotation.sig" "$T1,$T2" && assert_eq "…signer = key 2" "$T2" "$SIGNER"
# a two-key set where only the SECOND key matches (T-20a validate): {production key 1, test key 1}
{ head -n 3 "$PRODUCTION_SET"; cat "$TEST_PUBLIC_KEY"; } > "$WORK/second-matches.pem"
assert_ok "verifies when only the second key of the set matches" verify_any "$FIXTURES/manifest.json" "$WORK/fixture.sig" "$(pem_tokens "$WORK/second-matches.pem")"
# after a rotation to {key 2} the retired key 1 is refused
assert_fails "retired key 1 refused after rotation to {key 2}" verify_any "$FIXTURES/manifest.json" "$WORK/fixture.sig" "$T2"
assert_eq "…and SIGNER is empty" "" "$SIGNER"
assert_fails "no key verifies a flipped manifest" verify_any "$WORK/flipped.json" "$WORK/fixture.sig" "$T1,$T2"
# the rotation manifest: 9 lines, trustedKeys = {key 2} = its own signer
R="$FIXTURES/manifest-rotation.json"
assert_eq "rotation manifest line count" 9 "$(line_count "$R")"
assert_eq "rotation manifest version" "1.0.2" "$(checked "$R" version "$RE_VERSION")"
assert_eq "rotation manifest trustedKeys" "$T2" "$(checked "$R" trustedKeys "$RE_KEY_SET")"
assert_ok "the rotation's set contains its signer" bash -c '[[ ",'"$T2"'," == *",'"$T2"',"* ]]'
assert_fails "the rotation's set does not contain key 1" bash -c '[[ ",'"$T2"'," == *",'"$T1"',"* ]]'
assert_fails "the fixture has no trustedKeys" raw_field "$FIXTURES/manifest.json" trustedKeys
# the rotation rule: newer than the remembered set → replace; equal or older → keep (replay)
assert_fails "1.0.2 is not ≤ 1.0.1 → a 1.0.2 set replaces a 1.0.1 set" version_le 1.0.2 1.0.1
assert_ok "1.0.1 ≤ 1.0.2 → a replayed 1.0.1 set is ignored" version_le 1.0.1 1.0.2

printf 'parse.sh: %d checks, %d failures\n' "$checks" "$failures"
[[ $failures -eq 0 ]]
