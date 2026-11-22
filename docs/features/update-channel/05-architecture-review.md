# Architecture review: Update Channel — key handling and the host updater (T-20)
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Reviewer:** `architect` | **Date:** 2026-08-26 | **Scope:** REQ-728, remote-operations §6 ("key handling reviewed independently"), review escalations R3-3 / R3-6 / R3-9 / R3-10, the TLS trust-root note
**Branch:** `refactor/bce-subpackages-and-boundary-security` (uncommitted). Nothing was changed by this review.

## Verdict

**CONCERNS — fit to ship *after* four items, none of which is a redesign.**
The channel's trust chain is sound and tightly bound end to end (one
Ed25519 signature over exact bytes, two independent verifiers, a
registry-digest pin that Docker itself enforces, downgrade protection on
both sides, a root-owned guard memory). The R3 Critical (root writing
through a predictable name in the uid-185 directory) is fixed as
described and re-rehearsed. What blocks "ship" is not code quality but
**irreversibility**: a box that leaves the door trusting exactly one key
can never be told to trust another one if that key is lost, and the
production code path (systemd unit, `install.sh`, the digest pull) has
never executed even once. Both are cheap now and impossible later.

Not before:

1. **A-1** — a two-key trust set ships in the image and on the host before
   the first production box (owner decision, below).
2. **A-3** — the `place_file` race is closed (a few lines).
3. **A-4** — one execution of the production path: `install.sh` + the
   timer under systemd + a digest pull from a local `registry:2`
   (rehearsal scenario C).
4. **A-2** — key custody practice: key 1 backed up, key 2's private half
   stored separately and encrypted (owner decision, below).

Everything else is 🟡/🟢 and can follow in the normal pipeline.

## Deep-review checklist

| Area | Status | Details |
|------|--------|---------|
| Architecture | ✅ | Three components with one-directional trust: static files → app (verify, decide "available") → host (verify again, decide "apply"). The updater is the trust root and the app is hostile input to it — the right way round. One structural observation (A-9): the least-trusted component is the *initiator*; the trust root is passive. |
| Concurrency | ✅ | `flock -n` + oneshot + `TimeoutStartSec=30min` on the host; `ReentrantLock.tryLock` + `concurrentExecution = SKIP` in the app; single-row table, last-commit-wins on disjoint columns. No shared mutable state crosses the boundary except the two exchange files, both written by rename. |
| Data Layer | ✅ | V8 single row with `CHECK (id = 1)`; raw verified bytes stored in the same statement as the derived columns; `UpdateStateStore` keeps HTTP out of every transaction (D9). `pg_dump --format=custom` before any swap; the restore rung is incomplete by design (A-6). |
| External Integrations | ⚠️ | Update server: outbound `GET` only, 5 s/10 s/15 s deadlines, no redirects, 64 KiB cap, https-only — the survival table row holds. Registry: pull by digest is content-addressed and swap-proof (Q2), **but the production pull branch has never run** (A-4); the release process must pick one digest source (A-7). |
| Performance | ✅ N/A | One HTTP round-trip per hour with 0–600 s jitter; one worker thread ≤ 15 s. Nothing on the guest or manager path. |
| Security | ⚠️ | Key custody is the weak point (A-1, A-2). `place_file` still has a symlink race in the app-owned directory (A-3). Everything else re-audited and confirmed: `snapshot_file` fd-verification, shadow state, seam refusal under `INVOCATION_ID`, `pinned_ref`, regex table applied before every command line, no secret anywhere on the path. |
| Resilience | ✅ | Every failure is an outcome code, never a crash; every request is attempted at most once; rollback A/B rehearsed on the target hardware. The remaining un-rehearsed failure mode (digest pull) fails safe (`pull-failed`, no apply) — but see A-4 for why "safe" is not "acceptable" here. |
| Edge Cases | ✅ | Trailing newline / CRLF / BOM cannot break verification (bytes are signed, never text — Q2); `.sig` readers tolerate a missing final newline; `to_epoch` rejects shape-valid impossible instants; `version_le` hand-implements D6; qualifier ordering pinned in both test suites; DST gap/overlap covered by `ReconcileTest`. |

