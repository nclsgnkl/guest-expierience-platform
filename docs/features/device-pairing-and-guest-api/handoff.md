# Handoff: device-pairing-and-guest-api

**Last session:** 2026-08-19 (reviewer — **06_review complete: PASS ✅**,
🔴 0 · 🟠 0 · 🟡 2 (1 fixed in-review) · 🟢 6 — see
[06-review.md](06-review.md); nothing committed)

## Review (2026-08-19, reviewer): PASS ✅

- **Independently re-verified:** full suite **164/164 green** on a fresh
  throwaway Postgres 17.11 (own container, port 55435 — removed with
  volumes afterwards); TS client regeneration **byte-identical** (sha256
  before/after an in-review `generate-sources`); `pom.xml` zero diff +
  `dependency:tree` only baseline groupIds; log sweep 0 hits; contract
  security/params parsed (bearerAuth correct everywhere, **no scope
  parameter on any guest op**); V4 = design DDL verbatim; BCE visibility
  sweep clean; `backend/CLAUDE.md` amendment word-for-word the approved
  text; ceiling parity genuinely pinned (SQL-seeded contradictory state
  through `MenuControl.enabled`); missing `GXP-Pairing-Secret` header →
  **400** (probe test, then deleted — no 500/NPE path from HTTP).
  The compose TLS smoke was **taken from dev's same-day evidence** (script/
  config/compose match its claims line by line; not re-run).
- **All 8 recorded deviations ratified** and folded into
  [02-design.md → Implementation deviations](02-design.md) (spec stays the
  source of truth).
- **In-review fix:** `backend/README.md` manager-auth curl examples
  `http://localhost:8080` → `https://<host>` (plain-HTTP URLs for the
  password-carrying flow contradicted the HTTPS-only posture).
- **Findings (non-blocking):** 🟡 keytool `-storepass` as argv in
  `entrypoint-jvm.sh` (near-zero practical exposure; fix = `-storepass:file`
  at the next TLS touch **plus smoke re-run** — deliberately not changed
  in-review to keep the smoke evidence valid); 🟢 settings-labels
  skip-vs-fallback inconsistency (unreachable while `LanguageCode` = de|en),
  response-filter ordering unpinned (latent, carried from foundation),
  undocumented validation-400 on the poll op, IPv4-only SAN detection,
  no index on `device.room_id`, theoretical preference-write/stay-delete
  race. Details + REQ coverage table in [06-review.md](06-review.md).

## Next action

1. **⏸ PARKED (user decision, do not act until confirmed):** dev's proposed
   `backend/CLAUDE.md` Tests-section entry on `de.gxp.testsupport`/
   `DeviceFixtures`. Relay to the user; the exact text is written only
   after approval.
2. **Ready for the single commit** (user/lead commits): contract + backend
   + `packages/api-client/` + docs together, on `feature/backend`.
3. Tracked for the next TLS touch: the 🟡 `-storepass:file` hardening +
   compose-smoke re-run.

---

## Previous: 05_implement (2026-08-19, dev) — complete, superseded by the review above

T-01…T-27 all done, suite 164/164 green on a fresh Postgres 17, compose TLS
smoke fully green, nothing committed.

## Implementation (2026-08-19, dev): what was built

### Per package / area

- **Contract (`api/openapi.yaml`, T-01/T-02):** tags `pairing`,
  `manager-devices`, `guest`; the design's 11 operations + 19 new schemas;
  `bearerAuth` on guest + manager-device ops, none on pairing ops; NO
  room/stay/device parameter on any guest op (REQ-411 checkable on the
  contract). `getSystemInfo` gained `security` + 401 (REQ-432). `bearerAuth`
  description extended for device tokens. Both generators green; TS client
  regenerated **byte-identical across two runs** (sha256 over every file);
  `frontend/` untouched (`git status` clean there).
- **Schema (`V4__device_pairing.sql`, T-03):** the design DDL verbatim
  (`device`, `pairing_request`), V1–V3 untouched; `MigrationTest` extended
  (head = V4, zero seeded devices).
