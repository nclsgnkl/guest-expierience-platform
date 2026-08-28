#!/usr/bin/env bash
# verify.sh — read-only report on everything provision.sh configures.
#
#   tools/pi-provision/verify.sh
#
# Changes nothing. Run it after a provisioning reboot, and again whenever a
# box behaves oddly — the answer to "is this machine still set up the way we
# decided" should never require remembering ten separate commands.
#
# Lines marked FAIL are the ones that matter; WARN is informational.
set -uo pipefail
export LC_ALL=C

fails=0
ok()   { printf '  \033[32mOK  \033[0m %-34s %s\n' "$1" "${2-}"; }
bad()  { printf '  \033[31mFAIL\033[0m %-34s %s\n' "$1" "${2-}"; fails=$((fails+1)); }
warn() { printf '  \033[33mWARN\033[0m %-34s %s\n' "$1" "${2-}"; }
head_() { printf '\n== %s\n' "$*"; }

head_ "board"
model="$(tr -d '\0' < /proc/device-tree/model 2>/dev/null || echo unknown)"
ok "model" "$model"
ok "kernel" "$(uname -rm)"
ok "page size" "$(getconf PAGESIZE) bytes"

head_ "storage"
root_src="$(findmnt -no SOURCE /)"
case "$root_src" in
    /dev/nvme*) ok "root filesystem" "$root_src — NVMe" ;;
    /dev/mmcblk*) warn "root filesystem" "$root_src — still on the SD card" ;;
    *) warn "root filesystem" "$root_src" ;;
esac
ok "free on /" "$(df -h --output=avail / | tail -1 | tr -d ' ')"

head_ "memory and swap"
ok "RAM" "$(free -h | awk '/^Mem:/ {print $2}')"
if command -v zramctl >/dev/null && zramctl --noheadings 2>/dev/null | grep -q zram0; then
    ok "zram" "$(zramctl --noheadings --output NAME,ALGORITHM,DISKSIZE /dev/zram0 | tr -s ' ')"
else
    bad "zram" "no zram0"
fi
if [[ -f /var/swap ]]; then
    ok "zram writeback file" "$(du -h --apparent-size /var/swap | cut -f1) at /var/swap"
else
    warn "zram writeback file" "/var/swap missing"
fi
for key in vm.swappiness vm.page-cluster fs.inotify.max_user_watches; do
    ok "$key" "$(sysctl -n "$key")"
done

head_ "docker"
if command -v docker >/dev/null; then
    ok "docker" "$(docker --version | cut -d' ' -f3 | tr -d ,)"
    ok "compose" "$(docker compose version --short 2>/dev/null || echo '?')"
else
    bad "docker" "not installed"
fi
if grep -qw memory /sys/fs/cgroup/cgroup.controllers 2>/dev/null; then
    ok "memory cgroup" "available"
else
    bad "memory cgroup" "missing — add cgroup_enable=memory to cmdline.txt and reboot"
fi
if grep -q '"max-size"' /etc/docker/daemon.json 2>/dev/null; then
    ok "container log rotation" "configured"
else
    bad "container log rotation" "no max-size in /etc/docker/daemon.json"
fi

head_ "clock"
if [[ -e /sys/class/rtc/rtc0 ]]; then
    ok "rtc" "$(cat /sys/class/rtc/rtc0/name)"
else
    bad "rtc" "no rtc0"
fi
chg="$(cat /sys/class/rtc/rtc0/charging_voltage 2>/dev/null || echo '')"
if [[ "$chg" == "0" || -z "$chg" ]]; then
    bad "rtc battery charging" "off — add dtparam=rtc_bbat_vchg=3000000 and reboot"
else
    ok "rtc battery charging" "${chg} uV"
fi
ok "time source" "$(timedatectl show -p NTPSynchronized --value) (NTP synchronised)"

head_ "cooling"
ok "temperature" "$(vcgencmd measure_temp 2>/dev/null | cut -d= -f2)"
thr="$(vcgencmd get_throttled 2>/dev/null | cut -d= -f2)"
if [[ "$thr" == "0x0" ]]; then
    ok "throttling" "never since boot"
else
    warn "throttling" "$thr — see the Raspberry Pi docs for the bit meanings"
fi
if [[ -e /sys/class/thermal/cooling_device0 ]]; then
    ok "fan" "$(cat /sys/class/thermal/cooling_device0/type) at step $(cat /sys/class/thermal/cooling_device0/cur_state)/$(cat /sys/class/thermal/cooling_device0/max_state)"
else
    warn "fan" "no cooling device — is the fan on the Pi's own header?"
fi

head_ "dev toolchain (absent on a prod box by design)"
for pair in "java:-version" "node:-v" "npm:-v" "claude:--version"; do
    cmd="${pair%%:*}"; flag="${pair##*:}"
    if command -v "$cmd" >/dev/null; then
        ok "$cmd" "$("$cmd" "$flag" 2>&1 | head -1)"
    else
        warn "$cmd" "not installed"
    fi
done

head_ "network"
iface="$(ip route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="dev") print $(i+1); exit}')"
if [[ "$iface" == wlan* ]]; then
    sig="$(iw dev "$iface" link 2>/dev/null | awk '/signal:/ {print $2, $3}')"
    warn "uplink" "$iface (wireless), signal ${sig:-unknown}"
else
    ok "uplink" "${iface:-unknown}"
fi

printf '\n'
if [[ $fails -eq 0 ]]; then
    printf 'all checks passed\n'
else
    printf '%d check(s) FAILED\n' "$fails"
    exit 1
fi
