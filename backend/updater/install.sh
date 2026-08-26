#!/usr/bin/env bash
# install.sh — installs the host-side updater (update-channel REQ-718, REQ-725).
#
#   sudo backend/updater/install.sh [--replace-key] [--current-version MAJOR.MINOR.PATCH[-Q]]
#   sudo backend/updater/install.sh --uninstall
#
# --current-version seeds the updater's shadow state (only when none exists)
# with the version running on the box, so the host-side downgrade refusal is
# armed from day one instead of from the first applied update (review A-9).
# --uninstall disables the timer and removes the units, the script, the key
# set, the conf and the shadow state; the pre-update dumps are left in place.
#
# Copies gxp-updater.sh to /usr/local/lib/gxp/ (0755), the systemd units to
# /etc/systemd/system/, updater.conf.example to /etc/gxp/updater.conf (only
# if absent), and the release signing TRUST SET (one PEM, 2..4 PUBLIC KEY
# blocks — key 1 active, key 2 dormant; review A-1) from
# ../app/src/main/resources/update/signing-public-keys.pem to
# /etc/gxp/update-signing-public-keys.pem (0644). REFUSES when a host set
# exists and differs — a changed trust root is a deliberate act, never a
# side effect of re-running an installer; pass --replace-key to do it on
# purpose (that also forgets a rotation the updater's shadow state
# remembers — the installed file is the set again). Then: backup dir 0700,
# daemon-reload, enable --now the timer.
set -euo pipefail
export LC_ALL=C

HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_KEY="$HERE/../app/src/main/resources/update/signing-public-keys.pem"
LIB_DIR=/usr/local/lib/gxp
ETC_DIR=/etc/gxp
HOST_KEY="$ETC_DIR/update-signing-public-keys.pem"
CONF="$ETC_DIR/updater.conf"

replace_key=0
uninstall=0
current_version=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --replace-key) replace_key=1; shift ;;
        --uninstall) uninstall=1; shift ;;
        --current-version) current_version="${2:-}"; shift 2 ;;
        *) echo "usage: install.sh [--replace-key] [--current-version VERSION] | --uninstall" >&2; exit 2 ;;
    esac
done

[[ $EUID -eq 0 ]] || { echo "install.sh must run as root" >&2; exit 2; }
conf_value() { sed -n "s/^$1=//p" -- "$CONF" 2>/dev/null | tail -n 1 | sed "s/^[\"']//; s/[\"']\$//"; }
if [[ $uninstall -eq 1 ]]; then
    state_file="$(conf_value GXP_STATE_FILE)"
    [[ -n "$state_file" ]] || state_file=/var/lib/gxp-updater/state.json
    systemctl disable --now gxp-updater.timer >/dev/null 2>&1 || true
    systemctl stop gxp-updater.service >/dev/null 2>&1 || true
    rm -f -- /etc/systemd/system/gxp-updater.timer /etc/systemd/system/gxp-updater.service
    systemctl daemon-reload
    rm -f -- "$LIB_DIR/gxp-updater.sh" "$HOST_KEY" "$CONF" "$state_file"
    rmdir -- "$LIB_DIR" "$ETC_DIR" "$(dirname -- "$state_file")" 2>/dev/null || true
    echo "uninstalled gxp-updater (units, script, key set, conf, shadow state); pre-update dumps were left in place"
    exit 0
fi
for f in gxp-updater.sh gxp-updater.service gxp-updater.timer updater.conf.example; do
    [[ -f "$HERE/$f" ]] || { echo "missing: $HERE/$f" >&2; exit 2; }
done
[[ -f "$SOURCE_KEY" ]] || { echo "missing public key resource: $SOURCE_KEY" >&2; exit 2; }
# the resource must be a trust SET: every block an Ed25519 public key, at least two, none repeated
GXP_UPDATER_LIBRARY_ONLY=1 source "$HERE/gxp-updater.sh"
set_tokens="$(pem_tokens "$SOURCE_KEY")" || { echo "the resource is not a set of 1..4 distinct PUBLIC KEY blocks" >&2; exit 2; }
[[ "$set_tokens" == *,* ]] || { echo "the resource holds ONE key — the trust set must ship key 1 (active) and key 2 (dormant), review A-1" >&2; exit 2; }
for tool in docker openssl flock systemctl; do
    command -v -- "$tool" >/dev/null 2>&1 || { echo "missing tool on this host: $tool" >&2; exit 2; }
done

# the trust root: refuse a silent change
if [[ -f "$HOST_KEY" ]] && ! cmp -s -- "$HOST_KEY" "$SOURCE_KEY"; then
    if [[ $replace_key -ne 1 ]]; then
        echo "REFUSED: $HOST_KEY exists and differs from $SOURCE_KEY." >&2
        echo "         Replacing the trust root changes which releases this box accepts." >&2
        echo "         Re-run with --replace-key if that is intended." >&2
        exit 1
    fi
    echo "!!! REPLACING the update signing trust root at $HOST_KEY (--replace-key) !!!" >&2