- **`de.gxp.device` (T-04/05/06/17):** `Device`/`PairingRequest` entities +
  repositories (find-by-token-hash UNIQUE, claim-once `UPDATE … WHERE
  claimed_at IS NULL`, opportunistic expired-delete), `PairingCodes` (8-char
  alphabet `23456789ABCDEFGHJKLMNPQRSTUVWXYZ` — exactly 32 chars, no 0/O/1/I;
  32-byte SecureRandom secrets/tokens, sha-256-hex at rest),
  `PairingRateLimiter` (5/60 s global, 429 + `Retry-After`, LoginRateLimiter
  pattern, package-private `reset()` test hook). `DeviceControl` is the one
  public type: create (rate-limited, cleanup-first, raw secret returned
  once), poll (pending → **thrown 202**; confirmed → mint token, insert
  hash-only Device, atomic claim, raw token exactly once; all four misses →
  ONE identical `404 pairing-not-found`, constant-time secret compare),
  confirm (@RolesAllowed admin, default name `Kiosk <code>`, 404/409/400),
  list/rename/revoke (revoke idempotent 204; DTOs carry no token material),
  `verifyToken(hash)` (@PermitAll auth-internal, ≥60 s throttled
  `last_seen_at` touch), `caPem()` (reads `gxp.tls.ca-pem-path`, 404
  `ca-not-available` without material). Boundaries `PairingResource`
  (@ResponseStatus(201) on create) + `DeviceAdminResource`, every method one
  delegating line.
- **Room-delete guard (T-07):** `RoomControl.delete` → `409
  room-has-devices` naming active devices via a package-private native query
  in `RoomRepository` (the deviation-5 shape, same as `staysOf`); revoked
  rows deleted in the same transaction before the room row.
- **Auth (T-08):** `TokenIdentityProvider` resolves manager store first,
  then `DeviceControl.verifyToken(sha256(t))` → identity role `device` +
  `gxp.device.id`/`gxp.device.room-id` attributes (public constants on
  `AuthControl`). The device branch runs inside
  `AuthenticationRequestContext.runBlocking(...)` — authentication can start
  on the event loop and the lookup is blocking JDBC.
  `BearerAuthMechanism` untouched; `TokenExpiryResponseFilter` needed **no
  code change** (see findings).
- **Guest read model (T-09…T-14):** `@PermitAll` guest-read methods on the
  owning Controls (TemplateControl precedent): `PropertyControl.guestView()`,
  `RoomControl.guestView(roomId)`, `MenuControl.guestResolvedMenu(roomId,
  languages)` (resolved-ENABLED only through the existing ceiling
  `enabled()` — never re-implemented; per-field icon/sort room → property →
  catalogue) + `guestResolvedCards(roomId)` (wholesale rule, raw templates),
  `StayControl.activeStayOf(roomId)` (the one occupancy derivation) +
  `writePreference(roomId, lang, app)` (vacant → `409 room-vacant`),
  `AssetControl.guestBinary(...)` (same thrown-Response path, guest cache
  pin `private, max-age=2592000`). `GuestControl` (@RolesAllowed("device")
  everywhere, room exclusively from the identity attribute): session
  (occupancy, data-sparing vacancy, settings chrome constants, resolved
  defaults, raw preference block, standby templates resolved), homeScreen
  (vacant 409, resolved-disabled home 404, artwork room → property, 0–4
  cards resolved per language in the property timezone, defensive skip of a
  stranded card), asset (reachability set {logo, property artwork, own-room
  artwork} else `404 asset-not-found` — identical for existing-but-
  unreachable and nonexistent), writePreferences (whole record, `400
  value-not-offered`). `GuestEtagFilter`: `ContainerResponseFilter`, sha-256
  over the CDI-ObjectMapper-serialized entity → strong ETag,
  `If-None-Match` → 304 empty, `Cache-Control: no-cache`.
