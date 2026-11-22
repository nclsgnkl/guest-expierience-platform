# TODO: Device Pairing & Guest API
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md)
**Status:** **Done — 05_implement complete 2026-08-19 (dev)**: T-01…T-27 all implemented; suite **164/164 green** on a fresh Postgres 17; compose TLS smoke (T-24) fully green; nothing committed. Deviations + verify-at-implementation findings recorded in [handoff.md](handoff.md). Ready for `06_review`

> Every design decision referenced, never re-made. Environment note carried
> from domain-config: the `@QuarkusTest` suite needs Docker/Postgres (lead
> runs it if dev's sandbox has none); suite runs on this box want a quiet
> window + `-Dquarkus.datasource.jdbc.acquisition-timeout=60`.

> **Amendment 2026-08-24 (owner decision D2, constitution v1.6.0) — read
> before any `@PermitAll` / `@RolesAllowed` below.** Every security annotation
> these steps place *on a Control* now sits on the **Boundary** instead
> (`PairingResource` `@PermitAll` class-wide, `DeviceAdminResource`
> `@RolesAllowed("admin")` class-wide, `GuestResource`
> `@RolesAllowed("device")` class-wide, `SystemResource` method-level), and the
> Controls carry none — including `DeviceControl.verifyToken` and the
> `@PermitAll` guest-read methods on the owning Controls, which only ever
> needed the annotation so an internal call would pass.
> `quarkus.security.jaxrs.deny-unannotated-endpoints=true` now fails an
> unannotated endpoint closed globally, and the "annotation audit" the steps
> below refer to is `BoundaryAnnotationAuditTest` (plus the generated-interface
> guard and `ContractSecuredOperationsDeniedTest`). Roles, paths and wire
> behaviour are unchanged — only the carrier and the enforcing test are.

> **Amendment 2026-08-25 (owner decision D1, constitution v1.6.0) — read
> before any file path below.** Every `de/gxp/device/...` and
> `de/gxp/guest/...` path in these steps gained one level:
> `de/gxp/device/boundary/{PairingResource,DeviceAdminResource}.java`,
> `de/gxp/device/control/{DeviceControl,DeviceRepository,PairingRequestRepository,PairingCodes}.java`,
> `de/gxp/device/entity/{Device,PairingRequest,DeviceMapper}.java`,
> `de/gxp/guest/boundary/GuestResource.java`,
> `de/gxp/guest/control/{GuestControl,GuestEtagFilter}.java`. On the test
> side, `src/test/java/de/gxp/device/{DeviceLastSeenTest,PairingFlowTest}.java`
> moved to `.../device/control/` because they reach package-private internals;
> `PairingCaTest` drives HTTP only and stayed put.
>
> The "package-private" notes below still hold for `DeviceRepository`,
> `PairingRequestRepository`, `PairingCodes` and `GuestEtagFilter`, and
> `ControlPackageVisibilityTest` now fails the build if any of them is
> widened. They do **not** hold for `Device`, `PairingRequest` and
> `DeviceMapper`, which are `public` in `.entity` — Java has no sub-package
> visibility, and these two entities needed every field widened because
> `DeviceControl` drives their whole lifecycle across the split.

## Phase 1 — Contract & schema

- [x] **T-01** Extend `api/openapi.yaml`: tags `pairing`, `manager-devices`,
      `guest` (approved two-Boundary tag convention, resolved decision #5);
      the 11 operations and schemas from the
      design's contract table; `bearerAuth` on guest + manager-device ops,
      none on pairing ops; no room/stay/device parameter on any guest op.
      *(REQ-401, REQ-411)* — Files: `api/openapi.yaml`.
      Validate: `./mvnw generate-sources` green, both generators.
- [x] **T-02** Regenerate `packages/api-client/` twice, assert byte-identical
      and committed together with contract + backend code; `frontend/`
      untouched. *(REQ-401)* — Files: `packages/api-client/**`.
      Validate: sha256 comparison across two runs; `git status`.
- [x] **T-03** `V4__device_pairing.sql` exactly per the design's DDL
      (`device`, `pairing_request`); `V1`–`V3` untouched. *(REQ-402)* —
      Files: `backend/app/src/main/resources/db/migration/V4__device_pairing.sql`.
      Validate: `MigrationTest` extended → Flyway head = V4 on a fresh DB.

## Phase 2 — `de.gxp.device` (pairing + device identity)

- [x] **T-04** Entities + repositories: `Device`, `PairingRequest`,
      `DeviceRepository` (find-by-token-hash unique, active-of-room),
      `PairingRequestRepository` (by code, opportunistic expired-delete);
      `PairingCodes` (8-char unambiguous alphabet). All
      package-private except nothing. *(REQ-403, REQ-409)* — Files:
      `backend/app/src/main/java/de/gxp/device/*`.
      Validate: `./mvnw test-compile`.
      **Amended 2026-08-24 (owner decision):** the `PairingRateLimiter`
      (5/60 s, 429 + `Retry-After`) built by this step, and the
      `PairingConcurrencyGate` that briefly replaced it, are both removed —
      create is unthrottled. See T-20 and REQ-409.
- [x] **T-05** `DeviceControl` (the package's one public type):
      `createPairingRequest` (@PermitAll, **unthrottled** since 2026-08-24 —
      see T-04 — deletes expired rows first, returns code + raw poll secret + expiry; hashes at rest),
      `poll` (@PermitAll: secret verified by hash; pending → 202 semantics;
      confirmed → mint 256-bit token, insert `Device` with hash, atomic
      claim-once, return raw token exactly once; all misses → one identical
      `404 pairing-not-found` via `ApiErrors`), `confirmPairing`
      (@RolesAllowed admin: room must exist, default name `Kiosk <code>`,
      404/409), `listDevices`/`renameDevice`/`revokeDevice` (@RolesAllowed
      admin; revoke = `revoked_at`, idempotent 204; DTOs never carry token
      material), `verifyToken(hash)` (@PermitAll, auth-internal: active
      device → identity data + throttled ≥60 s `last_seen_at` touch),
      `activeDeviceCountAndNames(roomId)` for the room guard. *(REQ-403,
      REQ-404, REQ-405, REQ-406, REQ-407, REQ-409, REQ-413)* — Files:
      `de/gxp/device/DeviceControl.java`, `DeviceMapper.java`.
      Validate: unit + rest-assured tests in Phase 6.
- [x] **T-06** Boundaries: `PairingResource` (tag `pairing`) and
      `DeviceAdminResource` (tag `manager-devices`) — one delegating line
      per method, `@ResponseStatus(201)` on create-pairing per the settled
      convention (package shape approved, resolved decision #5).
      *(REQ-401, REQ-403–405, REQ-407)*
      — Files: `de/gxp/device/PairingResource.java`,
      `DeviceAdminResource.java`. Validate: compile against generated
      interfaces; BCE one-line check.
- [x] **T-07** Room-delete guard: `RoomControl.delete` additionally collects
      active devices via the package-private native-count/names query
      (deviation-5 precedent) → `409 room-has-devices` naming them; revoked
      rows deleted with the room. *(REQ-408)* — Files:
      `de/gxp/room/RoomControl.java`, `RoomRepository.java`.
      Validate: test in Phase 6.

## Phase 3 — Auth extension (`de.gxp.auth`)

- [x] **T-08** `TokenIdentityProvider`: manager store first, then
      `DeviceControl.verifyToken(sha256(t))` → `SecurityIdentity`
      role `device` + `deviceId`/`roomId` attributes; miss/revoked → the
      existing 401 + challenge channel. `TokenExpiryResponseFilter` skips
      device identities. `BearerAuthMechanism` untouched. *(REQ-410,
      REQ-412)* — Files: `de/gxp/auth/TokenIdentityProvider.java`,
      `TokenExpiryResponseFilter.java`. Validate: auth tests in Phase 6;
      annotation audit still green.

## Phase 4 — Guest read model (`de.gxp.guest` + guest-read methods)

- [x] **T-09** `@PermitAll` guest-read methods on owning Controls (the
      `TemplateControl` precedent; read-only projections, no HTTP mapping):
      `PropertyControl` (guest snapshot incl. offered options, brand,
      translations, asset ids), `RoomControl` (room view), `MenuControl`
      (resolved menu for room — the existing ceiling implementation),
      `StayControl` (active stay of room; `writePreference(roomId, lang,
      app)` validated ∈ offered). *(REQ-417, REQ-424)* — Files: the four
      Controls. Validate: audit test (exactly one annotation per public
      method) stays green.
- [x] **T-10** `GuestControl.session(identity)`: room from identity only;
      compose property/room/stay/menu/standby per the design; occupancy
      derivation; vacant → no stay-derived values; settings chrome constants
      (`einstellungen`, `phosphorGearSix`, `/einstellungen`, de/en labels);
      preference block (raw override) + resolved defaults; localized maps
      over exactly the offered languages. *(REQ-416, REQ-417, REQ-418,
      REQ-419, REQ-426)* — Files: `de/gxp/guest/GuestControl.java`,
      `GuestResource.java` (tag `guest`), mapper-style private builders.
      Validate: Phase 6 content-parity tests.
- [x] **T-11** `GuestControl.homeScreen(identity)`: vacant →
      `409 room-vacant`; resolved-disabled `home` → `404 screen-not-found`;
      property translations, artwork fallback room → property, 0–4 cards
      with per-language `TemplateControl` resolution against the active stay
      (defensive skip of an impossible stranded card); no braces ever leave
      the server. *(REQ-419, REQ-420, REQ-421)* — Files: `GuestControl.java`.
      Validate: Phase 6 resolution tests.
- [x] **T-12** `GuestControl.asset(identity, id, ifNoneMatch)`: reachability
      set {logo, property artwork, own-room artwork} else
      `404 asset-not-found`; delegate to `AssetControl`'s thrown-`Response`
      binary path (ETag/304, `Cache-Control: private, max-age=2592000`).
      *(REQ-422)* — Files: `GuestControl.java`, `de/gxp/asset/AssetControl.java`
      (@PermitAll binary read variant). Validate: Phase 6.
- [x] **T-13** `GuestEtagFilter`: strong sha-256 ETag over serialized
      `/api/guest/**` JSON, `If-None-Match` → `304` empty,
      `Cache-Control: no-cache`. Interception point is the design's
      verify-at-implementation item — record the finding in the handoff.
      *(REQ-423)* — Files: `de/gxp/guest/GuestEtagFilter.java`.
      Validate: Phase 6 ETag tests.
- [x] **T-14** `PUT /api/guest/preferences`: whole-record
      `{language, appearance}` nullable; `400 value-not-offered`;
      vacant → `409 room-vacant`; write on the active stay. *(REQ-424)* —
      Files: `GuestControl.java`, `GuestResource.java`.
      Validate: Phase 6.

## Phase 5 — TLS & system surface *(unblocked 2026-08-19 — decisions #1–#4 approved)*

- [x] **T-15** TLS init per the decided mechanism (resolved #1/#2):
      entrypoint `keytool` script — first start generates CA (20 y) +
      server cert (EC P-256, 10 y, SAN = `GXP_TLS_HOST`) onto the `gxp-tls`
      volume; restart reuses; host change re-issues the server cert under
      the unchanged CA; no key material in any log. *(REQ-427, REQ-430,
      REQ-433)* — Files: `backend/app/src/main/docker/*` (entrypoint),
      `backend/compose.yaml`. Validate: compose smoke T-24.
- [x] **T-16** `%prod` HTTPS-only config (resolved #3): keystore wiring,
      `quarkus.http.insecure-requests=disabled`, refuse-to-start without
      `GXP_TLS_HOST`/material; `%dev`/`%test` unchanged; commented config
      block (the `/q/openapi` lockdown precedent). *(REQ-428, REQ-433)* —
      Files: `application.properties`. Validate: config review + T-24;
      verify-at-implementation of the exact 3.33 property names recorded.
- [x] **T-17** `GET /api/pairing/ca` serving the CA PEM (public,
      unauthenticated; one-line Boundary → `DeviceControl` reads the volume
      path; in `%dev`/`%test` without material → `404` with a clear code).
      *(REQ-429)* — Files: `de/gxp/device/*`, contract already in T-01.
      Validate: rest-assured (`%test` 404 branch) + compose smoke (PEM
      parses, `curl --cacert` handshake).
- [x] **T-18** Compose production-shaped (resolved #3): `gxp-tls` volume,
      host `443` → container `8443`, `GXP_TLS_HOST` required interpolation,
      healthcheck under HTTPS-only, dev/preview warning removed, backup note
      (one `pg_dump` + TLS volume). *(REQ-431)* — Files:
      `backend/compose.yaml`, `backend/README.md`. Validate: T-24.
- [x] **T-19** `/api/system/info` → `@RolesAllowed({"admin","device"})`
      (resolved #4); health/ready stay `@PermitAll`; `SystemResourceTest`
      updated accordingly. *(REQ-432)* — Files:
      `de/gxp/system/SystemControl.java`, tests. Validate: suite.

## Phase 6 — Tests

- [x] **T-20** Pairing lifecycle (`PairingFlowTest`): create (201, code
      shape, expiry), poll pending 202, confirm (204; 404 unknown; 409
      double), claim exactly once (incl. two racing polls → one 200), the
      indistinguishable-404 matrix (unknown/expired/claimed/wrong-secret:
      status + body equality asserted), opportunistic cleanup, token stored
      as 64-hex hash only, no token material in device DTOs. *(REQ-403,
      REQ-404, REQ-405, REQ-406, REQ-407, REQ-409)* — Files:
      `src/test/java/de/gxp/device/*`.
      **Amended 2026-08-24 (owner decision):** the "rate limit 429 +
      `Retry-After`" assertion is withdrawn with the rate limit. Its
      replacement asserts the opposite and is the positive statement of
      REQ-409 as amended — a concurrent create-flood is **fully served**
      (every request 201, no `Retry-After` anywhere) and the kiosk arriving
      behind it is served too. The `resetRateLimiter` `@BeforeEach`/`@AfterEach`
      hook is gone: there is no shared budget left to reset.
- [x] **T-21** Device auth & scoping (`DeviceAuthTest`, `RoomScopingTest`):
      valid token → session 200; revoked → 401 + challenge next request;
      unknown/malformed → 401; admin token on guest op → 403; device token
      on manager op → 403; no `GXP-Token-Expires` on device responses;
      last-seen throttle (≤1 write for consecutive requests); the
      two-devices/two-rooms matrix (different stay names, preferences,
      room-scoped config; contract has no scope parameter to tamper with);
      room-delete guard (active device → `409 room-has-devices` naming it;
      after revoke the delete succeeds and revoked rows are gone).
      *(REQ-408, REQ-410, REQ-411, REQ-412, REQ-413, REQ-414, REQ-425)* —
      Files: `src/test/java/de/gxp/{auth,guest,room}/*`.
- [x] **T-22** Guest content & parity (`GuestSessionTest`,
      `GuestHomeScreenTest`): session equals seeded frontend shape (5 items,
      settings chrome outside items, offered-language keys exactly);
      ceiling parity (property disable removes item from a room with an
      explicit enable row); occupancy flip; vacancy data-sparing (no name,
      no override, standby texts present); home screen: artwork fallback,
      0–4 resolved cards, `|time`/`|date` per language in the property
      timezone, no braces in any payload; unknown screen key 404;
      resolved-disabled home 404; vacant 409. *(REQ-416–REQ-421, REQ-426)*
- [x] **T-23** ETags, assets, preferences (`GuestEtagTest`,
      `GuestAssetTest`, `GuestPreferenceTest`): ETag present, replay → 304
      empty, manager edit changes it; asset ETag/304 + reachability 404
      (other room's artwork); preference write validation (400/409/204 +
      visible in next session read); extended fail-closed annotation audit
      incl. `GuestControl`/`DeviceControl`; log sweep — no token/secret/key
      material (grep + boot/test logs, unchanged zero-SQL posture).
      *(REQ-422, REQ-423, REQ-424, REQ-414, REQ-415, REQ-434)*

## Phase 7 — Smoke, docs, registry

- [x] **T-24** Compose smoke on a fresh volume (lead runs it —
      needs Docker): first boot generates TLS material; restart reuses
      (fingerprint identical); `curl --cacert <GET /api/pairing/ca>`
      handshake OK; plain HTTP dead per decision; pairing→confirm→claim→
      session end-to-end over HTTPS; boot fails fast without `GXP_TLS_HOST`;
      log clean of key/token material; `/q/openapi` still 404;
      `/api/system/info` 401 anonymous / 200 with either token. *(REQ-427,
      REQ-428, REQ-429, REQ-430, REQ-431, REQ-432, REQ-433, REQ-434)*
- [x] **T-25** `backend/README.md`: pairing workflow (code, confirm, claim,
      CA install as a documented human provisioning step), device
      revocation, TLS lifecycle + backup note. *(REQ-429, REQ-431, REQ-435)*
- [x] **T-26** ~~`backend/CLAUDE.md` one-tag line amended~~ **DONE
      2026-08-19**: the user approved the exact text verbatim (resolved
      decision #5) and it is written into `backend/CLAUDE.md`; `dev` verifies
      the file matches the design's quoted text at implementation. *(REQ-401)*
- [x] **T-27** `docs/system-context.md` (device/guest/TLS component rows,
      Auth section: device tokens live, compose no longer dev-only,
      hand-off notes for `guest-frontend-integration`: token/CA client
      handling, `ngVersion` check, images fetched with auth header — no bare
      `<img src>`, kiosk cache still holds nothing sensitive) +
      `docs/features/index.md` row. *(REQ-435)*

## Traceability

Every REQ-401…435 appears in ≥1 task above; every task cites its REQ-IDs.
Cross-check table lives in [04-analyze.md](04-analyze.md).
