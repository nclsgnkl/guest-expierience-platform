# PRD: Update Channel (remote-operations Part C)
**Feature:** [index.md](../index.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Status:** In Review (2026-08-26)

Governed by constitution **v1.8.2** (principle 4, fourth bullet — *"Instances
are patched through an outbound, signed update channel"* — plus the survival
invariant and the "remote access is separately sold" bullet), by
`docs/backend/remote-operations.md` §2, §6, §11 (slug `update-channel`) and
§12 items 1, 8 and 9, and by `backend/CLAUDE.md`.

## Problem statement

Every instance runs in a building we may never visit again. Until now the
constitution stated that such a box *cannot be patched centrally*; v1.8.0
turned that into a decision: a fix reaches a box through a channel the box
itself polls. Nothing exists yet — today an update is a person at the box
running `docker compose up --build`. This feature builds the channel: the
box periodically asks for a signed manifest, verifies it against a key
compiled into its image, and a small component *beside* the application
applies the new image at a time no guest is reading the house rules, with a
database dump first and a rollback if the new image does not come up.

**Sequencing note (owner-delegated lead decision 2026-08-26).** Part C is
pulled ahead of Part B (`instance-identity-and-certs`): the manifest fetch
is an anonymous outbound `GET` that needs no instance identity, so nothing
in C depends on B. Consequently **nothing in this feature may assume B
exists** — no instance key, no instance name, no enrollment. B and D later
reuse C's outbound HTTP conventions (REQ-729), which is the one thing this
feature deliberately builds for them.

## Goals / Non-goals

- **Goals:**
  - The box learns, on its own and outbound only, that a newer signed
    release exists (§6 diagram), and can prove the manifest is ours before
    trusting a single field of it.
  - A verified update is applied by a host-side updater — dump, pull,
    swap, restart once, health-check, roll back on failure — inside a
    manager-chosen window (default 04:00 local property time, §12 item 8).
  - The manager can see the update state, disable automatic updates, change
    the time, and start an available update now — backend API only.
  - Rollback and the migration-failure case are **rehearsed by a script**,
    not described (§6 req 4–5, §12 item 9).
  - The guest screens and the LAN manager UI are unaffected by the update
    server being unreachable, slow, wrong, or gone (§2 table row "update
    server").
- **Non-goals:**
  - The manager **screen** for updates (owner decision: backend only).
  - The update **server** and the release pipeline that publishes to it —
    this feature specifies the manifest it must serve and the tool that
    signs it; hosting is an operations task recorded as an open item.
  - A container registry choice. The artifact reference in the manifest is
    any reference `docker pull` accepts; which registry we run is deferred
    (see Risks).
  - Updating PostgreSQL or the host OS. The channel updates the application
    image only.
  - Anything under `/api/guest/**` or `/api/pairing/**` (remote-operations
    §1); Part B (identity, ACME) and Part D (relay).
  - Frontend changes of any kind (see REQ-723 for why none are needed).

## Assumptions

- The production image is `gxp-backend:<version>` built from
  `backend/app/src/main/docker/Dockerfile.jvm`; `backend/compose.yaml` is the
  deployment unit; `backend/app/pom.xml` is **not** changed by this feature
  (owner constraint) — the version the app reports is
  `quarkus.application.version` = the pom version (`1.0.0-SNAPSHOT` today).
- The box has outbound HTTPS (port 443) to the update host and the artifact
  registry, *when it has internet at all*. A box without internet is a
  supported configuration and simply never sees an update.
- The host runs Docker with the Compose plugin, `systemd`, `bash`, GNU
  coreutils, `util-linux` (`flock`) and OpenSSL ≥ 1.1.1 — a stock
  Debian/Raspberry Pi OS. Nothing else is assumed on the host; in particular
  **no `jq`, no Python**.
- The JDK's `Signature("Ed25519")`, `KeyFactory("Ed25519")` and
  `java.net.http.HttpClient` are available (Java 21) — no dependency enters
  `pom.xml`.
- `/api/system/ready` is the readiness probe (a database probe query; the
  process only serves it after Flyway has migrated at start). **There is no
  `/q/health/ready`** — `quarkus-smallrye-health` is not in the pom and is
  not being added.
- The signing **private key is generated and used offline** by the owner;
  it never exists on the update server, on a box, or in this repository.

## User stories

- As the **operator**, I want every deployed box to pick up a signed release
  on its own so that a vulnerability does not persist in a building I cannot
  reach.
- As the **operator**, I want a box that cannot verify a manifest to do
  nothing at all so that a compromised update server is a nuisance, not a
  fleet compromise.
- As the **operator**, I want a failed update to roll itself back so that a
  box never bricks itself in a building nobody can visit.
- As a **manager**, I want updates to happen at 04:00 and not while a guest
  is reading the house rules, and to be able to change that time.
- As a **manager**, I want to see that an update is available and start it
  now, or switch automatic updates off, and I want each of those actions in
  the audit trail.
- As a **guest**, I never notice — the kiosk keeps showing what it showed.

## Requirements

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-701 | **Outbound only.** The application periodically issues `GET <base-url>/manifest/<channel>.json` and `GET <base-url>/manifest/<channel>.json.sig`. The base URL comes from `GXP_UPDATE_BASE_URL` (unset = channel off; status reports it), the channel from `GXP_UPDATE_CHANNEL` (default `stable`, pattern `^[a-z0-9-]{1,32}$`). Nothing ever connects to the box; the box opens no listener for this feature. | Must |
| REQ-702 | **Manifest format v1.** A UTF-8 JSON document with exactly the fields `schemaVersion` (`1`), `channel`, `version` (`MAJOR.MINOR.PATCH`), `artifactRef` (an image reference `docker pull` accepts), `sha256` (64 lowercase hex — ~~the **image ID** of the artifact~~ *amended 2026-08-26, Design D5/R3-6:* the **registry manifest digest** of the artifact, the value `docker pull <ref>@sha256:<digest>` enforces), `publishedAt` (ISO-8601 instant), optional `minimumFromVersion`, *added 2026-08-26 (D-1/A-1, D23):* optional `trustedKeys` (comma-separated base64 SPKI tokens of the 1..4 keys every box trusts from this version on; must contain the key signing this manifest). The signature is **detached**: raw 64-byte Ed25519 over the **exact bytes** of the manifest file, base64 in the `.sig` file. No canonicalisation anywhere — the bytes fetched are the bytes verified are the bytes stored. | Must |
| REQ-703 | **Verify before anything.** The application verifies the signature with the JDK (`Signature("Ed25519")`) against ~~the public key~~ *amended 2026-08-26 (owner decision D-1, architect review A-1, Design D23):* the **trusted key set** shipped **inside the image** (`src/main/resources/update/signing-public-keys.pem` — one PEM, 2..4 keys: key 1 active, key 2 dormant; a signature by any key of the set verifies) — or the set a verified newer manifest rotated to (persisted in `update_channel.trusted_keys`, see REQ-702's optional `trustedKeys` line) — before parsing a single manifest field. A manifest that fails verification — or fails REQ-705 — is treated as *no update*: never stored as "available", never written to the updater, and the failure is recorded in the check state as a fixed outcome code and logged as the exception's class name only. | Must |
| REQ-704 | **Missing key fails closed.** If the configured public key resource is absent or unparseable, the application refuses to start (the `GXP_ADMIN_PASSWORD` posture) and `./mvnw verify` fails (a test asserts the production resource is present, parses as an Ed25519 public key, and is **not** the test key). `%test` points at a test key in `src/test/resources` only. | Must |
| REQ-705 | **Acceptance rules** beyond the signature, all evaluated on the verified manifest: `schemaVersion == 1`; `channel` equals the configured channel; `version` is strictly greater than the running version (semver; a `-SNAPSHOT` qualifier sorts below the same release); `minimumFromVersion`, when present, is ≤ the running version; the manifest body is ≤ 64 KiB; the base URL is `https://` (plain `http://` only when `gxp.update.allow-plain-http=true`, which only a test profile sets). Any miss → *no update* with a distinct outcome code, no exception propagates. | Must |
| REQ-706 | **Survival.** The fetch uses `java.net.http.HttpClient` with a 5 s connect and 10 s request timeout, follows no redirects, retries nothing (the next tick is the retry). An unreachable, slow, erroring or malformed server leaves every guest and manager operation untouched, costs at most one worker thread for ≤ 15 s per tick, and shows as `lastCheckOutcome = unreachable` in the manager API. A box with `GXP_UPDATE_BASE_URL` unset behaves identically to one that never finds an update. | Must |
| REQ-707 | **Poll job.** One `@Scheduled` job in `de.gxp.infrastructure.scheduler` (`UpdateScheduler#checkForUpdate`, Quartz cron hourly at a fixed minute, `concurrentExecution = SKIP`) delegates to the public `UpdateControl` method, which sleeps a random jitter of 0–600 s (`gxp.update.check-jitter-seconds`, `%test` = 0) before fetching so a fleet does not hit the server on the same second. Pinned in `ScheduledJobInventoryTest` deliberately; `%test` scheduler stays halted and tests call the Control. Failure logging: class name only. | Must |
| REQ-708 | **Apply-time job.** A second job in the same class (`UpdateScheduler#reconcileDesiredUpdate`, Quartz cron every 15 minutes, `SKIP`) recomputes and (re)writes the desired-update file from persisted state per REQ-715 — including moving `applyNotBefore` to the next occurrence of the window when an earlier one passed unapplied (box was off at 04:00). Pinned in the inventory. The host updater is what fires at the time; the application never restarts itself. | Must |
| REQ-709 | **Settings persist.** Flyway `V8__update_channel.sql` creates a single-row table (`CHECK (id = 1)`, seeded) holding `auto_update_enabled` (default **true**), `apply_at` (`time`, default **04:00**), the last check (`last_check_at`, `last_check_outcome`) the currently available verified manifest (version, artifact ref, sha256, published-at, raw bytes, signature) and the pending manual request (`manual_requested_at`, `manual_requested_version` — what lets a manual apply survive a restart and out-rank the hold rule of REQ-715). `applyAt` is validated as `HH:MM` (24 h). A fresh instance is usable with the defaults and no manual work. | Must |
| REQ-710 | **Status API.** `GET /api/manager/updates` (admin) returns: `currentVersion` (the running application's own version), `channel`, `configured` (base URL set), `autoUpdateEnabled`, `applyAt`, `timezone` (the property's), `lastCheckAt?`, `lastCheckOutcome` (enum), `available?` (`version`, `publishedAt`, `artifactRef` — never the raw manifest), `scheduledFor?` (the instant the desired file currently names), and `updater` (the parsed `state.json`, REQ-716, or `null` when the updater never wrote one). | Must |
| REQ-711 | **Settings API.** `PUT /api/manager/updates/settings` with `{autoUpdateEnabled, applyAt}` (admin) validates, persists, rewrites the desired file per REQ-715 immediately, returns the status, and calls `AuditControl.record("update", "update-settings", "1", changedFields)` in the same transaction. | Must |
| REQ-712 | **Apply-now API.** `POST /api/manager/updates/apply` (admin) → `202` + status when a verified update is available: writes the desired file with `applyNotBefore = now`, `requestedBy = manual`, a fresh `requestedAt` — which is what lets a manager re-try a version the updater previously failed. `409 no-update-available` otherwise. Audited: `AuditControl.record("update-apply", "update", <version>, null)`. A disabled auto-update does not block this. | Must |
| REQ-713 | **Check-now API.** `POST /api/manager/updates/check` (admin) runs one fetch-and-verify cycle synchronously (no jitter), returns the status, and is audited as `update-check` on entity `update` (no entity id). It is the only way to trigger a check without waiting for the tick. (Amended 2026-08-26 at Design: the smoke script does **not** drive it — the app trusts only `https://` outside `%test`, and standing up a trusted HTTPS manifest server on the dev box is not worth a `%prod` plain-HTTP switch; the app→updater file contract is proven by a fixture shared between the JVM test and the updater's parser test instead, see Design §"Exchange files".) | Must |
| REQ-714 | **Disabled still informs.** With `autoUpdateEnabled = false` the poll job keeps running and the status keeps reporting `available`; only the desired file is not written automatically (§12 item 8: the manual trigger exists so a manager can pull an update early, not so they can block one indefinitely — but the switch is theirs). | Must |
| REQ-715 | **`desired-update.json` — the app → updater contract.** Written by the application into the shared exchange directory (REQ-717) **atomically** (temp file + rename), in the exact fixed layout in Design §"Exchange files", containing: `schemaVersion`, `version`, `artifactRef`, `sha256` (informational copies), `requestedAt`, `requestedBy` (`auto`\|`manual`), `applyNotBefore` (UTC instant), `applyWindowSeconds` (`7200`), `manifest` (base64 of the verified raw bytes) and `signature` (base64). Rules: written when a verified update is available **and** (auto enabled **or** a manual request exists); `applyNotBefore` for `auto` = the next occurrence of `applyAt` in the property's timezone that is still in the future; **hold rule** — not (re)written for a version the updater already attempted (`state.attemptedVersion == version`, any outcome) unless a manual request is newer; **removed** when auto is disabled and no manual request is pending, or when no verified update is available; contains no secret and no guest data. | Must |
| REQ-716 | **`state.json` — the updater → app contract.** Written by the updater (root, mode `0644`) in the exact layout in Design §"Exchange files": `schemaVersion`, `updatedAt`, `lastAttemptAt?`, `lastRequestedAt?`, `attemptedVersion?`, `outcome` (enum), `rolledBack`, `currentImage`, `currentVersion?`, `previousImage?`, `backupFile?` (file **name**, not path), `message?` (a fixed code, never free text from a log). The application reads it tolerant of absence and of a newer `schemaVersion`; it never writes it. No secret in either file. | Must |
| REQ-717 | **Shared volume `gxp-updates`.** A named compose volume mounted at `/updates` in the `app` container (Dockerfile creates it owned by uid 185, mode 0700), holding only the two exchange files. The updater resolves its host path through `docker volume inspect`, never a hard-coded Docker internals path. The application's path is `gxp.update.exchange-dir` (`/updates`; `%test` = a directory under `target/`). | Must |
| REQ-718 | **The updater is a host-side script + systemd timer**, not a container: `backend/updater/gxp-updater.sh`, `gxp-updater.service` (`Type=oneshot`), `gxp-updater.timer` (every 5 minutes), `install.sh`, config `/etc/gxp/updater.conf`. It **independently re-verifies** the manifest signature with OpenSSL against **its own copy** of ~~the public key (`/etc/gxp/update-signing-public-key.pem`~~ *amended 2026-08-26 (D-1/A-1, D23):* the **trusted key set** (`/etc/gxp/update-signing-public-keys.pem`, any key of the set may verify; a rotation a verified newer manifest carries is persisted in the shadow state and beats the file), installed from the repo resource; the updater is the trust root, the app's check is defence in depth), takes `version`/`artifactRef`/`sha256` **only from the verified manifest** (never from the desired file's informational copies), validates every value it passes to any command against a strict regex, parses both files without `jq` (fixed-layout extraction), runs under `flock`, and refuses to act on a `desired-update.json` whose `requestedAt` is not newer than `state.lastRequestedAt`. | Must |
| REQ-719 | **Dump first.** Before touching the image the updater runs `pg_dump --format=custom` through `docker compose exec db` into `GXP_BACKUP_DIR` (default `/var/lib/gxp/backups`, root, `0700`) as `pre-update-<version>-<UTC timestamp>.dump`; a failed dump aborts the attempt (`outcome = backup-failed`, nothing restarted). The five newest dumps are kept. This extends the README's backup story (one `pg_dump` + the `gxp-tls` volume) rather than replacing it. | Must |
| REQ-720 | **Pull and pin.** The updater `docker pull`s `artifactRef` (if the pull fails but a local image with that tag exists, it may proceed — the offline/rehearsal case), reads the image ID with `docker image inspect`, and refuses unless it equals `sha256:<manifest.sha256>` (`outcome = artifact-mismatch`). *Amended 2026-08-26 (D5/R3-6):* the pull is `artifactRef@sha256:<manifest.sha256>` — the registry manifest digest, enforced by Docker; `.env` carries the digest-pinned ref; the local image ID is recorded in the updater's own state, not compared (rehearsals with local tags use a rehearsal-only local-ID path refused under systemd). The compose file's `image:` becomes `${GXP_IMAGE:-gxp-backend:1.0.0-SNAPSHOT}`; the updater rewrites the `GXP_IMAGE=` line of the compose project's `.env` and runs `docker compose up -d --no-build --pull never app`. | Must |
| REQ-721 | **Health check.** After the swap the updater waits ≤ 180 s for **all** of: `docker compose exec app curl -fsk https://localhost:8443/api/system/ready` → 200, the newest `flyway_schema_history` row has `success = true` (via `docker compose exec db psql`), and the container is not restarting; then holds 30 s and re-checks once. Ready + Flyway both required — a Flyway failure aborts the Quarkus boot so ready never answers, and the row check is the belt that says *why*. | Must |
| REQ-722 | **Rollback, then restore.** On health failure: `GXP_IMAGE` is set back to `previousImage`, the old image is started and health-checked (→ `outcome = rolled-back`); if the old image is unhealthy too, the app is stopped, the pre-update dump is restored (`pg_restore --clean --if-exists`), the old image started and checked (→ `rolled-back-with-restore`); if that fails as well the updater stops (`rollback-failed`), leaves the old image running, and never retries that request. An update is never applied partially (§12 item 9). Each request results in **at most one attempt**. | Must |
| REQ-723 | **Timing never surprises a guest.** The updater applies only when `applyNotBefore ≤ now < applyNotBefore + applyWindowSeconds`; outside the window it exits and the application's reconcile job moves the time to the next window. A successful update restarts the application **exactly once**; a failed one restarts it a second time to the old image. **Kiosk impact, verified against the code:** during the restart the kiosk's 30 s poll fails without a `401`, `GuestDataService` shows the last-known-good payload with the staleness pill (STALE, not DISCONNECTED — a cache exists), and the `DISCONNECTED` retry heals it within 5–30 s of the app answering again; the token wipe only triggers on `401`, which a refused connection never produces. Manager sessions are lost (in-memory token store, already documented). **No frontend change is required**, and this feature makes none. | Must |
| REQ-724 | **Rehearsed, not described.** `backend/updater/test/smoke-apply.sh` (happy path: a second tag of the locally built image, a manifest signed with the **test** key, the updater applies it, `state.outcome = applied`, `/api/system/ready` 200, a marker written before the update is still readable after), `backend/updater/test/rehearse-rollback.sh` scenario **A** (a derived image whose entrypoint never becomes ready → `rolled-back`, old image serving, marker intact) and scenario **B** (a `success = false` row planted in `flyway_schema_history` after the dump → both images refuse to boot → dump restored → `rolled-back-with-restore`, marker intact, planted row gone). Each script asserts its outcome and exits non-zero on any miss; each runs against the compose unit via `sg docker`. Both are part of the definition of done and are documented in the README. | Must |
| REQ-725 | **Keys.** `tools/update/gen-signing-key.sh [dir] [suffix]` documents and runs `openssl genpkey -algorithm ed25519` → `signing-private-key[-suffix].pem` (mode 0600, **outside the repo**) + `signing-public-key[-suffix].pem` (*amended 2026-08-26, D-1/A-1:* two pairs are generated — key 1 and key 2 — and **only the public halves**, concatenated, form the resource `signing-public-keys.pem`; `sign-manifest.sh --trusted-keys <pem>` writes a rotation); `tools/update/sign-manifest.sh <private-key> <manifest.json>` writes the manifest in the fixed layout (validating every field) and `manifest.json.sig` (`openssl pkeyutl -sign -rawin`). `.gitignore` excludes `*signing-private-key*`; a test asserts no PEM private key exists under `src/main/resources` or `backend/updater`. The **test key pair** lives in `src/test/resources/update/` only and is used by the JVM tests and by the two scripts of REQ-724. The production ~~public key is~~ *amended 2026-08-26:* **key set** (≥ 2 distinct keys, none a test key — asserted by `SigningKeyResourceTest`) is the resource of REQ-703 and the host copy of REQ-718; `install.sh` refuses when the two differ (`--replace-key`) and refuses a single-key resource. | Must |
| REQ-726 | **Posture.** No `pom.xml` change; no change under `/api/guest/**` or `/api/pairing/**`; no guest-sensitive value, token, password or key material anywhere in the update path (files, DB row, logs, API); every log line on this path carries a fixed message and at most an exception class name or a version string; the manager API never returns the raw manifest or signature. | Must |
| REQ-727 | **Downgrade protection on both sides.** The application never records a manifest whose version is ≤ its own as available (REQ-705); the updater refuses a verified manifest whose version is ≤ `state.currentVersion` when that is set (`outcome = downgrade-refused`). A replayed old-but-validly-signed manifest therefore does nothing. *Amended 2026-08-26 (lead, review R3-3, Design D15a):* "state" here is the updater's **root-owned shadow state** (`GXP_STATE_FILE`, 0600) — the exchange `state.json` is only a mirror for the app, and deleting or replacing it does not weaken either guard; `lastRequestedAt` is carried forward on every outcome incl. `malformed`. | Must |
| REQ-728 | **Documentation and review.** `backend/README.md` gains an "Updates" section (install the updater, the env variables, the manifest/signature format, the release signing steps, the rehearsal scripts, what a manager sees); `backend/compose.yaml` comments explain `GXP_IMAGE`, the two new variables and the volume; `docs/system-context.md` gains the component rows and a "Update channel" cross-cutting bullet and the scheduled-job count moves from three to five. **Key handling gets an independent review by `architect`** before `06_review` (§6 "highest-value target" consequence, recorded verbatim in Design). | Must |
| REQ-729 | **Reusable outbound conventions.** The HTTP client shape (JDK `HttpClient`, timeouts, no redirects, size cap, https-only outside tests, one thread per tick, failure = outcome code + class-name log) lives in one package-private class with a javadoc naming it as the convention Parts B and D reuse — so they copy a shape, not re-decide one. Nothing in it references an instance identity. | Should |

## Acceptance criteria (testable)

- REQ-701: Given `GXP_UPDATE_BASE_URL=https://u.example` and no channel variable, when the poll runs, then exactly two GETs are issued, to `/manifest/stable.json` and `/manifest/stable.json.sig`, and no new listener exists (the compose `ports:` list is unchanged — `443:8443` only — and the update package opens no socket). Given the variable is unset, the status reports `configured=false` and no request is made. *(Amended 2026-08-26, review 06 #4: was "`netstat`/the code show".)*
- REQ-702: Given a manifest signed by `sign-manifest.sh`, when any single byte of the manifest body is changed (including whitespace), then verification fails.
- REQ-703: Given a manifest signed with a different Ed25519 key, when the check runs, then `lastCheckOutcome = invalid-signature`, no `available` is stored, no desired file exists, and the only log line contains no manifest content. The verification happens before JSON parsing (a syntactically invalid body with a bad signature yields `invalid-signature`, not a parse outcome).
- REQ-704: Given `src/main/resources/update/signing-public-keys.pem` (the trust **set**, D23) is deleted, when `./mvnw verify` runs, then `SigningKeyResourceTest` fails; given it holds fewer than two distinct keys, or any of the test keys, the same test fails. *(Amended 2026-08-26, review 06 #4: was the singular `signing-public-key.pem` and "the test public key".)*
- REQ-705: A manifest with `channel=beta` under channel `stable` → `wrong-channel`; `version` equal to the running version → `no-update`; `minimumFromVersion` above the running version → `minimum-from-version-not-met`; a 70 KiB body → `too-large`; base URL `http://` in the default profile → `insecure-base-url` and no request.
- REQ-706: Given the base URL points at a closed port, when the check runs, then it returns within 15 s with `unreachable`, and a concurrent `GET /api/guest/session` and `GET /api/manager/property` succeed unchanged.
- REQ-707/708: `ScheduledJobInventoryTest`'s pinned list contains exactly the two new entries with their crons and the existing three; the jobs never fire in the suite.
- REQ-709: `MigrationTest` head is `8`; the row exists with `auto_update_enabled = true` and `apply_at = 04:00`; `PUT` with `applyAt = "4:00"` or `"24:00"` is `400`.
- REQ-710–714: rest-assured tests per operation (200/202/400/401/409 shapes), and `AuditCoverageTest` lists the three new audited methods (25 → 28) with `update-settings`/`update`/`update` rows written in the caller's transaction; with auto disabled the status still carries `available`.
- REQ-715: Given an available verified manifest and auto enabled with `applyAt = 04:00` and timezone `Europe/Berlin`, when reconcile runs at 2026-09-01T10:00Z, then the file names `applyNotBefore = 2026-09-02T02:00:00Z`, `requestedBy = auto`, decodes back to the exact manifest bytes, and the file's byte layout equals the fixture in the test. Given `state.attemptedVersion` equals that version, the file is not written. Given auto is disabled and no manual request, an existing auto file is removed. A write is atomic (no partial file observable — temp name + rename asserted).
- REQ-716: A fixture `state.json` for every outcome round-trips into the status DTO; a missing file → `updater = null`; a file with `schemaVersion = 2` and unknown keys → still parsed for the known keys.
- REQ-717: `docker compose config` shows the volume on `app` at `/updates`; the smoke script resolves the volume through `docker volume inspect`'s mountpoint, places its `desired-update.json` there, and afterwards `GET /api/manager/updates` reports the updater-written `state.json` (`updater.outcome = applied`) — the live proof that both sides see one directory. The app-written layout is proven byte-exact against the shared fixture (REQ-715).
- REQ-718: Given a desired file whose top-level `artifactRef` is `evil` but whose signed manifest names the real one, the updater pulls the real one. Given a `version` of `1.1.0; rm -rf /` inside a validly signed manifest, the updater aborts with `malformed` before any docker command. Given `requestedAt` equal to `state.lastRequestedAt`, the updater exits without acting.
- REQ-719–722: proven by REQ-724's scripts: the dump file exists before the swap, the image ID is compared, ready + Flyway row are both checked, each scenario ends in its named outcome with the marker intact.
- REQ-723: Given `applyNotBefore` one hour in the future, the updater exits `0` with no state change; given it three hours in the past, the same, and the next reconcile moves it forward. The smoke script counts app container starts (`docker inspect … RestartCount`/`StartedAt`) and asserts exactly one restart.
- REQ-724: the three scripts exist, are executable, and pass on the dev's box (run output recorded in `handoff.md`).
- REQ-725: `git ls-files | grep -i private` is empty for `src/main`; the two tools run end to end with the test key; `install.sh` exits non-zero when the host key differs from the resource.
- REQ-726: `git diff --stat backend/app/pom.xml` empty; the contract diff touches no `/api/guest` or `/api/pairing` path; `LogSweep` over a failing check shows no manifest bytes, URL query or key material.
- REQ-727: an old validly signed manifest (`version` below current) yields `no-update` in the app; fed directly to the updater with `state.currentVersion` newer, the updater ends `downgrade-refused`.
- REQ-728: README section present; system-context rows present; the architect's key-handling review is recorded in `handoff.md` before `06_review` starts.
- REQ-729: the client class's javadoc names the convention; it has no import from `de.gxp.auth`, `de.gxp.user` or any identity-related type.

## Technical constraints

- Java 21 + Quarkus 3.33.3.1, BCE with `boundary`/`control`/`entity` sub-packages, contract-first (`api/openapi.yaml`, one tag `manager-updates` → one `UpdateResource`), Panache repositories, Flyway versioned SQL, `AuditControl.record` on every manager mutation (auth-hardening precedent), scheduling only in `de.gxp.infrastructure.scheduler` with a Quartz cron and `SKIP`, class-name-only failure logging. All per `backend/CLAUDE.md`.
- **No new dependency** (constitution allow-list is closed; remote-operations §9 row "Update channel: none in the application"). Ed25519, HTTP, JSON (`quarkus-rest-jackson`'s `ObjectMapper`) and semver comparison are JDK/approved.
- The updater is shell + systemd + Docker CLI + OpenSSL — host tooling, not application dependencies; the host prerequisites are listed in the README.
- Principle 3 and the constitution's "secrets never logged": there is nothing secret on this path, and it stays that way (REQ-726).
- The survival invariant (constitution v1.8.0): every acceptance criterion under REQ-706 is the invariant's test.

## Risks & open questions

- **The channel is the highest-value target** (§6 recorded consequence): whoever holds the private key controls every box. Mitigations in scope: offline generation, key never in the repo/server/box, signature verified twice by two implementations (JDK and OpenSSL) against two copies of the public key, downgrade protection, digest-pinned artifacts. Out of scope and recorded for the architect review (REQ-728): key rotation (a second key in the image, a rotation manifest) — a box whose only trusted key is compromised cannot be told to trust another one through the compromised channel; the answer is a manual `install.sh`/image rebuild today.
- **Registry not chosen.** The `artifactRef` is pull-agnostic; the rehearsal uses local images so nothing here depends on the choice. Deferred to operations — recorded, not blocking.
- **The pom version is `1.0.0-SNAPSHOT`.** The first real manifest must carry a version above it (`1.0.0` release sorts *above* `1.0.0-SNAPSHOT` by REQ-705's rule, so `1.0.0` itself would already be an update — deliberate).
- **Two host restarts of Postgres are never done** — the updater never touches the `db` service's image; a Postgres major upgrade is a different project.
- **A box without NTP** has a wrong clock: the window check and `publishedAt` sanity are time-based. The TOTP README note already demands NTP; this feature repeats it in the Updates section.
- **Docker socket exposure** is avoided entirely by the host-script choice; the price is that the updater is a root process reading a file the application (uid 185) writes — hence the strict-regex rule and "only the verified manifest is trusted" in REQ-718.
- No `[NEEDS CLARIFICATION]` markers remain: the owner delegated all remaining decisions (2026-08-26); see Design §"Decisions taken under delegated authority". No new dependency is required, so no stop is needed.
