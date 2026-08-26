#!/usr/bin/env bash
# rehearsal-lib.sh — shared plumbing of smoke-apply.sh and rehearse-rollback.sh
# (update-channel REQ-724). Sourced, never run.
#
# Every rehearsal runs against a THROWAWAY compose project ("gxp-rehearsal")
# in a temporary directory: a copy of backend/compose.yaml, its own .env, no
# published port (the checks go through `docker compose exec`), the app's
# scheduler halted (the app's own reconcile job must not delete the desired
# file a rehearsal planted). The developer's real `gxp` project, its volumes
# and its .env are never touched. Requires root (the volume mountpoint is
# root-only) — a non-root caller is re-executed through `sudo -n`.
set -euo pipefail
export LC_ALL=C

if [[ $EUID -ne 0 ]]; then
    exec sudo -n -E -- "$0" "$@"
fi

TEST_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"   # -P: a symlinked checkout under /home must not leak into paths a ProtectHome unit reads
UPDATER_DIR="$(cd -- "$TEST_DIR/.." && pwd -P)"
BACKEND_DIR="$(cd -- "$UPDATER_DIR/.." && pwd -P)"
REPO_DIR="$(cd -- "$BACKEND_DIR/.." && pwd -P)"
TEST_PRIVATE_KEY="$BACKEND_DIR/app/src/test/resources/update/test-signing-private-key.pem"
TEST_PRIVATE_KEY_2="$BACKEND_DIR/app/src/test/resources/update/test-signing-private-key-2.pem"   # the dormant test key (A-1)
TEST_PUBLIC_KEY_1="$BACKEND_DIR/app/src/test/resources/update/test-signing-public-key.pem"
TEST_PUBLIC_KEY_2="$BACKEND_DIR/app/src/test/resources/update/test-signing-public-key-2.pem"
TEST_PUBLIC_KEY="$BACKEND_DIR/app/src/test/resources/update/test-signing-public-keys.pem"   # the two-key TEST set (A-1)
SIGN="$REPO_DIR/tools/update/sign-manifest.sh"
BASE_IMAGE="${GXP_REHEARSAL_BASE_IMAGE:-gxp-backend:1.0.0-SNAPSHOT}"
PROJECT="gxp-rehearsal"
ADMIN_PASSWORD="rehearsal-admin-password"
DB_PASSWORD="rehearsal-db-password"

# the updater's readers, for state.json assertions
GXP_UPDATER_LIBRARY_ONLY=1
# shellcheck source=../gxp-updater.sh
source "$UPDATER_DIR/gxp-updater.sh"
unset GXP_UPDATER_LIBRARY_ONLY

# GXP_REHEARSAL_WORK_BASE: the systemd rehearsal needs the project OUTSIDE /tmp (PrivateTmp) and /home (ProtectHome)
WORK="$(mktemp -d "${GXP_REHEARSAL_WORK_BASE:-/tmp}/gxp-rehearsal.XXXXXX")"
COMPOSE_DIR="$WORK/compose"
BACKUPS="$WORK/backups"
SHADOW="$WORK/updater-state/state.json"   # the updater's own state (GXP_STATE_FILE)
DERIVED_IMAGES=()
failures=0

say() { printf '\n==> %s\n' "$*"; }
pass() { printf 'ok   %s\n' "$*"; }
miss() { printf 'MISS %s\n' "$*"; failures=$((failures + 1)); }
assert_ok() { # LABEL COMMAND... — the command's success is the check
    if "${@:2}" >/dev/null 2>&1; then pass "$1"; else miss "$1: expected success"; fi
}
assert_eq() { # LABEL EXPECTED ACTUAL
    if [[ "$2" == "$3" ]]; then pass "$1 = [$2]"; else miss "$1: expected [$2] got [$3]"; fi
}

compose() {
    docker compose --project-directory "$COMPOSE_DIR" --project-name "$PROJECT" "$@"
}

sql() { # SQL → tuples-only output
    compose exec -T db psql -U gxp -d gxp -tAc "$1"
}

