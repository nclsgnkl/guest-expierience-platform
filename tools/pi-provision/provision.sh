#!/usr/bin/env bash
# provision.sh — host setup for the Raspberry Pi 5 boxes this project runs on.
#
#   sudo tools/pi-provision/provision.sh --role dev    # build + agent machine
#   sudo tools/pi-provision/provision.sh --role prod   # delivery box
#
# Idempotent: re-running changes nothing that is already in place. Every file
# this script edits keeps ONE `.gxp-orig` copy from the first time it was
# touched, so the shipped state stays recoverable.
#
# Two roles share one baseline. `prod` gets only what the deployment unit in
# backend/compose.yaml needs to run: docker with memory cgroups, swap sized
# for the box, container log rotation, a charged RTC. `dev` adds the build
# toolchain on top — a delivery box standing in a building has no business
# carrying a compiler.
#
# Deliberately NOT done here, because each is per-person or per-machine and
# must not happen as a side effect of running an installer (see README):
#   * creating an SSH key or registering it with GitHub
#   * cloning the repository, or recreating packages/node_modules
#   * downloading Playwright browsers (needs the frontend's node_modules)
#   * logging Claude Code in
#
# Verified on Raspberry Pi OS Trixie (Debian 13) arm64, Pi 5 8 GB, NVMe boot.
set -euo pipefail
export LC_ALL=C

# --- pins -------------------------------------------------------------------
# Node 24: @angular/cli 22 declares engines.node "^22.22.3 || ^24.15.0 || >=26".
# 24 is the LTS line with the longest remaining support of the three.
NODE_MAJOR=24
# JDK 21: backend/app/pom.xml sets maven.compiler.release=21.
JDK_VERSION=21
# rpi-swap caps zram AND its writeback file at 2048 MiB — a default sized for
# 1 GB boards. 4096 is what systemd-zram-generator would have chosen on its own
# (50% of RAM, max 4 GiB); the writeback file is cheap on NVMe and would have
# been a mistake back when this ran off an SD card.
ZRAM_MIB=4096
SWAPFILE_MIB=8192

usage() { echo "usage: sudo provision.sh --role dev|prod [--skip-upgrade]" >&2; exit 2; }

role=""
skip_upgrade=0
while [[ $# -gt 0 ]]; do
    case "$1" in
        --role) role="${2:-}"; shift 2 ;;
        --skip-upgrade) skip_upgrade=1; shift ;;
        *) usage ;;
    esac
done
[[ "$role" == "dev" || "$role" == "prod" ]] || usage
[[ $EUID -eq 0 ]] || { echo "provision.sh must run as root" >&2; exit 2; }

target_user="${SUDO_USER:-}"
if [[ -z "$target_user" || "$target_user" == "root" ]]; then
    echo "REFUSED: run this with sudo from your normal account, not as root." >&2
    echo "         The docker group, the linger flag and the npm settings all" >&2
    echo "         belong to a real login, and root is not one." >&2
    exit 1
fi

# --- platform guard ---------------------------------------------------------
# This script writes to /boot/firmware. On anything but a Pi 5 that is at best
# useless and at worst destructive, so refuse loudly instead of guessing.
[[ "$(uname -m)" == "aarch64" ]] || { echo "REFUSED: not aarch64." >&2; exit 1; }
[[ -f /boot/firmware/config.txt ]] || { echo "REFUSED: no /boot/firmware/config.txt — not a Raspberry Pi OS box." >&2; exit 1; }
model="$(tr -d '\0' < /proc/device-tree/model 2>/dev/null || true)"
case "$model" in
    *"Raspberry Pi 5"*) : ;;
    *) echo "REFUSED: this board reports '$model', not a Raspberry Pi 5." >&2
       echo "         The swap sizes and the RTC parameter below assume one." >&2
       exit 1 ;;
esac

needs_reboot=0
note() { printf '  %s\n' "$*"; }
step() { printf '\n== %s\n' "$*"; }

