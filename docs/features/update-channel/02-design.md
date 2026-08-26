# Design: Update Channel (remote-operations Part C)
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **TODO:** [03-todo.md](03-todo.md)
**Status:** In Review (2026-08-26)

Governed by constitution **v1.8.0** (principle 4: the rewritten fourth
bullet, the survival invariant, "remote access is separately sold"),
`docs/backend/remote-operations.md` §6 + §12 items 1/8/9, and
`backend/CLAUDE.md`. Requirements are referenced as `REQ-7xx`, never
restated. **Nothing here assumes Part B** (no instance key, no name, no
enrollment) — the sequencing note in the PRD is binding.

**Recorded verbatim from §6 (the reason REQ-728 routes key handling to an
`architect` review):** *"the update channel becomes the highest-value target
associated with this product. Whoever controls it controls every deployed
instance. It deserves more scrutiny than any single application endpoint,
and its key handling should be reviewed independently."*

## Architecture decision

Three components, two of which are not Java:

```
 ┌───────────────────────── box ──────────────────────────────┐
 │  app container (uid 185)          host (root)              │
 │  de.gxp.update ──writes──▶ /updates/desired-update.json    │
 │       ▲                        (volume gxp-updates)        │
 │       └──reads──── /updates/state.json ◀──writes── updater │
 │  hourly GET manifest + .sig      systemd timer, 5 min      │
 │        │  (outbound only)        pg_dump → pull → swap →   │
 └────────┼─────────────────────────restart → health → keep/  │
          ▼                                          rollback │
   update server (static files, no logic, no key)             │
```

### 1. `de.gxp.update` — the application side (REQ-701…716, 727, 729)

| Sub-package | Type | Role |
|---|---|---|
| `boundary` | `UpdateResource` (implements generated `ManagerUpdatesApi`, class-level `@RolesAllowed("admin")`) | four one-line delegations |
| `control` | **`UpdateControl`** (public) | the only type other code references; orchestration, outcome mapping, audit calls, the four API operations, the two public job entry points |
| `control` | `ManifestClient` (package-private) | **the reusable outbound convention (REQ-729)**: `java.net.http.HttpClient` built once (`connectTimeout 5 s`, `followRedirects NEVER`, HTTP/1.1, no proxy, no cookie handler), `GET` with `requestTimeout 10 s`, `BodyHandlers.ofByteArray` guarded by a **64 KiB cap on `Content-Length` and on the streamed body** (`ofInputStream` + bounded read — a body without `Content-Length` is capped the same way), non-2xx → `Fetched.failure(HTTP_ERROR)`, any `IOException`/`InterruptedException`/`HttpTimeoutException` → `Fetched.failure(UNREACHABLE)` with the exception **class name** kept for the log. Javadoc names it as the shape Parts B and D copy; imports nothing from `de.gxp.auth`/`de.gxp.user`/any identity |
| `control` | `ManifestVerifier` (package-private) | `Signature.getInstance("Ed25519")` over the raw manifest bytes with the loaded public key; returns boolean, throws nothing outward |
| `control` | `SigningKeyLoader` (package-private, `@ApplicationScoped`, `@Observes StartupEvent`) | loads `gxp.update.public-key-resource` from the classpath, strips the PEM armour, `KeyFactory("Ed25519").generatePublic(new X509EncodedKeySpec(...))`; absent/unparseable → throws `IllegalStateException("update signing public key missing or invalid: <resource name>")` → Quarkus refuses to start (REQ-704) |
| `control` | `Manifest` (package-private record) + `ManifestParser` | Jackson `ObjectMapper` (already on the classpath via `quarkus-rest-jackson`) with `FAIL_ON_UNKNOWN_PROPERTIES = true` into the record; every REQ-705 rule as a method returning an `UpdateCheckOutcome` or `null` |
| `control` | `SemanticVersion` (package-private record, `Comparable`) | `MAJOR.MINOR.PATCH` with an optional `-QUALIFIER`; a qualifier sorts **below** the same release (`1.0.0-SNAPSHOT < 1.0.0`). Parses the running version (`quarkus.application.version`) and manifest versions; ~40 lines, no dependency |
| `control` | `ExchangeFiles` (package-private) | the **only** code touching `gxp.update.exchange-dir`: `writeDesired(DesiredUpdate)` (temp file `.desired-update.json.tmp` in the same directory + `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`), `deleteDesired()`, `readDesired()`, `readState()` (tolerant: missing file → `Optional.empty()`, unknown keys ignored, newer `schemaVersion` still read for the known keys, unparseable → `Optional.empty()` + class-name log) |
| `control` | `UpdateStateStore` (package-private, `@ApplicationScoped`) | the **transactional** collaborator: `@Transactional` methods `load()`, `recordCheck(outcome, manifestOrNull)`, `saveSettings(...)`, `recordManualRequest(version, at)`. Keeps HTTP out of every transaction (see "Transactions") |
| `control` | `UpdateChannelRepository` (package-private, Panache) | the single row |
| `entity` | `UpdateChannel` (`@Entity`, table `update_channel`) + `UpdateChannelMapper` (→ `UpdateStatusDto` pieces) | public fields only where `UpdateControl`/`UpdateStateStore` touch them |
| `de.gxp.infrastructure.scheduler` | `UpdateScheduler` (package-private) | two `@Scheduled` methods, each one delegating call + the class-name-only catch (REQ-707/708) |

Control dependencies: `UpdateControl → PropertyControl` (the property's
timezone via `PropertyControl.get().getTimezone()`), `UpdateControl →
AuditControl` (three request-path methods). No cycles; nothing references
`UpdateControl` except `UpdateResource` and `UpdateScheduler`.