## Findings (severity-ranked)

| # | Sev | Where | Finding | Recommendation |
|---|-----|-------|---------|----------------|
| A-1 | 🔴 | `SigningKeyLoader.java:37-41`, `ManifestVerifier.java:31-36`, `gxp-updater.sh:446-447`, `install.sh:43-55`, `signing-public-key.pem` | **One key, no successor, no revocation — and this cannot be retrofitted.** Every box trusts exactly one Ed25519 key (fingerprint `256caa77…f31ff`), in two copies (image resource, `/etc/gxp/…pem`). *Loss* of the private key: the only way to introduce a new key is a release signed with the old one — impossible without it — so every deployed box needs a site visit (`install.sh --replace-key` + a rebuilt image) and the channel is dead until then. *Theft*: there is no revocation statement and downgrade protection cuts the wrong way — a thief who signs `99.0.0` locks every box that accepted it out of *our* lower-numbered releases forever. A key-1-signed rotation release only helps if we win the race. | Ship a **trust set of two keys** now: key 1 active, key 2 dormant. App: `SigningKeyLoader` loads `update/signing-public-keys/*.pem` (or a concatenated PEM), `ManifestVerifier` accepts if *any* key verifies. Host: `GXP_PUBLIC_KEY` → a directory `/etc/gxp/update-signing-keys/`, `openssl pkeyutl -verify` looped over its PEMs, `install.sh` compares the *set*. `SigningKeyResourceTest` asserts ≥ 2 distinct production keys and that neither is the test key. ~60 lines both sides + tests. This is the **minimum** that makes loss recoverable (sign with key 2 tomorrow) and theft survivable (sign a release with key 2 whose image drops key 1 — the app side then refuses key 1, which is the only path to the host). A signed key-rotation manifest is *not* needed for v1; it can be layered later. **Must ship before the first production box** — a single-key box can never learn a second key without a visit. Owner decision D-1. |
| A-2 | 🟠 | `gen-signing-key.sh:34`, handoff "key custody", `$HOME/.gxp/keys/` | **The one private key is one unencrypted file on one laptop disk.** `openssl genpkey -algorithm ed25519` without `-aes256` writes a plaintext PEM; there is no backup; the laptop's disk (or its theft) is the fleet. | (a) `gen-signing-key.sh` gains `-aes256` (passphrase-protected PEM; `sign-manifest.sh` prompts) — plaintext at rest is the difference between "laptop stolen" and "fleet stolen". (b) One offline backup of key 1 (encrypted USB/paper, not cloud). (c) Key 2's private half generated with the same tool and stored on a **different** medium, never on the signing laptop, never used until rotation. (d) Record both fingerprints in `handoff.md`/README. Owner decision D-2 — this is the owner's own practice, nobody else can decide it. |
| A-3 | 🟠 | `gxp-updater.sh:277-283` (`place_file`), used at `:291` for the exchange mirror | **Symlink race remains in the uid-185 directory.** R3-1 closed the *pre-planted* symlink (mktemp is O_EXCL, unpredictable name). But after `mktemp` returns, `cat > "$tmp"` (line 280) and `chmod "$mode" -- "$tmp"` (281) re-open the path. The app owns `_data` (Dockerfile `chown 185 /updates`, mode 0700, **not sticky** — `fs.protected_symlinks` does not apply), so an inotify watcher (IN_CREATE) can unlink the fresh temp and drop a symlink in its place inside the fork/exec gap between `mktemp` and `cat`; root then truncates the target with the state JSON and chmods it. Reachable targets under `ProtectSystem=full`: `/opt/gxp/backend/.env` (compose refuses to start → outage), `…/volumes/gxp_gxp-pgdata/_data/*` (corrupt the database), `/var/lib/gxp/backups/*` (destroy the rollback belt). No confidentiality gain for the attacker (the container cannot read host files), but a compromised app is precisely the design's threat model and this is a denial-of-service/integrity path to root-owned files. `mv -f` (282) is safe — rename never follows. | Never open a path inside `$EXCHANGE` twice. Two equivalent fixes: (a) build the file in `$WORK` (root-only, `PrivateTmp`) and `mv -f` it into `$EXCHANGE` — works only if both are on one filesystem (rename); check `stat -c %d` equality and fall back to (b). (b) Skip `mktemp`; generate the name (`.state.$RANDOM$RANDOM$$`), open **once** with `(set -C; exec 3> "$dir/$name")` — noclobber = `O_CREAT\|O_EXCL`, which refuses to follow a symlink — write through fd 3, set the mode by `umask` in the subshell instead of `chmod`, `mv -f`. Same treatment in `rehearsal-lib.sh:141`. Add a `parse.sh` case: an inotify-free approximation is fine (plant a symlink at the *name the function will pick* by seeding `RANDOM`), or at least a regression test that `place_file` never calls `chmod`/`>` on a path in `$dir`. `set_image`'s `.env.tmp` (line 531) stays acceptable: the directory is root-owned. |
| A-4 | 🟠 | `gxp-updater.sh:517-524`, `install.sh` (never executed), `gxp-updater.service` (never started) | **The production path has zero execution evidence.** All rehearsals run the `GXP_REHEARSAL_LOCAL_IMAGE=1` branch as root *without* systemd. Never executed: `docker pull -q -- ref@sha256:…`, `docker image inspect` of a digest-pinned ref, compose starting `image: name:tag@sha256:…` from `.env`, `EnvironmentFile` parsing, `ProtectSystem=full`/`PrivateTmp`/`NoNewPrivileges` compatibility, `INVOCATION_ID` refusal, `TimeoutStartSec`, `install.sh` itself. "First release proves it" is not acceptable here because the channel is the **only remote fix path**: if the digest branch is broken, every box reports `pull-failed` (safe, no brick) and *no fix release can ever reach it* — a fleet-wide site visit. | Add rehearsal **scenario C** (~40 lines in `rehearsal-lib.sh`): `docker run -d --rm -p 127.0.0.1:5000:5000 registry:2` (Docker treats `localhost`/`127.0.0.1` registries as insecure without daemon config — no host change), push the smoke tag as `localhost:5000/gxp-backend:9.9.9`, take the digest from the push output, sign, run the updater **without** the rehearsal seam, assert `applied`, `.env` = `localhost:5000/gxp-backend:9.9.9@sha256:<digest>`, the running container's `RepoDigests` contains it, then a second run with the registry stopped (offline case → local pinned ref found → no `pull-failed`). `registry:2` is a dev-box test image, not a product dependency. Separately, run `install.sh` once on the dev Pi with `GXP_COMPOSE_DIR` pointed at a throwaway project and `GXP_PUBLIC_KEY` at the test key, `systemctl start gxp-updater.service`, assert exit 0 / "nothing to do" in the journal and that a seam set in the conf is refused, then `disable --now` and remove. Record both in `handoff.md`. |
| A-5 | 🟡 | `gxp-updater.service`, R3-10 | Unit hardening is the R3-10 baseline only. `/root` is writable (no `ProtectHome`), devices, kernel tunables and cgroups are reachable, and the bounding set is full root. | Add `ProtectHome=yes` (README: the compose dir must not be under `/home`), `PrivateDevices=yes`, `ProtectKernelTunables=yes`, `ProtectKernelModules=yes`, `ProtectControlGroups=yes`, `RestrictSUIDSGID=yes`, `LockPersonality=yes`, `RestrictRealtime=yes`, `SystemCallArchitectures=native`. Skip `CapabilityBoundingSet` for now (the docker CLI needs none, but `chown --reference`, reading a 0700 uid-185 directory and `chmod` need `CAP_CHOWN CAP_DAC_OVERRIDE CAP_DAC_READ_SEARCH CAP_FOWNER CAP_FSETID` — get it wrong and the failure is silent: `fail_config` exit 2, no state, the manager sees a stale `updater`). Every hardening line must be proven by the A-4 systemd run — untested hardening on an unreachable box is worse than none. |
| A-6 | 🟡 | `gxp-updater.sh:610-620`, R3-9, D15 rung 2 | `pg_restore --clean --if-exists` restores only what the dump contains; objects a *succeeded* migration created survive, and `flyway_schema_history` is restored to the old head — the next attempt re-runs that migration into "already exists" and fails forever until a hand `DROP SCHEMA`. Rung 2 is rare (Hibernate schema validation is off — `database.generation=none` — so the old image usually boots on a newer schema and rung 1 wins), but when it fires the box is exactly the "cannot reach it" case. | In rung 2, before the restore and only after `pg_restore --list < dump` succeeds (a dump that cannot be listed is not one to wipe for): `psql -c 'DROP SCHEMA public CASCADE; CREATE SCHEMA public; ALTER SCHEMA public OWNER TO gxp;'` then `pg_restore` **without** `--clean`. No migration creates extensions or other schemas (verified — `grep CREATE EXTENSION\|CREATE SCHEMA` over `db/migration/` is empty), so `public` is the whole database. Extend rehearsal B: plant a `CREATE TABLE leftover_v999` after the dump and assert it is gone. Lead decision, recommendation: do it. |
| A-7 | 🟡 | `sign-manifest.sh:16-23`, README "Keys and signing a release" | Three digest sources are documented (`docker push` output, `buildx imagetools inspect --format '{{.Manifest.Digest}}'`, `docker image inspect RepoDigests`). For a single-arch push they agree; for a multi-arch (index) push the first two print the **index** digest while `RepoDigests` on a box may hold either the index or the platform manifest digest depending on the image store. Both forms pull correctly with `@sha256:` — the risk is a release signed with the wrong one → `pull-failed` fleet-wide (safe but a wasted release and a confused operator). | One canonical command in the README and the tool's help: `docker buildx imagetools inspect <ref> --format '{{.Manifest.Digest}}'` (index digest for multi-arch, manifest digest for single-arch — always "the thing the tag resolves to"). Delete the `RepoDigests` hint. Scenario C (A-4) proves single-arch; note that the fleet is arm64-only today, so a single-platform push is the recommended release shape until an amd64 box exists. |
| A-8 | 🟡 | `ManifestClient.java:93-97` (JDK default `SSLContext`), `Dockerfile.jvm` base `ubi9/openjdk-21-runtime:1.24`, host `docker pull` | **TLS trust roots.** The app trusts the image JDK's `cacerts`, which in this base image is a symlink to the UBI system bundle (`ca-certificates-2025.2.80`; verified in the image: `…/lib/security/cacerts → /etc/pki/java/cacerts`). It is refreshed only by rebuilding from a newer base tag — i.e. through the channel itself. The updater's `docker pull` trusts the host's bundle, which nothing refreshes (host OS is out of scope). Let's Encrypt: ISRG Root X1 expires 2035-06, X2 2040 — **not** a fleet-bricking scenario within any plausible product life, and both are in the 2025 bundle. The realistic freeze: the update host or registry moves to a CA whose root is absent from the *oldest* deployed image/host → every box `unreachable`/`pull-failed` → no fix can be delivered. | Zero-cost policy in the README's release section: (1) every release rebuilds from the latest `ubi9/openjdk-21-runtime` tag (bundle refresh comes free); (2) the update host and the registry must chain to ISRG Root X1 (or another root present in every deployed image) — changing the CA is a release decision, not an ops one; (3) the host install guide requires `unattended-upgrades` (security pocket) so the host bundle and Docker keep moving. No custom truststore, no pinning — pinning would turn a CA change into the brick it is meant to prevent. |
| A-9 | 🟡 | Design §3, D2/D3 | **The trust root is passive; the initiator is the least-trusted component.** The host updater only ever acts on a `desired-update.json` the app wrote. A compromised app can therefore (a) never request an update — silently staying on the vulnerable version forever, exactly the version the attacker is in, with the manager API (also the app) reporting whatever it likes; (b) replay any validly signed manifest at any `applyNotBefore`, forcing restarts outside the window or, before the first applied update (deviation 32: no host `currentVersion` yet), a downgrade to an older signed release. The host has no independent view of "the newest signed release". | Accept for v1 and record it — a compromised app is a compromised box and the remedy is detection, which this feature does not attempt. Cheap belt now: `install.sh` seeds the shadow's `currentVersion` from a `--current-version` argument (or the pom version printed by the running container's `/api/system/health`) so the host-side downgrade refusal is armed from day one, not from the first apply. The structural fix — the updater fetching `<base>/manifest/<channel>.json` itself with `curl` and treating the app's file as a *schedule hint* only — is the right v2 shape and pairs naturally with updater self-update (A-10). Lead decision, recommendation: seed `currentVersion` now, structural change later. |
| A-10 | 🟡 | Design "Risks" bullet 1, `install.sh` | **The updater cannot update itself**, and it is also the host-side key holder. With A-1 the host's key set is frozen at install time; the second rotation (key 2 burnt) and every updater bug fix need `install.sh` on site. Acceptable for v1 *because* the script is ~600 lines with 242 parser checks and three rehearsals, and because A-1 gives one rotation of headroom. | Record as the next feature after Part B: the image ships `/deployments/updater/{gxp-updater.sh,*.service,*.timer,keys/}` + a `VERSION`; after `applied`, the (old) updater `docker create`s the pinned image, `docker cp`s the bundle to a root staging dir, `bash -n`s it, `install`s script+units+key set **by rename (new inode — a running bash script must never be overwritten in place)**, `daemon-reload`. The trust for that copy is the same chain (manifest signature → digest → image content). Keep the previous script as `.prev` for a manual revert. Not needed before the first box. |
| A-11 | 🟢 | `gxp-updater.sh:193-197` | `snapshot_file` compares inode numbers only (`%i`), not device+inode. A collision across filesystems is theoretical (the attacker would have to land a specific inode number on another fs) and the read feeds only regex readers in a private copy — no exfiltration path. | `stat -c '%d:%i'` on both sides. One-line hardening, no rehearsal impact. |
| A-12 | 🟢 | `gxp-updater.sh:572` | `healthy` runs `curl` **inside the app container** (`compose exec -T app curl …`). The verdict "ready" therefore trusts the container under test. Fine for the signed image; it would not be fine if the health probe ever became the guard against a malicious image. | Note only. A host-side probe (`curl -k https://127.0.0.1:443/api/system/ready` from the host) would be independent and is one line — optional. |
| A-13 | 🟢 | `ExchangeFiles.java:52`, `.desired-update.json.tmp` | The app writes its temp under a predictable name in its own directory. Harmless (same uid, the app is the owner) — noted so nobody "fixes" it into `mktemp` symmetry for the wrong reason. | None. |
| A-14 | 🟢 | Design D15a, `updater.conf.example` | Shadow state at `/var/lib/gxp-updater/state.json`, 0600 in a 0700 dir; `ProtectSystem=full` leaves `/var/lib` writable (verified: `full` = `/usr`, `/boot`, `/efi`, `/etc` read-only). Location and semantics confirmed. `lastRequestedAt` carry-forward on `malformed` verified at `gxp-updater.sh:260`. | None — confirm R3-3 closed. |