app_curl() { # PATH [curl args...] → the HTTP status code
    compose exec -T app curl -sk -o /dev/null -w '%{http_code}' "${@:2}" "https://localhost:8443$1"
}

ready_status() {
    app_curl /api/system/ready 2>/dev/null || true
}

wait_ready() { # SECONDS
    local deadline=$((SECONDS + $1))
    while (( SECONDS < deadline )); do
        if [[ "$(ready_status)" == "200" ]]; then
            return 0
        fi
        sleep 5
    done
    return 1
}

prepare_project() {
    say "throwaway compose project '$PROJECT' in $COMPOSE_DIR"
    docker image inspect -- "$BASE_IMAGE" >/dev/null 2>&1 || {
        echo "base image $BASE_IMAGE missing — build it first: cd backend/app && docker build -f src/main/docker/Dockerfile.jvm -t $BASE_IMAGE ." >&2
        exit 2
    }
    mkdir -p -- "$COMPOSE_DIR" "$BACKUPS"
    chmod 0700 -- "$BACKUPS"
    cp -- "$BACKEND_DIR/compose.yaml" "$COMPOSE_DIR/compose.yaml"
    ln -s -- "$BACKEND_DIR/app" "$COMPOSE_DIR/app"
    (
        umask 077
        {
            printf 'GXP_DB_PASSWORD=%s\n' "$DB_PASSWORD"
            printf 'GXP_ADMIN_PASSWORD=%s\n' "$ADMIN_PASSWORD"
            printf 'GXP_TLS_HOST=localhost\n'
            printf 'GXP_IMAGE=%s\n' "$BASE_IMAGE"
        } > "$COMPOSE_DIR/.env"
    )
    cat > "$COMPOSE_DIR/compose.override.yaml" <<'EOF'
# rehearsal only: no host port, scheduler halted (the app must not reconcile
# the desired file a rehearsal planted — its jobs are proven by the JVM tests)
services:
  app:
    ports: !reset []
    environment:
      QUARKUS_SCHEDULER_START_MODE: halted
EOF
    start_event_stream
    compose up -d --no-build --pull never >/dev/null
    wait_ready 360 || { echo "the base image never became ready within 360 s" >&2; exit 2; }
    EXCHANGE="$(docker volume inspect -f '{{.Mountpoint}}' -- "${PROJECT}_gxp-updates")"
    pass "ready; exchange dir $EXCHANGE ($(stat -c '%a %u' -- "$EXCHANGE"))"
}

# tag_base TAG — a second tag of the base image, removed by cleanup (call it directly, never inside $(…): the array append must reach this shell)
tag_base() {
    docker tag -- "$BASE_IMAGE" "$1"
    DERIVED_IMAGES+=("$1")
}

image_id() { # REF → 64 hex
    docker image inspect -f '{{.Id}}' -- "$1" | sed 's/^sha256://'
}

# write_desired VERSION ARTIFACT SHA APPLY_NOT_BEFORE REQUESTED_AT — signs with the TEST key
# (SIGN_KEY overrides; TRUSTED_KEYS_PEM adds a rotation line), writes atomically
write_desired() {
    local version="$1" artifact="$2" sha="$3" not_before="$4" requested="$5" out
    local -a extra=()
    [[ -z "${TRUSTED_KEYS_PEM:-}" ]] || extra=(--trusted-keys "$TRUSTED_KEYS_PEM")
    out="$(mktemp -d)"
    "$SIGN" "${SIGN_KEY:-$TEST_PRIVATE_KEY}" "$out" --channel stable --version "$version" --artifact-ref "$artifact" \
        --sha256 "$sha" --published-at "$requested" "${extra[@]}" >/dev/null
    {
        printf '{\n'
        printf '  "schemaVersion": 1,\n'
        printf '  "version": "%s",\n' "$version"
        printf '  "artifactRef": "%s",\n' "$artifact"
        printf '  "sha256": "%s",\n' "$sha"
        printf '  "requestedAt": "%s",\n' "$requested"
        printf '  "requestedBy": "manual",\n'
        printf '  "applyNotBefore": "%s",\n' "$not_before"
        printf '  "applyWindowSeconds": 7200,\n'
        printf '  "manifest": "%s",\n' "$(base64 -w0 < "$out/stable.json")"
        printf '  "signature": "%s"\n' "$(cat "$out/stable.json.sig")"
        printf '}\n'
    } | place_file "$EXCHANGE" desired-update.json 0644 "$(dirname -- "$EXCHANGE")"
    rm -rf -- "$out"
}

