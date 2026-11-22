# PRD: Device Pairing & Guest API
**Feature:** [index.md](../index.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Status:** Draft — **decision-complete**: all five open decisions approved verbatim by the user 2026-08-19 ("All five recommended go ahead"); resolutions recorded in [02-design.md → Resolved decisions](02-design.md)

> Third feature of the backend cut (`docs/backend/specification.md` §12),
> planned on top of `backend-foundation` and `backend-domain-config` (both
> reviewed **PASS**, committed `f4b6f64`). Settled ground — referenced, never
> re-opened: spec §2 decision log (esp. decisions 3, 7, 9, 11, 18),
> constitution **v1.5.1**, the closed dependency allow-list,
> `backend/CLAUDE.md`, and the **ceiling** rule for menu `enabled`
> (user decision 2026-08-19, REQ-307 as amended).
>
> **REQ-ID block: `REQ-401+`** — fresh 4xx block (0xx home-screen,
> 1xx appearance-and-language, 2xx foundation, 3xx domain-config).

## Problem statement

The backend now holds the whole domain and a secured manager CRUD API — but
no kiosk can read any of it. There is no device identity (a kiosk cannot
prove *which room it is*), no `/api/guest/**` surface, and the instance still
speaks **plain HTTP on an untrusted LAN**: the manager password and bearer
tokens from `backend-domain-config` ride unencrypted, which is why the
compose unit is explicitly dev/preview-only today.

This feature closes all three gaps, exactly as constitution v1.5.1 records
them (approved 2026-08-19):

1. **Device pairing** — the manager-confirmed flow that gives each kiosk a
   long-lived opaque token; the token is identity *and* authentication, and
   the room is derived from it, never from a client-supplied parameter
   (principle 4, the single most important security property of this backend).
2. **The guest API** — `GET /api/guest/session` + `GET /api/guest/screens/home`
   + guest asset delivery, all room-scoped, ETag-polled, occupancy-aware, and
   resolving menu enablement under the same ceiling rule the manager API
   enforces.
3. **HTTPS with an instance-generated CA** — non-negotiable per the
   constitution; this is the feature the foundation and domain-config
   handoffs both point at ("until TLS lands…"). After it, the compose unit is
   production-shaped, not preview-only.

The guest *frontend* does not change: consuming this API (plus the
last-known-good cache and the standby screen) is `guest-frontend-integration`
— including the generated client's `ngVersion` ceiling check on first
consumption.

## Goals / Non-goals

**Goals**

1. Pairing flow per spec §6: kiosk requests a short-lived code, the manager
   confirms it to a room, the kiosk polls and receives the device token
   **exactly once** — hardened so that observing the code on the kiosk screen
   (a guest-accessible room) can never yield the token.
2. Device identity: `Device` and `PairingRequest` entities (spec §5), device
   tokens stored **hashed**, revocable from the manager API, and integrated
   into the existing `de.gxp.auth` bearer mechanism as the `device` role —
   composing with, not replacing, that package's structure.
3. `/api/guest/**` read model: session (shell) + `screens/home`, resolved
   strictly per the settled rules — preferences Stay → Room → Property, menu
   per-field room → property → catalogue **with the property as the global
   ceiling for `enabled`**, `einstellungen` served as fixed chrome outside
   the menu items, templates resolved server-side, everything localized as
   language-keyed maps so a kiosk language switch fetches nothing.
4. Occupancy state and vacancy data-sparing (spec decision 9): a vacant room
   serves no stay-derived value anywhere.
5. Strong ETags on every guest response; polling normally costs `304`s.
6. The guest preference write (the `PreferenceGateway` seam's server side):
   stay-anchored language/appearance override, resolved into the session.
7. TLS: instance-generated CA + server certificate on first start, HTTPS on
   the LAN, CA distributable to kiosks at pairing (constitution v1.4.0/1.5.1);
   compose updated accordingly.

**Non-goals** (explicit, with owners)

- **Any frontend change** — `frontend/` untouched; the TS client is
  regenerated and committed but not consumed. Kiosk-side pairing UI, token
  storage, CA installation on devices, the last-known-good cache and the
  standby *screen* → `guest-frontend-integration` (screen designs are spec
  §11 items 1–2, still open).
- **The §6 preview endpoint** → `manager-frontend` (decided in
  `backend-domain-config` planning; unchanged).
- **Guest screens beyond `home`** (WiFi, house rules, surroundings,
  appliances, departure) → later features; decision 7 makes each an additive
  `screens/{key}` contract change. Consequently the **sensitive-field list**
  (spec §11 item 9) is *not* needed yet — nothing this feature serves is
  guest-sensitive (no door codes, no WiFi passwords) — but it must exist
  before the first sensitive screen ships.
- **Kiosk device lockdown / provisioning** (spec §11, system-context open
  item) — operational; the CA-install step documented here is part of it but
  does not resolve it.
- **Per-user manager accounts, audit trail** — still deferred (spec
  decision 4).
- **Clock/NTP correctness** (spec §11 item 6) — occupancy derivation sharpens
  the dependency again; recorded, not solved here.

## Assumptions

- Both prior backend features are committed (`f4b6f64`); work continues on
  branch `feature/backend`.
- The closed dependency allow-list suffices: `SecureRandom`, `MessageDigest`
  are JDK; the device role rides the already-implemented `quarkus-security`
  SPI adapters; Quarkus/Vert.x serves TLS natively (core, no extension).
  **No `pom.xml` change is expected; if implementation finds one is needed it
  stops with ⏸** — the one candidate (TLS material generation) was decided
  2026-08-19 in favour of the zero-dependency `keytool` path.
- The kiosk reaches the server at one stable host (IP or name) configured at
  installation via the required **`GXP_TLS_HOST`** variable (decided
  2026-08-19) — the certificate's subject is derived from it.
- A paired kiosk persists its device token on the device; that is the
  device's own credential, not guest data, and its client-side handling is
  `guest-frontend-integration`'s.
- Pairing is a rare, manager-attended act (a handful per building lifetime),
  so aggressive rate limiting costs nothing.

## User stories

- As the **property manager**, I want to pair a new kiosk by reading a short
  code off its screen and confirming it to a room, so that provisioning needs
  no typing on the kiosk and no credentials in the clear.
- As the **property manager**, I want to see every paired device (room, name,
  last seen) and revoke one immediately when a tablet disappears, so that a
  stolen device stops being a key to a room's data.
- As a **kiosk in room 101**, I want one `session` call and one `screens/home`
  call to render everything the static data renders today — in every offered
  language at once — and cheap `304` polling afterwards.
- As **anyone on the guest WiFi**, I must not be able to read any room's
  data, obtain a device token by watching a kiosk's pairing screen, or read
  the manager password off the wire — and no parameter I send may ever select
  a room.
- As the **next feature** (`guest-frontend-integration`), I want the contract
  to mirror `PropertyContentService`/`PreferenceGateway` closely enough that
  the swap is the one-provider-line change principle 2 promised.

## Requirements

### Contract & schema

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-401 | `api/openapi.yaml` gains the pairing, manager-device and guest surfaces (operations below) with the new schemas; guest operations require `bearerAuth` (device token), pairing operations are unauthenticated by design, manager-device operations require `bearerAuth` (admin). Tags follow the **approved two-Boundary convention** (user decision 2026-08-19, `backend/CLAUDE.md` amendment written): `pairing` + `manager-devices` are two Boundaries over the single public `DeviceControl`, one generated interface per Boundary. Both generators run green; `packages/api-client/` is regenerated deterministically and committed with contract and backend code together; `frontend/` is untouched. | Must |
| REQ-402 | All schema changes are one Flyway migration (`V4__device_pairing.sql`): `device` and `pairing_request` tables per spec §5. `V1`–`V3` untouched; no Hibernate schema generation in any profile. | Must |

### Pairing flow (spec §6, hardened)

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-403 | `POST /api/pairing/requests` (unauthenticated, rate-limited): creates a short-lived pairing request and returns a human-readable **display code** (shown on the kiosk for the manager), a high-entropy **poll secret** (kept by the kiosk, never displayed), and the expiry. The two-credential split is the point: the display code alone must never suffice to obtain a token, because the kiosk screen stands in a guest-accessible room. | Must |
| REQ-404 | `GET /api/pairing/requests/{code}` (unauthenticated, poll secret required): `202` while pending; `200` with the device token **exactly once** after the manager confirms (the request is atomically claimed — a second poll gets `404`); expired, unknown, claimed, or wrong-secret requests are all the **same** `404` (no oracle distinguishing them). | Must |
| REQ-405 | `POST /api/manager/pairings/{code}/confirm` (admin): assigns the request to an existing room, with an optional device name (default derived from the room). Unknown/expired code → `404`; already confirmed → `409`. | Must |
| REQ-406 | The device token is minted **at claim time** (never stored raw): 256-bit `SecureRandom`, returned once in the poll response, persisted only as a SHA-256 hash on the created `Device` row (room, name, created-at). Same treatment as manager tokens (constitution: token is identity and authentication, stored hashed). | Must |
| REQ-407 | Manager device API: list all devices (id, name, room, created, last-seen, revoked state), rename, and **revoke** (soft — `revoked_at` set, the token is dead on the very next request). No operation ever returns a token or a token hash. | Must |
| REQ-408 | Deleting a room with **active (non-revoked) devices** is `409` naming them (manager work/state is never silently destroyed — the manager revokes or re-pairs first); rows of revoked devices are cleaned up with the room. | Must |
| REQ-409 | Pairing hygiene: codes are single-use and unambiguous (no 0/O/1/I confusion), expire after the pinned TTL, and expired rows are cleaned up opportunistically (no scheduler dependency — none is on the allow-list). **Amended 2026-08-24 (owner decision): creation is deliberately not rate-limited** — no rate limit, no admission gate. Code entropy + TTL alone make guessing infeasible, and they suffice: the display code is 8 characters from a 32-symbol alphabet (32⁸ ≈ 1.1 × 10¹² combinations) valid for 10 minutes, and a flooder reaches neither `confirm` (admin-only) nor `poll` (256-bit secret). The poll secret alone (256-bit) protects the token. **Consequence, accepted:** live pairing rows are no longer capped in number — they are bounded only in age, by the 10-minute TTL and the opportunistic cleanup that runs on every create. | Must |

### Device authentication

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-410 | One bearer authentication path (constitution: "one authentication path to review, not two"): the existing `de.gxp.auth` mechanism resolves a presented token first against the in-memory manager store (`admin`), then against the device table by hash (`device`, with the device/room identity attached). The auth package is **extended, not restructured** — the later per-user swap still touches no Boundary or Control. | Must |
| REQ-411 | The room is derived **exclusively** from the authenticated device identity. No guest operation accepts any room, stay, device or property identifier from the client — not in path, query, header or body (principle 4, verbatim). | Must |
| REQ-412 | A revoked, unknown or malformed device token → `401` + `Bearer` challenge, effective on the next request after revocation (no grace window). Device tokens do not idle-expire and do not slide; the `GXP-Token-Expires` header remains a manager-token concern and is not sent for device identities. | Must |
| REQ-413 | `last_seen_at` is maintained per device, **throttled** (at most one write per device per pinned interval) so ETag polling does not cost a DB write per request. | Must |
| REQ-414 | Fail-closed posture extended: every public Control method still carries exactly one security annotation (audit test updated); guest Control methods are `@RolesAllowed("device")` — an admin token does not grant guest endpoints (it has no room) and a device token grants no manager endpoint. **Amended 2026-08-24 (owner decision D2, constitution v1.6.0):** the annotation carrier is the **Boundary**, not the Control. `GuestResource` is `@RolesAllowed("device")` class-wide, `PairingResource` `@PermitAll` class-wide and `DeviceAdminResource` `@RolesAllowed("admin")` class-wide; `DeviceControl` and `GuestControl` carry none. The wire behaviour of this requirement is unchanged (same roles, same 401s) — what changed is where the annotation sits and which test enforces it: the `*Control` sweep this REQ pinned by name became `BoundaryAnnotationAuditTest`, which pins the ten Boundaries the same way. Note `DeviceControl.verifyToken` in particular: it ran under a `@PermitAll`-on-internal only because `TokenIdentityProvider` calls it DURING authentication, before any identity exists — the wart the move removes. | Must |
| REQ-415 | No token, poll secret, hash, TLS private key or password material reaches any log or exception message, in any profile; pairing/auth errors are generic. The foundation's logging posture is not weakened. | Must |

### Guest API (spec §6, decisions 3/7/9)

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-416 | `GET /api/guest/session` returns everything route-independent for the device's room: property (name, the three brand primitives, default appearance, logo asset reference), room (code, display name), the settings config (offered languages and appearance options with the same fields the frontend's `settingsConfig` carries, plus resolved defaults), the guest's current preference override, occupancy state, guest display name (occupied only), the resolved menu, and the resolved standby texts. | Must |
| REQ-417 | Resolution rules are **the settled ones, via the same implementations** — never re-implemented: preferences resolve Stay → Room → Property; menu resolves per-field room → property → catalogue with the **ceiling** rule for `enabled` (`enabled(room,k) = enabled(property,k) AND room-override(default true)`, user decision 2026-08-19 — a property disable wins over any room enable). The guest menu contains only resolved-enabled items, ordered by resolved sort index, with resolved label/icon and the catalogue route. | Must |
| REQ-418 | `einstellungen` is served as **fixed chrome outside the menu items** (id, icon `phosphorGearSix`, route `/einstellungen`, localized labels matching the shipped frontend data) — it is not a catalogue item, no configuration can remove it, and guests can always reach language/theme switching (carried-forward design decision). | Must |
| REQ-419 | Occupancy is derived (`BOOKED` ∧ check-in ≤ now < check-out — the same derivation as `backend-domain-config`). When **vacant**, no stay-derived value is served anywhere (spec decision 9, maximally data-sparing): no display name, no preference override (defaults apply), and stay-dependent screens answer with a named `409 room-vacant`. The session still serves the standby texts so the kiosk can render the standby screen. | Must |
| REQ-420 | `GET /api/guest/screens/home` returns the home screen content for the device's room: headline/subtitle/paragraph (property translations), the artwork asset reference (room override → property default), and 0–4 resolved action cards (icon, title, description, target menu key + route). Card titles/descriptions are **resolved server-side** by the existing template resolver — per offered language, checkout time/date in the property timezone with each language's locale. The kiosk never receives a raw template. | Must |
| REQ-421 | `screens/{key}` semantics: only implemented screen keys exist (`home` in this feature); an unknown key, or a key whose menu item resolves disabled for this room, is `404`. When the room is vacant, `screens/home` is the named `409 room-vacant` (REQ-419). | Must |
| REQ-422 | `GET /api/guest/assets/{assetId}` (device role): binary with the SHA-256 as strong `ETag`, `304` on `If-None-Match`, long `Cache-Control`. Only assets **reachable from the device's own resolved view** (property logo, property default artwork, the own room's artwork) are served; any other id is `404` — no enumeration of stored assets from a guest device. | Must |
| REQ-423 | Every guest JSON response carries a strong `ETag`; a matching `If-None-Match` yields `304` with an empty body, so steady-state kiosk polling is `304`s (spec §6). The ETag is content-derived, so occupancy flips and config edits change it naturally. | Must |
| REQ-424 | `PUT /api/guest/preferences` (device role): whole-record write `{language, appearance}` (both nullable = "follow the defaults"), mirroring the frontend's `StoredPreferences` gateway record. Values outside the property's offered sets → `400`; vacant room → `409 room-vacant` (the client's fire-and-forget write treats failure as normal). The write lands on the **active stay** (the `Stay` anchor decided 2026-08-19), so it clears at stay change by construction. | Must |
| REQ-425 | **Room scoping is proven by tests**: two devices paired to different rooms receive different room/stay data; a device can never obtain another room's stay name, preference or room-scoped config through any input (no such input exists, REQ-411); a revoked device receives nothing. Every query touching room-scoped data is written as security-relevant (constitution principle 4). | Must |
| REQ-426 | All localized guest fields are language-keyed maps covering exactly the property's offered languages — a kiosk language switch renders from what it already holds, fetching nothing (system-context cross-cutting rule: "nothing is fetched to render either one"). | Must |

### TLS (constitution v1.4.0/v1.5.1, spec decision 18, §9)

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-427 | On first start the instance generates its own CA and a CA-signed server certificate; on later starts the existing material is reused. Private keys never leave the server and never appear in any log. Generation mechanism (**decided 2026-08-19**): a `keytool`-based init step in the app container entrypoint (the JVM image ships `keytool`) writing to the named `gxp-tls` volume — no new dependency, no second image. | Must |
| REQ-428 | In `%prod` the instance serves **HTTPS only** — plain HTTP is not served, not even as a redirect (**decided 2026-08-19**: `quarkus.http.insecure-requests=disabled`; a redirect would still invite the first password over HTTP). Dev and test profiles stay plain HTTP (Dev Services, local iteration) — the posture split mirrors the `%prod` `/q/openapi` lockdown precedent. | Must |
| REQ-429 | The CA **certificate** (public material, PEM) is retrievable for kiosk provisioning via an unauthenticated pairing-surface endpoint, and the README documents the install step as part of the pairing workflow ("the pairing flow installs the CA on the kiosk" — realistically: a person installs it into the device's trust store during provisioning; this PRD does not pretend a web page can do that). | Must |
| REQ-430 | Certificate identity and parameters (**decided 2026-08-19**): the server certificate's SAN is the host kiosks use, from the required **`GXP_TLS_HOST`** variable — in `%prod` the instance refuses to start without it, same posture as `GXP_ADMIN_PASSWORD`; keys **EC P-256**; **CA valid 20 years, server certificate 10 years** (no internet, no ACME, unattended boxes); when the configured host no longer matches the existing certificate, the **server certificate** is re-issued under the unchanged CA so paired kiosks keep trusting without re-pairing. | Must |
| REQ-431 | `backend/compose.yaml` becomes production-shaped: TLS material on a named volume (backup story updated: one `pg_dump` **plus** the TLS volume — or the documented consequence of losing it), the HTTPS port published, the new required variable(s) wired with fail-fast interpolation, healthcheck working under HTTPS-only, and the "dev/preview-only, no TLS" warning **removed** — this feature is what the warning was waiting for. | Must |

### System surface

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-432 | `/api/system/info` is restricted to `@RolesAllowed({"admin","device"})` (**decided 2026-08-19** — version fingerprinting on an unpatchable box was the foundation review's revisit-with-auth note, now closed). `/api/system/health` and `/ready` stay public (compose healthcheck, "a person in the building can tell whether the box is the problem"). | Must |

### Operations, tests, docs

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-433 | First run still yields a usable instance with zero manual database work: migrations, seeds, admin credential, **TLS material** and a workable pairing flow end to end. Refuse-to-start conditions extended only by the required `GXP_TLS_HOST` variable in `%prod` (decided 2026-08-19). | Must |
| REQ-434 | Every implemented behavior has a test (`quarkus-junit` + `rest-assured` on the real contract paths): the full pairing lifecycle (create, 202 poll, confirm, claim-exactly-once, expiry, wrong-secret indistinguishability, rate limit, double-confirm), device auth (valid, revoked-next-request, unknown, admin-token-on-guest-endpoint, device-token-on-manager-endpoint), room scoping (REQ-425), resolution parity with the manager semantics incl. the ceiling rule, occupancy/vacancy incl. the `409`s, template-resolution output per language, ETags/`304` on JSON and assets, asset reachability `404`, preference write validation, and the extended fail-closed audit. TLS wiring is proven by config review + the compose smoke (the `%test` profile cannot exercise `%prod` TLS — same limitation as the `/q/openapi` lockdown, same compensations). | Must |
| REQ-435 | Docs move with the code: `backend/README.md` (pairing workflow incl. CA install, TLS material lifecycle + backup note, device revocation), `docs/system-context.md` (new components, Auth updated to include device tokens, compose no longer dev-only, hand-off notes for `guest-frontend-integration`), `docs/features/index.md`. | Must |

## Acceptance criteria (testable)

- **REQ-401:** `./mvnw verify` green; regenerating twice leaves
  `packages/api-client/` diff-clean; `git status` shows nothing under
  `frontend/`; every guest/manager-device operation declares `bearerAuth`,
  pairing operations declare none.
- **REQ-402:** boot log shows Flyway at V4; `V1`–`V3` files unchanged.
- **REQ-403/404:** create returns code + secret + expiry; poll before confirm
  → `202`; after confirm → `200` with a token exactly once; the second poll,
  an expired code, an unknown code and a wrong secret all return an
  indistinguishable `404` (status + body asserted equal).
- **REQ-405:** confirm with unknown code → `404`; second confirm → `409`;
  confirm assigns the named room.
- **REQ-406/407:** the DB row holds a 64-hex-char hash, never the raw token
  (asserted at repository/store level); list/rename/revoke round-trip; no
  device DTO field contains token material.
- **REQ-408:** deleting a room with an active device → `409` naming it;
  after revoking, the delete succeeds.
- **REQ-409:** **amended 2026-08-24** — the former criterion *"a create-flood
  hits the rate limit (`429` + `Retry-After`)"* was withdrawn with the rate
  limit itself (owner decision); it is replaced by its opposite, which is now
  the behaviour under test: a **concurrent create-flood is fully served**
  (every request `201`, no `Retry-After` on any of them) and the kiosk
  arriving behind it is served too. An expired request is gone after the next
  create (opportunistic cleanup) — that cleanup is what bounds live rows now,
  so its test carries more weight than before.
- **REQ-410/412/414:** the same `Authorization: Bearer` header authenticates
  both roles; a revoked token → `401` + challenge on the immediate next
  request; the annotation audit covers the new Controls (**amended
  2026-08-24:** it covers the new **Boundaries** — `GuestResource`,
  `PairingResource`, `DeviceAdminResource` — pinned by name in
  `BoundaryAnnotationAuditTest`); an admin token on
  `/api/guest/session` → `403`; a device token on `/api/manager/rooms` → `403`.
- **REQ-411:** the OpenAPI contract contains **no** room/stay/device
  parameter on any guest operation (asserted by contract inspection).
- **REQ-413:** two immediate consecutive guest requests cause at most one
  `last_seen_at` write (asserted at the throttle component).
- **REQ-416/417/418/426:** a paired device's session equals the seeded
  frontend data shape: 5 catalogue items when nothing is disabled, settings
  entry outside `items` with the fixed route/icon; disabling `smart-home` at
  property scope removes it from the session of a room with an explicit
  room-level enable row (ceiling); every localized field carries exactly the
  offered language keys.
- **REQ-419/421/424:** with no active stay: session says vacant, carries no
  display name and no override; `screens/home` and the preference write →
  `409 room-vacant`; with an active stay, all three work and the preference
  write shows up in the next session read.
- **REQ-420:** with a card titled `"Bis {stay.checkOut|time} Uhr am
  Abreisetag"`, the device receives the resolved de/en strings for the active
  stay in the property timezone — no braces in any guest payload (asserted).
- **REQ-422:** the logo and own-room artwork are served with `ETag` and
  answer `304` on `If-None-Match`; another room's artwork id → `404`.
- **REQ-423:** session/screen responses carry an `ETag`; replaying it in
  `If-None-Match` → `304` empty; after a manager edit the ETag changes.
- **REQ-425:** the two-device/two-room matrix passes; a revoked device gets
  `401` everywhere.
- **REQ-427–431:** compose smoke on a fresh volume: first start generates CA
  + server cert; restart reuses them (fingerprints identical); HTTPS
  handshake succeeds with `curl --cacert <fetched CA>`; plain HTTP is dead
  (no listener, no redirect); the CA endpoint serves valid PEM; the log contains no key
  material; boot fails fast when the TLS host variable is unset.
- **REQ-432:** `/api/system/info` anonymous → `401`, admin and device tokens
  → `200`; health/ready still `200` unauthenticated.
- **REQ-433–435:** `./mvnw verify` green with the new suites; README /
  system-context / index updated in the same change.

## Technical constraints

Anchored to `docs/constitution.md` v1.5.1 — all binding: auth & transport
model **verbatim** (this feature implements its remaining half: device tokens
via pairing, hashed + revocable, room derived from the token; HTTPS with an
instance-generated CA distributed at pairing); principle 4 in full (room
scoping, offline, untrusted LAN, minimal dependencies); BCE strict per spec
§4 + `backend/CLAUDE.md` as amended 2026-08-19 (one-line Boundaries,
`@Transactional` on Controls, security annotations on Controls (**amended
2026-08-24, D2: on the Boundary**), `ApiErrors` for thrown
non-2xx, one tag per Boundary — with the approved two-Boundary exception for
`de.gxp.device`); contract-first workflow; Flyway-only schema; closed
dependency allow-list (**this feature adds nothing** — the decided `keytool`
path keeps TLS inside the JDK/base image); the kiosk
cache constraint (nothing served here is guest-sensitive yet — recorded, and
the §11-item-9 field list is required before the first sensitive screen);
principle 3 for logs; principles 6/7; branch discipline (`feature/backend`).

## Risks & open questions

- ~~Five decisions are open~~ — **all five approved verbatim by the user
  2026-08-19** (TLS generation mechanism, certificate identity/parameters,
  HTTPS-only posture, `/api/system/info` exposure, the two-Boundary package
  + `backend/CLAUDE.md` amendment). Resolutions recorded in
  [02-design.md → Resolved decisions](02-design.md); the amendment is
  written into `backend/CLAUDE.md`.
- **CA installation on kiosks is a human step.** A browser cannot install a
  trust anchor; the endpoint + README make it a documented provisioning step.
  If field reality demands more automation, that is kiosk-provisioning work
  (the still-open lockdown question), not a backend change.
- **Certificate lifetime vs. unattended boxes.** Long-lived certs are the
  only honest option without internet; the trade-off (a long-lived key on a
  physically accessible box) is recorded. Rotation = regenerate + re-install
  CA at the next physical visit; the design keeps that a documented manual
  path, not a feature.
- **Clock correctness now matters twice** — occupancy derivation *and*
  certificate validity windows both read the box's clock (spec §11 item 6,
  owner-level, still open).
- **`BYTEA` asset delivery** inherits domain-config's bounded-memory
  honesty (hard cap, no streaming fiction) — unchanged, now reachable by
  devices.
- **ETag over serialized JSON** must be deterministic; generated DTOs have a
  fixed property order, but the mechanics (where the hash is computed) are a
  *verify-at-implementation* item pinned in the design.
- **Admin surface enumerability is closed** (`/q/openapi` off in `%prod`,
  decided 2026-08-19) — this feature inherits that posture and adds no
  dev-time surface to prod.
- **Nothing served to guests is sensitive yet.** The first screen that is
  (WiFi, door codes) must bring the spec §11 item 9 field-level list and the
  per-field cache rules with it — recorded as a hand-off requirement for that
  future feature, and the reason `screens/{key}` endpoints can be secured
  separately (spec decision 7).