## The six questions, answered with evidence

### 1. Key custody and rotation

**As built:** one Ed25519 key pair, generated 2026-08-26 on the owner's
Pi by `tools/update/gen-signing-key.sh` into `/home/nclsgnkl/.gxp/keys/`
(0700; private PEM 0600, **unencrypted**); public half committed as
`backend/app/src/main/resources/update/signing-public-key.pem`
(fingerprint `256caa773eb8ce34e86e8056b389c6e801ea250248da564a8a80abfdb27f31ff`,
verified from the file) and installed to
`/etc/gxp/update-signing-public-key.pem` by `install.sh`, which refuses a
differing host key without `--replace-key`. The app fails closed without
the resource (`SigningKeyLoader`, `SigningKeyResourceTest`), and the test
walks `src/main/resources`, `backend/updater`, `tools/update` for any
`PRIVATE KEY` block. `.gitignore` excludes `*signing-private-key*` with an
anchored negation for the test key. All of that is correct.

**Loss:** no fleet re-key is possible without a site visit. The only
channel to a box is signed by the lost key. Every box: `install.sh
--replace-key` by hand plus a rebuilt image loaded by hand. Part D (relay)
gives manager-UI access, not a shell, so it does not help.

**Theft:** no revocation exists. Worse, REQ-727's downgrade protection
means a thief who ships a high version number *locks us out* of those
boxes; a revocation race is the best case. Until every box is re-keyed by
hand, the thief owns the fleet.