**Transactions and audit.** `AuditControl.record` reads the request-scoped
`ClientAttribution`, so it may only be called on a request path. Therefore:

| `UpdateControl` method | Path | `@Transactional`? | Audit |
|---|---|---|---|
| `checkForUpdate()` | scheduler | no — sleeps the jitter, then `runCheck()` | never |
| `reconcileDesiredUpdate()` | scheduler (and called at the end of every request-path mutation) | no — reads via the store, writes a file | never |
| `status()` | `GET` | no (store reads in its own tx) | no |
| `updateSettings(UpdateSettingsRequest)` | `PUT` | **yes** | `record(ACTION_UPDATE, ENTITY_UPDATE_SETTINGS, "1", changedFields)` — `changedFields` = `AuditControl.changedFields(before, after)` over the two `UpdateSettingsRequest` shapes |
| `applyNow()` | `POST /apply` | **yes** | `record(ACTION_UPDATE_APPLY, ENTITY_UPDATE, <version>, null)` |
| `checkNow()` | `POST /check` | **no** (HTTP must not run inside a transaction); the store commits the outcome, then `record(ACTION_UPDATE_CHECK, ENTITY_UPDATE, null, null)` opens its own `REQUIRED` tx | yes |

`AuditCoverageTest`: `updateSettings`/`applyNow` are found by the
`@Transactional public` scan; `checkNow` joins `EXPLICIT_ENTRIES`
(`de/gxp/update/control/UpdateControl.java` → `List.of("checkNow")`), the
`login` precedent. The pinned count moves **25 → 28**. Constants added to
`AuditControl`: `ACTION_UPDATE_APPLY = "update-apply"`,
`ACTION_UPDATE_CHECK = "update-check"`, `ENTITY_UPDATE = "update"`,
`ENTITY_UPDATE_SETTINGS = "update-settings"` (the settings change reuses
`ACTION_UPDATE = "update"`, exactly as the PRD's REQ-711 spells it).

`runCheck()` (private) is guarded by a `ReentrantLock.tryLock()`: a check
arriving while one runs (scheduler vs. `POST /check`) returns the current
status without a second fetch. Sequence:

```
runCheck():
  cfg = (baseUrl?, channel)                      unset baseUrl → NOT_CONFIGURED, no request
  baseUrl not https:// and !allow-plain-http     → INSECURE_BASE_URL, no request
  m  = client.get(base + "/manifest/" + ch + ".json")      failure → UNREACHABLE | HTTP_ERROR | TOO_LARGE
  s  = client.get(... + ".sig")                            same
  sig = base64-decode(s) — not 64 bytes / bad base64       → INVALID_SIGNATURE
  verifier.verify(m.bytes, sig) == false                   → INVALID_SIGNATURE   (before any parse)
  manifest = parser.parse(m.bytes)   parse error           → MALFORMED
  parser.accept(manifest, channel, runningVersion)         → WRONG_SCHEMA_VERSION | WRONG_CHANNEL |
                                                             NO_UPDATE | MINIMUM_FROM_VERSION_NOT_MET
  store.recordCheck(UPDATE_AVAILABLE, manifest, m.bytes, sig)
  reconcileDesiredUpdate()
```
Every non-available outcome calls `store.recordCheck(outcome, null)`, which
**clears the stored available manifest** — an update that stops being
offered (or a box that was just updated and now sees `NO_UPDATE`) stops
being "available", and the next reconcile removes the desired file. The
log line for every failure is `LOG.warnf("Update check failed: %s", code)`
plus, for `UNREACHABLE`/`HTTP_ERROR`, the exception class name — never a
URL, never a body byte.

**`reconcileDesiredUpdate()`** (REQ-708/714/715), runs every 15 minutes and
after every request-path mutation:

```
row = store.load(); state = files.readState()
if row.available == null                        → files.deleteDesired(); return
manualPending = row.manualRequestedAt != null
             && row.manualRequestedVersion == row.available.version
             && (state.lastRequestedAt == null || row.manualRequestedAt > state.lastRequestedAt)
if manualPending → files.writeDesired(requestedAt = row.manualRequestedAt, requestedBy = manual,
                                       applyNotBefore = row.manualRequestedAt); return
if !row.autoUpdateEnabled                        → files.deleteDesired(); return
if state.attemptedVersion == row.available.version → files.deleteDesired(); return   (hold rule)
next = nextOccurrence(row.applyAt, ZoneId.of(property.timezone), now)   // strictly in the future
existing = files.readDesired()
if existing matches (version, requestedBy = auto) && existing.applyNotBefore + window > now → return  (stable requestedAt)
files.writeDesired(requestedAt = now, requestedBy = auto, applyNotBefore = next)
```
The write is idempotent per window: `requestedAt` only changes when a window
passed unapplied, which is exactly when the updater must see a *new*
request (REQ-718's replay guard keys on `requestedAt`). The timezone
fallback is `UTC` only if the property row's zone fails to parse (it is
validated on write, so this is a defensive line, logged class-name-only).

**`applyNow()`**: `409 no-update-available` when `row.available == null`;
otherwise `store.recordManualRequest(version, now)`, audit,
`reconcileDesiredUpdate()` (writes the manual file immediately), `202` +
status. **`updateSettings()`**: validate (`applyAt` `^([01][0-9]|2[0-3]):[0-5][0-9]$`
enforced by the contract pattern → bean validation `400`), persist, audit,
reconcile, return status. **`status()`** assembles `UpdateStatusDto` from
the row, the config, the property timezone, `SemanticVersion` of the running
app, `scheduledFor` = the `applyNotBefore` of the desired file if one exists
(else `null`), and `updater` = `files.readState()` mapped 1:1 or `null`.

**Configuration** (`application.properties`, all names verified against
existing conventions; `GXP_*` env names map through MP Config):

```
gxp.update.base-url=${GXP_UPDATE_BASE_URL:}          # empty = channel off
gxp.update.channel=${GXP_UPDATE_CHANNEL:stable}      # ^[a-z0-9-]{1,32}$, checked at startup
gxp.update.exchange-dir=/updates
gxp.update.public-key-resource=update/signing-public-key.pem
gxp.update.check-jitter-seconds=600
gxp.update.allow-plain-http=false
%test.gxp.update.exchange-dir=target/update-exchange
%test.gxp.update.public-key-resource=update/test-signing-public-key.pem
%test.gxp.update.check-jitter-seconds=0
%test.gxp.update.allow-plain-http=true
%test.gxp.update.base-url=                           # tests set it per case via a test profile / WireMock-free local HttpServer
```
Tests stand up `com.sun.net.httpserver.HttpServer` on an ephemeral loopback
port (JDK, no dependency) and point the client at it through a
`@TestProfile` override (`gxp.update.base-url` is read per check, so a
package-private setter on the Control for tests is **not** needed: the
profile's `http://127.0.0.1:<port>` is injected via a
`QuarkusTestResourceLifecycleManager` that starts the server and hands the
URL to the profile config — the Dev Services pattern already used for
Postgres).

### 2. The exchange files — the app ↔ updater contract (REQ-715, 716, 717)

Both files use a **fixed, line-oriented layout** — one key per line, two-space
indent, keys in exactly this order, string values never containing `"` or
`\` (every producer validates against the regexes below before writing) —
so the updater parses them with `sed` and no JSON library. The application
**writes** `desired-update.json` with a hand-built template (never
`ObjectMapper` serialization: field order and whitespace are not something
to rely on) and **reads** `state.json` with Jackson, tolerant.

`desired-update.json` (written by the app, uid 185, mode `0644`; deleted by
the app only):
```
{
  "schemaVersion": 1,
  "version": "1.0.1",
  "artifactRef": "ghcr.io/example/gxp-backend:1.0.1",
  "sha256": "0123…cdef",
  "requestedAt": "2026-09-01T10:00:00Z",
  "requestedBy": "auto",
  "applyNotBefore": "2026-09-02T02:00:00Z",
  "applyWindowSeconds": 7200,
  "manifest": "<base64 of the verified raw manifest bytes, one line>",
  "signature": "<base64 of the 64 raw signature bytes>"
}
```
`version`/`artifactRef`/`sha256` are **informational copies** — the updater
never acts on them (REQ-718); the signed `manifest` is the only source of
truth on the host.

`state.json` (written by the updater, root, mode `0644`, atomically via temp
+ `mv`; never written by the app):
```
{
  "schemaVersion": 1,
  "updatedAt": "2026-09-02T02:03:11Z",
  "lastAttemptAt": "2026-09-02T02:00:04Z",
  "lastRequestedAt": "2026-09-01T10:00:00Z",
  "attemptedVersion": "1.0.1",
  "outcome": "applied",
  "rolledBack": false,
  "currentImage": "ghcr.io/example/gxp-backend:1.0.1",
  "currentVersion": "1.0.1",
  "previousImage": "gxp-backend:1.0.0-SNAPSHOT",
  "backupFile": "pre-update-1.0.1-20260902T020004Z.dump",
  "message": null
}
```
Absent values are the literal `null` (unquoted). `outcome` ∈ `applied`,
`rolled-back`, `rolled-back-with-restore`, `rollback-failed`,
`backup-failed`, `pull-failed`, `artifact-mismatch`, `downgrade-refused`,
`invalid-signature`, `malformed`, `none` (installed, nothing attempted
yet). `message` is one of a fixed set of codes (`health-timeout`,
`flyway-failed`, `container-restarting`, `restore-failed`) or `null` —
never log text.

Value regexes (shared by `ExchangeFiles`, `sign-manifest.sh` and the
updater; a miss on either side is `malformed`):

| Field | Regex |
|---|---|
| version / attemptedVersion / currentVersion / minimumFromVersion | `^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.]{1,32})?$` |
| artifactRef / currentImage / previousImage | `^[a-z0-9][a-z0-9._-]{0,127}(:[0-9]{1,5})?(/[a-z0-9][a-z0-9._-]{0,127}){0,7}(:[A-Za-z0-9_][A-Za-z0-9._-]{0,127})?(@sha256:[0-9a-f]{64})?$` (*amended 2026-08-26, T-20c:* the first component may carry a `:port` — the old shape refused `localhost:5000/gxp-backend:9.9.9`, i.e. every registry on a non-default port; path components are now one-per-slash, max 8) |
| sha256 | `^[0-9a-f]{64}$` |
| instants | `^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$` (UTC, second precision, always `Z`) |
| channel | `^[a-z0-9-]{1,32}$` |
| backupFile | `^pre-update-[0-9A-Za-z.-]+-[0-9]{8}T[0-9]{6}Z\.dump$` |
| base64 fields | `^[A-Za-z0-9+/]+={0,2}$` |

**The shared fixture.** `backend/updater/test/fixtures/desired-update.json`
(+ `manifest.json`, `manifest.json.sig`, `state.json` for every outcome) is
read by **both** the JVM test (`ExchangeFilesTest` asserts the app's write
is byte-identical to it, and that each `state.json` fixture maps into the
DTO) and the updater's parser test (`backend/updater/test/parse.sh` asserts
the `sed` extraction yields the expected values). That is how the contract
is proven without a manifest server on the dev box (PRD REQ-713/717
amendment 2026-08-26).

**Volume.** `compose.yaml` gains `gxp-updates:` under `volumes:` and
`- gxp-updates:/updates` on `app`; `Dockerfile.jvm` gains
`RUN mkdir -p /updates && chown 185:root /updates && chmod 700 /updates`
beside the `/tls` line (a fresh named volume copies the image directory's
ownership). The updater resolves the host path once per run:
`docker volume inspect -f '{{.Mountpoint}}' gxp_gxp-updates`.

### 3. The updater — host-side script + systemd timer (REQ-718…724, 727)

**Choice: a bash script under `systemd`, not a supervisor container.** A
container that can restart the app needs the Docker socket, which is
root-equivalent on the host — so the "container" option does not lower the
privilege, it only hides it behind a second image that itself needs patching
(principle 4's dependency posture) and cannot replace *its own* container
either. A root script under a timer is the smallest privileged surface that
exists on every target (stock Debian / Raspberry Pi OS): ~350 lines of bash,
`docker`, `openssl`, `flock`, `sed`, `date` — nothing installed,
nothing to keep patched beyond the OS. Rejected alternatives recorded in
D2.

Files (`backend/updater/`):

| File | Role |
|---|---|
| `gxp-updater.sh` | the updater (below) |
| `gxp-updater.service` | `Type=oneshot`, `ExecStart=/usr/local/lib/gxp/gxp-updater.sh`, `EnvironmentFile=/etc/gxp/updater.conf`, `ProtectSystem=full`, `PrivateTmp=yes` |
| `gxp-updater.timer` | `OnCalendar=*:0/5`, `Persistent=true`, `RandomizedDelaySec=60` |
| `updater.conf.example` | `GXP_COMPOSE_DIR=/opt/gxp/backend` (the directory holding `compose.yaml`; the compose project name is `gxp`), `GXP_BACKUP_DIR=/var/lib/gxp/backups`, `GXP_PUBLIC_KEY=/etc/gxp/update-signing-public-key.pem`, `GXP_HEALTH_TIMEOUT=180`, `GXP_HEALTH_HOLD=30`, `GXP_KEEP_BACKUPS=5` |
| `install.sh` | copies the script (`0755`, root), units, `updater.conf` (only if absent), the public key from `../app/src/main/resources/update/signing-public-key.pem` → `/etc/gxp/update-signing-public-key.pem` (`0644`); **refuses** when a host key exists and differs (`--replace-key` to override, printed loudly); `mkdir -p` the backup dir `0700`; `systemctl daemon-reload && systemctl enable --now gxp-updater.timer` |
| `test/smoke-apply.sh`, `test/rehearse-rollback.sh`, `test/parse.sh`, `test/fixtures/…` | REQ-724 and the shared fixture |

`gxp-updater.sh` control flow (each step's failure sets the named
`outcome` in `state.json` and exits `0` — a failed update is a state, not a
crash; the script exits non-zero only for a configuration error before
touching anything):

```
 0  flock -n /run/lock/gxp-updater.lock  (already running → exit 0)
 1  resolve EXCHANGE=$(docker volume inspect -f '{{.Mountpoint}}' gxp_gxp-updates)
    no desired-update.json → exit 0
 2  parse desired (sed, fixed layout; any line not matching → outcome=malformed)
    requestedAt ≤ state.lastRequestedAt → exit 0 (already handled)      [REQ-718 replay guard]
    now < applyNotBefore or now ≥ applyNotBefore + applyWindowSeconds → exit 0   [REQ-723 window]
 3  base64 -d manifest → $T/manifest.json ; base64 -d signature → $T/manifest.sig
    openssl pkeyutl -verify -pubin -inkey $GXP_PUBLIC_KEY -rawin -in manifest.json -sigfile manifest.sig
        ≠ 0 → outcome=invalid-signature                                     [REQ-718 trust root]
 4  parse manifest (fixed layout, 8 or 9 lines, regex per field) → VERSION ARTIFACT SHA
        miss → outcome=malformed (before any docker command)
    state.currentVersion set and VERSION ≤ it (version_le: numeric triple, qualifier < release — NOT sort -V, D6)
                                                          → outcome=downgrade-refused   [REQ-727]
 5  write state: lastAttemptAt=now, lastRequestedAt=desired.requestedAt, attemptedVersion=VERSION,
    outcome=in-progress          ← written BEFORE any side effect: a crash mid-way never re-attempts
 6  PREVIOUS=$(docker compose config --format json | … image of app)  — read the effective image
    pg_dump: docker compose exec -T db pg_dump -U gxp -d gxp --format=custom > $BACKUP/pre-update-$VERSION-$TS.dump
        fail/empty → outcome=backup-failed ; prune to newest $GXP_KEEP_BACKUPS               [REQ-719]
 7  docker pull "$ARTIFACT" (fail but `docker image inspect` finds it locally → continue, else pull-failed)
    docker image inspect -f '{{.Id}}' "$ARTIFACT" == "sha256:$SHA" or outcome=artifact-mismatch [REQ-720]
    (amended 2026-08-26, D5/R3-6: pull "$ARTIFACT@sha256:$SHA" — Docker enforces the registry digest —
     and record the local image ID in the shadow state; the local-ID comparison is the rehearsal-only path)
 8  set_image "$ARTIFACT"  →  rewrite/append the GXP_IMAGE= line of $GXP_COMPOSE_DIR/.env (temp + mv)
    docker compose up -d --no-build --pull never app                                          [one restart]
 9  healthy?  loop ≤ $GXP_HEALTH_TIMEOUT s, every 5 s, ALL of:
        docker compose exec -T app curl -fsk https://localhost:8443/api/system/ready → 200
        docker compose exec -T db psql -U gxp -d gxp -tAc
            "SELECT success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1" == t
        docker inspect -f '{{.State.Restarting}}' <app container> == false
     then sleep $GXP_HEALTH_HOLD and re-check once                                            [REQ-721]
     ok → state: outcome=applied, currentImage=ARTIFACT, currentVersion=VERSION, previousImage=PREVIOUS,
               backupFile=…, rolledBack=false → exit 0