# write $2 to $1 only when it differs; keep one .gxp-orig of the original
write_file() {
    local path=$1 content=$2
    if [[ -f "$path" && "$(cat -- "$path")" == "$content" ]]; then
        note "unchanged  $path"; return 0
    fi
    install -d -m 0755 -- "$(dirname -- "$path")"
    if [[ -f "$path" && ! -f "$path.gxp-orig" ]]; then cp -a -- "$path" "$path.gxp-orig"; fi
    printf '%s\n' "$content" > "$path"
    note "wrote      $path"
}

# append one whole line to a config file, once
ensure_line() {
    local file=$1 line=$2
    if grep -qxF -- "$line" "$file" 2>/dev/null; then note "present    $file: $line"; return 0; fi
    if [[ -f "$file" && ! -f "$file.gxp-orig" ]]; then cp -a -- "$file" "$file.gxp-orig"; fi
    printf '%s\n' "$line" >> "$file"
    note "appended   $file: $line"
    needs_reboot=1
}

# append a parameter to the single line of cmdline.txt, once
ensure_cmdline_param() {
    local param=$1 file=/boot/firmware/cmdline.txt
    if grep -qF -- "$param" "$file"; then note "present    cmdline: $param"; return 0; fi
    if [[ ! -f "$file.gxp-orig" ]]; then cp -a -- "$file" "$file.gxp-orig"; fi
    # cmdline.txt is ONE line and the kernel honours the LAST value of a
    # repeated parameter. That is the whole trick: the firmware puts
    # cgroup_disable=memory near the front, ours goes behind it and wins.
    sed -i -- "1 s|\$| $param|" "$file"
    note "appended   cmdline: $param"
    needs_reboot=1
}

step "base packages"
apt-get update -qq
if [[ $skip_upgrade -eq 0 ]]; then apt-get full-upgrade -y -qq; fi
apt-get install -y -qq \
    ca-certificates curl gnupg git jq ripgrep tmux htop \
    util-linux-extra iw

step "memory cgroups for docker"
# Without these, docker silently loses per-container memory limits: no
# mem_limit, no memory numbers in `docker stats`, no OOM isolation between
# the app and PostgreSQL. Raspberry Pi OS disables the memory controller by
# default to save about one percent of RAM.
ensure_cmdline_param "cgroup_enable=memory"
ensure_cmdline_param "cgroup_memory=1"

step "RTC battery"
# The Pi 5 has an on-board RTC; the battery on J5 is NOT charged unless this
# is set. It matters most on the delivery box: that one runs offline by
# constitution, so after a power cut there is no NTP to correct a wrong clock
# — and a wrong clock breaks the instance's own TLS certificate validity,
# device-token expiry and every audit timestamp.
ensure_line /boot/firmware/config.txt "dtparam=rtc_bbat_vchg=3000000"

step "swap"
write_file /etc/rpi/swap.conf.d/10-gxp.conf "\
# Managed by tools/pi-provision/provision.sh — see that file for the reasoning.
# rpi-swap's own defaults are RamMultiplier=1 with MaxSizeMiB=2048 for both
# zram and its writeback file; the cap is what bites on an 8 GB board.
[Zram]
FixedSizeMiB=${ZRAM_MIB}
MaxSizeMiB=${ZRAM_MIB}

[File]
Path=/var/swap
FixedSizeMiB=${SWAPFILE_MIB}
MaxSizeMiB=${SWAPFILE_MIB}"

step "sysctl"
write_file /etc/sysctl.d/80-gxp.conf "\
# Managed by tools/pi-provision/provision.sh.
# Compressing a page into zram is far cheaper than writing it to disk, so the
# kernel may reclaim more eagerly than the default 60.
vm.swappiness=100
# vm.page-cluster is deliberately absent: rpi-swap's generator already writes
# page-cluster=0 to /run/sysctl.d/70-rpi-swap.conf. Setting it twice would
# only create a second place to look.
# An Angular node_modules under a watching build exhausts the Debian default
# long before anything else does; the symptom is an ENOSPC that names no file.
fs.inotify.max_user_watches=524288
fs.inotify.max_user_instances=1024"
sysctl --system >/dev/null
note "sysctl reloaded"