**Minimum viable rotation design (recommended): a two-key trust set.**
Both verifiers accept a signature under *any* key of a shipped set
{key 1 active, key 2 dormant}. Loss of key 1 → sign with key 2 tomorrow,
nothing on any box changes. Theft of key 1 → sign a release with key 2
whose image carries {key 2, key 3} only; the app then refuses key 1, and
the app is the only path to the host updater, so the stale host copy of
key 1 is defence-in-depth only. Cost ≈ 60 lines across
`SigningKeyLoader`/`ManifestVerifier`/`gxp-updater.sh`/`install.sh` plus
tests. A signed key-rotation manifest (TUF-style) is more machinery on
both sides and a second document type; it can be layered on the set later
and is not required for v1.

**Must it ship before the first production box? Yes, without
qualification.** A box that trusts one key can only learn a second key
through a release signed by the first — which is exactly what is missing
in the loss case. Every other item in this review can be retrofitted
through the channel; this one cannot. (Owner decisions D-1, D-2.)

### 2. Trust chain end to end

- **Same bytes.** The app fetches the manifest as `byte[]`
  (`ManifestClient`, `BodyHandlers` with a 64 KiB cap), verifies those
  bytes (`ManifestVerifier.verify(manifest, signature)`, `Signature("Ed25519")`
  — pure Ed25519, no prehash, no parameters), stores them verbatim
  (`available_manifest bytea`, written in the same statement as the derived
  columns), base64-encodes them into `desired-update.json`
  (`ExchangeFiles.writeDesired`), and the updater `base64 -d`s into
  `$WORK/manifest.json` and runs `openssl pkeyutl -verify -pubin -rawin`
  (pure Ed25519 over the file bytes). No serialisation, canonicalisation or
  text handling touches the signed bytes anywhere. **Cross-implementation
  proof exists:** the fixture pair `backend/updater/test/fixtures/manifest.json{,.sig}`
  is signed by OpenSSL (`sign-manifest.sh`) and verified by the JDK in
  `UpdateCheckTest` (served through `ManifestServer.serve(fixture(...))`)
  and by OpenSSL in `parse.sh`. Same key, same bytes, two verifiers agree.