fi
if [[ $replace_key -eq 1 ]]; then
    # a rotation the shadow remembers would otherwise keep beating the file just installed
    shadow="$(sed -n 's/^GXP_STATE_FILE=//p' -- "$CONF" 2>/dev/null | tail -n 1 | sed "s/^[\"']//; s/[\"']\$//")"
    [[ -n "$shadow" ]] || shadow=/var/lib/gxp-updater/state.json
    if [[ -f "$shadow" ]] && grep -q '^  "trustedKeys": "' -- "$shadow"; then
        sed -i 's/^  "trustedKeys": ".*",$/  "trustedKeys": null,/; s/^  "trustedKeysVersion": ".*",$/  "trustedKeysVersion": null,/' -- "$shadow"
        echo "forgot the rotated trust set in $shadow (--replace-key)" >&2
    fi
fi

install -d -m 0755 -- "$LIB_DIR" "$ETC_DIR"
install -m 0755 -o root -g root -- "$HERE/gxp-updater.sh" "$LIB_DIR/gxp-updater.sh"
install -m 0644 -o root -g root -- "$SOURCE_KEY" "$HOST_KEY"
if [[ ! -f "$CONF" ]]; then
    install -m 0600 -o root -g root -- "$HERE/updater.conf.example" "$CONF"
    echo "wrote $CONF from the example — check GXP_COMPOSE_DIR"
fi

# the compose directory named in the conf must hold the deployment unit
# (systemd's EnvironmentFile syntax allows surrounding quotes — strip one pair)
compose_dir="$(conf_value GXP_COMPOSE_DIR)"
backup_dir="$(conf_value GXP_BACKUP_DIR)"
state_file="$(conf_value GXP_STATE_FILE)"
[[ -n "$compose_dir" ]] || compose_dir=/opt/gxp/backend
[[ -n "$backup_dir" ]] || backup_dir=/var/lib/gxp/backups
[[ -n "$state_file" ]] || state_file=/var/lib/gxp-updater/state.json
[[ -f "$compose_dir/compose.yaml" ]] || { echo "no compose.yaml in GXP_COMPOSE_DIR=$compose_dir (edit $CONF)" >&2; exit 1; }
# the updater reads the deployment through `docker compose config` as root with NO environment but the
# conf: every variable compose.yaml requires (GXP_DB_PASSWORD, GXP_TLS_HOST, …) must be in that directory's
# .env, or every run ends in a configuration error and the box never updates (review 06 #2)
project="$(conf_value GXP_COMPOSE_PROJECT)"
[[ -n "$project" ]] || project=gxp
if ! compose_err="$(env -i PATH="$PATH" HOME=/root docker compose --project-directory "$compose_dir" --project-name "$project" config -q 2>&1)"; then
    echo "REFUSED: \`docker compose config\` fails in $compose_dir — the updater would fail the same way on every run." >&2
    echo "         Put every variable compose.yaml requires into $compose_dir/.env (README, \"First run\"). Compose said:" >&2
    printf '         %s\n' "$compose_err" >&2
    exit 1
fi
install -d -m 0700 -o root -g root -- "$backup_dir" "$(dirname -- "$state_file")"
# ProtectHome=yes in the unit: a compose dir under /home is invisible to the updater
[[ "$compose_dir" != /home/* && "$compose_dir" != /root/* ]] || { echo "GXP_COMPOSE_DIR=$compose_dir is under /home or /root — the unit runs with ProtectHome=yes; move the deployment (e.g. /opt/gxp/backend)" >&2; exit 1; }
if [[ -n "$current_version" ]]; then
    image="$(sed -n 's/^GXP_IMAGE=//p' -- "$compose_dir/.env" 2>/dev/null | tail -n 1)"
    [[ -n "$image" ]] || image="unknown"
    if [[ -e "$state_file" ]]; then
        echo "shadow state $state_file exists — --current-version ignored (it never overwrites)"
    else
        GXP_STATE_FILE="$state_file" seed_shadow "$current_version" "$image" || { echo "invalid --current-version (MAJOR.MINOR.PATCH[-QUALIFIER]) or image" >&2; exit 2; }
        echo "seeded $state_file with currentVersion=$current_version (downgrade refusal armed)"
    fi
fi

install -m 0644 -o root -g root -- "$HERE/gxp-updater.service" /etc/systemd/system/gxp-updater.service
install -m 0644 -o root -g root -- "$HERE/gxp-updater.timer" /etc/systemd/system/gxp-updater.timer
systemctl daemon-reload
systemctl enable --now gxp-updater.timer
echo "installed: $LIB_DIR/gxp-updater.sh, $HOST_KEY, $CONF, gxp-updater.timer (every 5 min)"
n=0
IFS=, read -r -a tokens <<< "$set_tokens"
for token in "${tokens[@]}"; do
    n=$((n + 1))
    echo "trust set key $n SHA-256: $(printf '%s' "$token" | base64 -d | openssl dgst -sha256 | sed 's/^.*= //')"
done
