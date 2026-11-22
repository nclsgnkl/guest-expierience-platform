#!/usr/bin/env bash
# rehearse-systemd.sh — the PRODUCTION unit, once, on the developer box
# (update-channel T-20c, review A-4/A-5, REQ-718/724): install.sh installs
# the real units under /etc/systemd/system, the timer is enabled, and
# gxp-updater.service is started by systemd against a throwaway compose
# project — with every hardening line of gxp-updater.service in effect.
#
#   sudo backend/updater/test/rehearse-systemd.sh
#
# REFUSES to run when /etc/gxp or the units already exist (a real install
# must never be clobbered). Flow: throwaway project under /opt (PrivateTmp
# hides /tmp, ProtectHome hides /home) → /etc/gxp/updater.conf pointing at
# it and at the TEST key set → `install.sh --current-version 1.0.0` → timer
# enabled, shadow seeded → run 1: no request → exit 0 → run 2: a rehearsal
# seam in the conf → refused (exit 2, journal line) → run 3: a real request
# for an image pushed to a local registry:2 → the digest-pull branch under
# systemd → applied → `install.sh --uninstall` → no unit, no timer, no
# file left. Cleans up the registry and the project.
# shellcheck source=rehearsal-lib.sh
GXP_REHEARSAL_WORK_BASE=/opt
source "$(dirname -- "${BASH_SOURCE[0]}")/rehearsal-lib.sh"

for f in /etc/gxp /etc/systemd/system/gxp-updater.service /etc/systemd/system/gxp-updater.timer /usr/local/lib/gxp /var/lib/gxp-updater; do
    [[ ! -e "$f" ]] || { echo "refusing: $f exists — this box has a real updater install" >&2; rm -rf -- "$WORK"; exit 2; }
done

REGISTRY_NAME="gxp-rehearsal-registry"
REGISTRY_REF="localhost:5000/gxp-backend"
cleanup_all() {
    "$UPDATER_DIR/install.sh" --uninstall >/dev/null 2>&1 || true
    docker rm -f -- "$REGISTRY_NAME" >/dev/null 2>&1 || true
    cleanup   # compose down first: a pinned ref cannot be untagged while the app container still runs it
    untag_registry_refs "$REGISTRY_REF"
    docker rmi -- registry:2 >/dev/null 2>&1 || true
}
trap cleanup_all EXIT

journal_since() { # EPOCH → the unit's journal lines since then
    journalctl -t gxp-updater --since "@$1" --no-pager -o cat 2>/dev/null || true
}
unit_result() { systemctl show -p Result -p ExecMainStatus gxp-updater.service | tr '\n' ' ' | sed 's/ $//'; }

prepare_project
say "conf for the throwaway project + install.sh --current-version 1.0.0"
install -d -m 0755 /etc/gxp
# the unit runs with ProtectHome=yes: hand it a root-owned copy of the TEST set under the /opt work dir, not a checkout path
UNIT_TEST_KEYS="$WORK/test-signing-public-keys.pem"
install -m 0644 -- "$TEST_PUBLIC_KEY" "$UNIT_TEST_KEYS"
(
    umask 077
    {
        printf 'GXP_COMPOSE_DIR=%s\n' "$COMPOSE_DIR"
        printf 'GXP_COMPOSE_PROJECT=%s\n' "$PROJECT"
        printf 'GXP_BACKUP_DIR=%s\n' "$BACKUPS"
        printf 'GXP_STATE_FILE=%s\n' "$SHADOW"
        printf 'GXP_PUBLIC_KEYS=%s\n' "$UNIT_TEST_KEYS"
        printf 'GXP_HEALTH_TIMEOUT=180\nGXP_HEALTH_HOLD=5\n'
    } > /etc/gxp/updater.conf
)
install_out="$("$UPDATER_DIR/install.sh" --current-version 1.0.0 2>&1)" && install_rc=0 || install_rc=$?
printf '%s\n' "$install_out" | sed 's/^/     install: /'
assert_eq "install.sh exit code" 0 "$install_rc"
assert_eq "timer enabled" enabled "$(systemctl is-enabled gxp-updater.timer 2>/dev/null)"
assert_eq "timer active" active "$(systemctl is-active gxp-updater.timer 2>/dev/null)"
assert_eq "timer listed" 1 "$(systemctl list-timers --all --no-legend 2>/dev/null | grep -c gxp-updater.timer)"
assert_eq "script installed 0755 root" "755 0" "$(stat -c '%a %u' /usr/local/lib/gxp/gxp-updater.sh)"
assert_eq "host key SET installed = the repo resource" "" "$(cmp /etc/gxp/update-signing-public-keys.pem "$BACKEND_DIR/app/src/main/resources/update/signing-public-keys.pem" 2>&1)"
assert_eq "install prints two fingerprints" 2 "$(printf '%s\n' "$install_out" | grep -c 'trust set key [12] SHA-256')"
assert_eq "shadow seeded (A-9)" 1.0.0 "$(shadow currentVersion)"
assert_eq "shadow mode" 600 "$(stat -c %a -- "$SHADOW")"
systemctl disable --now gxp-updater.timer >/dev/null 2>&1   # the rest is driven by hand; the timer's own firing is not part of the count