- **Canonicalisation risks:** none on the signed bytes. A trailing newline,
  CRLF or BOM in the manifest is *part of the signed bytes*; a server or
  CDN that rewrites them (minify, newline normalisation) yields
  `invalid-signature` = no update — fail-safe. The `.sig` is base64 and is
  whitespace-tolerant on both sides (`strip()` in `decodeSignature`; the
  `read -r line || [[ -n $line ]]` idiom in the updater, pinned by
  `parse.sh` with a fixture that has no trailing newline). Jackson skips a
  UTF-8 BOM; the updater's fixed-layout reader ignores the `{` line, so a
  BOM there is harmless too; `sign-manifest.sh` never writes one.
- **`artifactRef@sha256:<digest>` binding:** tight. `docker pull` by digest
  verifies the fetched manifest against the digest and every layer against
  the digests inside it — content-addressed end to end; the name part is
  irrelevant to what is accepted, so a registry-side swap is impossible
  without breaking the pin. `pinned_ref` refuses a manifest whose ref
  already carries a *different* digest. The offline fallback
  (`docker image inspect` of the pinned ref) is safe because only a pull
  or push can create a `name@sha256:` reference locally — `docker tag`
  cannot. Verified the reference form `name:tag@sha256:…` is accepted by
  the daemon (a lookup with a zero digest returns "No such image", not a
  parse error).