- **TLS & system surface (T-15…T-19):**
  `backend/app/src/main/docker/entrypoint-jvm.sh` (keytool first-start init:
  CA EC P-256 self-signed CA:true 20 y; server EC P-256 CSR signed with
  SAN = `GXP_TLS_HOST` (ip: for IPv4 literals, dns: otherwise) 10 y; PKCS12
  + generated passphrase file, umask 077, onto `/tls`; reuse on restart;
  host change re-issues the server cert under the unchanged CA; FATAL exit
  without `GXP_TLS_HOST`; exports `GXP_TLS_KEYSTORE_PATH`/`…_PASSWORD`/
  `GXP_TLS_CA_PEM_PATH` as env — never argv — then execs run-java).
  `Dockerfile.jvm`: `/tls` pre-created chown 185 (named-volume ownership
  seeding), entrypoint COPY, EXPOSE 8443; `.dockerignore` whitelists the
  script. `application.properties` `%prod` block (commented, lockdown-
  precedent style): `insecure-requests=disabled`,
  `quarkus.tls.key-store.p12.path/password=${GXP_TLS_…}` (missing env →
  refuse-to-start via config expansion), `gxp.tls.ca-pem-path`.
  `compose.yaml` production-shaped: `gxp-tls` volume, 443→8443, required
  `GXP_TLS_HOST` interpolation, app healthcheck `curl -fsk` against
  localhost HTTPS, dev/preview warning REMOVED, backup note.
  `/api/system/info` → `@RolesAllowed({"admin","device"})`;
  `SystemResourceTest` + `FirstRunSeedTest` updated (anon → 401).
- **Tests (T-20…T-23, 41 new, suite 123 → 164):**
  `device/PairingFlowTest` (8: lifecycle, code shape, 202, claim-once,
  racing polls via executor → exactly one 200, indistinguishable-404 matrix
  with body EQUALITY, 429 + Retry-After, opportunistic cleanup, 64-hex
  hash-only storage, token-free DTOs, rename/revoke idempotency),
  `device/DeviceLastSeenTest` (2: ≤1 write for consecutive verifications;
  unknown/revoked → null), `device/PairingCaTest` (1: %test 404 branch),
  `auth/DeviceAuthTest` (6: valid/revoked-next-request/unknown/malformed,
  role separation both ways, NO `GXP-Token-Expires` on device responses,
  device token on system/info 200), `guest/GuestSessionTest` (3: seeded
  frontend shape incl. settings chrome + exactly-offered-language keys,
  occupancy flip + vacancy data-sparing, ceiling parity), `guest/
  GuestHomeScreenTest` (5: vacant 409, per-language |time resolution in
  Europe/Berlin with de-DE/en-GB locales + no-placeholder sweep, 0 cards,
  resolved-disabled home 404, unknown screen key 404),
  `guest/RoomScopingTest` (2: two-devices/two-rooms matrix incl. room-scoped
  config; revoked device gets nothing anywhere),
  `room/RoomDeviceGuardTest` (1), `guest/GuestEtagTest` (1: strong ETag,
  304 empty, manager edit changes it), `guest/GuestAssetTest` (4: ETag/304,
  unreachable≡unknown 404 body-equal, guest cache pin, artwork fallback,
  manager surface closed to devices), `guest/GuestPreferenceTest` (3),
  `testsupport/DeviceFixtures` (shared DB-seeding helper),
  audit test extended (must SEE DeviceControl/GuestControl),
  `ManagerSurfaceDeniedTest` +3 paths, `MigrationTest` +1.
- **Docs (T-25/T-27):** `backend/README.md` (HTTPS-only first run,
  `GXP_TLS_HOST`, TLS lifecycle table, pairing workflow incl. the CA install
  as a documented human step, device admin/revocation, backup = `pg_dump` +
  `gxp-tls` with the honest loss consequence); `docs/system-context.md`
  (device/guest/auth/TLS/V4 component rows, Auth section: device tokens
  live + compose production-shaped, hand-off notes for
  `guest-frontend-integration`); `docs/features/index.md` row → 05 done.
  **T-26 verified:** `backend/CLAUDE.md`'s two-Boundary clause matches the
  design's quoted approved text word for word.

