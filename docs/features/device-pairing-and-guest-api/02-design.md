# Design: Device Pairing & Guest API
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **TODO:** [03-todo.md](03-todo.md)
**Status:** Draft — **decision-complete**: all 5 decisions approved verbatim by the user 2026-08-19 (record at the end)

> HOW for [01-prd.md](01-prd.md). Settled ground referenced, not re-made:
> spec §2/§4/§5/§6/§9, constitution v1.5.1, `backend/CLAUDE.md`, the
> foundation/domain-config pinned patterns (one-line Boundary, thrown non-2xx
> via `ApiErrors`, infrastructure adapters beside `ApiErrorMappers`,
> verify-at-implementation for version-sensitive names), and the **ceiling**
> rule (user decision 2026-08-19).

## Scope decisions made in planning (with rationale)

| Decision | Call | Why |
|---|---|---|
| Two-credential pairing | **Display code + poll secret** (hardening over the spec §6 sketch) | The kiosk screen stands in a guest-accessible room. If polling the code alone returned the token, anyone photographing the screen could win the race after the manager confirms. The code is the *human* channel (manager reads it, confirms); the 256-bit poll secret is the *machine* channel (kiosk keeps it, never displays it). Spec §6 paths are "illustrative" — this strengthens, contradicts nothing. |
| Token minted at claim, not at confirm | Claim-time | The raw token must be returned exactly once and never stored. Minting at confirm would force storing it (raw) until the kiosk polls. At claim: poll sees the confirmed request → creates the `Device` row with the hash → returns the raw token → marks the request claimed, one transaction. |
| Pairing + devices are **one BCE package** (`de.gxp.device`) | One consistency unit, one public `DeviceControl` | The claim is a cross-entity transaction (read `PairingRequest`, write `Device`, mark claimed). Two packages force a Control cycle (pairing→device for claim, device→pairing for confirm) — the same reasoning that made menu+cards one package. Consequence: **two Boundaries in one package** (kiosk-facing + admin-facing) — **approved by the user 2026-08-19** together with the exact `backend/CLAUDE.md` amendment (resolved decision #5 below, amendment written). |
| Guest read model = coordinating Control | `de.gxp.guest`, `GuestControl` | Exactly what spec §4 already reserves: "the guest read model is a coordinating Control … the one place where cross-feature composition is expected, deliberately read-only" (plus the one stay-anchored preference write, which delegates to `StayControl`). |
| Resolution logic is **never duplicated** | Owning Controls gain `@PermitAll` guest-read methods (**amended 2026-08-24, D2:** the methods stayed, the annotation did not — Controls carry none) | `MenuControl` already implements the ceiling; `TemplateControl` the resolver; `StayControl` the active-stay derivation. `GuestControl` composes them. The `@PermitAll`-on-internal-methods reading follows the `TemplateControl` precedent the domain-config review explicitly called "forward-correct for the guest API's device role". Authorization lives on `GuestControl`'s methods (`@RolesAllowed("device")`) — the only ones a guest HTTP request can reach. |
| Guest content is localized maps | All offered languages in one payload | System-context cross-cutting rule: a language switch fetches nothing. Templates are resolved per language server-side (checkout in the property timezone, each language's locale), so the kiosk still only ever sees finished text (spec §5). |
| Vacancy → named `409 room-vacant` | For `screens/home` and the preference write | Decision 9 (data-sparing): no stay-derived value leaves the server for a vacant room. The session still answers `200` (it carries the standby texts and settings config the standby screen needs). The kiosk's preference write is fire-and-forget with silent failure by design (system-context), so a `409` costs nothing client-side. |
| Guest asset reachability | Serve only assets in the device's resolved view; else `404` | Asset ids are sequential; without the check a device could enumerate every stored image. Logo, property default artwork, own-room artwork — nothing else. Cheap: `GuestControl` already resolves those ids for the session. |
| Preference write shape | `PUT /api/guest/preferences`, whole record `{language, appearance}`, both nullable | Mirrors `StoredPreferences` and the gateway's whole-record-write contract verbatim, so `guest-frontend-integration` maps 1:1. Stay-anchored (decided 2026-08-19): a new stay starts clean by construction; no explicit check-out cleanup needed. |
| No scheduler | Opportunistic cleanup | `quarkus-scheduler` is not on the allow-list and a pairing-request table with a handful of rows needs none: each create deletes expired rows first. |
| Rate limiting | **None — withdrawn 2026-08-24 (owner decision, REQ-409).** As built this was a global in-memory `PairingRateLimiter` (5 creates / 60 s), and the global counter was the bug: anyone on the LAN — or one clock-skewed kiosk retrying by itself (`guest-frontend-integration` 06-review 🟡 #3) — could keep a real kiosk from ever getting a code. A paced admission gate replaced it the same day and was removed with it. | Guessing stands on entropy + TTL alone: 32⁸ ≈ 1.1 × 10¹² codes, 10-minute TTL, and a flooder reaches neither `confirm` (admin-only) nor `poll` (256-bit secret). Poll needed no limit for the same reason. **Consequence:** live rows are unbounded in count, bounded in age only — by the TTL and the opportunistic cleanup on create. |
| `GXP-Token-Expires` | Manager identities only | Device tokens are long-lived and don't slide; `TokenExpiryResponseFilter` skips device identities (it already reads an identity attribute — the device identity simply doesn't carry one). |

## Pinned parameters

| What | Pin |
|---|---|
| Display code | 8 chars from a 32-char unambiguous alphabet (Crockford-style, no `0/O/1/I`), ~40 bits; unique among live requests |
| Poll secret | 32 bytes `SecureRandom` → base64url (no padding); stored as SHA-256 hex; presented via header `GXP-Pairing-Secret` on the poll |
| Pairing TTL | **10 minutes** (spec §6 "short-lived"); expired rows deleted opportunistically on each create |
| Pairing rate limit | **none** (removed 2026-08-24) — create is unthrottled; the 10-minute TTL + opportunistic cleanup on create are what bound the table |
| Device token | 32 bytes `SecureRandom` → base64url; stored SHA-256 hex — identical treatment to manager tokens |
| Device name default | `Kiosk <room code>` when the manager supplies none at confirm; rename any time |
| `last_seen_at` throttle | ≥ **60 s** between writes per device (in-memory last-write map) |
| Guest JSON cache | `ETag: "<sha-256 over the response bytes>"` (strong), `Cache-Control: no-cache` (revalidate always — the ETag does the saving) |
| Guest asset cache | `ETag = stored sha-256`, `Cache-Control: private, max-age=2592000` (asset content per id is immutable — a replacement is a new id) |
| Screen keys (v1) | `home` only; the contract models `/api/guest/screens/home` as a concrete path (decision 7: a new screen is a new path, additive) |
| Settings chrome constants | `id=einstellungen`, icon `phosphorGearSix`, route `/einstellungen`, labels `{de: "Einstellungen", en: "Settings"}` — verbatim from `example-property.data.ts`; server-side constants, not DB |

*Verify-at-implementation* (names/mechanics, not intent): the exact Quarkus
TLS property set on 3.33 (`quarkus.http.ssl.certificate.*` vs the newer
`quarkus.tls.*` registry) and `quarkus.http.insecure-requests` semantics;
where the guest ETag is computed (a `ContainerResponseFilter` sees the entity
pre-serialization — if hashing serialized bytes needs a `WriterInterceptor`,
that is the same infrastructure-adapter category as `ResponseStatusFilter`);
whether `SecurityIdentity` attributes flow to a `ContainerResponseFilter` for
the expiry-header skip (they did for `GXP-Token-Expires`).

## Architecture — BCE packages

> **Amended 2026-08-25 (owner decision D1, constitution v1.6.0) — read the
> block below as the FEATURE map, not the package map.** Both packages have
> since been split into sub-packages. `de.gxp.device` is now
> `boundary.{PairingResource, DeviceAdminResource}` /
> `control.{DeviceControl, DeviceRepository, PairingRequestRepository,
> PairingCodes}` / `entity.{Device, PairingRequest, DeviceMapper}`, and
> `de.gxp.guest` is `boundary.GuestResource` /
> `control.{GuestControl, GuestEtagFilter}` — it owns no table, so it has no
> `.entity` package at all, and is one of the two features named as that
> exception in the architecture guards (`de.gxp.text` is the other). The
> two-Boundary exception fits the layout unchanged: both Boundaries sit in
> `de.gxp.device.boundary` over the single public
> `de.gxp.device.control.DeviceControl`.
>
> **"(all package-private)" on the last device row still holds** for
> `DeviceRepository`, `PairingRequestRepository` and `PairingCodes` — that is
> why they moved into `.control` — but **not** for `DeviceMapper`, which went
> to `.entity` and had to become `public`, as did `Device` and
> `PairingRequest` and every one of their fields. Java has no sub-package
> visibility; entity fields were widened one at a time and these two
> entities happened to need all of theirs, because `DeviceControl` drives the
> whole pairing and revocation lifecycle across the split.
>
> **`TokenIdentityProvider` was the risk in this move and was checked at
> runtime, not by compilation:** it lives in `de.gxp.auth`, injects
> `DeviceControl` *during* authentication, and Quarkus discovers it as an SPI
> provider rather than by ordinary injection. It is now package-private in
> `de.gxp.auth.control`, and `DeviceAuthTest`, `AuthResourceTest`,
> `PairingFlowTest` and `PairingCaTest` all pass unchanged — discovery is by
> implemented interface, not by package. Same result for
> `BearerAuthMechanism` and `TokenExpiryResponseFilter`.

```
de.gxp.device   Device, PairingRequest (Entities) | DeviceControl (the package's ONE public
                Control: pairing lifecycle, device CRUD/revoke, token verification for auth)
                | PairingResource (Boundary, tag `pairing` — unauthenticated kiosk ops)
                | DeviceAdminResource (Boundary, tag `manager-devices` — confirm/list/rename/revoke)
                  (two Boundaries over one Control — approved 2026-08-19, CLAUDE.md amended)
                | DeviceRepository, PairingRequestRepository, DeviceMapper,
                  PairingCodes (all package-private)
de.gxp.guest    GuestResource (Boundary, tag `guest`; @RolesAllowed("device") class-wide since
                2026-08-24 — D2, see the security bullet below) | GuestControl (public
                coordinating read model + the one preference write)
                | GuestEtagFilter (infrastructure adapter — strong ETag / If-None-Match → 304
                  for /api/guest/** JSON; sits beside the package, carries no business logic)
de.gxp.auth     EXTENDED, not restructured: TokenIdentityProvider resolves manager store first,
                then DeviceControl.verifyToken(hash) → SecurityIdentity(role=device,
                attributes deviceId/roomId). BearerAuthMechanism unchanged.
                TokenExpiryResponseFilter: skips device identities.
existing        PropertyControl / RoomControl / MenuControl / StayControl / AssetControl gain
                package-public guest-read methods (marked @PermitAll until 2026-08-24 — D2
                removed every Control annotation; see the security bullet below);
                StayControl additionally writePreference(roomId, lang, appearance).
```

- **Cross-feature injection (public Controls only):** `GuestControl` →
  `PropertyControl`, `RoomControl`, `MenuControl`, `StayControl`,
  `AssetControl`, `TemplateControl` (the spec-§4-sanctioned composition
  point); `TokenIdentityProvider` (auth) → `DeviceControl`;
  `DeviceControl` → `RoomControl` (room existence at confirm);
  `RoomControl` → `DeviceControl` (active-device delete guard, REQ-408 —
  mirror of the existing stays guard; **not** circular with the above:
  `DeviceControl` uses `RoomControl` only for reads, and dependency cycles
  between beans are legal in CDI via client proxies — but to keep the graph
  reviewable, the delete guard reads via a package-private native count query
  on the `device` table instead, the exact deviation-5 precedent from
  domain-config).
- Every public Control method carries exactly one security annotation:
  `DeviceControl` pairing ops `@PermitAll` (unauthenticated by design, and
  since 2026-08-24 unthrottled), manager ops `@RolesAllowed("admin")`,
  `verifyToken` `@PermitAll` (invoked during authentication, pre-identity);
  `GuestControl` all `@RolesAllowed("device")`; new guest-read methods on
  existing Controls `@PermitAll` (not HTTP-mapped — no Boundary routes them).
  - **Amended 2026-08-24 (owner decision D2, constitution v1.6.0):** the whole
    bullet moved one layer out. Security annotations now sit on the
    **Boundary** and Controls carry none — `PairingResource` `@PermitAll`
    class-wide, `DeviceAdminResource` `@RolesAllowed("admin")` class-wide,
    `GuestResource` `@RolesAllowed("device")` class-wide — with
    `quarkus.security.jaxrs.deny-unannotated-endpoints=true` failing an
    unannotated endpoint closed globally. The roles and the resulting wire
    behaviour are unchanged; the two `@PermitAll`-on-internal readings above
    (`verifyToken`, the guest-read methods) simply stop existing, which was the
    point — `verifyToken` in particular runs BEFORE any identity does.
- All non-2xx thrown from Controls via `de.gxp.api.ApiErrors`
  (`404 pairing-not-found` — one shared code for unknown/expired/claimed/
  wrong-secret, deliberately; `409 pairing-already-confirmed`,
  `409 room-has-devices`, `409 room-vacant`, `400 value-not-offered`,
  `404 screen-not-found`, `404 asset-not-found`). **No 429 is produced
  anywhere** since the rate limiter was withdrawn on 2026-08-24.

### Pairing flow (sequence)

```
Kiosk                            Backend                          Manager
  | POST /api/pairing/requests     |                                 |
  |------------------------------->| create(code, secretHash, exp)   |
  |  201 {code, pollSecret,        |                                 |
  |       expiresAt}               |                                 |
  |  [displays code]               |    POST /api/manager/pairings/{code}/confirm {roomId, name?}
  |                                |<--------------------------------|
  | GET /api/pairing/requests/{code}                                 |
  |   GXP-Pairing-Secret: <secret> |  204                            |
  |------------------------------->|                                 |
  |  202 (pending)  ... poll ...   |                                 |
  |------------------------------->| confirmed? mint token,          |
  |  200 {deviceToken, roomCode,   | insert Device(hash), claim      |
  |       deviceName}   [ONCE]     | (single @Transactional)         |
  |------------------------------->|                                 |
  |  404 (claimed — and identical for unknown/expired/wrong-secret)  |
```

Claim atomicity: the `UPDATE pairing_request SET claimed_at = now() WHERE id
= ? AND claimed_at IS NULL` guard (or the row lock) makes two racing polls
yield exactly one `200`.

### Device authentication (extension of the settled flow)

```
any request with Authorization: Bearer t
  BearerAuthMechanism (unchanged) → TokenIdentityProvider:
    1. TokenStore.slide(sha256(t))        → hit: identity{role=admin}          (unchanged)
    2. DeviceControl.verifyToken(sha256(t)) → active device:
         identity{role=device, attrs: deviceId, roomId} + throttled last-seen touch
    3. miss/revoked → 401 + Bearer challenge (unchanged channel)
```

Revocation is immediate by construction: verification reads the row
(`revoked_at IS NULL`) on every request. Device lookup is one indexed query
by `token_hash` (unique) — the per-request cost the in-memory manager store
avoids is acceptable here and buys instant revocation, which the constitution
requires ("revocable from the manager UI").

### Guest read model

`GET /api/guest/session` — `GuestControl.session(deviceIdentity)`:

1. `roomId` from the identity (never from the request — REQ-411).
2. Compose via the owning Controls' guest reads (`@PermitAll` until D2 removed
   every Control annotation on 2026-08-24): property snapshot (name, brand, default
   appearance, logo id, offered options, translations), room (code, name,
   artwork id, overrides), active stay (or vacant), resolved menu
   (`MenuControl` — the ceiling implementation itself, REQ-417), standby
   texts resolved per offered language (`TemplateControl`,
   `{property.name}` only).
3. Preference resolution: stay override → room default → property default —
   returned both resolved (`language`, `appearance`) and as the raw override
   (`preference{language?, appearance?}`) so the kiosk's
   `PreferencesService` keeps its default-⊕-choice model.
4. Occupied → `guestDisplayName`; vacant → occupancy `VACANT`, no name, no
   override.
5. Menu block: `items[]` (resolved-enabled only, resolved sort/label/icon +
   catalogue route) + `settingsEntry` (fixed chrome constants — REQ-418).

`GET /api/guest/screens/home` — `GuestControl.homeScreen(identity)`:
vacant → `409 room-vacant`; else property translations
(headline/subtitle/paragraph as localized maps), artwork id (room → property
fallback), and the resolved card set: `cards(scope)` per the settled
wholesale rule (room rows if ≥ 1 else property rows), each card's
title/description templates resolved per offered language against the active
stay, plus `targetKey` and the catalogue route. A card whose target resolves
disabled cannot exist (invariants 2/3 + ceiling guards) — the guest side
still skips defensively rather than 500ing if it ever meets one.

`GET /api/guest/assets/{assetId}` — `GuestControl.asset(identity, id,
ifNoneMatch)`: id ∈ {property logo, property artwork, own room artwork} else
`404`; then delegate to `AssetControl`'s existing thrown-`Response` binary
path (200-with-ETag / 304).

`PUT /api/guest/preferences` — `GuestControl.writePreference(identity, dto)`:
values validated against the property's offered sets (`400`), vacant →
`409 room-vacant`, else `StayControl.writePreference(...)` on the active stay.

### ETag for guest JSON

`GuestEtagFilter` (infrastructure adapter, `de.gxp.guest.control` since
2026-08-25 — package-private there, D1): for
`/api/guest/**` JSON responses, compute `sha-256` over the serialized
response bytes → strong `ETag`; on `If-None-Match` equality answer `304`
with an empty body. Deterministic because generated DTOs serialize with a
fixed property order and localized maps are `LinkedHashMap`s built in offered-
language order. Exact interception point is *verify-at-implementation* (see
above); the contract-visible behavior (strong ETag, `304`) is pinned.

## TLS (constitution: instance CA, HTTPS on the LAN) — decided 2026-08-19

**What is settled** (constitution v1.4.0/1.5.1, spec decision 18, §9): the
instance generates **its own CA** and a CA-signed **server certificate** on
first start; the CA is distributed to kiosks at pairing; all LAN traffic is
HTTPS; no internet, no ACME, no manual certificate work on site. The CA
indirection (rather than one self-signed server cert) is what lets the server
certificate be re-issued — new host/IP, parameter bump — without touching a
single paired kiosk's trust store.

**Decided mechanics** (user, 2026-08-19 — resolved decision #1/#2): a
first-start init step in the app container **entrypoint** using `keytool`
(ships in the JVM base image — no new dependency, no second image, plain
reviewable shell): generate CA keypair (self-signed,
`BasicConstraints CA:true`, **valid 20 years**), server keypair + CSR
(**EC P-256**), sign with SAN = `GXP_TLS_HOST` (**valid 10 years**), export
the CA certificate as PEM; write a PKCS12 keystore + a generated keystore
passphrase file to a dedicated named volume (`gxp-tls`), handed to Quarkus
via config/env before `exec java`. Idempotent: material present and SAN
matches → reuse; SAN mismatch → re-issue the **server cert only** under the
existing CA. Rejected alternatives, recorded: BouncyCastle (a new dependency
to patch in the field); `openssl` (tooling the UBI-minimal-based JVM image
does not carry).

**Config posture** (user, 2026-08-19 — resolved decision #3): `%prod` serves
HTTPS only — `quarkus.http.insecure-requests=disabled`, keystore from the
volume; compose publishes host `443` → container `8443`. No HTTP listener,
no redirect (a redirect still invites the first credential over HTTP).
`%dev` and `%test` stay plain HTTP — mirroring the `%prod`-only `/q/openapi`
lockdown: config-review + compose smoke prove the `%prod` half, tests prove
everything profile-independent.

**CA distribution** (REQ-429): `GET /api/pairing/ca` returns the CA
certificate PEM (public material, unauthenticated — tag `pairing`, one-line
Boundary, `DeviceControl` reads the PEM from the TLS volume path). The
README's pairing workflow says plainly: a person installs it in the kiosk's
OS/browser trust store during provisioning — before or at pairing; fetching
it over not-yet-trusted HTTPS is a documented accept-once/out-of-band step.

**Refuse-to-start** (user, 2026-08-19 — part of resolved decision #2):
`%prod` without `GXP_TLS_HOST` or without readable TLS material fails at
boot with a clear message (the `GXP_ADMIN_PASSWORD` posture; Quarkus itself
refuses when the configured keystore is absent — verified at implementation).

**Backup story** (REQ-431): one `pg_dump` **plus** the `gxp-tls` volume; the
documented consequence of losing the TLS volume is a regenerated CA and a
re-install on every kiosk — one line in the README, honestly stated.

## Data model (Flyway `V4__device_pairing.sql`; `V1`–`V3` untouched)

```sql
device (id bigserial PK,
        room_id bigint NOT NULL REFERENCES room ON DELETE RESTRICT,  -- guard in Control (REQ-408)
        name text NOT NULL CHECK (name <> ''),
        token_hash text NOT NULL UNIQUE,          -- sha-256 hex, never the raw token
        created_at timestamptz NOT NULL,
        last_seen_at timestamptz NULL,
        revoked_at timestamptz NULL)
pairing_request (id bigserial PK,
        code text NOT NULL UNIQUE CHECK (code <> ''),
        poll_secret_hash text NOT NULL,           -- sha-256 hex
        room_id bigint NULL REFERENCES room ON DELETE CASCADE,  -- set at confirm
        device_name text NULL,
        created_at timestamptz NOT NULL,
        expires_at timestamptz NOT NULL,
        confirmed_at timestamptz NULL,
        claimed_at timestamptz NULL)
```

Room deletion: Control pre-check → `409 room-has-devices` naming active
devices; rows of revoked devices are deleted in the same transaction before
the room row (the `RESTRICT` FK is the DB backstop, domain-config precedent).
No seeds — a fresh instance has zero devices by definition.

## API contract (added to `api/openapi.yaml`)

Tags: `pairing` (kiosk-facing, unauthenticated), `manager-devices` (admin),
`guest` (device) — one generated interface per Boundary, under the approved
two-Boundary convention (resolved decision #5, `backend/CLAUDE.md` amended).

| Op | Method & path | Auth | Success | Named errors |
|---|---|---|---|---|
| `createPairingRequest` | `POST /api/pairing/requests` | none | 201 `PairingRequestCreated{code, pollSecret, expiresAt}` | none — unthrottled since 2026-08-24, and the contract's 429 was removed the same day (see 06-review addendum) |
| `pollPairingRequest` | `GET /api/pairing/requests/{code}` (+ header `GXP-Pairing-Secret`) | none | 202 empty (pending) / 200 `PairingCompleted{deviceToken, deviceName, roomCode}` **once** | 404 `pairing-not-found` (unknown = expired = claimed = wrong secret) |
| `getPairingCa` | `GET /api/pairing/ca` | none | 200 `application/x-pem-file` | — |
| `confirmPairing` | `POST /api/manager/pairings/{code}/confirm` | bearer admin | 204 | 404, 409 `pairing-already-confirmed`, 400 unknown room |
| `listDevices` | `GET /api/manager/devices` | bearer admin | 200 `[DeviceDto]` (never any token material) | — |
| `renameDevice` | `PUT /api/manager/devices/{deviceId}` | bearer admin | 200 `DeviceDto` | 400, 404 |
| `revokeDevice` | `POST /api/manager/devices/{deviceId}/revoke` | bearer admin | 204 (idempotent) | 404 |
| `getGuestSession` | `GET /api/guest/session` | bearer device | 200 `GuestSession` + `ETag` / 304 | — |
| `getGuestHomeScreen` | `GET /api/guest/screens/home` | bearer device | 200 `GuestHomeScreen` + `ETag` / 304 | 404 `screen-not-found` (resolved-disabled), 409 `room-vacant` |
| `getGuestAsset` | `GET /api/guest/assets/{assetId}` | bearer device | 200 binary + `ETag` / 304 | 404 `asset-not-found` (unknown **or** unreachable) |
| `putGuestPreferences` | `PUT /api/guest/preferences` | bearer device | 204 | 400 `value-not-offered`, 409 `room-vacant` |

No guest operation carries a room/stay/device/property parameter (REQ-411 —
checkable on the contract itself).

Schema sketch (names final at contract writing; localized = language-keyed
map over the offered languages):

```
GuestSession {
  property { name, brand{primary, primaryDeep, secondary}, logoAssetId? }
  room { code, displayName }
  occupancy: OCCUPIED | VACANT
  guestDisplayName?                       // occupied only
  settings { languages[{code, shortLabel, label, locale}],
             appearances[{mode, icon}],
             defaultLanguage, defaultAppearance }   // resolved Room→Property defaults
  preference { language?, appearance? }   // the stay's raw override; absent when vacant
  menu { items[{key, label:localized, icon, route, sortIndex}],
         settingsEntry {id, label:localized, icon, route} }
  standby { headline:localized, message:localized }   // resolved templates
}
GuestHomeScreen {
  headline: localized, subtitle: localized, paragraph: localized,
  artworkAssetId?,
  actionCards[{icon, title:localized, description:localized,   // resolved, no braces
               targetKey, targetRoute}]
}
GuestPreferencesUpdate { language: string|null, appearance: string|null }  // whole record
DeviceDto { id, name, roomId, roomCode, createdAt, lastSeenAt?, revokedAt? }
```

## Risks & trade-offs

- **A DB read per authenticated guest request** (token verification) —
  deliberate: instant revocation beats caching a credential check; one
  indexed unique lookup at kiosk-polling rates is noise. The last-seen
  *write* is the throttled part.
- **Two Boundaries in one package** — the alternative (one mixed tag) would
  fuse unauthenticated kiosk ops and admin ops into one generated interface
  and one Boundary; the chosen shape keeps each Boundary a one-line adapter
  over the single public `DeviceControl`. **Approved 2026-08-19**; the
  `backend/CLAUDE.md` amendment (exact text in resolved decision #5) is
  written.
- **`@PermitAll` guest-read methods on manager Controls** widen the internal
  call surface. Bounded: they are read-only projections/resolutions, not
  HTTP-mapped, and the pattern is the review-blessed `TemplateControl` one.
  The annotation audit still enforces exactly-one-annotation.
  **Amended 2026-08-24 (D2):** this trade-off is retired — those methods now
  carry no annotation at all, because an internal call is not a trust boundary.
  What still bounds them is unchanged (read-only, not HTTP-mapped, no Boundary
  routes them); the exactly-one audit moved to the Boundaries
  (`BoundaryAnnotationAuditTest`).
- **ETag = hash of the full response** recomputes the payload on every poll
  (no server-side caching). Honest and correct-by-construction; at a few
  dozen kiosks polling occasionally this is well inside budget. A cache would
  add invalidation complexity for load nobody has.
- **TLS material on a volume** is a second thing to back up (documented) and
  a long-lived key on a physically accessible box (recorded in the PRD).
- **`sessionStorage` of the pairing poll secret / token on the kiosk** —
  client-side, `guest-frontend-integration`'s to design; noted as hand-off.
- **Clock dependence doubled** (occupancy + cert validity) — spec §11 item 6
  stays open at owner level; this design only *reads* the clock.

## Resolved decisions — approved verbatim by the user, 2026-08-19

All five recommendations were approved without change ("All five recommended
go ahead", relayed 2026-08-19 while the user granted overnight autonomy).
Recorded here as the binding record; the recommendations are now the design:

1. **TLS material generation** *(infrastructure)* — **first-start init in
   the app container entrypoint using `keytool`** (already in the JVM base
   image): CA + CA-signed server cert into the named volume `gxp-tls`
   (PKCS12 + passphrase file), reused on restart. No new dependency, no
   second image. Rejected alternatives recorded above (BouncyCastle = new
   dependency; openssl = second image; manual = contradicts spec §9).
2. **Certificate identity & parameters** — required env **`GXP_TLS_HOST`**
   (`%prod` refuses to start without it, `GXP_ADMIN_PASSWORD` posture);
   SAN = that host; keys **EC P-256**; **CA 20 years, server cert 10 years**;
   on host change the server cert is re-issued under the unchanged CA
   (kiosks keep trusting without re-pairing).
3. **HTTPS posture in `%prod`** — HTTPS **only**
   (`quarkus.http.insecure-requests=disabled`), compose publishes host `443`
   → container `8443`, no HTTP redirect; `%dev`/`%test` stay HTTP.
4. **`/api/system/info`** — restricted to `@RolesAllowed({"admin","device"})`
   (closes the foundation review's fingerprinting revisit note); `/health`
   and `/ready` stay public.
5. **Two Boundaries in one BCE package** — approved together with this exact
   `backend/CLAUDE.md` amendment, **written 2026-08-19**: *"Contract tags
   map **one tag to one Boundary** (so the generator emits exactly one
   interface per Boundary). A package normally has one Boundary; it may host
   a second **only** when one consistency unit spans two auth surfaces (e.g.
   `pairing` + `manager-devices` in `de.gxp.device`) — every Boundary stays
   a one-line adapter over the package's single public Control."*

Everything else in this design is settled ground, a recorded planner call,
or a pinned parameter. **No approval gate remains open**: no dependency, no
datastore change, no token-realisation change, no `CLAUDE.md` edit beyond
the approved amendment above. Should implementation contradict the
no-new-dependency claim, `dev` stops with ⏸ per the constitution.

## Implementation deviations — ratified at review (2026-08-19)

Recorded by `dev` in [handoff.md](handoff.md), judged against PRD/design at
`06_review` and accepted as the as-built truth (none touches the contract):

1. `DeviceControl` gained no `activeDeviceCountAndNames` — the room-delete
   guard reads via the package-private native query in `RoomRepository`
   (this design's own resolution; the T-05 method would have been dead API).
2. Room existence at confirm uses `RoomControl.lockForStayWrite` (400
   `unknown-room` + row held until commit); poll/list read the room *code*
   via a package-private native query in `DeviceRepository` (the claim path
   is unauthenticated, every `RoomControl` read is admin-annotated).
3. `resolveLocalized` falls back to the **main language** for a missing
   translation row — REQ-426's "exactly the offered keys" is met by filling
   the hole with honest untranslated text, never by omitting the key.
4. Host-change re-issue regenerates the server **keypair** (new key + cert
   under the unchanged CA) — strictly safer than re-signing the old key;
   the kiosks-keep-trusting property was proven in the compose smoke.
5. Tests outside `de.gxp.device` (any sub-package of it, since 2026-08-25)
   seed devices by direct DB insert
   (`testsupport/DeviceFixtures`, hash-only); the HTTP lifecycle is proven
   once in `PairingFlowTest`. (This originally protected the global create
   rate limit; that limit is gone since 2026-08-24, but seeding by insert
   remains the right call — it is a fixture concern, not a contract test.)
6. The last-seen-throttle test lives in `de.gxp.device` (package-private
   reset hook) — **since 2026-08-25 in `de.gxp.device.control`**, which is
   where the reset hook moved with `DeviceControl`; `PairingFlowTest` moved
   with it for the same reason (`PairingCodes.sha256Hex`). `PairingCaTest`
   drives HTTP only and stayed at the feature root.
7. The ceiling-parity test seeds the contradictory state via SQL — the
   manager API correctly refuses to create it; guest resolution must still
   ceiling the item out if the state ever exists.
8. `If-None-Match` is contract-modeled only on the asset op; the JSON ops'
   filter reads the standard header and the contract documents the 304s.