- **Index vs. manifest digest:** sign **whatever the tag resolves to** —
  the index digest for a multi-arch push, the manifest digest for a
  single-arch push — which is exactly what
  `docker buildx imagetools inspect <ref> --format '{{.Manifest.Digest}}'`
  prints in both cases. Make that the one documented source (A-7).

### 3. TLS trust root

Answered in A-8. Not a fleet-bricking scenario for Let's Encrypt (roots
valid to 2035/2040 and present in the 2025 bundle of the base image);
the realistic freeze is *us* moving the update host or registry to a CA
absent from the oldest deployed image/host. Mitigation is policy, at zero
code cost: rebuild from the latest base tag per release, keep the update
host on ISRG Root X1, require `unattended-upgrades` on the host. Do not
pin.

### 4. Privilege surface of `gxp-updater.sh`

Re-audit of the R3 fixes, each read against the current script:

- **`snapshot_file` (lines 189-204):** `-L`/`-f` pre-check, open, then
  `stat -L` through `/proc/self/fd/8` for inode, type and size, compared to
  the entry's `stat %i`; ≤ 64 KiB copied to `$WORK` (PrivateTmp); every
  later read is from the copy. Correct. Add the device number (A-11).
- **`place_file` (277-283):** the pre-planted symlink is defeated
  (`mktemp` O_EXCL); the post-`mktemp` race is **not** (A-3). This is the
  one residual path where uid-185 content changes what root writes to.