### Verify-at-implementation findings (the three pinned items)

1. **Quarkus 3.33 TLS property names:** verified against the 3.33.3.1 jars
   (javap on `quarkus-tls-registry` + `quarkus-vertx-http`):
   `quarkus.http.insecure-requests` exists (`VertxHttpConfig.insecureRequests`,
   enum `ENABLED/REDIRECT/DISABLED`); the TLS **registry** default bucket is
   `quarkus.tls.key-store.p12.path` / `.password`
   (`TlsConfig` → `TlsBucketConfig` → `KeyStoreConfig` → `P12KeyStoreConfig`),
   and the HTTP server picks the default bucket up for HTTPS — proven live in
   the compose smoke. The older `quarkus.http.ssl.certificate.*` set still
   exists but the registry was used (current mechanism; do not mix).
2. **Guest-ETag interception point:** a `WriterInterceptor` sees the truly
   serialized stream but cannot rewrite an already-started response into a
   304; a `ContainerResponseFilter` can. Chosen: `ContainerResponseFilter`
   serializing the entity through the **CDI `ObjectMapper`** — the same
   mapper Quarkus REST Jackson writes the response with, so the hashed bytes
   are the wire bytes. Costs one extra serialization per hit (the design's
   accepted recompute-over-cache trade-off). Asset/304/error responses pass
   through untouched (status + media-type guards).
3. **Identity attributes in response filters:** they DO reach
   `ContainerResponseFilter`s (as already exploited for `GXP-Token-Expires`).
   The device skip needed **no filter change**: device identities simply
   carry no expiry attribute, the existing null-check skips them — asserted
   by `DeviceAuthTest` (header absent on device responses).

### Deviations from the TODO text (with rationale — none touch the contract)

- **T-05's `activeDeviceCountAndNames(roomId)` was NOT added to
  `DeviceControl`.** The design itself resolves the room-delete guard the
  other way (package-private native query on the `device` table in
  `RoomRepository`, deviation-5 precedent, exactly what T-07 says) to keep
  the Control graph acyclic; adding an additionally unused public Control
  method would have been dead API surface. REQ-408 is fully covered.
- **Room existence at confirm** uses `RoomControl.lockForStayWrite(roomId)`:
  it answers the contract's **400** `unknown-room` (requireExists answers
  404) and holds the room row until commit, so a concurrent room delete
  cannot slip between check and confirm. Poll/list read the room **code**
  via a package-private native query in `DeviceRepository` — the claim path
  is unauthenticated and every `RoomControl` read is admin-annotated.
- **`resolveLocalized` falls back to the main language** when a translation
  row is missing: REQ-426 demands maps over EXACTLY the offered languages,
  but the V2 seed ships only a `de` property-translation row — honest
  untranslated text beats a hole in the kiosk. Menu labels resolve
  per-language room → property → catalogue (leaf-level fallback, the
  catalogue carries all languages), same reason.
- **Host-change re-issue regenerates the server keypair** (new key + new
  cert under the unchanged CA) rather than re-signing the old key — simpler
  and strictly safer; the decision's trust property (kiosks keep trusting,
  no re-install/re-pair) holds and was proven in the smoke.
- **Tests outside `de.gxp.device` seed devices by direct DB insert**
  (`testsupport/DeviceFixtures`, hash-only like a real claim): pairing over
  HTTP from every suite would burn the global 5/60 s create limit across
  unrelated tests. The HTTP lifecycle is proven once, in `PairingFlowTest`
  (which resets the package-private limiter around each test).
- **The last-seen throttle test lives in `de.gxp.device`**
  (`DeviceLastSeenTest`), not `DeviceAuthTest` — the throttle reset hook is
  package-private by design.
