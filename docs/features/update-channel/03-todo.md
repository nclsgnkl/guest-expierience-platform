# TODO: Update Channel (remote-operations Part C)
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md)
**Status:** Implemented (T-01…T-21 done 2026-08-26; T-20 architect review CONCERNS → owner D-1 yes / D-2 deferred → T-20a…T-20f done, T-21 green; next: `06_review`)

> Each task traces to a REQ-ID and references a Design decision (D1–D22)
> instead of re-deciding. Tasks are small and in dependency order; every
> task ends green (`./mvnw verify` or the named script) before the next.
>
> **Environment (2026-08-26):** branch `refactor/bce-subpackages-and-boundary-security`,
> auth-hardening's work is uncommitted on it — do not touch
> `docs/features/auth-hardening/`. Build:
> `JAVA_HOME=/tmp/gxp-jdk/jdk-21.0.12+8 sg docker -c "cd <repo>/backend/app && ./mvnw -q verify"`
> (~25 min, run in the background; the Dev Services container drops under
> load → re-run the failing class, do not "fix" a flake). Docker via
> `sg docker -c "…"`; `sudo -n` works on this box (needed for the volume
> mountpoint in the updater scripts). **`backend/app/pom.xml` may not be
> changed** (owner constraint) — no dependency is needed (D22).
> **Nothing may assume Part B exists.** No `[NEEDS CLARIFICATION]` remains.

## Setup

- [x] **T-01 Test key pair, fixture directory, `.gitignore`** — _REQ-725, REQ-702_
  - Files: `backend/app/src/test/resources/update/test-signing-private-key.pem`, `…/test-signing-public-key.pem` (generated with `openssl genpkey -algorithm ed25519` — public by definition, commit both), `backend/updater/test/fixtures/` (empty `.gitkeep` for now), `.gitignore` (+ `*signing-private-key*` **with an explicit negation for the test key** `!test-signing-private-key.pem` — the ignore must not hide the test fixture).
  - Validate: `git status` shows both test PEMs as added; `openssl pkey -pubin -in test-signing-public-key.pem -noout` exits 0.

- [x] **T-02 Release tools + production public key** — _REQ-725, REQ-703, REQ-704_ (D17)
  - Files: `tools/update/gen-signing-key.sh`, `tools/update/sign-manifest.sh` (Design §4: regex validation per the table in §2, fixed manifest layout, `openssl pkeyutl -sign -rawin`, base64 `-w0`, self-verify before exit 0, prints the `docker image inspect -f '{{.Id}}'` hint), both `0755`, `set -euo pipefail`, no `eval`. Run `gen-signing-key.sh "$HOME/.gxp/keys"` **once** on this machine; copy only the public key to `backend/app/src/main/resources/update/signing-public-key.pem`. Record the path and the public-key SHA-256 fingerprint in `handoff.md`.
  - Validate: `sign-manifest.sh` with the **test** private key produces `stable.json` + `stable.json.sig` that `openssl pkeyutl -verify -pubin -inkey <test public> -rawin -in stable.json -sigfile <(base64 -d stable.json.sig)` accepts; flipping one byte of `stable.json` makes it fail; `git ls-files | grep -i 'private'` lists only the test key; `$HOME/.gxp/keys` is outside the work tree and `0700`.

- [x] **T-03 Contract: tag `manager-updates`** — _REQ-710, REQ-711, REQ-712, REQ-713, REQ-726_ (Design §"API contract")
  - Files: `api/openapi.yaml` (four operations, `UpdateStatusDto`, `UpdateSettingsRequest`, `AvailableUpdateDto`, `UpdaterStateDto`, enum `UpdateCheckOutcome`, error code `no-update-available`, all `bearerAuth`), `packages/api-client/` (regenerated, committed with the contract, never hand-edited), `ContractSecuredOperationsDeniedTest` pin `54 → 58`.
  - Validate: `./mvnw verify` regenerates both sides; compile fails only for the missing `UpdateResource` — proceed to T-04…T-11 in the same working state, or add a temporary `UpdateResource` throwing `501` so the build is green between steps (remove in T-11). No raw manifest/signature field exists in any schema (`grep -n manifest api/openapi.yaml` shows only descriptions).