- **Shadow state (D15a):** confirmed as the only guard memory
  (`LAST_REQUESTED_AT`, `CURRENT_VERSION`, `PREVIOUS_IMAGE` read from
  `$GXP_STATE_FILE` only, lines 369-376); the mirror is never read back;
  carry-forward at line 260. Location `/var/lib/gxp-updater/` is writable
  under `ProtectSystem=full`.
- **Seam refusal (322-331):** `INVOCATION_ID` set → `fail_config` when
  either seam is present; the hook must be a root-owned, non-symlink,
  non-group/world-writable regular file. Correct — but **never executed
  under systemd** (A-4).
- **`pinned_ref` (209-217):** correct.
- **uid-185 → root influence, full list:** (1) the `place_file` race
  (A-3); (2) the informational `version` copied into `attemptedVersion` on
  `invalid-signature`/`malformed` (regex-validated, JSON only — fine);
  (3) replay/timing of any validly signed manifest and the pre-first-apply
  downgrade gap (A-9); (4) up to 64 KiB read per 5 minutes into a private
  copy (bounded). Nothing else in the directory is opened by root.
- **`docker compose` as root with the `.env` value:** `.env` is
  root-written by `set_image` from `$PINNED`, which passed `RE_ARTIFACT`
  (no `$`, `{`, `#`, whitespace, quotes) — compose interpolation cannot be
  influenced; the directory is root-owned so the predictable `.env.tmp`
  is acceptable (R3-7 stands). The app never writes there.
- **`pg_restore` rung:** see A-6 — yes, `DROP SCHEMA public CASCADE`
  after a `pg_restore --list` sanity check, then restore without `--clean`.
- **Unit hardening:** A-5, gated on the A-4 systemd run.

### 5. Availability under §2

- Update server down / DNS / TLS / slow / wrong: the app records
  `unreachable`/`http-error`/`too-large`/`invalid-signature` within a
  15 s budget on one worker thread; nothing else changes (proven by
  `UpdateCheckTest`, including the never-answering-listener case with
  concurrent guest/manager requests). The updater makes no network call
  except `docker pull`, which fails to `pull-failed` with nothing applied.
  The §2 row holds.
- Signed-but-broken release: rung 1 (`rolled-back`) and rung 2
  (`rolled-back-with-restore`) rehearsed on the target hardware with
  restart counts and marker integrity asserted. Good.