utc() { # OFFSET-SECONDS → instant
    date -u -d "@$(( $(date -u +%s) + $1 ))" +%Y-%m-%dT%H:%M:%SZ
}

# run_updater [ENV=VALUE ...] → exit code in UPDATER_RC, output in UPDATER_LOG.
# GXP_REHEARSAL_LOCAL_IMAGE=1 (default; LOCAL_IMAGE_SEAM= disables it — scenario
# C runs the PRODUCTION pull branch against a local registry): the rehearsal
# images are local tags without a registry digest, so the updater's
# rehearsal-only path compares the local image ID instead of pulling by
# digest (refused under systemd).
run_updater() {
    UPDATER_RC=0
    UPDATER_LOG="$(env GXP_COMPOSE_DIR="$COMPOSE_DIR" GXP_COMPOSE_PROJECT="$PROJECT" GXP_PUBLIC_KEYS="$TEST_PUBLIC_KEY" \
        GXP_BACKUP_DIR="$BACKUPS" GXP_HEALTH_TIMEOUT="${GXP_HEALTH_TIMEOUT:-180}" GXP_HEALTH_HOLD="${GXP_HEALTH_HOLD:-5}" \
        GXP_LOCK_FILE="$WORK/updater.lock" GXP_STATE_FILE="$SHADOW" GXP_REHEARSAL_LOCAL_IMAGE="${LOCAL_IMAGE_SEAM-1}" \
        "$@" bash "$UPDATER_DIR/gxp-updater.sh" 2>&1)" || UPDATER_RC=$?
    printf '%s\n' "$UPDATER_LOG" | sed 's/^/     updater: /'
}

state() { # FIELD → value ("" for null)
    optional_string_field "$EXCHANGE/state.json" "$1"
}

state_bool() {
    bool_field "$EXCHANGE/state.json" "$1"
}

shadow() { # FIELD → value from the updater's own state ("" for null)
    optional_string_field "$SHADOW" "$1"
}

env_image() {
    sed -n 's/^GXP_IMAGE=//p' -- "$COMPOSE_DIR/.env" | tail -n 1
}