10  rollback: set_image "$PREVIOUS"; compose up -d --no-build --pull never app; healthy?
     ok → outcome=rolled-back, rolledBack=true, message=<why the new one failed>            [REQ-722]
11  restore: docker compose stop app; pg_restore -U gxp -d gxp --clean --if-exists < dump
     (via docker compose exec -T db); compose up app; healthy?
     ok → outcome=rolled-back-with-restore ; else outcome=rollback-failed, message=restore-failed,
     old image left running, nothing else touched, never retried for this requestedAt
```
Every value that reaches a command line (`$ARTIFACT`, `$VERSION`, `$SHA`,
file names) has passed its regex in step 2/4 — the desired file is written
by a lower-privileged process and is treated as hostile input. `set -euo
pipefail`, no `eval`, arrays for every argument list, `--` before
positional arguments. `state.json` is written via `$EXCHANGE/.state.json.tmp`
+ `mv`. The script logs to the journal with fixed messages and the outcome
code — no manifest content, no URLs.

**Why ready + the Flyway row.** A failed migration aborts the Quarkus boot,
so `/api/system/ready` never answers and the timeout alone would catch it;
the row check is what makes the *message* say `flyway-failed` instead of
`health-timeout`, and it also catches the pathological "ready answers but
the newest migration is recorded failed" case that a manually repaired
history could produce. Both are cheap.

**Timing and the kiosk (REQ-723).** Verified in
`frontend/src/app/core/guest/guest-data.service.ts`: a poll failure with a
held config sets `STALE` (staleness pill, keeps polling); only
`HttpErrorResponse.status === 401` wipes the token — a refused connection is
status `0`. `DISCONNECTED` (no cache) retries 5 → 30 s. A successful update
therefore costs one restart (~20–40 s on the Pi) seen as a `Stand HH:MM`
pill; a failed one costs two. **No frontend change.** The restart also drops
every manager token (in-memory store, already documented in the README).

### 4. Keys and release tooling (REQ-725, 703, 704)

- `tools/update/gen-signing-key.sh [dir]` — `umask 077; openssl genpkey
  -algorithm ed25519 -out $dir/signing-private-key.pem; openssl pkey -in …
  -pubout -out $dir/signing-public-key.pem`; refuses a directory inside a git
  work tree; prints the public key's SHA-256 fingerprint for the README.
- `tools/update/sign-manifest.sh <private-key.pem> <out-dir> --channel
  --version --artifact-ref --sha256 --published-at [--minimum-from-version]`
  — validates every field against the regex table, writes
  `<channel>.json` in the fixed layout, signs (`openssl pkeyutl -sign -rawin
  -inkey key -in <channel>.json | base64 -w0 > <channel>.json.sig`), then
  **verifies its own output** with the derived public key before exiting `0`.
  ~~It prints the image ID to put in `--sha256`~~ *Amended 2026-08-26 (D5):*
  `--sha256` is the **registry manifest digest** of the pushed artifact
  (`docker push` prints it; `docker buildx imagetools inspect <ref> --format
  '{{println .Manifest.Digest}}'` — the `println` is load-bearing: a bare `{{.Manifest.Digest}}` makes buildx v0.29 print its default table instead, T-20c), strip `sha256:` — the updater pulls
  `<ref>@sha256:<digest>`.
- **Production public key**: `backend/app/src/main/resources/update/signing-public-key.pem`,
  committed. Generated by the dev running `gen-signing-key.sh` on **the
  owner's machine** (this box) into `$HOME/.gxp/keys/` (outside the repo,
  `0700`); the private key stays there and the handoff records the path and
  fingerprint. The build **fails without the resource** (`SigningKeyResourceTest`
  reads it from disk, parses it, asserts it differs from the test public
  key, and asserts no `PRIVATE KEY` block exists under `src/main/resources`,
  `backend/updater`, `tools/update`). `.gitignore` gains
  `*signing-private-key*`.
- **Test key pair**: `src/test/resources/update/test-signing-private-key.pem`
  + `test-signing-public-key.pem` — public by definition, used by JVM tests,
  `parse.sh`, `smoke-apply.sh`, `rehearse-rollback.sh` (they pass
  `GXP_PUBLIC_KEY=<test public key>` to the updater).
- ~~**Rotation** is out of scope …~~ *Amended 2026-08-26 (owner decision D-1
  after the architect review, A-1) — **D23, the two-key trust set with
  in-band rotation:*** the resource is `signing-public-keys.pem` (one PEM,
  2..4 `PUBLIC KEY` blocks: key 1 active, key 2 dormant; fingerprints in the
  README); `SigningKeyLoader` loads the set, `ManifestVerifier.verify(bytes,
  sig, set)` returns the key that verified (any key of the set may). The host
  copy is `/etc/gxp/update-signing-public-keys.pem` (`GXP_PUBLIC_KEYS`), split
  into per-key PEMs and looped over with `openssl pkeyutl -verify`. Keys
  travel as **tokens** (base64 of the 44-byte SPKI DER, 60 chars — one
  fixed-layout line). A manifest may carry an optional `trustedKeys` line
  (`sign-manifest.sh --trusted-keys <pem>`): after verification, if the set
  contains the signer (else `malformed` — a set that retires its own signer is
  a mistake) and the manifest's version is **newer** than the version that
  introduced the currently persisted set, the set is replaced — app: columns
  `trusted_keys`/`trusted_keys_version` (V9, `UPDATE_AVAILABLE` only, same
  statement); updater: shadow lines `trustedKeys`/`trustedKeysVersion`
  (written with the next state, whatever the apply's outcome). **Effective
  set:** app = the persisted set when its version is newer than the running
  image's, else the image's own set (a rolled-back older image does not
  re-trust a retired key; a hand-installed newer image carries its own);
  updater = the shadow's set if one exists, else the file. A replayed older
  manifest cannot roll the set back (version rule). Rejected: a separate
  TUF-style rotation document (a second signed document type on both sides —
  the architect's own recommendation was to layer nothing beyond the set for
  v1; one optional line does the same with the existing readers).

## Data model

```sql
-- V8__update_channel.sql
CREATE TABLE update_channel (
    id                       smallint    PRIMARY KEY CHECK (id = 1),
    auto_update_enabled      boolean     NOT NULL DEFAULT true,
    apply_at                 time        NOT NULL DEFAULT '04:00',
    last_check_at            timestamptz,
    last_check_outcome       text        NOT NULL DEFAULT 'never-checked',
    available_version        text,
    available_artifact_ref   text,
    available_sha256         text,
    available_published_at   timestamptz,
    available_manifest       bytea,
    available_signature      bytea,
    manual_requested_version text,
    manual_requested_at      timestamptz,
    updated_at               timestamptz NOT NULL,
    CHECK ((available_version IS NULL) = (available_manifest IS NULL))
);
INSERT INTO update_channel (id, updated_at) VALUES (1, now());
```
`MigrationTest` head moves `7 → 8` and asserts the seeded defaults. The
row carries the verified raw bytes so a restart never re-fetches to write
the desired file, and so `available_*` columns are provably derived from
bytes that verified (they are written in the same statement).

## API contract (`api/openapi.yaml`, new tag `manager-updates` → `UpdateResource`)

| Operation | Path | Request | Responses |
|---|---|---|---|
| `getUpdateStatus` | `GET /api/manager/updates` | — | `200 UpdateStatusDto` |
| `updateUpdateSettings` | `PUT /api/manager/updates/settings` | `UpdateSettingsRequest{autoUpdateEnabled: boolean (required), applyAt: string pattern ^([01][0-9]\|2[0-3]):[0-5][0-9]$ (required)}` | `200 UpdateStatusDto`, `400` |
| `applyUpdateNow` | `POST /api/manager/updates/apply` | — | `202 UpdateStatusDto`, `409 no-update-available` |
| `checkForUpdateNow` | `POST /api/manager/updates/check` | — | `200 UpdateStatusDto` |

All four `bearerAuth` (the `ContractSecuredOperationsDeniedTest` pin grows
54 → 58). Schemas:

```
UpdateStatusDto {
  currentVersion: string           # quarkus.application.version, e.g. "1.0.0-SNAPSHOT"
  channel: string
  configured: boolean              # GXP_UPDATE_BASE_URL set
  autoUpdateEnabled: boolean
  applyAt: string                  # "HH:MM"
  timezone: string                 # the property's IANA zone
  lastCheckAt?: date-time
  lastCheckOutcome: UpdateCheckOutcome
  available?: AvailableUpdateDto { version, publishedAt: date-time, artifactRef }
  scheduledFor?: date-time         # applyNotBefore of the current desired file
  updater?: UpdaterStateDto {
    updatedAt: date-time, lastAttemptAt?: date-time, attemptedVersion?: string,
    outcome: string, rolledBack: boolean, currentImage: string, currentVersion?: string,
    previousImage?: string, backupFile?: string, message?: string }
}
UpdateCheckOutcome (enum): never-checked | not-configured | insecure-base-url | unreachable |
  http-error | too-large | invalid-signature | malformed | wrong-schema-version | wrong-channel |
  no-update | minimum-from-version-not-met | update-available
