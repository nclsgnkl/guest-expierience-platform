# Host provisioning for the Raspberry Pi 5 boxes

Two machines run this project and they are meant to be twins: a **dev** box
where agents build and test, and a **prod** box that runs the deployment unit
in `backend/compose.yaml` inside a building. Everything below exists so the
second one never becomes an afternoon of rediscovery.

`provision.sh` is the executable part. This file is the part that explains
*why* each line is in it — the half that gets lost otherwise.

## Usage

```bash
sudo tools/pi-provision/provision.sh --role dev     # build + agent machine
sudo tools/pi-provision/provision.sh --role prod    # delivery box
tools/pi-provision/verify.sh                        # read-only status report
```

Both scripts are idempotent. `provision.sh` keeps one `.gxp-orig` copy of every
file it touches for the first time, and refuses to run on anything that is not
an aarch64 Raspberry Pi 5 — it writes to `/boot/firmware`, and doing that on
the wrong machine is not a recoverable mistake.

`--role prod` deliberately installs no JDK, no Node and no Claude Code. The
delivery box pulls a container image; it does not compile anything.

## Bringing up a box from nothing

The script covers the middle of this list. The rest is deliberately manual —
each step is either a decision or a secret, and neither belongs in an installer.

1. **Assemble the case and the NVMe first**, before the first boot. Connect the
   RTC battery to J5 while the board is open.
2. **Flash Raspberry Pi OS Lite (64-bit)** to an SD card with the Raspberry Pi
   Imager. Set the hostname (`gxp-dev`, `gxp-prod` — never leave the default,
   two boxes called `raspberrypi.local` collide on mDNS), the user, and an SSH
   **public key**. Prefer Ethernet.
3. **Boot from the SD card**, then `sudo apt update && sudo apt full-upgrade -y`
   and `sudo rpi-eeprom-update -a`.
4. **Move the system to the NVMe.** Cloning the running SD system beats writing
   a fresh image, because the user, the SSH key and the hostname come along.
   Partition the NVMe the same way (512 MiB FAT32 + ext4 rest) with an
   explicitly different disk identifier, `rsync -axHAWXS` both filesystems, then
   rewrite the PARTUUIDs in the copy's `/etc/fstab` and
   `/boot/firmware/cmdline.txt`. Skipping that last step produces a box that
   does not boot.
5. **Shut down, remove the SD card, power on.** Keep the card labelled as a
   rescue medium — do not leave it in the slot. The Pi 5 boots SD before NVMe by
   default, so inserting the card *is* the recovery procedure; leaving it in
   means a failed NVMe silently boots a stale system that answers on the same
   hostname. A machine that is alive and wrong costs more than one that is
   honestly dead.
6. **Run `provision.sh`** with the right role, then reboot.
7. **Dev box only:** create an SSH key *on that machine*
   (`ssh-keygen -t ed25519`) and register the public half as a **deploy key with
   write access** on this repository — not as an account key. A private key
   should never leave the machine it was made on, and a box running unattended
   agents should not hold credentials for every repository you own.
8. **Clone, then recreate the symlink** that `.gitignore` cannot carry:
   ```bash
   cd frontend && npm ci && cd ..
   ln -s ../frontend/node_modules packages/node_modules
   ```
   Without it the committed API client cannot resolve its own bare imports and
   the error points at the client rather than at the missing link.
9. **Dev box only:** `npx playwright install chromium` (native arm64 builds
   exist) and `claude` once to log in.
10. `tools/pi-provision/verify.sh`.

## Decisions

**Raspberry Pi OS, not Ubuntu Server.** The stack is Docker plus containers, so
the host distribution barely matters; what does matter is first-class hardware
support (PCIe/NVMe, bootloader, firmware updates) and a small footprint. Ubuntu
buys nothing here and costs RAM.