say "run 1 under systemd: no request → exit 0 (hardening lines in effect)"
T1="$(date +%s)"; sleep 1
systemctl start gxp-updater.service && rc=0 || rc=$?
assert_eq "systemctl start exit code" 0 "$rc"
assert_eq "unit result" "Result=success ExecMainStatus=0" "$(unit_result)"
assert_eq "no configuration error in the journal" 0 "$(journal_since "$T1" | grep -c 'configuration error')"

say "run 2 under systemd: a rehearsal seam in the conf → refused before anything is touched"
printf 'GXP_REHEARSAL_LOCAL_IMAGE=1\n' >> /etc/gxp/updater.conf
write_desired 9.9.9 "$BASE_IMAGE" "$(image_id "$BASE_IMAGE")" "$(utc -60)" "$(utc 0)"
T2="$(date +%s)"; sleep 1
systemctl start gxp-updater.service >/dev/null 2>&1 && rc=0 || rc=$?
assert_eq "systemctl start fails" 1 "$(( rc != 0 ))"
assert_eq "unit exit status 2 (fail_config)" "Result=exit-code ExecMainStatus=2" "$(unit_result)"
assert_eq "journal names the refusal" 1 "$(journal_since "$T2" | grep -c 'rehearsal seams are refused under systemd')"
[[ -e "$EXCHANGE/state.json" ]] && touched=yes || touched=no
assert_eq "nothing written to the exchange" no "$touched"
sed -i '/^GXP_REHEARSAL_LOCAL_IMAGE=/d' /etc/gxp/updater.conf
rm -f -- "$EXCHANGE/desired-update.json"

say "run 3 under systemd: a real request, digest pull from a local registry:2, the hardened unit applies it"
docker run -d --rm --name "$REGISTRY_NAME" -p 127.0.0.1:5000:5000 registry:2 >/dev/null
for _ in $(seq 1 30); do curl -fs http://127.0.0.1:5000/v2/ >/dev/null 2>&1 && break; sleep 1; done
docker tag -- "$BASE_IMAGE" "$REGISTRY_REF:9.9.9"
docker push -q -- "$REGISTRY_REF:9.9.9" >/dev/null
DIGEST="$(docker buildx imagetools inspect "$REGISTRY_REF:9.9.9" --format '{{println .Manifest.Digest}}' 2>/dev/null | sed 's/^sha256://')"
docker rmi -- "$REGISTRY_REF:9.9.9" >/dev/null
PINNED="$REGISTRY_REF:9.9.9@sha256:$DIGEST"
create_marker
write_desired 9.9.9 "$REGISTRY_REF:9.9.9" "$DIGEST" "$(utc -60)" "$(utc 5)"
sleep 1
T3="$(date +%s)"
systemctl start gxp-updater.service && rc=0 || rc=$?
journal_since "$T3" | sed 's/^/     journal: /'
assert_eq "systemctl start exit code" 0 "$rc"
assert_eq "unit result" "Result=success ExecMainStatus=0" "$(unit_result)"
assert_eq "journal: applying" 1 "$(journal_since "$T3" | grep -c 'applying version=9.9.9')"
assert_eq "journal: outcome=applied" 1 "$(journal_since "$T3" | grep -c 'outcome=applied version=9.9.9')"
assert_eq "state.outcome" applied "$(state outcome)"
assert_eq ".env GXP_IMAGE = the digest-pinned ref" "$PINNED" "$(env_image)"
assert_eq "running container image" "$PINNED" "$(docker inspect -f '{{.Config.Image}}' -- "$(compose ps -q app)")"
assert_eq "/api/system/ready" 200 "$(ready_status)"
assert_eq "exactly one app start" 1 "$(app_starts "$T3")"
assert_eq "marker intact" written-before-the-update "$(marker)"
assert_eq "dump taken into the backup dir" 1 "$(find "$BACKUPS" -maxdepth 1 -type f -name 'pre-update-9.9.9-*.dump' | wc -l)"
assert_eq "shadow.currentVersion" 9.9.9 "$(shadow currentVersion)"
assert_eq "GET /api/manager/updates → updater.outcome" applied "$(manager_updater_outcome)"

say "install.sh --uninstall → no unit, no timer, no file"
"$UPDATER_DIR/install.sh" --uninstall | sed 's/^/     uninstall: /'
assert_eq "no gxp timer listed" 0 "$(systemctl list-timers --all --no-legend 2>/dev/null | grep -c gxp)"
assert_eq "no gxp unit files" 0 "$(systemctl list-unit-files --no-legend 2>/dev/null | grep -c gxp-updater)"
for f in /etc/gxp /usr/local/lib/gxp /etc/systemd/system/gxp-updater.service /etc/systemd/system/gxp-updater.timer; do
    [[ -e "$f" ]] && left=yes || left=no
    assert_eq "removed: $f" no "$left"
done
[[ -e "$SHADOW" ]] && left=yes || left=no
assert_eq "shadow removed" no "$left"

verdict rehearse-systemd
