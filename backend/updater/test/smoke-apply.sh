#!/usr/bin/env bash
# smoke-apply.sh — the updater's happy path against a real compose unit
# (update-channel T-16, REQ-724/717/719/720/721/723).
#
#   sudo backend/updater/test/smoke-apply.sh        (or run it as yourself: it re-executes via sudo -n)
#
# Needs: Docker, the base image gxp-backend:1.0.0-SNAPSHOT (built from this
# tree), the TEST signing key pair. Uses a THROWAWAY compose project
# (see rehearsal-lib.sh) and cleans it up — the developer's own `gxp`
# project, volumes and .env are untouched.
#
# Flow: tag the base image a second time → sign a manifest (test key) for
# version 9.9.9 naming that tag and its image ID → write desired-update.json
# into the gxp-updates volume → marker table → run the updater → assert:
# dump non-empty, state applied + currentImage = the tag, ready 200, exactly
# one app start, marker readable, GET /api/manager/updates shows
# updater.outcome = applied (the shared-directory proof), .env carries the
# tag. Then the window negatives: +1 h and −3 h → exit 0, state unchanged;
# the replay guard, also after the exchange mirror was deleted (the shadow
# state is the memory, review R3-3). Before the first run two symlinks are
# planted in the exchange dir (`.state.json.tmp` and `state.json` → a root
# canary): the canary must be untouched afterwards (review R3-1).
# shellcheck source=rehearsal-lib.sh
source "$(dirname -- "${BASH_SOURCE[0]}")/rehearsal-lib.sh"
trap cleanup EXIT

prepare_project
TAG="gxp-backend:smoke-$(date -u +%Y%m%dT%H%M%SZ)"
tag_base "$TAG"
ID="$(image_id "$TAG")"
create_marker

say "install-time seed (A-9): shadow currentVersion=1.0.0 → a signed LOWER version is refused before any apply"
GXP_STATE_FILE="$SHADOW" seed_shadow 1.0.0 "$BASE_IMAGE"
assert_eq "seeded shadow mode" 600 "$(stat -c %a -- "$SHADOW")"
assert_eq "seeded shadow.outcome" none "$(shadow outcome)"
assert_eq "seeded shadow.currentVersion" 1.0.0 "$(shadow currentVersion)"
write_desired 0.9.9 "$TAG" "$ID" "$(utc -60)" "$(utc -5)"
run_updater
assert_eq "exit code" 0 "$UPDATER_RC"
assert_eq "state.outcome (downgrade refused with no update ever applied)" downgrade-refused "$(state outcome)"
assert_eq "shadow keeps currentVersion" 1.0.0 "$(shadow currentVersion)"

say "desired: 9.9.9 → $TAG ($ID)"
REQUESTED="$(utc 0)"
write_desired 9.9.9 "$TAG" "$ID" "$(utc -60)" "$REQUESTED"
CANARY="$WORK/canary"
printf 'untouched\n' > "$CANARY"
chmod 0600 -- "$CANARY"
ln -s -- "$CANARY" "$EXCHANGE/.state.json.tmp"
ln -sf -- "$CANARY" "$EXCHANGE/state.json"   # -f: the install-seed run above already wrote a mirror
sleep 1
T0="$(date +%s)"

say "run the updater"
run_updater
assert_eq "updater exit code" 0 "$UPDATER_RC"
assert_eq "canary untouched despite the planted symlinks (R3-1)" "untouched 600" "$(cat "$CANARY") $(stat -c %a -- "$CANARY")"
[[ -L "$EXCHANGE/state.json" ]] && dest=symlink || dest=regular
assert_eq "state.json is a regular file now" regular "$dest"
assert_eq "exchange dir holds exactly the planted temp link + the two files" ".state.json.tmp desired-update.json state.json" "$(ls -A -- "$EXCHANGE" | sort | tr '\n' ' ' | sed 's/ $//')"
assert_eq "no temp file left in the volume's parent (A-3 staging dir)" "_data" "$(ls -A -- "$(dirname -- "$EXCHANGE")" | tr '\n' ' ' | sed 's/ $//')"
rm -f -- "$EXCHANGE/.state.json.tmp"
dump="$(find "$BACKUPS" -maxdepth 1 -type f -name 'pre-update-9.9.9-*.dump' | head -n 1)"
if [[ -n "$dump" && -s "$dump" ]]; then pass "dump exists and is non-empty: $(basename -- "$dump") ($(stat -c %s -- "$dump") bytes)"; else miss "no non-empty dump in $BACKUPS"; fi
assert_eq "state.outcome" applied "$(state outcome)"
assert_eq "state.rolledBack" false "$(state_bool rolledBack)"
assert_eq "state.currentImage" "$TAG" "$(state currentImage)"
assert_eq "state.currentVersion" 9.9.9 "$(state currentVersion)"
assert_eq "state.previousImage" "$BASE_IMAGE" "$(state previousImage)"
assert_eq "state.backupFile" "$(basename -- "$dump")" "$(state backupFile)"
assert_eq "state.message" "" "$(state message)"
assert_eq "state.json mode" 644 "$(stat -c %a -- "$EXCHANGE/state.json")"
assert_eq "shadow state mode (R3-3)" 600 "$(stat -c %a -- "$SHADOW")"
assert_eq "shadow.outcome" applied "$(shadow outcome)"
assert_eq "shadow.currentImageId (R3-6)" "sha256:$ID" "$(shadow currentImageId)"
assert_eq "mirror = shadow minus currentImageId/trustedKeys/trustedKeysVersion" "$(cat "$EXCHANGE/state.json")" "$(sed '/^  "currentImageId": \|^  "trustedKeys": \|^  "trustedKeysVersion": /d' "$SHADOW")"
assert_eq ".env GXP_IMAGE" "$TAG" "$(env_image)"
assert_eq "running container image" "$TAG" "$(docker inspect -f '{{.Config.Image}}' -- "$(compose ps -q app)")"
assert_eq "/api/system/ready" 200 "$(ready_status)"
assert_eq "app starts since the request (exactly one restart)" 1 "$(app_starts "$T0")"
assert_eq "marker still readable" written-before-the-update "$(marker)"
assert_eq "GET /api/manager/updates → updater.outcome" applied "$(manager_updater_outcome)"