- **`GuestSessionTest`'s ceiling test seeds the contradictory state via
  SQL** (room enable row + property disable): the manager API itself refuses
  to create it in either order (the ceiling guards `menu-item-enabled-by-
  rooms` / `item-disabled-at-property`), which is correct — the test proves
  guest resolution still ceilings the item out even if the state exists.
- **`If-None-Match` is contract-modeled only on the asset op** (where the
  Control consumes it); for the JSON ops the filter reads the standard
  header and the contract documents the 304 responses — no dead interface
  parameter on every guest method.

### Exact build/test results

- `./mvnw test` (fresh `postgres:17.11` container, port 55434,
  `-Dquarkus.datasource.jdbc.acquisition-timeout=60`):
  **Tests run: 164, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS**
  (baseline 123 → 164; also green on a reused DB across three consecutive
  runs, i.e. test cleanup is sound). Load average at run time ~0.3–0.5, no
  environmental flakes, no retries needed.
- TS client: regenerated twice, sha256 over every file **byte-identical**;
  `git status frontend/` = 0 entries.
- `dependency:tree`: **zero** non-baseline groupIds; `pom.xml` untouched.
- Source log sweep: **0** logger/System.out/System.err hits in
  `src/main/java`.
- **Compose smoke (T-24, executed by dev — Docker via sudo), all green:**
  image built from the updated `Dockerfile.jvm`; first boot generated CA
  (subject `CN=GXP Instance CA`, 2026→2046 ≈ 20 y) + server cert; app
  healthcheck (curl inside the container) healthy; `curl --cacert <fetched
  /api/pairing/ca>` handshake OK incl. hostname verification; plain HTTP
  dead (no listener — curl exit 000); `/q/openapi` 404; `/api/system/info`
  anon 401 / admin 200 / device 200; full pairing→confirm→claim→session E2E
  over HTTPS (poll pending 202, claim 200 with `Kiosk 1`, second poll 404);
  restart → CA **and** server fingerprints identical + "reusing existing TLS
  material" logged; `GXP_TLS_HOST=127.0.0.1` restart → "re-issuing" logged,
  NEW server fingerprint, SAME CA fingerprint, SAN `IP Address:127.0.0.1`,
  handshake OK against the ORIGINAL fetched CA; compose refuses to
  interpolate without `GXP_TLS_HOST`; bare `docker run` without it → FATAL
  + exit; container log clean of key/passphrase/token material.
  *Environmental note:* the smoke published host port **8543** instead of
  443 via a throwaway compose override file (binding 443 needs it free on
  this box); the shipped `compose.yaml` publishes 443. Stack and BOTH
  volumes removed afterwards; throwaway test Postgres removed with volumes.

### Not executed / left open

- Nothing from the TODO is unexecuted. Not covered by automation (inherent):
  the `%prod` TLS half is proven by config review + the smoke above, not by
  `@QuarkusTest` (the `/q/openapi` limitation, same compensation).
- Port-443 binding on a real box and a real kiosk CA-install were not
  exercised (no kiosk hardware here) — the README documents both.

## Next: `06_review` (reviewer)

Everything uncommitted on `feature/backend` (per instruction). Suggested
review focus: the guest-scoping tests (REQ-425), the indistinguishable-404
matrix, the entrypoint script (shell, key handling), the `%prod` config
block, and the deviations listed above.

---

## Previous: round 2 (2026-08-19, planner) — all five decisions approved, gate PASS