- The un-rehearsed registry path: **not acceptable on the "first release
  proves it" basis** (A-4). The failure mode is safe for the box but fatal
  for the fleet's fixability. A `registry:2` scenario C is cheap and needs
  no daemon reconfiguration (localhost registries are insecure-allowed by
  default).

### 6. Updater self-update

Acceptable for v1 only together with A-1 (the frozen host key set then
has one rotation of headroom). The intended path today is `install.sh` on
site — record that honestly in the README (it says "there is one key and
no rotation mechanism yet"; add "and the updater itself is updated by
re-running `install.sh`"). The v2 mechanism (A-10) reuses the existing
trust chain and must install by rename, never in place.

## Decisions for the owner

Only these two are genuinely the owner's; everything else above is a
lead decision with the recommendation stated in the finding.

| # | Decision | Recommendation | Why it is yours |
|---|----------|----------------|-----------------|
| D-1 | **Ship a two-key trust set before the first production box?** Changes the approved REQ-703/718/725 wording ("the public key" → "the trusted key set"), `SigningKeyLoader`/`ManifestVerifier`/`gxp-updater.sh`/`install.sh`, and the resource layout. | **Yes.** It is the one item that cannot be retrofitted; cost ≈ 60 lines + tests; no dependency. The alternative — accept that a lost key means visiting every building — should be a conscious choice, not a default. | Changes approved PRD text and is the single irreversible decision in the feature. |
| D-2 | **Key custody practice.** (a) Encrypt the private PEM(s) with a passphrase (`gen-signing-key.sh -aes256`); (b) one offline backup of key 1; (c) key 2's private half on a separate medium, never on the signing machine; (d) both fingerprints recorded. | **All four.** (a) is the only one that costs code (a prompt at signing time). | It is your laptop, your media and your routine — no agent can decide or verify it. |

Everything else (A-3…A-14) is delegated to the lead; recommendations are
in the table.

**Outcome (owner, 2026-08-26):** **D-1 — yes**, built as T-20a (Design D23:
two-key set in one resource, any key verifies, optional signed `trustedKeys`
line rotates the set version-monotonically, persisted on both sides; key 2
generated 2026-08-26, fingerprint `09009e53…9853b`). **D-2 — deferred by
the owner until the first customer** (reason given: the deployment context
is not yet known and the custody routine is the owner's own practice; no
custody tooling is built beyond what T-20a needs — `gen-signing-key.sh`
gained only the suffix for key 2, no `-aes256`). The four steps are
recorded in `backend/README.md` → "Updates" → "Keys" as an explicit **open
operator task** to be done before the first production box.

## Resulting TODO rows (proposed for `03-todo.md`)

- **T-20a** (🔴, before the first box) — two-key trust set on both sides + `SigningKeyResourceTest` (≥ 2 distinct production keys) + `install.sh` set comparison + README. Blocked on D-1.
- **T-20b** (🟠) — `place_file` single-open fix (A-3) in `gxp-updater.sh` and `rehearsal-lib.sh`, `parse.sh` regression; `stat '%d:%i'` in `snapshot_file` (A-11).
- **T-20c** (🟠) — rehearsal scenario C (`registry:2`, production pull branch, offline re-run) + one `install.sh`/systemd execution on the dev box with the journal captured (A-4). Then, and only then, the unit hardening lines of A-5.
- **T-20d** (🟡) — restore rung: `pg_restore --list` → `DROP SCHEMA public CASCADE` → `pg_restore` without `--clean`; rehearsal B plants a leftover table (A-6).
- **T-20e** (🟡, docs only) — one canonical digest command (A-7); TLS policy lines and `unattended-upgrades` in the README (A-8); "the updater is updated by re-running `install.sh`" (A-10); `gen-signing-key.sh -aes256` (A-2a).
- **T-20f** (🟡) — `install.sh --current-version` seeds the shadow's `currentVersion` (A-9 belt).
- Recorded for later features, not TODO rows: updater self-update from the verified image (A-10), updater-initiated manifest fetch (A-9 structural).

T-20 stays **unticked** until D-1/D-2 are answered and T-20a…T-20c are
green; T-20d…T-20f may run inside `06_review`'s fix rounds.