say "window negative: applyNotBefore one hour ahead → no action"
before="$(cat "$EXCHANGE/state.json")"
write_desired 9.9.9 "$TAG" "$ID" "$(utc 3600)" "$(utc 5)"
run_updater
assert_eq "exit code" 0 "$UPDATER_RC"
assert_eq "state unchanged" "$before" "$(cat "$EXCHANGE/state.json")"
assert_eq "no further app start" 1 "$(app_starts "$T0")"

say "window negative: applyNotBefore three hours behind (window of 2 h passed) → no action"
write_desired 9.9.9 "$TAG" "$ID" "$(utc -10800)" "$(utc 10)"
run_updater
assert_eq "exit code" 0 "$UPDATER_RC"
assert_eq "state unchanged" "$before" "$(cat "$EXCHANGE/state.json")"
assert_eq "no further app start" 1 "$(app_starts "$T0")"

say "replay guard: the applied requestedAt again inside an open window → no action (REQ-718)"
write_desired 9.9.9 "$TAG" "$ID" "$(utc -60)" "$REQUESTED"
run_updater
assert_eq "exit code" 0 "$UPDATER_RC"
assert_eq "state unchanged" "$before" "$(cat "$EXCHANGE/state.json")"

say "the app deletes the mirror → the shadow still remembers (R3-3)"
rm -f -- "$EXCHANGE/state.json"
write_desired 9.9.9 "$TAG" "$ID" "$(utc -60)" "$REQUESTED"
run_updater
assert_eq "exit code" 0 "$UPDATER_RC"
[[ -e "$EXCHANGE/state.json" ]] && mirror=recreated || mirror=absent
assert_eq "mirror not recreated (the guard exited before any write)" absent "$mirror"
assert_eq "shadow unchanged" "$(sed '/^  "currentImageId": \|^  "trustedKeys": \|^  "trustedKeysVersion": /d' "$SHADOW")" "$before"
assert_eq "no further app start" 1 "$(app_starts "$T0")"

say "a symlinked desired file → malformed, nothing followed (R3-1/R3-2)"
ln -sf -- "$CANARY" "$EXCHANGE/desired-update.json"
run_updater
assert_eq "exit code" 0 "$UPDATER_RC"
assert_eq "state.outcome" malformed "$(state outcome)"
assert_eq "lastRequestedAt carried forward" "$REQUESTED" "$(state lastRequestedAt)"
assert_eq "canary still untouched" "untouched" "$(cat "$CANARY")"

say "trust-set rotation (A-1): 9.9.10 signed by the DORMANT key 2 with trustedKeys={key 2}, artifact unknown → pull-failed, set rotated anyway"
T1="$(pem_tokens "$TEST_PUBLIC_KEY_1")"
T2="$(pem_tokens "$TEST_PUBLIC_KEY_2")"
assert_eq "shadow.trustedKeys before" "" "$(shadow trustedKeys)"
SIGN_KEY="$TEST_PRIVATE_KEY_2" TRUSTED_KEYS_PEM="$TEST_PUBLIC_KEY_2" write_desired 9.9.10 gxp-backend:does-not-exist "$ID" "$(utc -60)" "$(utc 20)"
run_updater
assert_eq "exit code" 0 "$UPDATER_RC"
assert_eq "updater log names the rotation" 1 "$(printf '%s\n' "$UPDATER_LOG" | grep -c 'trust set rotated by version=9.9.10 keys=1')"
assert_eq "state.outcome" pull-failed "$(state outcome)"
assert_eq "shadow.trustedKeys = {key 2}" "$T2" "$(shadow trustedKeys)"
assert_eq "shadow.trustedKeysVersion" 9.9.10 "$(shadow trustedKeysVersion)"
assert_eq "the mirror never carries the set" "" "$(raw_field "$EXCHANGE/state.json" trustedKeys 2>/dev/null || true)"
assert_eq "no further app start" 1 "$(app_starts "$T0")"

