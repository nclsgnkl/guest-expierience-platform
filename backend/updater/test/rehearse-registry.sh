#!/usr/bin/env bash
# rehearse-registry.sh — scenario C: the PRODUCTION pull path against a local
# registry (update-channel T-20c, review A-4, REQ-720/724). No rehearsal seam:
# the updater pulls `ref@sha256:<digest>` exactly as it will on a box.
#
#   sudo backend/updater/test/rehearse-registry.sh
#
# Flow: start `registry:2` on 127.0.0.1:5000 (Docker treats localhost
# registries as insecure without daemon configuration — a dev-box test image,
# not a product dependency) → push the base image as
# localhost:5000/gxp-backend:9.9.9 → take the digest with the ONE canonical
# command (`docker buildx imagetools inspect … '{{println .Manifest.Digest}}'`, review
# A-7) and check it against the push output → remove the local tag so the
# pull is real → sign 9.9.9 with the test key → run the updater WITHOUT the
# seam → assert applied, `.env` = the digest-pinned ref, the running image's
# RepoDigests carries it → a wrong digest → `pull-failed` → stop the registry
# → 9.9.11 pre-pulled → the pull fails, the pinned ref is found locally → applied, never
# `pull-failed` — the offline artifact is a distinct image (base + one label)
# pulled by digest while the registry still answered. Throwaway compose
# project; the registry container, every localhost:5000 image and the
# registry:2 image are removed afterwards.
# shellcheck source=rehearsal-lib.sh
source "$(dirname -- "${BASH_SOURCE[0]}")/rehearsal-lib.sh"

REGISTRY_NAME="gxp-rehearsal-registry"
REGISTRY_REF="localhost:5000/gxp-backend"

cleanup_registry() {
    docker rm -f -- "$REGISTRY_NAME" >/dev/null 2>&1 || true
    cleanup   # compose down first: a pinned ref cannot be untagged while the app container still runs it
    untag_registry_refs "$REGISTRY_REF"
    docker rmi -- registry:2 >/dev/null 2>&1 || true
}
trap cleanup_registry EXIT

prepare_project
say "local registry:2 on 127.0.0.1:5000"
docker run -d --rm --name "$REGISTRY_NAME" -p 127.0.0.1:5000:5000 registry:2 >/dev/null
for _ in $(seq 1 30); do curl -fs http://127.0.0.1:5000/v2/ >/dev/null 2>&1 && break; sleep 1; done
assert_eq "registry answers /v2/" 200 "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:5000/v2/)"

say "push $REGISTRY_REF:9.9.9 and take the digest"
docker tag -- "$BASE_IMAGE" "$REGISTRY_REF:9.9.9"
push_out="$(docker push -- "$REGISTRY_REF:9.9.9" 2>&1)"
push_digest="$(printf '%s\n' "$push_out" | sed -n 's/^9\.9\.9: digest: sha256:\([0-9a-f]\{64\}\).*/\1/p' | tail -n 1)"
DIGEST="$(docker buildx imagetools inspect "$REGISTRY_REF:9.9.9" --format '{{println .Manifest.Digest}}' 2>/dev/null | sed 's/^sha256://')"
assert_ok "canonical digest command yields 64 hex (A-7)" bash -c '[[ "$1" =~ ^[0-9a-f]{64}$ ]]' _ "$DIGEST"
assert_eq "...and equals the digest docker push printed (single-arch)" "$push_digest" "$DIGEST"
PINNED="$REGISTRY_REF:9.9.9@sha256:$DIGEST"
docker rmi -- "$REGISTRY_REF:9.9.9" >/dev/null
[[ -z "$(docker images -q --filter "reference=$REGISTRY_REF" 2>/dev/null)" ]] && local_gone=yes || local_gone=no
assert_eq "no local image under $REGISTRY_REF before the run (the pull is real)" yes "$local_gone"
create_marker