- [x] **T-04 Flyway V8 + entity + repository** — _REQ-709_ (D8)
  - Files: `src/main/resources/db/migration/V8__update_channel.sql` (Design §"Data model", verbatim), `de/gxp/update/entity/UpdateChannel.java`, `de/gxp/update/entity/UpdateChannelMapper.java`, `de/gxp/update/control/UpdateChannelRepository.java` (`theRow()`), `de/gxp/update/boundary/` (directory present for `FeatureInternalsImportTest`), `MigrationTest` (`flywayHeadIsV8UpdateChannel`: head `8`, row `id = 1`, `auto_update_enabled = true`, `apply_at = '04:00'`, `last_check_outcome = 'never-checked'`).
  - Validate: `MigrationTest` + the five `de.gxp.architecture` guards green (`PersistenceAccessTest` sees only the entity and the repository touching JPA).

## Implementation — application

- [x] **T-05 `SemanticVersion`** — _REQ-705, REQ-727_ (D6)
  - Files: `de/gxp/update/control/SemanticVersion.java` (package-private record, `parse`, `compareTo`; qualifier < release), `SemanticVersionTest` (`1.0.0-SNAPSHOT < 1.0.0 < 1.0.1 < 1.1.0 < 2.0.0`; `1.0.0-SNAPSHOT` vs `1.0.0-rc1` ordered lexically; garbage → `IllegalArgumentException`).
  - Validate: unit test green (plain JUnit, no `@QuarkusTest`).