**16 KB pages kept — the distribution default.** Reverting to the 4 KB kernel
(`kernel=kernel8.img`) was considered and rejected. The stack has nothing that
bakes in a page size: the JVM queries it at runtime, the official PostgreSQL
image is Debian-based, esbuild is Go, the Rollup native modules are Rust, and
Playwright ships an arm64 Linux Chromium. Every deviation from a tested default
is a deviation that has to be explained again later. If something ever does
fail with SIGBUS on start, that one line is the escape hatch.

**PCIe left at Gen 2.** `dtparam=pciex1_gen=3` would roughly double sequential
throughput, but the builds are bound by four cores and thousands of small
files, and small-file IOPS live in the controller, not the link. Not worth
leaving officially supported territory for.

**Memory cgroups enabled.** Raspberry Pi OS ships `cgroup_disable=memory`.
Docker then loses per-container memory limits entirely. `cgroup_enable=memory
cgroup_memory=1` is appended to the single line of `cmdline.txt`, where it wins
because the kernel honours the last value of a repeated parameter.

**Swap: the distribution's mechanism, resized.** Raspberry Pi OS Trixie runs
zram *with a writeback file* (`rpi-swap`, mechanism `zram+file`) — the two-tier
design one would otherwise build by hand. Adding a second, independent swap
file beside it would have produced two mechanisms with competing priorities and
distribution services that clean up after each other. Instead the existing
knobs are raised: zram to 4096 MiB, the writeback file to 8192 MiB. Both of
rpi-swap's defaults cap at 2048 MiB, which is sized for a 1 GB board.

**`vm.swappiness=100`.** Compressing a page into zram is far cheaper than
writing it to disk, so the kernel may reclaim more eagerly than the default 60.
`vm.page-cluster` is *not* set here — rpi-swap's own generator already writes
it to `/run/sysctl.d/70-rpi-swap.conf`.

**inotify limits raised.** Unrelated to memory, but the same class of
invisible ceiling: an Angular `node_modules` under a watching build exhausts the
Debian default, and the resulting `ENOSPC` names no file and points nowhere.

**Docker log rotation.** Unbounded container logs fill the filesystem. The
delivery box runs the same container for months, so this is not cosmetic.

**RTC battery charging on.** The Pi 5's on-board RTC does not charge its battery
unless `dtparam=rtc_bbat_vchg=3000000` is set. This matters most on the box that
will never have it checked: the delivery box runs offline by constitution, so
after a power cut nothing corrects a wrong clock — and a wrong clock invalidates
the instance's own TLS certificate, device-token expiry and every audit
timestamp. The battery belongs on the prod hardware list as a requirement, not
an accessory.

**Linger enabled.** Agent processes are supposed to survive the SSH session
that started them.

**npm retry settings.** These are not a workaround for a broken network. A
single stalled socket makes npm abandon an entire install, which is exactly
what a brief Wi-Fi dropout produces on a machine that builds unattended.

## Reference numbers

Measured on the dev box (Pi 5 8 GB, NVMe, Argon NEO 5, 5 GHz Wi-Fi at about
-70 dBm). Useful mainly as a baseline: when a build later takes twice as long,
these say whether the machine changed or the code did.

| | cold | warm |
|---|---|---|
| `./mvnw verify` real | 5m33s | 3m29s |
| `./mvnw verify` user | 6m48s | 6m12s |
| peak temperature | 56 °C | 59 °C |
| throttling | none | none |

`ng build` takes about 8.5 s. The near-identical `user` times show the cold
run's extra two minutes were download waiting, not computation. The warm
parallelism of roughly 1.8 of 4 cores means two agents can build side by side
without meaningfully slowing each other down.

Throttling begins at 80 °C, so an actively cooled aluminium case leaves about
20 K of headroom under sustained load.

## Open

- **Wi-Fi.** The dev box sits at about -70 dBm on 5 GHz, partly because an
  all-aluminium case surrounds the on-board antenna. It works, but it already
  cost one aborted `npm ci`. Powerline is the low-effort wired alternative if
  it recurs.
- **RTC battery.** Charging is enabled; that the cell actually holds the clock
  across a full power removal is only proven by pulling the plug and checking
  `journalctl -b | grep rtc` after the next boot.