```
`UpdaterStateDto.outcome`/`message` are **strings, not enums**, on purpose:
a newer updater may write a code this app version does not know, and the
status must still render (REQ-716 tolerance). `lastCheckOutcome` is
app-owned and therefore an enum. The raw manifest and signature never
appear in any DTO (REQ-726).

## Sequence — the automatic path

```
01:00Z  UpdateScheduler#checkForUpdate ── jitter ≤ 600 s ── UpdateControl.runCheck
          GET …/stable.json, …/stable.json.sig → verify (JDK, image key) → parse → 1.0.1 > 1.0.0-SNAPSHOT
          store.recordCheck(UPDATE_AVAILABLE, …) ── reconcile ── writes desired-update.json
                                                       applyNotBefore = next 04:00 Europe/Berlin = 02:00Z
02:00Z  gxp-updater.timer ── verify again (OpenSSL, host key) ── window ok ── state: in-progress
          pg_dump ── pull ── image ID == sha256 ── .env GXP_IMAGE=… ── compose up app   (restart #1)
          ready 200 + flyway success + not restarting ── hold 30 s ── re-check ── state: applied
02:05Z  app (1.0.1) checkForUpdate → NO_UPDATE → available cleared → reconcile deletes desired file
        GET /api/manager/updates → currentVersion 1.0.1, updater.outcome applied
```

## Risks & trade-offs

- **The updater is not updated by the channel.** It is a host file; a bug in
  it needs `install.sh` again. Accepted for a ~700-line script with a
  rehearsed test suite; recorded as an open item for the architect review
  (a future image could carry a copy and `install.sh` could refresh from the
  running container — not built now). *Architect review 2026-08-26 (A-10,
  A-9), recorded as follow-up features in `docs/features/index.md`:*
  **updater self-update from the verified image** (the image ships
  `/deployments/updater/{gxp-updater.sh,*.service,*.timer,keys/,VERSION}`;
  after `applied` the old updater `docker create`s the pinned image,
  `docker cp`s the bundle to a root staging dir, `bash -n`s it and installs
  script + units + key set **by rename — a running bash script is never
  overwritten in place** — keeping `.prev`; same trust chain), and
  **updater-initiated manifest fetch** (the updater `curl`s
  `<base>/manifest/<channel>.json` itself and treats the app's file as a
  schedule hint only — closes the "compromised app never requests an
  update / replays any signed manifest" gap of a passive trust root).
  Acceptable for v1 because D23 gives one rotation of headroom on the host's
  frozen key set and D25 arms the downgrade guard at install.
- **Root reads a file written by uid 185.** Mitigated by regex-validating
  every extracted value, acting only on the OpenSSL-verified manifest, and
  never passing anything to a shell without validation. This is the single
  most security-relevant boundary of the feature and the second item for the
  architect review.
- **Sleeping 0–600 s on the scheduler thread.** Core `quarkus-scheduler`
  runs jobs on a worker pool; one blocked thread per hour is negligible and
  `SKIP` prevents pile-up. Rejected: an `every=` sub-tick (constitution
  forbids relative intervals) and a randomised cron (not re-randomised).
- **Two concurrent writers of the row** (scheduler check vs. manual check)
  are serialized by the lock; a concurrent settings write and a check touch
  different columns and the last commit wins harmlessly.
- **`docker compose` invocations from a root script** depend on the compose
  project directory holding `.env` and `compose.yaml`; `install.sh` checks
  `GXP_COMPOSE_DIR/compose.yaml` exists and the project name is `gxp`.
- **The pre-update dump is not the manager's backup** — it is the updater's
  belt, pruned to five; the README's `pg_dump` + `gxp-tls` story stays the
  backup.
- **A Postgres `pg_restore --clean`** during scenario B drops and recreates
  every object; device tokens and the audit trail return to the pre-update
  state — the rehearsal asserts the marker, and the README says an update
  window is a few minutes in which a manager edit could be lost on a
  rollback-with-restore. Acceptable at 04:00.
- **Clock.** The window check, `publishedAt` and the audit timestamps assume
  NTP — repeated in the README's Updates section (the TOTP note already
  demands it).

## Open decisions

None — no `[NEEDS CLARIFICATION]` markers. No new dependency is required
in `pom.xml`, on the host, or in the image.

## Decisions taken under delegated authority (owner via lead, 2026-08-26)

| # | Decision | Chosen | Rejected alternative |
|---|---|---|---|
| D1 | Readiness endpoint | `/api/system/ready` (exists; answers only after Flyway) + the `flyway_schema_history` row | `/q/health/ready` — needs `quarkus-smallrye-health`, a new dependency |
| D2 | Updater form | root bash script + `systemd` timer, `flock`, OpenSSL verification | supervisor container with the Docker socket (root-equivalent anyway, one more image to patch, cannot replace itself); Watchtower-style tools (dependency, no signature model) |
| D3 | App ↔ updater transport | shared named volume, two files, fixed line layout, no jq | Docker socket in the app (never); a Unix socket/HTTP listener on the host (inbound, against §6); polling `docker exec` from the host (fails while the app is down) |
| D4 | Signature encoding | detached raw Ed25519, base64 in `.sig`, over the exact file bytes | JWS/JSON canonicalisation (a canonicaliser on both sides = two implementations to keep equal, and a dependency in bash) |
| D5 | Artifact pin | ~~`sha256` = the image ID compared after pull via `docker image inspect`~~ **Amended 2026-08-26 (lead, review R3-6):** `sha256` is the **registry manifest digest**; the updater pulls `artifactRef@sha256:<digest>` so Docker itself enforces it, writes that digest-pinned ref into `.env`, and additionally records the local image ID (`{{.Id}}`) in its own state — never compared, because its meaning depends on the image store (config digest under graphdriver, manifest digest under containerd). Rehearsals use local tags without a registry digest and therefore run a clearly marked rehearsal-only path (`GXP_REHEARSAL_LOCAL_IMAGE=1`, refused under systemd) that compares the local ID | local image ID (its meaning differs between graphdriver and containerd stores — a manifest signed on one kind of box is `artifact-mismatch` on the other) |
| D6 | Version comparison | hand-written `SemanticVersion`, qualifier sorts below release; the bash side implements the **same rule by hand** (compare the numeric triple; on equality the side WITH a qualifier is lower; two qualifiers compare byte-wise) — **not** `sort -V`, which orders `1.0.0 < 1.0.0-SNAPSHOT` and would leave a box on a qualified version permanently stuck (review 2026-08-26 #2) | a semver library; string compare; `sort -V` |
| D7 | Where verification lives | twice: JDK in the app (defence in depth, decides "available") and OpenSSL in the updater (trust root, decides "apply") | app-only (the updater would trust a file uid 185 wrote) |
| D8 | State persistence | one Flyway V8 row incl. the raw verified manifest and the manual request | in-memory (lost on restart; the desired file could not be rebuilt after a reboot) |
| D9 | Transactions | `UpdateStateStore` collaborator holds `@Transactional`; HTTP never inside a tx; `checkNow` audited via `EXPLICIT_ENTRIES` | `@Transactional` on `checkNow` (15 s HTTP inside a DB tx) |
| D10 | Jitter | in the Control, `0–600 s`, `%test = 0` | none (fleet thundering herd); a second cron |
| D11 | Reconcile cadence | every 15 min, `SKIP` | on write only (a box off at 04:00 would carry a stale `applyNotBefore` until the next hourly check) |
| D12 | Apply window | 2 h (`applyWindowSeconds = 7200`), timer every 5 min | open-ended (an update could land at 11:00 after a long outage — a guest surprise) |
| D13 | Hold rule | one attempt per request; a failed version is re-tried only by a manual apply | automatic retry next night (could restart the kiosk every night into the same failure) |
| D14 | Health check | ready 200 + newest Flyway row `success` + not restarting, ≤ 180 s, then 30 s hold + re-check | ready only (no reason code); a longer soak (longer kiosk staleness) |
| D15 | Rollback ladder | image → image + restore → give up with the old image running | restore first (destroys manager edits unnecessarily when the old image is compatible) |
| D15a | Updater memory (amendment 2026-08-26, lead, review R3-3) | The updater's **only** memory for the replay guard (`lastRequestedAt`) and the downgrade refusal (`currentVersion`) is a **root-owned shadow state** `GXP_STATE_FILE` (`/var/lib/gxp-updater/state.json`, 0600, one extra line `currentImageId`). The exchange `state.json` is a **mirror** written after it, for the app to read; the app may delete or replace the mirror without any effect on the updater. `lastRequestedAt` is carried forward on every outcome incl. `malformed` (an unusable request never erases the guard). *Amended 2026-08-26 (A-3, T-20b):* root **never opens a path inside the exchange directory for writing** — `place_file DIR NAME MODE STAGE` builds the file under STAGE (the volume's root-owned parent, same device — checked, else `fail_config`) via `mktemp` + `chmod`, then `mv -fT` = `rename(2)` (never follows the entry, refuses a directory); the shadow (root 0700 dir) stages in place. The shadow carries `currentImageId`, `trustedKeys`, `trustedKeysVersion` (17 lines); the mirror keeps 14. The desired file is snapshotted once (symlink / non-regular / > 64 KiB → `malformed`; entry and descriptor compared by device:inode, A-11) | the exchange file as the memory (deviation 24: under the app's control, so the host-side half of REQ-727 collapsed to the app's own refusal) |
| D16 | Manifest server test on the dev box | none; JDK `HttpServer` in JVM tests + the shared fixture for the file contract | `%prod` plain-HTTP switch (a footgun on real boxes); trusting a self-signed manifest server (truststore config = more surface) |
| D17 | Production key custody | generated on the owner's machine by the tool, private key in `$HOME/.gxp/keys/` (0700), public key committed; fingerprint in the handoff | dev-generated placeholder key in the image (a release would ship unverifiable) |
| D18 | Backups | `pg_dump --format=custom` into `/var/lib/gxp/backups`, keep 5 | none (§12 item 9 requires it); unbounded retention |
| D19 | Updater privilege model | root via systemd, `ProtectSystem=full`, `PrivateTmp`; input validated by regex | a dedicated user in the `docker` group (docker group is root-equivalent — no gain, more moving parts) |
| D20 | Audit action names | `update`/`update-settings`/`1`, `update-apply`/`update`/`<version>`, `update-check`/`update`/null — the PRD's spelling | a single `update-*` family for all three |
| D21 | `UpdaterStateDto.outcome` type | free string (forward tolerant) | enum (a newer updater code would break the status) |
| D22 | Dependencies | **none** (JDK `Signature`, `HttpClient`, `HttpServer` in tests; host `openssl`, `docker`, `systemd`; `registry:2` is a dev-box rehearsal image only) | any HTTP/crypto/semver library; `quarkus-smallrye-health`; `jq` on the host |
| D23 | Trust set + rotation (2026-08-26, owner decision D-1 via lead; architect review A-1) | see §4 — two keys in one resource, any verifies, optional signed `trustedKeys` line rotates, version-monotonic, persisted on both sides | one key + `install.sh --replace-key` (a lost key = a site visit per box); a separate rotation document |
| D24 | Restore rung (2026-08-26, lead per A-6) | rung 2 = `pg_restore --list` readable → `DROP SCHEMA public CASCADE; CREATE SCHEMA public; ALTER … OWNER TO gxp` → `pg_restore` without `--clean`; an unlistable dump → no wipe, `--clean --if-exists` as before | `--clean --if-exists` only (objects a succeeded migration created survive; the re-attempt of a fixed release fails "already exists") |
| D25 | Install-time seed (2026-08-26, lead per A-9) | `install.sh --current-version X` writes a shadow (`outcome none`, `currentVersion X`, never overwrites) so the host-side downgrade refusal is armed before the first apply | armed only from the first applied update (deviation 32) |

## Decisions confirmed by the user
| Decision | Chosen | Date |
|----------|--------|------|
| Update cadence and consent | automatic by default, manager-chosen time (default 04:00 local), disable/change/apply-now in the API, disabled still reports "available"; manager screen out of scope | 2026-08-25 (§12 item 8) |
| Migration failure policy | refuse-and-roll-back with a `pg_dump` first | 2026-08-25 (§12 item 9) |
| Constitution v1.8.0 | outbound signed channel, updater beside the app, key in the image | 2026-08-25 (§12 item 1) |
| Part C before Part B | yes — no instance identity assumed | 2026-08-26 (lead, owner-delegated) |
| Everything else in this feature | delegated (table above) | 2026-08-26 |