# The app-start counter (review 06 #5). `docker events --since/--until` replays
# a bounded in-memory ring that the health probes' exec events overflow within
# minutes, so a past start silently disappears. Instead a LIVE stream is opened
# in prepare_project (`docker events` streams every event from now on, no
# ring) and every `start` event is annotated AT ONCE with the container's
# `docker inspect '{{.RestartCount}}'`, written to $EVENTS_LOG as
# "<epoch> <container id> <action> <restart count | ->". `docker inspect`
# alone cannot count afterwards: a compose recreate destroys the previous
# container, so its start is gone.
#
# Counted are the starts the UPDATER causes (compose up / recreate / start
# after stop). With `restart: unless-stopped` in compose.yaml a crash-looping
# image (rehearsal B) is restarted by Docker's policy as well. The one thing
# that tells the two apart is RestartCount at the moment of the start: a
# policy restart increments it (>= 1), every start through the start API —
# a fresh container, or `compose up` after `compose stop` — resets it to 0
# (verified on Docker 28.5: "docker stop" mid-loop → rc=3, "docker start" →
# rc=0). Event ORDER cannot tell them apart: a stop during a restart back-off
# emits no stop/die of its own, so the compose start then follows a bare
# `die` exactly like a policy restart would.
EVENTS_LOG=""
EVENTS_PID=""
EVENTS_READER=""
annotate_events() {
    local t id action rc
    while read -r t id action; do
        rc=-
        if [[ "$action" == start ]]; then
            rc="$(docker inspect -f '{{.RestartCount}}' -- "$id" 2>/dev/null || printf '?')"
        fi
        printf '%s %s %s %s\n' "$t" "$id" "$action" "$rc"
    done
}
start_event_stream() {
    EVENTS_LOG="$WORK/app-events.log"
    : > "$EVENTS_LOG"
    mkfifo -- "$WORK/app-events.fifo"
    docker events --filter type=container --filter event=create --filter event=start --filter event=die --filter event=stop \
        --filter "label=com.docker.compose.project=$PROJECT" --filter label=com.docker.compose.service=app \
        --format '{{.Time}} {{.Actor.ID}} {{.Action}}' > "$WORK/app-events.fifo" 2>/dev/null &
    EVENTS_PID=$!
    annotate_events < "$WORK/app-events.fifo" >> "$EVENTS_LOG" &
    EVENTS_READER=$!
    sleep 1   # the stream is attached before anything the rehearsal counts
}
stop_event_stream() {
    [[ -z "$EVENTS_PID" ]] || { kill "$EVENTS_PID" 2>/dev/null || true; wait "$EVENTS_PID" 2>/dev/null || true; }
    [[ -z "$EVENTS_READER" ]] || wait "$EVENTS_READER" 2>/dev/null || true   # EOF once the writer is gone
    EVENTS_PID=""
    EVENTS_READER=""
}
# app_starts SINCE-EPOCH → the compose-initiated app starts since then (start events with RestartCount 0)
app_starts() {
    sleep 2   # let the last event pass the annotation
    awk -v since="$1" '$3 == "start" && $1 >= since && $4 == "0" { n++ } END { print n + 0 }' "$EVENTS_LOG"
}
# app_restart_count → Docker's RestartCount of the running app container (0 = the policy never had to restart it)
app_restart_count() {
    docker inspect -f '{{.RestartCount}}' -- "$(compose ps -q app)" 2>/dev/null || printf 'no-container'
}

create_marker() {
    sql 'CREATE TABLE rehearsal_marker(v text)' >/dev/null
    sql "INSERT INTO rehearsal_marker(v) VALUES ('written-before-the-update')" >/dev/null
}

marker() {
    sql 'SELECT v FROM rehearsal_marker' 2>/dev/null || true
}

# manager_updater_outcome → the `outcome` under `updater` of GET /api/manager/updates (the REQ-717 live proof)
manager_updater_outcome() {
    local token body
    token="$(compose exec -T app curl -sk -X POST https://localhost:8443/api/manager/login -H 'Content-Type: application/json' \
        -d "{\"username\":\"admin\",\"password\":\"$ADMIN_PASSWORD\"}" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
    [[ -n "$token" ]] || { printf 'login-failed'; return 0; }
    body="$(compose exec -T app curl -sk https://localhost:8443/api/manager/updates -H "Authorization: Bearer $token")"
    printf '%s' "$body" | sed -n 's/.*"updater":{[^}]*"outcome":"\([^"]*\)".*/\1/p'
}

# untag_registry_refs REPOSITORY — remove every tag and digest reference under a rehearsal registry repository BY NAME
# (never `rmi -f <id>`: the pushed image shares its ID with the developer's base image, which must survive)
untag_registry_refs() {
    docker images --filter "reference=$1" --format '{{.Repository}}:{{.Tag}}' 2>/dev/null | grep -v ':<none>$' | xargs -r -n1 docker rmi -- >/dev/null 2>&1 || true
    docker images --digests --filter "reference=$1" --format '{{.Repository}}@{{.Digest}}' 2>/dev/null | grep -v '@<none>$' | xargs -r -n1 docker rmi -- >/dev/null 2>&1 || true
}

cleanup() {
    say "cleanup"
    stop_event_stream
    compose down -v --remove-orphans >/dev/null 2>&1 || true
    local image
    for image in "${DERIVED_IMAGES[@]}"; do
        docker rmi -- "$image" >/dev/null 2>&1 || true
    done
    rm -rf -- "$WORK"
}

verdict() { # NAME
    if [[ $failures -eq 0 ]]; then
        printf '\n%s: PASS\n' "$1"
    else
        printf '\n%s: FAIL (%d misses)\n' "$1" "$failures"
        exit 1
    fi
}