step "docker"
if ! command -v docker >/dev/null 2>&1; then
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg
    codename="$(. /etc/os-release && echo "$VERSION_CODENAME")"
    # Docker publishes per-release; fall back one release rather than fail the
    # whole run. The packages are compatible.
    if ! curl -fsI "https://download.docker.com/linux/debian/dists/$codename/Release" >/dev/null 2>&1; then
        note "docker has no '$codename' suite; falling back to bookworm"
        codename=bookworm
    fi
    write_file /etc/apt/sources.list.d/docker.list \
        "deb [arch=arm64 signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian $codename stable"
    apt-get update -qq
    apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
else
    note "docker already installed ($(docker --version))"
fi

# Without rotation a container log grows until the filesystem is full. The
# delivery box runs the same container for months, so this is not cosmetic.
write_file /etc/docker/daemon.json '{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" }
}'
systemctl restart docker
if id -nG "$target_user" | grep -qw docker; then
    note "$target_user already in group docker"
else
    usermod -aG docker "$target_user"
    note "added $target_user to group docker — needs a fresh login"
fi

step "user session"
# Agents are supposed to keep working after the SSH session goes away.
loginctl enable-linger "$target_user"
note "linger enabled for $target_user"

if [[ "$role" == "dev" ]]; then
    step "dev toolchain: JDK ${JDK_VERSION}"
    apt-get install -y -qq "openjdk-${JDK_VERSION}-jdk-headless" build-essential pkg-config
    # Maven itself is not installed on purpose: backend/app ships ./mvnw.

    step "dev toolchain: Node ${NODE_MAJOR}"
    if ! command -v node >/dev/null 2>&1; then
        install -m 0755 -d /etc/apt/keyrings
        curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key \
            | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg
        chmod a+r /etc/apt/keyrings/nodesource.gpg
        write_file /etc/apt/sources.list.d/nodesource.list \
            "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_${NODE_MAJOR}.x nodistro main"
        write_file /etc/apt/preferences.d/nodesource "Package: nodejs
Pin: origin deb.nodesource.com
Pin-Priority: 600"
        apt-get update -qq
        apt-get install -y -qq nodejs
    else
        note "node already installed ($(node -v))"
    fi

    step "dev toolchain: npm patience"
    # This box builds over Wi-Fi and runs unattended agents. npm aborts a whole
    # install on a single stalled socket, which is exactly what a brief radio
    # dropout produces. These are retries, not a workaround for a broken link.
    sudo -u "$target_user" npm config set fetch-retries 5
    sudo -u "$target_user" npm config set fetch-retry-mintimeout 20000
    sudo -u "$target_user" npm config set fetch-retry-maxtimeout 120000
    sudo -u "$target_user" npm config set fetch-timeout 300000
    note "npm retry settings written for $target_user"

    step "dev toolchain: Claude Code"
    if ! command -v claude >/dev/null 2>&1; then
        npm install -g @anthropic-ai/claude-code >/dev/null
        note "installed $(claude --version 2>/dev/null || echo '@anthropic-ai/claude-code')"
    else
        note "claude already installed ($(claude --version 2>/dev/null || true))"
    fi
    note "login is manual: run 'claude' once in the repository"
fi

step "done — role: $role"
if [[ $needs_reboot -eq 1 ]]; then
    echo
    echo "  A REBOOT IS REQUIRED: /boot/firmware was changed (cgroups and/or RTC)."
    echo "  Afterwards run: tools/pi-provision/verify.sh"
else
    echo
    echo "  No boot configuration changed. Run tools/pi-provision/verify.sh to check the result."
fi