say "…the retired key 1 is refused from now on, although GXP_PUBLIC_KEYS still lists it"
write_desired 9.9.11 "$TAG" "$ID" "$(utc -60)" "$(utc 30)"
run_updater
assert_eq "exit code" 0 "$UPDATER_RC"
assert_eq "state.outcome (signed by key 1 after rotation)" invalid-signature "$(state outcome)"
assert_eq "shadow.trustedKeys unchanged" "$T2" "$(shadow trustedKeys)"

say "…a replayed OLDER set (9.9.10 again with {key 1, key 2}, signed by key 2) is ignored: the set stays {key 2}"
{ cat "$TEST_PUBLIC_KEY_1"; cat "$TEST_PUBLIC_KEY_2"; } > "$WORK/both.pem"
SIGN_KEY="$TEST_PRIVATE_KEY_2" TRUSTED_KEYS_PEM="$WORK/both.pem" write_desired 9.9.10 gxp-backend:does-not-exist "$ID" "$(utc -60)" "$(utc 40)"
run_updater
assert_eq "exit code" 0 "$UPDATER_RC"
assert_eq "state.outcome" pull-failed "$(state outcome)"
assert_eq "shadow.trustedKeys still {key 2}" "$T2" "$(shadow trustedKeys)"

say "…and a NEWER set signed by key 2 (9.9.12: {key 1, key 2}) re-admits key 1 — a rotation is any signed newer set"
SIGN_KEY="$TEST_PRIVATE_KEY_2" TRUSTED_KEYS_PEM="$WORK/both.pem" write_desired 9.9.12 gxp-backend:does-not-exist "$ID" "$(utc -60)" "$(utc 50)"
run_updater
assert_eq "shadow.trustedKeys = {key 1, key 2}" "$T1,$T2" "$(shadow trustedKeys)"
write_desired 9.9.13 gxp-backend:does-not-exist "$ID" "$(utc -60)" "$(utc 60)"
run_updater
assert_eq "key 1 verifies again (pull-failed = past the signature, before docker)" pull-failed "$(state outcome)"

say "review 06 #3: the ACTIVE key 1 signs 9.9.14 with {key 1} (retiring the dormant key 2) → malformed, set unchanged"
SIGN_KEY="$TEST_PRIVATE_KEY" TRUSTED_KEYS_PEM="$TEST_PUBLIC_KEY_1" write_desired 9.9.14 gxp-backend:does-not-exist "$ID" "$(utc -60)" "$(utc 70)"
run_updater
assert_eq "exit code" 0 "$UPDATER_RC"
assert_eq "state.outcome" malformed "$(state outcome)"
assert_eq "shadow.trustedKeys still {key 1, key 2}" "$T1,$T2" "$(shadow trustedKeys)"
assert_eq "shadow.trustedKeysVersion still 9.9.12" 9.9.12 "$(shadow trustedKeysVersion)"

say "review 06 #2: a .env missing a variable compose requires → exit 2, the app sees config-failed, the shadow is untouched"
sed -i '/^GXP_TLS_HOST=/d' "$COMPOSE_DIR/.env"
write_desired 9.9.15 gxp-backend:does-not-exist "$ID" "$(utc -60)" "$(utc 80)"
run_updater
assert_eq "exit code (configuration error)" 2 "$UPDATER_RC"
assert_eq "updater log names the cause" 1 "$(printf '%s\n' "$UPDATER_LOG" | grep -c 'configuration error: cannot read the app image from compose')"
assert_eq "state.outcome (mirror)" config-failed "$(state outcome)"
assert_eq "state.message (a fixed code)" compose-config "$(state message)"
assert_eq "state.lastRequestedAt carried forward (the request is NOT marked handled)" "$(shadow lastRequestedAt)" "$(state lastRequestedAt)"
assert_eq "state.currentImage = the shadow's" "$(shadow currentImage)" "$(state currentImage)"
assert_eq "shadow.outcome untouched" malformed "$(shadow outcome)"
assert_eq "GET /api/manager/updates → updater.outcome" config-failed "$(manager_updater_outcome)"
printf 'GXP_TLS_HOST=localhost\n' >> "$COMPOSE_DIR/.env"
run_updater
assert_eq "…with the variable back the same request is handled (past the config check: pull-failed)" pull-failed "$(state outcome)"
assert_eq "app starts unchanged by all of it" 1 "$(app_starts "$T0")"
assert_eq "the restart policy never restarted the app (RestartCount)" 0 "$(app_restart_count)"

verdict smoke-apply
