# Review: Device Pairing & Guest API
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Result:** **PASS ✅** (2026-08-19, reviewer) — 🔴 0 · 🟠 0 · 🟡 2 (1 fixed in-review) · 🟢 6, against constitution **v1.5.1** and `backend/CLAUDE.md` as amended 2026-08-19. All REQ-401…REQ-435 implemented, 0 orphans. One approval-gate item **parked** (user decision pending, see "Open items").

> Visual check (code-review skill step 0): **skipped — backend-only change**,
> no rendered surface; `frontend/` is verifiably untouched (`git status` 0
> entries).

## What was verified independently (this review) vs. taken from dev's evidence

**Independently re-verified:**

- **Full suite:** `./mvnw test` against a fresh throwaway `postgres:17.11`
  (port 55435, `-Dquarkus.datasource.jdbc.acquisition-timeout=60`) —
  **164 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS** (surefire
  totals summed independently; matches dev's 164/164, baseline 123 + 41 new).
  Container and volumes removed afterwards.
- **Client regeneration determinism:** sha256 over every file in
  `packages/api-client/` before/after an in-review `./mvnw generate-sources`
  — **byte-identical**; `frontend/` untouched.
- **Dependency posture:** `backend/app/pom.xml` has a **zero diff** vs HEAD;
  `dependency:tree` re-run shows only baseline/transitive groupIds — nothing
  outside the allow-list was added (the decided `keytool` path is JDK-only).
- **Log sweep:** 0 logger/`System.out`/`System.err` hits in
  `src/main/java`; no config raising SQL/access logging; no token, secret,
  hash, key or password material in any thrown message (all `ApiErrors`
  call sites read).
- **Contract security (parsed `api/openapi.yaml`):** `bearerAuth` on all 4
  guest + all 4 manager-device ops and on `getSystemInfo` (+401 response);
  the 3 pairing ops carry none (by design); **no room/stay/device/property
  parameter on any guest operation** (REQ-411 checked on the contract);
  tags `pairing`/`manager-devices`/`guest` = one generated interface per
  Boundary; 11 new operations exactly as the design table.
- **V4 DDL vs design:** `V4__device_pairing.sql` matches the design's data
  model verbatim (RESTRICT/CASCADE FKs, UNIQUE `token_hash`/`code`, no
  seeds); `V1`–`V3` untouched; `MigrationTest` pins head = V4 + zero devices.
- **BCE strictness:** in `de.gxp.device`/`de.gxp.guest` the only public
  types are the Controls and the Boundary adapters (matching every existing
  package's convention); entities/repositories/mappers/`PairingCodes`/
  `PairingRateLimiter`/`GuestEtagFilter` package-private; every Boundary
  method is one delegating line; `@Transactional` and exactly one security
  annotation per public Control method (audit test now must-sees
  `DeviceControl`/`GuestControl`; `FailClosedTest` untouched and green).
- **`backend/CLAUDE.md` amendment:** the working-tree diff contains exactly
  the user-approved two-Boundary clause, word-for-word the design's quoted
  text — nothing else changed in that file.
- **Ceiling parity (REQ-417):** `MenuControl.guestResolvedMenu` filters via
  the *existing* `enabled(roomId, key)` — the ceiling implementation itself,
  not a re-implementation (read in full); `GuestSessionTest`'s SQL-seeded
  contradictory state (room enable + property disable) genuinely pins it
  end-to-end through `/api/guest/session`.
- **Missing poll-secret header (probe, then deleted):** a poll without
  `GXP-Pairing-Secret` answers **400** via the generated `@NotNull`
  (hibernate-validator), not a 500 — the NPE path in
  `DeviceControl.hashMatches` is unreachable from HTTP.
- All new/changed source and test files read in full (pairing flow,
  auth path, guest read model, ETag filter, TLS entrypoint/config/compose,
  all 41 new tests).

**Taken from dev's same-day evidence (not re-run):** the compose TLS smoke
(T-24) — first-boot CA+cert generation onto `gxp-tls`, restart reuse with
identical fingerprints, `curl --cacert` handshake via `GET /api/pairing/ca`,
plain HTTP dead, pairing→confirm→claim→session E2E over HTTPS, host-change
re-issue under the unchanged CA, fail-fast without `GXP_TLS_HOST`,
`/q/openapi` 404 in `%prod`, log cleanliness. Accepted because the shipped
script/config/compose match the smoke's claims line by line and nothing
found in review contradicts them; re-running was judged not worth the cost.

## Security review (the feature's core concern — kiosks on an untrusted LAN)

- **Pairing hijack/replay/enumeration:** the two-credential split holds —
  the display code (8 chars / 32-char unambiguous alphabet ≈ 40 bits,
  10-min TTL, global 5/60 s create limit) never yields a token without the
  256-bit poll secret (constant-time hash compare,
  `MessageDigest.isEqual`); all four misses (unknown/expired/claimed/wrong
  secret) are ONE `404 pairing-not-found` with **body equality asserted**;
  claim-once is an atomic `UPDATE … WHERE claimed_at IS NULL` with a racing-
  polls test proving exactly one 200; the raw token exists once in the
  claiming response, stored SHA-256-hex only (asserted at DB level); a
  pending poll with the wrong secret is 404, not 202 (checked in code:
  secret verified before the pending branch).
- **Device auth path:** one bearer path preserved — manager store first,
  then `DeviceControl.verifyToken(sha256)` under
  `AuthenticationRequestContext.runBlocking` (blocking JDBC off the event
  loop); revocation reads the row per request → next-request effect
  (tested); device identities carry no expiry attribute → no
  `GXP-Token-Expires` leak (tested); role separation both ways (admin ↛
  guest ops, device ↛ manager ops — `ManagerSurfaceDeniedTest` +3 paths,
  `DeviceAuthTest`); fail-closed audit extended and must-see-guarded.
- **Guest exposure:** every `@PermitAll` guest-read on the owning Controls
  is a read-only projection with no HTTP mapping; the composed session/home
  payloads expose only the device's own room (code, display name), the
  property's public branding/texts, the resolved menu, and — occupied only —
  the stay display name and raw preference override. No other room's
  anything is reachable (two-rooms matrix + no scope parameter on the
  contract); vacancy is data-sparing (no name, no override — tested); asset
  reachability is the 3-id set {logo, property artwork, own-room artwork},
  with existing-but-unreachable ≡ nonexistent (body-equal 404, tested) — no
  asset enumeration. Nothing served is guest-sensitive yet (recorded: the
  §11-item-9 list is required before the first sensitive screen).
- **TLS:** parameters match the approved decision exactly (EC P-256
  `-groupname secp256r1`; CA 7300 d ≈ 20 y self-signed `bc:c=ca:true`;
  server 3650 d ≈ 10 y, `SAN = GXP_TLS_HOST` with `ip:`/`dns:` selection,
  `eku=serverAuth`); umask 077, passphrase 0600 on the volume, CA PEM
  deliberately 644 (public material); `%prod` HTTPS-only via
  `quarkus.http.insecure-requests=disabled` + TLS-registry keystore
  properties (names verified by dev against the 3.33.3.1 jars, consistent
  with the commented block); missing env → refuse-to-start by config
  expansion; entrypoint FATALs without `GXP_TLS_HOST`. See finding #1 for
  the one hardening note.
- **`GuestEtagFilter`:** correct — path/status-200/entity/JSON-media-type
  guards keep asset (image types), 304, 202 and every error/mapper response
  untouched; hashes through the same CDI `ObjectMapper` the response is
  written with (wire-byte-stable, proven by the replay-304 test); no
  interaction with `ResponseStatusFilter` today (no `@ResponseStatus` on any
  guest op; both no-op on non-200) — see finding 🟢-2 for the latent
  ordering note.

## Findings

| # | Severity | Category | File | Finding | Fix |
|---|----------|----------|------|---------|-----|
| 1 | 🟡 | Security (hardening) | `backend/app/src/main/docker/entrypoint-jvm.sh:49-60` | The keystore passphrase is passed to each `keytool` call as `-storepass <pass>` **argv** — visible in `/proc/<pid>/cmdline` while keytool runs. Practical exposure ≈ zero (boot-time only; no co-resident process exists in the container's PID namespace during init; host root sees everything anyway), and the script's "never argv" claim refers to the Quarkus hand-off (env), which is honest. Still, keytool natively supports `-storepass:file`. | **Track** — switch to `-storepass:file "$PASS_FILE"` at the next TLS touch **and re-run the compose smoke then**; deliberately not changed in-review because it would invalidate dev's same-day smoke evidence for near-zero risk. |
| 2 | 🟡 | Docs | `backend/README.md:139-154` | The "Manager auth workflow" curl examples still said `http://localhost:8080/...` — plain-HTTP URLs for the one flow that carries the shared password, in a README whose first-run section says HTTPS-only (they pre-date this feature; REQ-431 removed the dev-only warning but left these). | **Fixed in-review**: all four now `https://<host>/...`, consistent with the device-administration section. |
| 3 | 🟢 | Correctness (latent) | `GuestControl.java:255-264` | `settingsEntry` silently *skips* an offered language missing from the hardcoded de/en `SETTINGS_LABELS`, while `resolveLocalized` *falls back* — inconsistent strategies for the same REQ-426 duty. Unreachable today (contract `LanguageCode` enum is exactly `[de, en]`), but a future enum growth would put a hole in the settings label map. | When a third language ever lands: fall back like `resolveLocalized`, or assert exhaustiveness against the enum. |
| 4 | 🟢 | Conventions (latent) | `GuestEtagFilter.java` / `de/gxp/api/ResponseStatusFilter.java` | Both `ContainerResponseFilter`s run at default priority — order unspecified. No interacting operation exists today (no `@ResponseStatus` on guest ops; both filters no-op unless status is exactly 200), continuing the foundation review's latent-interaction note. | Pin explicit `@Priority` values when a third response filter (or a `@ResponseStatus` guest op) appears. |
| 5 | 🟢 | Contract | `api/openapi.yaml` (pollPairingRequest) | A poll without the required `GXP-Pairing-Secret` header answers 400 (generated `@NotNull`, **verified empirically in-review**) — safe, but the op documents only 200/202/404. Also `DeviceControl.poll` would NPE on a null secret if the generator ever stopped emitting `@NotNull`. | Optionally document the 400 and add a one-line null guard (`pollSecret == null → pairingNotFound()`) at the next contract touch. |
| 6 | 🟢 | Ops (edge) | `entrypoint-jvm.sh:40-44` | The SAN type check recognizes IPv4 literals only; an IPv6 `GXP_TLS_HOST` would get a `dns:` SAN and fail hostname verification — visibly, at kiosk install time. README says "host name or IP". | Note only; constrain the README to hostname/IPv4 or add IPv6 handling if a deployment ever needs it. |
| 7 | 🟢 | Performance | `V4__device_pairing.sql` | `device.room_id` has no index (Postgres does not index FKs) — the delete-guard and room-code lookups scan. At "a handful of devices per building" this is noise. | Note only. |
| 8 | 🟢 | Correctness (edge) | `StayControl.writePreference` | A guest preference write racing a concurrent manager stay-delete can in theory end in a `StaleStateException` 500 (the write does not take the invariant-5 room lock). The client write is fire-and-forget with silent failure by design, so the impact is a log-line-free 500 once in a blue moon. | Note only; take `lockForStayWrite` there if it ever shows up in practice. |

Counts: 🔴 0 · 🟠 0 · 🟡 2 (1 fixed in-review) · 🟢 6

## Deviations from the TODO text — all ratified

All 8 deviation bullets in [handoff.md](handoff.md) were judged against
PRD/design and **accepted**; they are now recorded as the as-built truth in
[02-design.md → Implementation deviations](02-design.md) (added at this
review). None touches the contract; #1/#2 follow the design's own
resolution over the TODO's wording; #3 serves REQ-426's intent better than
a literal reading; #4 is strictly safer and smoke-proven; #5–#8 are test/
contract-modeling pragmatics with sound rationale. The resolved-decisions
section (user-approved verbatim 2026-08-19) was treated as binding and not
re-litigated.

## REQ coverage — all implemented, 0 orphans

| REQ | Verified by |
|-----|-------------|
| REQ-401 | Contract parsed (ops/tags/security), both generators green in suite run, client regen byte-identical (in-review), `frontend/` untouched |
| REQ-402 | V4 vs design DDL (verbatim), V1–V3 zero diff, `MigrationTest` head=V4 |
| REQ-403/404 | `DeviceControl` create/poll read in full + `PairingFlowTest` (202, claim-once, racing polls, body-equal 404 matrix) |
| REQ-405 | `confirmPairing` (404/409/400, default name) + tests |
| REQ-406 | Claim-time mint, hash-only storage asserted at DB level in test |
| REQ-407 | list/rename/revoke idempotent; `DeviceMapper` maps no token material; DTO leak assertions |
| REQ-408 | `RoomControl.delete` guard naming active devices, revoked rows deleted in-tx; `RoomDeviceGuardTest` |
| REQ-409 | Alphabet/TTL/limit pinned values in code; opportunistic cleanup tested; 429+`Retry-After` tested |
| REQ-410 | `TokenIdentityProvider` manager-first-then-device, auth pkg extended not restructured (diff-verified) |
| REQ-411 | Contract has zero scope params on guest ops (parsed); `roomId()` reads the identity attribute exclusively |
| REQ-412 | Next-request revocation + 401/challenge tested; no expiry header on device responses tested |
| REQ-413 | ≥60 s throttle in `verifyToken`; `DeviceLastSeenTest` (≤1 write) |
| REQ-414 | Audit test extended (must-see DeviceControl/GuestControl); role separation both ways tested |
| REQ-415 | Log sweep 0 hits; all error messages inspected; generic pairing/auth errors |
| REQ-416/418 | `session()` composition + `GuestSessionTest` shape assertions (chrome outside items) |
| REQ-417 | `guestResolvedMenu` → existing `enabled()`; SQL-seeded ceiling-parity test |
| REQ-419 | Vacancy data-sparing in code + tests (no name/override, standby present, 409s) |
| REQ-420 | Template resolution per language/locale/timezone tested incl. the PRD's acceptance card; no-braces sweep |
| REQ-421 | Unknown key 404 (no such path), resolved-disabled home 404, vacant 409 — all tested |
| REQ-422 | Reachability set + body-equal 404; stored-SHA ETag/304; `private, max-age=2592000` tested |
| REQ-423 | `GuestEtagFilter` + round-trip/invalidation test (strong 64-hex ETag, empty 304) |
| REQ-424 | Whole-record write, named 400/409, stay-anchored, visible in next session — tested |
| REQ-425 | `RoomScopingTest` two-rooms matrix incl. room-scoped config; revoked-device-gets-nothing |
| REQ-426 | Offered-language keySet equality asserted; fallback (ratified deviation #3) |
| REQ-427/430 | Entrypoint read line-by-line (params match the approved decision); smoke (dev) |
| REQ-428 | `%prod` config block (verified property names); smoke: plain HTTP dead (dev) |
| REQ-429 | `GET /api/pairing/ca` public, PEM from volume, `%test` 404 branch tested; README human install step |
| REQ-431 | compose diff (volume, 443→8443, required interpolation, healthcheck, warning removed, backup note) |
| REQ-432 | `@RolesAllowed({"admin","device"})` on `SystemControl.info` + anon-401/admin-200/device-200 tests |
| REQ-433 | Fail-fast paths (entrypoint FATAL, config expansion) + smoke (dev); zero-manual-DB first run unchanged |
| REQ-434 | 164/164 independently re-run; every behavior above has a named test |
| REQ-435 | README/system-context/index diffs reviewed (accurate, incl. hand-off notes) |

Orphans: none — every code/test/doc change traces to a REQ; the only
out-of-scope-looking edits (audit/denied/system test updates) are REQ-414/
REQ-432 consequences.

## Open items

1. **⏸ PARKED (approval gate, user decision pending):** dev's proposed
   `backend/CLAUDE.md` Tests-section entry about `de.gxp.testsupport` /
   `DeviceFixtures` (seed devices via DB insert outside `de.gxp.device`).
   Per the constitution, the exact text is written **only after the user
   confirms** — neither dev nor reviewer writes it before that.
2. 🟡 #1 (keytool `-storepass` → `:file`) — with the next TLS-touching
   change, followed by a compose-smoke re-run.
3. 🟢 #3–#8 — tracked, none blocking.
4. Carried forward (pre-existing): `ngVersion` ceiling check on first client
   consumption; kiosk CA-install/token handling (`guest-frontend-
   integration`); §11 item 9 sensitive-field list before the first sensitive
   screen; port-443 binding and a real kiosk CA install were not exercised
   on this box (documented).

### Top 3 recommendations

1. Relay the parked `backend/CLAUDE.md` Tests-entry proposal to the user;
   write it only on confirmation.
2. Fold 🟡 #1 into the next TLS touch (mechanical `-storepass:file` change +
   smoke re-run) rather than a dedicated round.
3. Commit as the established single unit: contract + backend + regenerated
   `packages/api-client/` + docs together, on `feature/backend`.

**Ready for commit: yes** (the user/lead commits per project convention).