The user approved **all five recommendations verbatim** ("All five
recommended go ahead", relayed 2026-08-19 with overnight autonomy granted).
Folded in and re-gated:

1. **TLS material**: first-start `keytool` init in the app container
   entrypoint → CA + CA-signed server cert onto named volume `gxp-tls`;
   no new dependency, no second image. (PRD REQ-427; design "Decided
   mechanics"; T-15.)
2. **Certificate identity**: required `GXP_TLS_HOST` (refuse-to-start in
   `%prod`, `GXP_ADMIN_PASSWORD` posture); SAN = that host; EC P-256;
   CA 20 y / server cert 10 y; host change re-issues the server cert under
   the unchanged CA. (REQ-430/433; T-15/T-16.)
3. **`%prod` HTTPS only**: `quarkus.http.insecure-requests=disabled`,
   compose 443→8443, no redirect; dev/test stay HTTP. (REQ-428/431;
   T-16/T-18.)
4. **`/api/system/info`** → `@RolesAllowed({"admin","device"})`; health/
   ready stay public. (REQ-432; T-19.)
5. **Two Boundaries over one Control in `de.gxp.device`** (`pairing` +
   `manager-devices`): the exact `backend/CLAUDE.md` amendment was approved
   verbatim and **is written** (T-26 done) — diff-verified at the gate:
   the file's clause matches the design's quoted text word for word.

Artifacts after the fold-in:

- **01-prd.md** — decision-complete (REQ-401…REQ-435, no markers).
- **02-design.md** — decision-complete; the former "Open decisions" section
  is now "**Resolved decisions — approved verbatim by the user,
  2026-08-19**" (the binding record, incl. the amendment text and the
  rejected TLS alternatives).
- **03-todo.md** — T-01…T-27 all unblocked; **T-26 already done** (planner
  wrote the approved amendment; `dev` re-verifies the text at
  implementation).
- **04-analyze.md** — **PASS** (round 2): full coverage REQ↔task, 0 orphans,
  fidelity + constitution v1.5.1 checks green, no unresolved markers
  (grep-verified — the only remaining "⏸" strings are the standing
  "stop on unexpected dependency" process rule).
- `backend/CLAUDE.md` — amended (approved clause).
- `docs/features/index.md` — row updated to 04_analyze PASS, ready for dev.
- Nothing committed (per instruction); no source code written;
  `frontend/` untouched.

### (superseded) Next: `05_implement` (dev) — done, see the implementation entry above

Suggested order = TODO phases: contract/V4 → `de.gxp.device` → auth
extension → guest read model → TLS/system → tests → smoke/docs. Environment
notes carried from domain-config: the `@QuarkusTest` suite needs
Docker/Postgres (lead runs it if dev's sandbox has none); on this box use a
quiet window + `-Dquarkus.datasource.jdbc.acquisition-timeout=60`. Compose
smoke (T-24) needs Docker — lead runs it.

**Verify-at-implementation items** (pinned in the design, record findings
here): exact Quarkus 3.33 TLS property names (`quarkus.http.ssl.*` vs
`quarkus.tls.*` registry) and `insecure-requests` semantics; the guest-ETag
interception point (`ContainerResponseFilter` vs `WriterInterceptor`);
whether identity attributes reach the response filter for the
`GXP-Token-Expires` device-skip.

**Autonomy note for the re-run** (coordinator, user asleep): if anything new
surfaces, prefer resolutions inside the already-approved decisions and
record them here; only a genuinely new gate item (new dependency,
datastore/infra choice, new pattern) comes back as ⏸.

## Carried-forward notes for later features (already recorded in the docs)

- `guest-frontend-integration`: `ngVersion` ceiling check on first client
  consumption; kiosk-side token/CA handling; images fetched with the auth
  header (no bare `<img src>`); standby *screen* needs a Figma design
  (spec §11 item 1).
- First sensitive guest screen (WiFi/door codes): requires the spec §11
  item 9 field-level sensitivity list + per-field kiosk-cache rules first.
- `manager-frontend`: pairing-confirm UI, device list/revoke UI, §6 preview
  endpoint, neutral-seed "please configure me" surfacing.

---

## Previous: round 1 (2026-08-19, planner) — superseded

Phases 01–04 drafted from scratch (fresh REQ-4xx block); gate ran **GAPS**
with the five-decision batch as the sole gap class; registry row added.
All five decisions were recommended exactly as later approved.