say "production branch: no seam, pull by digest"
write_desired 9.9.9 "$REGISTRY_REF:9.9.9" "$DIGEST" "$(utc -60)" "$(utc 0)"
sleep 1
T0="$(date +%s)"
LOCAL_IMAGE_SEAM= run_updater
assert_eq "updater exit code" 0 "$UPDATER_RC"
assert_eq "state.outcome" applied "$(state outcome)"
assert_eq "state.currentImage = the digest-pinned ref" "$PINNED" "$(state currentImage)"
assert_eq "state.currentVersion" 9.9.9 "$(state currentVersion)"
assert_eq ".env GXP_IMAGE = the digest-pinned ref" "$PINNED" "$(env_image)"
cid="$(compose ps -q app)"
assert_eq "running container image = the pinned ref" "$PINNED" "$(docker inspect -f '{{.Config.Image}}' -- "$cid")"
repo_digests="$(docker image inspect -f '{{join .RepoDigests " "}}' -- "$(docker inspect -f '{{.Image}}' -- "$cid")")"
assert_ok "the running image's RepoDigests carry $REGISTRY_REF@sha256:$DIGEST" bash -c '[[ " $1 " == *" $2 "* ]]' _ "$repo_digests" "$REGISTRY_REF@sha256:$DIGEST"
assert_eq "shadow.currentImageId is the local image ID" "$(docker inspect -f '{{.Image}}' -- "$cid")" "$(shadow currentImageId)"
assert_eq "/api/system/ready" 200 "$(ready_status)"
assert_eq "exactly one app start" 1 "$(app_starts "$T0")"
assert_eq "marker intact" written-before-the-update "$(marker)"
assert_eq "GET /api/manager/updates → updater.outcome" applied "$(manager_updater_outcome)"

say "a WRONG digest (signed) → Docker refuses the pull → pull-failed, nothing swapped"
wrong="$(printf '%s' "$DIGEST" | tr '0-9a-f' '1-9a-f0')"
write_desired 9.9.10 "$REGISTRY_REF:9.9.9" "$wrong" "$(utc -60)" "$(utc 5)"
LOCAL_IMAGE_SEAM= run_updater
assert_eq "state.outcome" pull-failed "$(state outcome)"
assert_eq ".env unchanged" "$PINNED" "$(env_image)"
assert_eq "no further app start" 1 "$(app_starts "$T0")"

say "registry stopped: 9.9.11 = a DIFFERENT image that was pulled while the registry was up → pull fails, the pinned ref is found locally → applied"
# a distinct image (one label on top of the base) pushed as 9.9.11 and pulled by digest while the registry answers —
# the pre-loaded case; then the registry goes away and the updater must find the pinned ref locally
mkdir -p -- "$WORK/context-c"
printf 'FROM %s\nLABEL de.gxp.rehearsal=offline\n' "$BASE_IMAGE" > "$WORK/context-c/Dockerfile"
docker build -q -t "$REGISTRY_REF:9.9.11" -- "$WORK/context-c" >/dev/null
docker push -q -- "$REGISTRY_REF:9.9.11" >/dev/null
DIGEST2="$(docker buildx imagetools inspect "$REGISTRY_REF:9.9.11" --format '{{println .Manifest.Digest}}' 2>/dev/null | sed 's/^sha256://')"
assert_ok "second digest is 64 hex" bash -c '[[ "$1" =~ ^[0-9a-f]{64}$ ]]' _ "$DIGEST2"
[[ "$DIGEST2" != "$DIGEST" ]] && distinct=yes || distinct=no
assert_eq "9.9.11 is a different artifact" yes "$distinct"
PINNED2="$REGISTRY_REF:9.9.11@sha256:$DIGEST2"
docker pull -q -- "$PINNED2" >/dev/null
docker rm -f -- "$REGISTRY_NAME" >/dev/null
write_desired 9.9.11 "$REGISTRY_REF:9.9.11" "$DIGEST2" "$(utc -60)" "$(utc 10)"
LOCAL_IMAGE_SEAM= run_updater
assert_eq "updater exit code" 0 "$UPDATER_RC"
assert_eq "state.outcome (offline, pinned ref known locally)" applied "$(state outcome)"
assert_eq "state.currentVersion" 9.9.11 "$(state currentVersion)"
assert_eq ".env GXP_IMAGE = the second pinned ref" "$PINNED2" "$(env_image)"
assert_eq "running container image = the second pinned ref" "$PINNED2" "$(docker inspect -f '{{.Config.Image}}' -- "$(compose ps -q app)")"
assert_eq "app starts (one more)" 2 "$(app_starts "$T0")"
assert_eq "/api/system/ready" 200 "$(ready_status)"
assert_eq "marker intact" written-before-the-update "$(marker)"

verdict rehearse-registry