- [x] **T-06 `SigningKeyLoader`, `ManifestVerifier`, config, fail-closed test** — _REQ-703, REQ-704, REQ-725_ (D7, D17)
  - Files: `de/gxp/update/control/SigningKeyLoader.java` (`@Observes StartupEvent`, PEM → `X509EncodedKeySpec` → `KeyFactory("Ed25519")`, throws `IllegalStateException` naming only the resource path), `ManifestVerifier.java`, `application.properties` (the `gxp.update.*` block from Design §1 incl. the `%test` lines), `de/gxp/update/SigningKeyResourceTest.java` (plain JUnit: reads `src/main/resources/update/signing-public-key.pem` from disk via the `moduleRoot()` pattern, parses, asserts `!= ` the test public key bytes; walks `src/main/resources`, `backend/updater`, `tools/update` and asserts no file contains `PRIVATE KEY`), `ManifestVerifierTest` (signs with the test private key in-test via `Signature("Ed25519")`, verifies; one flipped byte → false; a wrong key → false).
  - Validate: both tests green; temporarily renaming the resource makes `SigningKeyResourceTest` fail AND a `%prod`-shaped boot refuse (checked by a `@QuarkusTest` with a `@TestProfile` pointing `gxp.update.public-key-resource` at a non-existent name → `Quarkus startup fails`, asserted through `QuarkusTestProfile` + expecting the boot error — if that proves awkward under Dev Services, assert the loader's behaviour directly with a unit test and record the deviation).

- [x] **T-07 `ManifestClient` — the outbound convention** — _REQ-706, REQ-729, REQ-701_ (D16)
  - Files: `de/gxp/update/control/ManifestClient.java` (Design §1 row; javadoc names it "the outbound HTTP convention Parts B and D copy"; result type `Fetched(byte[] body | FailureKind kind, String exceptionClassName)`), `ManifestClientTest` (JDK `com.sun.net.httpserver.HttpServer` on an ephemeral loopback port: 200 → body; 404 → `HTTP_ERROR`; 70 KiB body → `TOO_LARGE`; a handler that sleeps 12 s → `UNREACHABLE` within 11 s; a 302 → `HTTP_ERROR`, never followed; a closed port → `UNREACHABLE` fast). No `de.gxp.auth`/`de.gxp.user` import (asserted by a one-line source grep in the test).
  - Validate: test green; wall time of the timeout case < 15 s.

- [x] **T-08 `Manifest` + `ManifestParser` + acceptance rules** — _REQ-702, REQ-705, REQ-727_
  - Files: `de/gxp/update/control/Manifest.java` (record), `ManifestParser.java` (Jackson `ObjectMapper` with `FAIL_ON_UNKNOWN_PROPERTIES`, 64 KiB guard, every REQ-705 rule → `UpdateCheckOutcome`), `ManifestParserTest` (fixture manifests: good; `beta` channel → `wrong-channel`; `schemaVersion 2`; equal version → `no-update`; lower version → `no-update`; `minimumFromVersion` above running → `minimum-from-version-not-met`; unknown field → `malformed`; a `version` of `1.1.0; rm -rf /` → `malformed`).
  - Validate: test green.

- [x] **T-09 `ExchangeFiles` + the shared fixture** — _REQ-715, REQ-716, REQ-717_ (D3)
  - Files: `de/gxp/update/control/ExchangeFiles.java` (Design §2: hand-built fixed layout, temp + `ATOMIC_MOVE`, tolerant `readState`), `backend/updater/test/fixtures/desired-update.json` (the **canonical** expected output, built from `fixtures/manifest.json` + `manifest.json.sig` signed with the test key in T-02), `fixtures/state-<outcome>.json` for every outcome in Design §2 plus `state-future-schema.json` (`schemaVersion: 2` + an unknown key), `ExchangeFilesTest` (`writeDesired` output **byte-equals** the fixture; write is observed as temp-then-rename — assert no partial file by writing into a watched directory and checking only the final name ever exists with content; every `state-*.json` round-trips into `UpdaterStateDto`; missing → empty; garbage → empty + one log line with a class name only).
  - Validate: test green; `diff <(java-written) fixtures/desired-update.json` empty.

- [x] **T-10 `UpdateStateStore` + `UpdateControl` core (`runCheck`, `checkForUpdate`, `reconcileDesiredUpdate`)** — _REQ-701, REQ-703, REQ-705, REQ-706, REQ-707, REQ-708, REQ-714, REQ-715, REQ-727_ (D9, D10, D11, D12, D13)
  - Files: `de/gxp/update/control/UpdateStateStore.java` (`@Transactional` `load`/`recordCheck`/`saveSettings`/`recordManualRequest`), `de/gxp/update/control/UpdateControl.java` (public; `runCheck` sequence and `reconcileDesiredUpdate` algorithm exactly as Design §1; `ReentrantLock.tryLock`; jitter via `gxp.update.check-jitter-seconds` + `SecureRandom`-free `ThreadLocalRandom`; timezone via `PropertyControl.get().getTimezone()`), `UpdateCheckTest` (`@QuarkusTest`, JDK `HttpServer` through a `QuarkusTestResourceLifecycleManager` that injects `gxp.update.base-url`; cases: signed-good → `update-available` + row holds the raw bytes + desired file with `applyNotBefore` = next 04:00 `Europe/Berlin` from a fixed `Clock` (2026-09-01T10:00Z → 2026-09-02T02:00:00Z); wrong key → `invalid-signature`, no row change, no file, log line without body content; syntactically invalid body + bad signature → `invalid-signature` (verify precedes parse); base URL unset → `not-configured`, zero requests (server hit counter); `http://` in a profile with `allow-plain-http=false` → `insecure-base-url`, zero requests; closed port → `unreachable` within 15 s while a concurrent `GET /api/guest/session` (device fixture) and `GET /api/manager/property` succeed), `ReconcileTest` (hold rule with a planted `state.json`; auto disabled + no manual → file removed; auto disabled + manual pending → manual file; passed window → moved to the next occurrence with a new `requestedAt`; unchanged window → `requestedAt` stable).
  - Validate: tests green; `AuditCoverageTest` unchanged so far (no `@Transactional public` on `UpdateControl` yet).

- [x] **T-11 Manager API: `UpdateResource` + the four Control operations + audit** — _REQ-710, REQ-711, REQ-712, REQ-713, REQ-714, REQ-726_ (D9, D20, D21)
  - Files: `de/gxp/update/boundary/UpdateResource.java` (class-level `@RolesAllowed("admin")`, four one-liners; remove any T-03 stub), `UpdateControl` (`status`, `updateSettings` `@Transactional` + `AuditControl.changedFields`, `applyNow` `@Transactional` + `409`, `checkNow` non-transactional + audit), `AuditControl` (the four constants), `AuditCoverageTest` (`EXPLICIT_ENTRIES` + `UpdateControl.java → checkNow`; pinned count `25 → 28`), `UpdateApiTest` (rest-assured: 401 anonymous on all four; `GET` shape with defaults (`autoUpdateEnabled true`, `applyAt "04:00"`, `timezone` = property's, `lastCheckOutcome never-checked`, `updater null`); `PUT` `"4:00"`/`"24:00"` → 400; `PUT` valid → 200 + two audit rows (`update`/`update-settings`/`1`/`autoUpdateEnabled`,`applyAt`); `POST /apply` with nothing available → 409; with an available manifest → 202, manual file with `applyNotBefore = now`, audit row `update-apply`/`update`/`<version>`; `POST /check` → 200 + audit row `update-check`/`update`; auto disabled → status still carries `available`).
  - Validate: `./mvnw verify` green incl. `BoundaryAnnotationAuditTest`, `ContractSecuredOperationsDeniedTest` (58), `AuditCoverageTest` (28), the five architecture guards.

- [x] **T-12 `UpdateScheduler` + inventory pin** — _REQ-707, REQ-708_
  - Files: `de/gxp/infrastructure/scheduler/UpdateScheduler.java` (`checkForUpdate` `cron = "0 17 * * * ?"` — minute 17, hourly; `reconcileDesiredUpdate` `cron = "0 */15 * * * ?"`; both `SKIP`, `RuntimeException` caught, class name only, no success log unless an outcome changed — keep it silent), `ScheduledJobInventoryTest` (`PINNED_INVENTORY` gains the two lines; javadoc "three jobs" → "five"; `INVENTORY_FLOOR` stays 2).
  - Validate: `ScheduledJobInventoryTest` green; `%test` scheduler still `halted`.

## Implementation — deployment unit and updater

- [x] **T-13 Compose + Dockerfile** — _REQ-717, REQ-720, REQ-701_
  - Files: `backend/compose.yaml` (`image: ${GXP_IMAGE:-gxp-backend:1.0.0-SNAPSHOT}` with a comment naming the updater as the writer of `.env`'s `GXP_IMAGE=`; env `GXP_UPDATE_BASE_URL: ${GXP_UPDATE_BASE_URL:-}`, `GXP_UPDATE_CHANNEL: ${GXP_UPDATE_CHANNEL:-stable}` with comments; volume `gxp-updates:/updates` on `app`; `gxp-updates:` under `volumes:` with a comment "exchange files only, no secrets, not part of the backup"), `backend/app/src/main/docker/Dockerfile.jvm` (`RUN mkdir -p /updates && chown 185:root /updates && chmod 700 /updates` beside `/tls`).
  - Validate: `sg docker -c "cd backend && GXP_DB_PASSWORD=x GXP_TLS_HOST=h docker compose config"` shows the volume, the default image and the two variables; `docker compose up --build` → `docker compose exec app ls -ld /updates` is `drwx------ 185`.

- [x] **T-14 The updater: script, units, config, installer** — _REQ-718, REQ-719, REQ-720, REQ-721, REQ-722, REQ-723, REQ-727, REQ-725_ (D2, D13, D14, D15, D19)
  - Files: `backend/updater/gxp-updater.sh` (Design §3 steps 0–11 verbatim; `set -euo pipefail`; `export LC_ALL=C`; `sed`-based fixed-layout readers `desired_field`/`manifest_field`/`state_field`; regex table from Design §2 as readonly variables; a `version_le A B` function implementing D6 **by hand — never `sort -V`** (review 2026-08-26 #2: split on `.`/`-`, compare the three integers, on equality "only one side qualified" → the qualified side is lower, two qualifiers → byte-wise `[[ < ]]`); `write_state` via temp + `mv`; every docker call through arrays; journal messages fixed + outcome code; the `.sig`/base64 readers must not require a trailing newline — review R2-10), `gxp-updater.service`, `gxp-updater.timer`, `updater.conf.example`, `install.sh` (key comparison refuse + `--replace-key`; `GXP_COMPOSE_DIR/compose.yaml` must exist; `systemctl enable --now`).
  - Validate: `bash -n` on every script; `shellcheck` if present (not required); T-15…T-17 are the functional proof.

- [x] **T-15 Updater parser test against the shared fixture** — _REQ-715, REQ-716, REQ-718_
  - Files: `backend/updater/test/parse.sh` (sources the reader functions from `gxp-updater.sh` via a `GXP_UPDATER_LIBRARY_ONLY=1` guard; asserts every field of `fixtures/desired-update.json`, of the decoded manifest, and of each `state-*.json`; asserts a desired file with a top-level `artifactRef` of `evil` still yields the manifest's ref; asserts a manifest with `"version": "1.1.0; rm -rf /"` is rejected as `malformed`; asserts `version_le` against the qualifier fixture cases (`1.0.0-SNAPSHOT` ≤ `1.0.0` true, `1.0.0` ≤ `1.0.0-SNAPSHOT` false, `1.0.0` ≤ `1.0.1`, `1.0.10` > `1.0.9`, `1.0.0-rc1` ≤ `1.0.0-rc2` — the same table `SemanticVersionTest` pins); asserts OpenSSL verification of the fixture with the test public key passes and fails on a flipped byte; **`fixtures/manifest.json.sig` has NO trailing newline** (`base64 -w0` output, review R2-10) — every reader of a `.sig`, here and in the updater, must accept a final line without `\n`).
  - Validate: `bash backend/updater/test/parse.sh` exits 0 — no Docker needed.

- [x] **T-16 `smoke-apply.sh` (happy path)** — _REQ-724, REQ-717, REQ-719, REQ-720, REQ-721, REQ-723_
  - Files: `backend/updater/test/smoke-apply.sh`. Flow: requires the compose unit running (`sg docker`), `sudo` for the mountpoint; tags the current image as `gxp-backend:smoke-<ts>` (a second tag, same ID); signs a manifest with the **test** key for version `9.9.9`, `artifactRef` = that tag, `sha256` = the image ID; writes `desired-update.json` (`applyNotBefore = now`, `requestedBy manual`) into `$(docker volume inspect -f '{{.Mountpoint}}' gxp_gxp-updates)`; creates a marker table (`CREATE TABLE rehearsal_marker(v text)` + one row) via `docker compose exec db psql` **before** running the updater; records the app container's `StartCount`/`StartedAt`; runs `gxp-updater.sh` with `GXP_PUBLIC_KEY=<test public key>`, `GXP_BACKUP_DIR=$(mktemp -d)`, `GXP_COMPOSE_DIR=backend`; asserts: dump file exists and is non-empty; `state.json` `outcome = applied`, `currentImage` = the tag; `/api/system/ready` 200; exactly one restart; marker readable; `GET /api/manager/updates` (login with `GXP_ADMIN_USERNAME`/`GXP_ADMIN_PASSWORD` from the environment) shows `updater.outcome = applied` (the live proof of REQ-717's shared directory); the window negative — a second desired file with `applyNotBefore` 1 h ahead → updater exits 0, state unchanged; 3 h behind → same. Cleans up: marker table, `.env` back to the previous image, `compose up`, state file removed.
  - Validate: exits 0; output recorded in `handoff.md`.

- [x] **T-17 `rehearse-rollback.sh` scenarios A and B** — _REQ-724, REQ-722, REQ-719, REQ-721, REQ-723_
  - Files: `backend/updater/test/rehearse-rollback.sh` (`A|B|all`). **A:** builds `gxp-backend:rehearsal-a` from a two-line Dockerfile (`FROM gxp-backend:1.0.0-SNAPSHOT` + `ENTRYPOINT ["sleep","infinity"]`), signs a manifest for it (version `9.9.9`, its image ID), marker as in T-16, runs the updater with `GXP_HEALTH_TIMEOUT=40`; asserts `outcome = rolled-back`, `rolledBack = true`, `message = health-timeout`, the old image serving `ready` 200, marker intact, **two** restarts. **B:** manifest for a second tag of the good image (as T-16); runs the updater with a hook `GXP_REHEARSAL_AFTER_DUMP=<script>` (an env-only test seam, documented in the script header, ignored unless the variable is set) that plants `INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) VALUES (999, '999', 'rehearsal', 'SQL', 'V999__rehearsal.sql', 0, 'gxp', now(), 0, false)` **after** the dump; asserts both boots fail (`message = flyway-failed`), the dump is restored, `outcome = rolled-back-with-restore`, the old image serving, marker intact, the planted row gone. Cleanup as T-16 plus `docker rmi` of the derived image.
  - Validate: `sudo -E sg docker -c "backend/updater/test/rehearse-rollback.sh all"` exits 0; output recorded in `handoff.md`.

## Tests — posture

- [x] **T-18 Posture sweep** — _REQ-726, REQ-701, REQ-725_
  - Files: none new; run and record: `git diff --stat backend/app/pom.xml` empty; `git diff api/openapi.yaml` touches no `/api/guest` or `/api/pairing` path; a log sweep over `UpdateCheckTest` output (`grep -iE 'manifest|artifact|sha256|BEGIN|http' target/surefire-reports/*UpdateCheck*` shows no body bytes/URL); `git ls-files | grep -i private` = the test key only; `ss -ltn` inside the app container unchanged (8443 only).
  - Validate: all five observations pasted into `handoff.md`.

## Cleanup

- [x] **T-19 Documentation** — _REQ-728, REQ-723_
  - Files: `backend/README.md` (new "## Updates" section: host prerequisites, `install.sh`, `GXP_UPDATE_BASE_URL`/`GXP_UPDATE_CHANNEL`, the manifest + `.sig` format, the release signing steps with `tools/update/*`, what the updater does and the rollback ladder, the rehearsal scripts and when to run them, what a manager sees in `GET /api/manager/updates`, the NTP note, the "edits in the minutes before a rollback-with-restore are lost" note, the backup story extension), `backend/compose.yaml` comments (done in T-13 — verify), `docs/system-context.md` (component rows: `de.gxp.update`, `backend/updater/`, `tools/update/`, volume `gxp-updates`; "Scheduled housekeeping" bullet: three → **five** jobs incl. the two crons; new cross-cutting bullet "Update channel"; the "No central patching" deployment row gets a 2026-08-26 note; the `[NEEDS DECISION: how do instances get updated]` item is resolved).
  - Validate: the README commands are copy-pasted and run; `system-context.md` job count equals `PINNED_INVENTORY`.
  - Note (dev 2026-08-26): README also covers the R3 changes — registry-digest `sha256`, the root-owned shadow state, recovering from `rollback-failed`, the R3-9 restore limit; `docs/backend/specification.md` §11 item 8 and the `system-context.md` open question are marked resolved.

- [x] **T-20 Architect review of key handling (before `06_review`)** — _REQ-728_
  - Files: none (review output goes into `handoff.md`). Scope handed to `architect`: production key custody (D17), the single-key/no-rotation posture and whether a dormant second key ships before the first release, the root-reads-uid-185-file boundary (regex + verified-manifest-only), the updater not being channel-updated, `install.sh --replace-key`; **TLS trust root** (review 2026-08-26): `ManifestClient` uses the image JDK's default `cacerts` — no custom truststore — so the update server needs a publicly trusted certificate, and the CA bundle on a box is refreshed only through the channel itself (a JDK rebuild); rotation of the signing key (already recorded). Record the verdict and any resulting TODO rows.
  - Validate: `handoff.md` carries the architect's verdict dated.
  - **Result (architect, 2026-08-26): CONCERNS — not fit to ship before T-20a…T-20c** (`05-architecture-review.md`). Left unticked on purpose; tick together with T-20c.

- [x] **T-20a Two-key trust set (before the first production box)** — done 2026-08-26 (D23; one concatenated resource `signing-public-keys.pem`, not a directory; rotation via a signed `trustedKeys` line; V9 persists the set) — _REQ-703, REQ-718, REQ-725_ (review A-1; **blocked on owner decision D-1**)
  - Files: `SigningKeyLoader` (loads every PEM under `update/signing-public-keys/`), `ManifestVerifier` (accepts if any key verifies), `gxp-updater.sh` (`GXP_PUBLIC_KEY` → a directory, `openssl pkeyutl -verify` looped over its PEMs), `install.sh` (compares the *set*, `--replace-key` semantics unchanged), `updater.conf.example`, `SigningKeyResourceTest` (≥ 2 distinct production keys, none equal to the test key), `gen-signing-key.sh -aes256` (A-2a), README "Keys" (rotation story, both fingerprints), PRD REQ-703/718/725 + Design §4 amended (dated).
  - Validate: `./mvnw verify`; `parse.sh` verifies the fixture under a two-key directory where only the second key matches; `install.sh` refuses a differing set.

- [x] **T-20b `place_file` single-open + `snapshot_file` device check** — done 2026-08-26 (D15a amendment: staged under the root-owned parent + `rename(2)`, the parse.sh `mktemp`-wrapper case proves no path in DIR is opened) — _REQ-716, REQ-718_ (review A-3, A-11)
  - Files: `gxp-updater.sh` `place_file` (no `mktemp`; `(set -C; exec 3> "$dir/$name")` = `O_CREAT|O_EXCL`, write via the fd, mode via `umask`, `mv -f`), `rehearsal-lib.sh:141` the same; `snapshot_file` compares `stat -c '%d:%i'`; `parse.sh` regression (a symlink at the chosen name → refused, nothing written through it).
  - Validate: `parse.sh` green; `smoke-apply.sh` canary checks still green.

- [x] **T-20c Production-path execution: scenario C + one systemd run** — done 2026-08-26 (`rehearse-registry.sh` PASS 30/0, `rehearse-systemd.sh` PASS 37/0 with the A-5 lines; the offline case uses a distinct pre-pulled image, not "the same version"; found and fixed: `artifactRef` regex refused `host:port/…`, `{{.Manifest.Digest}}` needs `println` on buildx 0.29 — see handoff) — _REQ-720, REQ-718, REQ-724_ (review A-4, then A-5)
  - Files: `rehearsal-lib.sh`/`smoke-apply.sh` scenario **C**: `docker run -d --rm -p 127.0.0.1:5000:5000 registry:2`, push the smoke tag as `localhost:5000/gxp-backend:9.9.9`, digest from the push output, sign with the test key, run the updater **without** `GXP_REHEARSAL_LOCAL_IMAGE`; assert `applied`, `.env` = `localhost:5000/gxp-backend:9.9.9@sha256:<digest>`, `RepoDigests` contains it; stop the registry, re-run the same version with a fresh request → local pinned ref found, no `pull-failed`. Then `install.sh` once on the dev box against a throwaway `GXP_COMPOSE_DIR` + the test key, `systemctl start gxp-updater.service` → exit 0 / "nothing to do" in the journal, a seam in the conf → refused; `disable --now` and remove. Only after that: the A-5 hardening lines in `gxp-updater.service`, re-run under systemd. README: the compose dir must not be under `/home`.
  - Validate: both logs in `handoff.md`; `docker images | grep -E 'gxp|registry'` = base image only afterwards.

- [x] **T-20d Restore rung completeness** — done 2026-08-26 (D24; rehearsal B asserts `leftover_v999` gone) — _REQ-722_ (review A-6)
  - Files: `gxp-updater.sh` rung 11: `pg_restore --list < dump` must succeed → `psql -c 'DROP SCHEMA public CASCADE; CREATE SCHEMA public; ALTER SCHEMA public OWNER TO gxp;'` → `pg_restore` **without** `--clean`; `rehearse-rollback.sh B` plants `CREATE TABLE leftover_v999` after the dump and asserts it is gone; README "Known limit (R3-9)" removed.
  - Validate: `rehearse-rollback.sh all` green.

- [x] **T-20e Docs: one digest command, TLS policy, updater update path** — done 2026-08-26 (`gen-signing-key.sh -aes256` NOT done: D-2 deferred by the owner) — _REQ-728_ (review A-7, A-8, A-10)
  - Files: `sign-manifest.sh` help + README: the only digest source is `docker buildx imagetools inspect <ref> --format '{{.Manifest.Digest}}'` (drop the `RepoDigests` hint; single-platform push recommended while the fleet is arm64-only); README release policy: rebuild from the latest `ubi9/openjdk-21-runtime` tag per release, update host + registry chain to ISRG Root X1, host install requires `unattended-upgrades`; README "the updater itself is updated by re-running `install.sh`"; Design "Risks" records updater self-update from the verified image (install by rename) and updater-initiated manifest fetch as later features.
  - Validate: doc review.

- [x] **T-20f Seed the host-side downgrade guard at install** — done 2026-08-26 (D25; smoke asserts `downgrade-refused` before any apply) — _REQ-727_ (review A-9)
  - Files: `install.sh --current-version <MAJOR.MINOR.PATCH[-Q]>` writes a shadow state with `currentVersion` (0600, `outcome = none`) when none exists; README install step.
  - Validate: `parse.sh` case; the updater refuses a signed lower version before any apply (`downgrade-refused`).

- [x] **T-21 Full verification + handoff** — done 2026-08-26 (430 tests / 0 failures / 2 container-drop errors re-run green in isolation → honest 432; parse.sh 305/0; smoke 64/0; rollback A+B PASS; C PASS; systemd PASS) — _all REQ-701…729_
  - Files: `docs/features/update-channel/handoff.md`, `docs/features/index.md` (row → `05_implement done → 06_review`).
  - Validate: one full background `./mvnw verify` green (count recorded), `parse.sh`, `smoke-apply.sh`, `rehearse-rollback.sh all` green; `packages/api-client/` regenerated byte-identically on a second run.
