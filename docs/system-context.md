# System Context

High-level map of existing components and cross-cutting concerns. Read this
before planning a new feature to spot related or conflicting work.

> **Status:** `frontend/` exists and is implemented — the
> `home-screen-prototype` and `appearance-and-language` features; everything
> below reflects real code, not only intent. Updated 2026-08-19 with
> `appearance-and-language`'s components, its two runtime axes and the one
> thing the app now persists.
>
> **Updated again 2026-08-19: the backend is decided and in flight.** The
> owner's decision interview is recorded in `docs/backend/specification.md`
> and ratified as constitution **v1.4.0**. Sections below marked *(decided
> 2026-08-19)* describe that decided-but-being-built state; the component
> tables still list only real code.
>
> **Updated 2026-08-20: the kiosk is wire-fed** (`guest-frontend-integration`).
> The static-content stage is over — `example-property.data.ts` and the
> `sessionStorage` preference stand-in are deleted, the guest app renders from
> `GET /api/guest/**`, pairs itself, degrades offline, and ships inside the
> backend jar (served at `/`, D7). References below to "static TS today" are
> kept as the historical record of those decisions.

## Deployment model

**Self-hosted, one instance per building.** Every hotel, apartment house or
holiday home runs its own server inside the building, serving **n room kiosks**
and one manager UI on site. There is no central multi-tenant service.

What this buys us, and what it costs:

| | |
|---|---|
| ✅ **Tenant isolation is physical — *between buildings*** | One instance holds one building's data; cross-*building* leakage is impossible. **Narrowed 2026-08-19:** inside a building the backend serves many rooms, so **room-level scoping is mandatory** — the room is derived from the authenticated device token, never from a client-supplied parameter, and every query touching room-scoped data is security-relevant (constitution v1.4.0, principle 4) |
| ✅ **No internet dependency** | The app keeps working when the property's connection is down — which is exactly when a guest needs the WiFi password |
| ⚠️ **No central patching** | A vulnerability persists until someone updates that box. This is the dominant long-term risk and the reason to keep the dependency count low. *Note 2026-08-25 (constitution v1.8.0):* remote operations is now a separately sold capability with an opt-in, box-initiated relay (`docs/backend/remote-operations.md`); it does not change this row for a box that never enrols, and the dependency-minimisation posture stays regardless. *Note 2026-08-26 (`update-channel`):* every box now pulls its own updates — hourly outbound check of a signed manifest, host-side updater applies at night with dump + rollback (`backend/README.md` → "Updates"). Patching is no longer manual, but it is still per-box and still depends on the operator publishing a signed release; the updater itself is not channel-updated |
| ⚠️ **Shared network** | The server sits on the same LAN as guest devices |
| ⚠️ **Physical access** | The touchscreen stands in a guest-accessible space |

Per-property configuration remains **data, not code** (constitution principle 1)
even though each property has its own instance — one build artifact, configured
per site. Building a bespoke image per customer would defeat reproducible
deployments and make patching harder, which is the thing this model is already
weakest at.

### Open security questions

Three of the five were **resolved on 2026-08-19** by the owner's backend
decisions (`docs/backend/specification.md`, constitution v1.4.0):

- ~~Does the app authenticate the guest at all?~~ — **Resolved: device
  pairing with an opaque token.** The kiosk is paired once by the manager and
  holds a long-lived token (stored hashed server-side, revocable). The token
  is identity *and* authentication: no valid token, no room data, and a device
  cannot change which room it claims to be. Sensitive content is served, never
  bundled.
- ~~HTTPS on the local network?~~ — **Resolved: yes, always.** The instance
  generates its own CA and server certificate on first start; the pairing flow
  installs the CA on the kiosk. No internet, no ACME, no manual certificate
  work on site.
- ~~How is the guest-preference endpoint secured?~~ (raised 2026-08-18 by
  `appearance-and-language`) — **Resolved by the same device token.** The
  preference write rides the authenticated guest API; the record's scope
  ("the screen", no identifiers) is unchanged — the stay-level language/theme
  override now has a `Stay` anchor server-side.

Still open, both operational rather than application code:

- `[NEEDS DECISION: how does the touchscreen get locked down?` Kiosk mode, no
  browser chrome, no navigating away, no developer tools. This is device
  provisioning rather than application code, but it is part of the security
  story and nobody else will do it.`]`
- ~~How do instances get updated, and who is responsible?~~ — **Resolved
  2026-08-26 by `update-channel`.** *How:* the app checks a signed manifest
  hourly (outbound only), the host-side updater applies it in the night
  window (default 04:00 local, manager-changeable, disable/apply-now via
  `/api/manager/updates`) with a `pg_dump` first, one restart, ready + Flyway
  health check and an automatic rollback ladder. *Who:* the release signer
  (the owner, private key off-repo — D17); the manager only chooses when.
  ~~Still open, handed to T-20: key rotation and the updater's own update.~~
  *T-20 (architect, 2026-08-26):* key rotation is built — every box trusts a
  **two-key set** (key 1 active, key 2 dormant) and a signed newer manifest
  can rotate it in-band; the updater's own update stays `install.sh` on site
  (tracked follow-up: refresh it from the verified image).

## Stack

Per `README.md` → "Erste Technische Richtung":

- **Angular + TypeScript**, standalone components
- **Angular Router** for the main areas
- **Signals** for local UI state
- Content initially **static JSON / TypeScript files** — no backend, no auth,
  no admin area in the first step
- Target: **touch-optimised web app** for tablet and touchscreen

**Backend: Java 21 + Quarkus — decided 2026-08-19, in flight.** No longer
"planned after the MVP": the full decision log (BCE packaging, Panache
repositories, contract-first OpenAPI with a generated TS client, PostgreSQL,
Flyway, Docker Compose, device pairing, admin bearer token, HTTPS with a
self-generated CA) lives in `docs/backend/specification.md` and is ratified in
constitution v1.4.0. The static JSON stage is explicitly temporary. Two things
follow:

- Constitution principle 2 (*content is reached through a service, never
  imported into a component*) is what makes this swap cheap. Components must
  never import a JSON file directly, however tempting it looks while the data is
  static — that shortcut is the one thing that would turn the backend migration
  into a rewrite of every component.
- Quarkus suits the on-premise model well: compiled to a native image it starts
  fast and holds a small memory footprint, which matters when the target is a
  modest box in a building rather than a cloud instance.

A backend also opens the natural answer to the open authentication question
below — access control can live server-side instead of relying on the LAN
boundary. It does not answer it for the MVP, which has no server-side logic.

**Two seams already exist for it (added 2026-08-18).** Appearance and language
are the first two settings deliberately shaped for that backend, and they mark
where it plugs in:

| Seam | Direction | What crosses it |
|---|---|---|
| `PropertyContentService` → `PreferencesService.applyPropertyDefaults()` | read | The property's configuration: which options are offered and what the defaults are. Static TS today; one service changes when it comes over the wire |
| `PreferenceGateway` (`core/preferences/`) | read **and** write | The guest's own choice. One token, one provider line, `sessionStorage` behind it today, an HTTP implementation later |

Nothing else in the app is a candidate for a server call, which is what keeps
the migration small.

**Both seams are CLOSED as of 2026-08-20** (`guest-frontend-integration`):
the first is fed by `GuestDataService` (one content fetch + ETag poll), the
second by `HttpPreferenceGateway` (`PUT /api/guest/preferences`; the stored
value arrives inside the content payload — no separate read). The principle-2
bet held: components changed only where the content's *shape* changed
(variable menu/card counts, server-resolved strings, object-URL images).

## Components

Introduced by `docs/features/home-screen-prototype/` (see that feature's
`02-design.md` for the full rationale). **Revised 2026-08-18**: the sidebar
and background moved into a shared, route-agnostic `layout/` area (they are
no longer Home-specific), and the generic placeholder was replaced by a
section-aware one:

| Component | Location | Responsibility |
|-----------|----------|-----------------|
| `PropertyContentService` | `frontend/src/app/core/content/` | Supplies per-property/guest content via DI (constitution principle 2), including sidebar `menuItems`/`settings` (the Einstellungen nav entry — **not** the settings *config*, which is the separate `settingsConfig` below); fed by `GuestDataService` from the wire since REQ-510, not by a static import. **Three signals:** `property()` resolves every localized field into the active language (falling back to the property's `defaultLanguage`), and `settingsConfig()` exposes the language-independent settings slice — the offered options and the property's defaults — which is what `PreferencesService.applyPropertyDefaults()` consumes; a third, `locale()`, resolves the active language's `Intl` locale from the property's own option list (falling back to the bare language code) and is what the sidebar's popover collates with. Validates the raw config at construction. *(`StalenessPillComponent` and `StatusBarComponent` still perform that same lookup inline; moving them onto `locale()` is a recorded follow-up.)* |
| `ShellComponent` | `frontend/src/app/layout/shell/` | App-wide shell rendered on every route: mesh-gradient background + `SidebarComponent` + `<router-outlet>` for the routed main content |
| `SidebarComponent` | `frontend/src/app/layout/sidebar/` | Config-driven nav with **5 fixed slots on every viewport**, plus 1 structurally-fixed `settings` entry (`MenuIcons`); active state from the current route, and it is the only component that decides it. At or below 5 delivered `menuItems` it renders exactly the nav that shipped — no trigger in the DOM at all. Above 5: ranks 1–3 fixed, slot 4 **rotating**, slot 5 an **overflow trigger** opening a `@angular/cdk/menu` popover (the `StatusBarMenuComponent` pattern, right of the trigger and vertically centred on it) holding every remaining section, alphabetically by the label in the active language. Choosing one — or reaching one by an action card, a deep link or browser back — promotes it into slot 4, so the active section is always visible in the sidebar. The promotion is **per-session view state**: never `localStorage`, never the wire. Panel spec: [`docs/design/screens-inhouse/menu-overflow.md`](design/screens-inhouse/menu-overflow.md) |
| `layout/sidebar/menu-split.ts` | `frontend/src/app/layout/sidebar/` | **A file, not a service.** Exported pure functions — the `resolveProperty` precedent — holding `splitMenu`, `routeMatches`/`itemForRoute`, `sortByLabel` and a memoised `collatorFor`. **The single place `SIDEBAR_SLOTS = 5` lives.** No injectable and no state beyond one module-level `Map<string, Intl.Collator>` |
| `HomeContentComponent` | `frontend/src/app/features/home/` | Home's main-content region only (heading block, hero artwork, 4 action cards, reserved empty widget-column space) — composed inside `ShellComponent`'s router-outlet, not the sidebar/background |
| `HeroArtworkComponent` | `frontend/src/app/features/home/hero-artwork/` | Renders the no-artwork fallback (no image; background now lives in `ShellComponent`) |
| `HeadingBlockComponent` | `frontend/src/app/features/home/heading-block/` | Subtitle, headline, paragraph — from `PropertyContentService`; property-level content, not per-guest |
| `ActionCardComponent` | `frontend/src/app/features/home/action-card/` | `GlassPanel` action card; navigates the server-resolved `route` the wire delivers (REQ-511) — there is no client-side id→route lookup — keyboard- and touch-activatable |
| `SectionPlaceholderComponent` | `frontend/src/app/features/section-placeholder/` | Shared main-content stub, rendered inside `ShellComponent`, for **every** section with no design yet. It is no longer "the 4 sections": `app.routes.ts` carries one `:sectionKey` child (declared **after** `einstellungen`, which keeps its real screen) plus a `**` fallback **as the shell's last child**, so every route the catalogue can name resolves by construction — including on a cold, offline boot, because the table is a static constant. The component reads the path reactively from `ActivatedRoute.url` (not `snapshot`, so a parameter change re-resolves under a reused instance) and matches the delivered menu **by `route`, not by key**, through `menu-split.ts`'s shared `itemForRoute()`. An unclaimed route renders a defined `section.unavailable` state — no redirect |

Added by `docs/features/appearance-and-language/` (2026-08-18). Same rule as
above: these are real files under `frontend/src/`, not intent.

| Component | Location | Responsibility |
|-----------|----------|-----------------|
| `TranslationService` | `frontend/src/app/core/i18n/` | Signal-based translation. `t(key, params?)` is an arrow-function property, so templates may hold it detached; keys are typed from the German catalogue, so a missing English string fails the build. Both catalogues are **compiled into the bundle** — nothing is fetched to render a language (constitution principle 4). Unknown key → default catalogue → the key itself |
| `PreferencesService` | `frontend/src/app/core/preferences/` | The **policy** layer for appearance and language: the property's defaults ⊕ the guest's override. Exposes `language()`, `appearance()` and `resolvedTheme()` (`'light' \| 'dark'`, the only place `prefers-color-scheme` is ever read, and only when a property configures the `system` option); `applyPropertyDefaults()`, `hydrate()`, `setLanguage()`, `setAppearance()`, `reset()`. Policy lives here and not in the transport, which is what makes swapping the transport a no-behaviour-change operation |
| `PreferenceGateway` (+ `SessionPreferenceGateway`) | `frontend/src/app/core/preferences/` | The **transport** contract for the guest's stored preference — `load()` / `save()` / `clear()`, all `Promise`-returning, whole-record writes, behind one injection token. `SessionPreferenceGateway` is the `sessionStorage` **stand-in** until the backend endpoint exists; it is *deleted*, not adapted, when the HTTP one lands. It is a **DI leaf**: it injects nothing, and must never reach for `PropertyContentService` |
| `ClockService` | `frontend/src/app/core/clock/` | A `now` signal that ticks on the **minute boundary** (a `setTimeout` to the boundary, then a `setInterval`), torn down via `DestroyRef`. Zoneless change detection cannot see a bare `setInterval`, so the clock has to be a signal |
| `StatusBarComponent` | `frontend/src/app/layout/status-bar/` | App chrome on **every** route: language trigger, appearance trigger (sun/moon by resolved theme), hairline divider, clock as `<time [attr.datetime]>`. Positioned absolutely in the content area's top-right corner rather than in the flow, so Home's measured geometry does not move. `:host { display: contents }`; deliberately **no** `role="toolbar"` — that role promises roving `tabindex` and arrow traversal, which this does not implement |
| `StatusBarMenuComponent` | `frontend/src/app/layout/status-bar/status-bar-menu/` | One generic CDK dropdown (`CdkMenuTrigger`/`CdkMenu`/`CdkMenuItem`) used by both status-bar triggers: takes options, the selected value and the trigger content, emits a selection. Renders into the CDK **overlay container** on `document.body`, not inside the shell |
| `SettingsComponent` | `frontend/src/app/features/settings/` | The `/einstellungen` screen (took that route from `SectionPlaceholderComponent`): screen title plus two glass panels, *Sprache* and *Darstellung*. Reads no route data |
| `SettingChoiceComponent` | `frontend/src/app/features/settings/setting-choice/` | One setting: label, hint and a row of **all-visible** option buttons, each at least the minimum touch target. Two controls per setting is intentional — compact in the status bar, everything visible in Settings, both bound to the same signals |

Full rationale for the eight rows above:
`docs/features/appearance-and-language/02-design.md`.

Added by `docs/features/backend-foundation/` (2026-08-19) — **the backend's
first real code**, same rule as above (real files, not intent):

| Component | Location | Responsibility |
|-----------|----------|-----------------|
| `api/openapi.yaml` | repo root `api/` | **The API contract — source of truth.** OpenAPI 3.1; tag `system` with `getSystemHealth`/`getSystemReadiness`/`getSystemInfo` plus the shared `ApiError` shape. The Maven build generates both sides from it; editing it is how any API change starts |
| Quarkus backend | `backend/app/` | Java 21, Quarkus 3.33 LTS (3.33.3.1), Maven wrapper, dependencies exactly the constitution v1.5.0 allow-list. Flyway `V1__foundation_baseline.sql` (single-row `instance_info` + seed) migrates at startup; Dev Services provisions `postgres:17.11` for dev/test; `%prod` reads the datasource from env vars. Fail-closed security (`quarkus.security.jaxrs.deny-unannotated-endpoints=true` — global, build-time, on the Boundary since v1.6.0; `deny-unannotated-members=true` stays on as a second line but is inert for the shipped app now that no Control carries an annotation) and no-SQL/no-access logging committed for all profiles |
| `de.gxp.system` | `backend/app/src/main/java/de/gxp/system/` | **The BCE reference package** every later feature copies (spec §4 strict), and the first one migrated to the v1.6.0 sub-package layout: `boundary/SystemResource` (one delegating line per method, implements generated `SystemApi`, carries the security annotation), `control/SystemControl` (public Control, `@Transactional`; carries **no** security annotation since v1.6.0, speaks generated DTOs, throws the 503) and package-private `control/InstanceInfoRepository`, `entity/InstanceInfo` + `entity/InstanceInfoMapper` (hand-written; **public types, but their fields stayed package-private** — nothing outside `.entity` reads them). Every feature package now carries `boundary`/`control`/`entity`; the feature boundary is enforced by the five `de.gxp.architecture` guards, not by the compiler |
| `packages/api-client/` | repo root `packages/` | **Generated** TypeScript-Angular client — committed, never hand-edited, deterministic (no diff on unchanged contract); a diff there means the contract changed. Uses only `@angular/common/http` + `rxjs`; no npm manifest changed anywhere. Not yet consumed — that is `guest-frontend-integration`'s |
| Compose unit | `backend/compose.yaml` | Deployment unit: `app` (JVM image) + `db` (`postgres:17.11`), named volumes `gxp-pgdata` + `gxp-tls` (backup = one `pg_dump` **plus** the TLS volume), required interpolation for `GXP_DB_PASSWORD`/`GXP_TLS_HOST` (fails fast when unset); `GXP_ADMIN_USERNAME`/`GXP_ADMIN_PASSWORD` optional in compose (first start only, the seeder refuses a fresh boot without the password); `GXP_TRUSTED_PROXY_ENABLED`/`GXP_TRUSTED_PROXY` default off / `127.0.0.1/32`. **Production-shaped since `device-pairing-and-guest-api`: HTTPS only, host 443 → container 8443, no plain listener, no redirect** |

Added by `docs/features/backend-domain-config/` (2026-08-19) — the domain
model, every spec §5 invariant server-side, and the secured manager CRUD API
(same rule: real files, not intent):

| Component | Location | Responsibility |
|-----------|----------|-----------------|
| Manager contract | `api/openapi.yaml` (tags `manager-*`) | Auth (`managerLogin` with `username`/`password`/optional `totpCode`/`recoveryCode`, `429` + `Retry-After`, `managerLogout`, `changeManagerPassword`), **`manager-users`** (list/create/deactivate/reactivate/password reset/second-factor reset; own second-factor enrol/confirm/status/disable + recovery-code regeneration), **`manager-audit`** (`listAuditEntries`, paged), property, rooms, menu catalogue (read-only) + menu-config + action-cards (both scopes), stays, assets, devices — every operation except login under the `bearerAuth` scheme (54 secured operations pinned by `ContractSecuredOperationsDeniedTest`). One generated interface per `manager-*` tag keeps one Boundary per BCE package |
| `de.gxp.auth` | `backend/app/src/main/java/de/gxp/auth/` | Manager **sessions** only (auth-hardening A2, 2026-08-26): `AuthResource`/`AuthControl` (`login`/`logout`/own password change, no `@Transactional` on login — the one DB write of a login commits inside `UserControl.verifyLogin`), package-private `TokenStore` (in-memory, SHA-256 hashes, 600 s sliding idle, `removeAllOf(username)` on the `UserControl.CredentialsInvalidated` CDI event), **`LoginThrottle` per attributed address** (reopened REQ-326 on 2026-08-25: 5 failures / 15 min → block 60 s doubling per exhaustion, cap 900 s, success clears, ≤ 10 000 tracked addresses evicted after 1 h; `429 too-many-attempts` + `Retry-After` BEFORE any PBKDF2 or DB read; never global, never per account — the withdrawn 2026-08-24 mechanisms answered the correct password with 429), the **TOTP-via-proxy rule** (a via-trusted-proxy login without `totpCode`/`recoveryCode` → `401 second-factor-required` before password verification and uncounted; with one, password + factor verified together, every miss the generic `invalid-credentials`; on the LAN the code fields are ignored), and the SPI adapters `BearerAuthMechanism`/`TokenIdentityProvider`/`TokenExpiryResponseFilter` (`GXP-Token-Expires` on every authenticated response). Logs nothing on the login path (REQ-605, asserted) |
| `de.gxp.user` | `.../de/gxp/user/` | Manager **accounts** (A2/A3): `manager_user` (V5 — the pre-existing `admin_credential` row is carried over as active user `admin`; `FirstUserSeeder` creates the first account from `GXP_ADMIN_USERNAME` (default `admin`) / `GXP_ADMIN_PASSWORD` only when no row exists, refuse-to-start otherwise), `Pbkdf2PasswordHasher` (moved here; JDK, 600k iterations, self-describing format, dummy-hash verification for unknown/deactivated names so all three misses cost one derivation), `UserResource`/`UserControl` (`manager-users`: create/deactivate/reactivate/reset password/reset factor by an admin — never on yourself, `409`; own enrol/confirm/status/disable/regenerate), TOTP RFC 6238 SHA-1/6 digits/30 s ±1 step with a per-user last-step replay guard and 10 SHA-256-at-rest recovery codes (V6), both consumed by **conditional UPDATEs** (one affected row → a code presented concurrently twice is accepted once). `Verdict(ok, username, matchedUsername)` — the matched name feeds the audit only, never the client. Every account change fires `CredentialsInvalidated(username)` (D14) |
| `de.gxp.audit` | `.../de/gxp/audit/` | Append-only **audit trail** (A4, V7 `audit_entry`: occurred-at, actor nullable, attributed address, via-proxy, action, entity, entity id nullable, field nullable — **no value column exists, by schema**): `AuditControl.record(...)` called EXPLICITLY at all 25 manager mutation sites (`AuditCoverageTest` derives the set from every `@Transactional public` Control method against a pinned exemption list — no interceptor, every call site greppable), one row per changed field on update (`changedFields` = generated-DTO getter diff, names only) or one field-less row on create/delete/replace, in the caller's transaction (a rolled-back mutation leaves none); login outcomes in `REQUIRES_NEW` (success = username as actor + entity id; failure = matched canonical username or null, never the submitted text; the `429` and the code-less via-proxy `second-factor-required` 401 are never recorded — unthrottled paths must not write, 06_review 2026-08-26). `AuditResource` `GET /api/manager/audit?page&size` (admin, newest first, default 50, max 200, no update/delete). Retention 365 days via `AuditScheduler` |
| `ClientAttribution` | `.../de/gxp/infrastructure/http/ClientAttribution.java` | Request-scoped, the **one** place the transport fact is read (constitution v1.8.0 allows exactly two consumers: the throttle bucket and the second-factor requirement): `Attribution(address, viaTrustedProxy)` from Quarkus `quarkus.http.proxy.*` — with `GXP_TRUSTED_PROXY_ENABLED=false` (default) every `Forwarded`/`X-Forwarded-*` header is ignored and the socket peer is the address; with it on, only the standard `Forwarded` header (`allow-x-forwarded` and `use-proxy-protocol` pinned off) and only from `GXP_TRUSTED_PROXY` (default `127.0.0.1/32`; a LAN range here would turn the throttle into a weapon on that range) is honoured, and `X-Forwarded-Trusted-Proxy: true` as stamped by Quarkus is the via-proxy signal. The relay must REPLACE the header with one `for=` element |
| `de.gxp.text` | `.../de/gxp/text/` | `TemplateControl` — stateless raw-template validation (unknown/out-of-context placeholder → named 400) and server-side resolution (`\|time`/`\|date` in the property timezone + language locale). Consumed by property/menu now, by the guest API next feature |
| `de.gxp.property` | `.../de/gxp/property/` | The single property row (`CHECK (id = 1)`, no create/delete API) incl. offered languages/appearances, brand primitives, asset refs (kind-checked) and raw greeting/standby templates |
| `de.gxp.room` | `.../de/gxp/room/` | Rooms CRUD; unique non-empty code, overrides validated against the property's offered sets; delete = 409 naming stays, cascades the room's own config rows; hosts the per-room row lock stays serialize on |
| `de.gxp.menu` | `.../de/gxp/menu/` | ONE consistency unit on purpose: catalogue reads (5 seeded kiosk sections; `einstellungen` deliberately not in it), per-field menu-config inheritance (room → property → catalogue default) — **except `enabled`, which resolves under the CEILING rule** (user decision 2026-08-19: `enabled(room, k) = enabled(property, k) AND room-override(default true)`; a property disable wins over any room enable — the guest API MUST resolve the same way), whole-set action cards (0–4), and invariants 1–3 incl. the affected-scope 409 listing every stranded card plus the ceiling guards (`item-disabled-at-property`, `menu-item-enabled-by-rooms`) |
| `de.gxp.stay` | `.../de/gxp/stay/` | Stays CRUD; `BOOKED`/`CANCELLED` stored, "active" derived from the clock; invariant 5 (no overlap per room) under the room-row lock — concurrent writes serialize |
| `de.gxp.asset` | `.../de/gxp/asset/` | PNG/JPEG only (content-verified by decoding), 10 MiB cap → 413, > 2560 px downscaled, sha-256 = strong ETag (304), payload-free metadata projection, delete guard naming referrers |
| `de.gxp.infrastructure.http` (formerly listed as `de.gxp.api`) | `.../de/gxp/infrastructure/http/` | Infrastructure: `ApiErrorMappers` (bean-validation + malformed JSON → the shared `ApiError` shape) and `ApiErrors` (the thrown non-2xx builders every Control uses) |
| Flyway V2/V3 | `.../db/migration/` | `V2__domain_config.sql` (all domain tables + checks + `UNIQUE NULLS NOT DISTINCT` scopes + neutral seeds), `V3__admin_credential.sql` (single-row, seeded by code, not SQL). `V1`/`instance_info` untouched. **auth-hardening (2026-08-26):** `V5__manager_user.sql` (carries the credential row over as user `admin`, drops `admin_credential`), `V6__second_factor.sql` (TOTP columns + `manager_recovery_code`), `V7__audit_entry.sql` |

Added by `docs/features/device-pairing-and-guest-api/` (2026-08-19) — device
identity, the kiosk-facing guest read model, and HTTPS with the instance CA
(same rule: real files, not intent):

| Component | Location | Responsibility |
|-----------|----------|-----------------|
| Pairing/guest contract | `api/openapi.yaml` (tags `pairing`, `manager-devices`, `guest`) | 11 new operations: pairing create/poll/CA (unauthenticated by design), confirm/list/rename/revoke (admin), session/screens-home/asset/preferences (device). **No guest operation carries any room/stay/device/property parameter** — checkable on the contract itself (REQ-411). `pairing` + `manager-devices` are the one approved two-Boundaries-over-one-Control package (`backend/CLAUDE.md` amendment) |
| `de.gxp.device` | `.../de/gxp/device/` | One consistency unit: `Device`/`PairingRequest` entities, the single public `DeviceControl` (pairing lifecycle incl. the claim-once transaction, device CRUD/revoke, `verifyToken` for auth), TWO Boundaries (`PairingResource` kiosk-facing, `DeviceAdminResource` admin-facing). Two-credential pairing (display code = human channel, 256-bit poll secret = machine channel); every miss is ONE indistinguishable 404; tokens/secrets stored SHA-256-hex only; create unthrottled since 2026-08-24 (live rows bounded by the 10-minute TTL and the opportunistic cleanup on create, not by a budget) |
| `de.gxp.guest` | `.../de/gxp/guest/` | `GuestControl` — THE spec-§4 coordinating read model (room exclusively from the identity; the `@RolesAllowed("device")` sits on `GuestResource` since v1.6.0, and the guest-reads it composes no longer need the `@PermitAll`-on-internal wart that used to be required) composing the guest-reads of the owning Controls: menu via `MenuControl`'s **ceiling** implementation (never re-implemented), occupancy via `StayControl`, templates via `TemplateControl` (resolved per offered language, property timezone — no braces leave the server), `einstellungen` served as fixed chrome outside `menuItems`. Since `guest-payload-consolidation` (Part B) there is **one guest content resource** — `GET /api/guest/content` (`GuestContent` with a nested, occupied-only `home`); `GET /api/guest/session` and `GET /api/guest/screens/home` are gone, and with them the `409 room-vacant` race and the `404 screen-not-found` branch. Vacancy is data-sparing by construction: the one authoritative 200 simply carries no stay-derived half (the preference write still answers the named `409 room-vacant`). `home` is a **mandatory menu item** (`MenuControl.MANDATORY_KEYS`) — always enabled, `sortIndex` forced to 0, a config write refusing it with `400 menu-item-mandatory`, legacy rows neutralised by `V11`. Asset reachability = {logo, property artwork, own-room artwork}, anything else the same 404. **`GuestEtagFilter` is deleted:** `GuestControl.content(ifNoneMatch)` throws the `304`/`200` itself (the `getGuestAsset` pattern), and the ETag is a **version stamp** — `GuestContentVersion` = `sha256(bootId | max(audit_entry.id) | roomId | stay fingerprint | 300 s bucket)` — so a `304` costs no resolve-and-serialise |
| `de.gxp.auth` (extended) | `.../de/gxp/auth/` | Still ONE bearer path: `TokenIdentityProvider` resolves manager store first, then the device table by hash (role `device`, deviceId/roomId identity attributes, throttled ≥60 s `last_seen_at` touch). Revocation is effective on the very next request (row read per request — deliberate). Device identities carry no expiry attribute, so `GXP-Token-Expires` stays manager-only. `/api/system/info` now `@RolesAllowed({"admin","device"})` (decided 2026-08-19) |
| TLS init | `backend/app/src/main/docker/entrypoint-jvm.sh` | First start: `keytool` (in the JVM image — zero new dependencies) generates the instance CA (EC P-256, 20 y) + CA-signed server cert (EC P-256, SAN = `GXP_TLS_HOST`, 10 y) onto the `gxp-tls` volume; restarts reuse; host change re-issues the server cert under the UNCHANGED CA. `%prod` = HTTPS only (`quarkus.http.insecure-requests=disabled`, TLS-registry keystore config); `%dev`/`%test` stay plain HTTP. `GET /api/pairing/ca` serves the CA PEM for kiosk provisioning (a documented human install step) |
| Flyway V4 | `.../db/migration/V4__device_pairing.sql` | `device` (token hash UNIQUE, `revoked_at`, room FK RESTRICT) + `pairing_request` (code UNIQUE, secret hash, `confirmed_at`/`claimed_at`). No seeds — a fresh instance has zero devices |

Added by `docs/features/guest-frontend-integration/` (2026-08-20) — the kiosk
consumes the guest API end to end, the three state screens exist, and the
production jar serves the app itself (same rule: real files, not intent).
**The two backend seams above are now CLOSED**: `PropertyContentService` is
wire-fed and `PreferenceGateway`'s stand-in was deleted for the HTTP
implementation, exactly as their designs promised.

| Component | Location | Responsibility |
|-----------|----------|-----------------|
| `DeviceTokenStore` | `frontend/src/app/core/api/` | The kiosk's own credential: the opaque device bearer in `localStorage` (`gxp.device-token`, D4) as a signal. Set exactly once per pairing claim, wiped on any guest-call 401 (REQ-509). Read by exactly one consumer — the `Configuration.bearerAuth` credential fn in `provide-guest-api.ts` (resolved per request). The dev fallback (`environment.development.ts`, D3) is consulted only when storage is empty and is grep-provably absent from a production bundle |
| `provideGuestApi()` | `frontend/src/app/core/api/` | Wires `provideHttpClient()` + the generated client's `Configuration` (`basePath: ''` — same-origin in prod, the `ng serve` proxy in dev; **CORS never exists on the backend**). First consumption of `packages/api-client/` — by path, via the `@gxp/api-client` tsconfig alias + the `packages/node_modules` symlink; the `ngVersion` ceiling question is closed (compiles green under Angular 22 strict) |
| `GuestDataService` | `frontend/src/app/core/guest/` | Owns ALL content wire I/O: bootstrap fetch (4 s bound), 30 s ETag revalidation loop (`If-None-Match` is a **declared contract parameter** — `getGuestContent(ifNoneMatch)`; the `defaultHeaders` hack is gone; 304 = no-op), the ONLINE/STALE/DISCONNECTED state machine, occupancy signal, the last-known-good `localStorage` cache, DISCONNECTED retry with 5→30 s backoff. Since `guest-payload-consolidation` there is **one of everything**: one request, one ETag, one held copy and one cache key (`gxp.cache.content` — written after successful mapping only, cleared on 401; the two legacy keys are evicted once at construction). Zoneless-safe: signals + `DestroyRef`-torn timers |
| `guest-content.mapper.ts` | `frontend/src/app/core/guest/` | The one wire boundary: generated models → the internal `PropertyConfig` shape, with validation (brand hex rejected → token defaults stay; unknown icon → neutral fallback glyph; localized maps must carry the default language; 0–5 items / 0–4 cards). A rejected payload counts as a failed fetch — never a crash, never rendered, never cached |
| `GuestAssetService` | `frontend/src/app/core/guest/` | Authorized blob fetch per asset id (`Authorization` header — never a bare `<img src>`) → memoized object-URL signals, revoke-on-replace/destroy, 404 → `null` (components render their existing fallbacks) |
| `GuestBootstrapService` | `frontend/src/app/core/guest/` | The app initializer's body (and the pairing claim's re-entry): token check → content bootstrap → defaults ⊕ stored-preference hydration before first paint — the REQ-126 no-flash structure, wire edition. Re-syncs defaults/preference on every poll-adopted session under the existing never-overwrite-a-fresh-choice rule |
| State guards + `GuestStateRoutingService` | `frontend/src/app/core/guest/` | `/pairing`, `/standby`, `/connection` live OUTSIDE the shell; guards arbitrate initial/deep-link URLs, the routing effect moves the kiosk at runtime (poll-driven occupancy flips, 401 wipe, healing) — both read the same signals, precedence: unpaired → pairing, nothing-to-render → connection, vacant → standby. Returning to the app restores the previous route |
| `PairingComponent` + `PairingService` | `frontend/src/app/features/pairing/` | The unattended kiosk half of pairing (spec: `docs/design/screens-inhouse/pairing.md`): create → show grouped code → 3 s poll (secret in a component-scoped service field, never stored/rendered/logged) → claim-once → store token → boot in place, no reload. 404 renews unattended; network loss — and since 2026-08-24 any other failed create, the pairing `429` and its `cooldown` state having been removed with the rate limiter — resumes the same code until expiry on a 5 s → 30 s backoff, then renews. Brandless by design |
| `StandbyComponent` | `frontend/src/app/features/standby/` | The vacant room (spec: `standby.md`): server-resolved standby texts, optional logo, the functional status bar — and no navigation surface at all (client-side data-sparing). Poll-driven in and out |
| `ConnectionComponent` | `frontend/src/app/features/connection/` | The last resort (spec: `connection.md`): paired, unreachable, nothing cached. Honest copy, `WifiSlash` glyph in the in-house `--color-status-offline` role, no button, no status bar; heals unaided |
| `StalenessPillComponent` | `frontend/src/app/layout/staleness-pill/` | The constitution's "discreet staleness hint": a glass pill under the status-bar cluster, `Stand HH:MM`/`As of HH:MM` from the cache's `storedAt`, `role="status"` set once per episode. Visible when `StalenessService.level() === 'PILL'` — i.e. in STALE, until the escalation replaces it |
| `StalenessService` | `frontend/src/app/core/guest/` | The tri-state `level: 'NONE' \| 'PILL' \| 'BANNER'` (REQ-952), derived from `connection()`, `staleSince()` and the minute-aligned `ClockService`. `STALE_ESCALATION_MS` reads `environment.staleEscalationMs` — **1 h in production, 1 min under the `development` configuration** (owner, 2026-08-28), so the state is reachable by hand instead of by waiting an hour or editing a running system; the production value is the behaviour, the dev value only makes it testable, and it is grep-provably absent from a production bundle. A **presentation property of STALE, not a fourth `ConnectionState`** — no new timer, no change to `GuestDataService`. Tri-state, so "banner up ⇒ pill down" holds by construction |
| `StalenessBannerComponent` | `frontend/src/app/layout/staleness-banner/` | The constitution v1.9.0 escalation: after 60 min without successful backend contact the pill is *replaced*, in its own anchor, by a glass **bar** (`width: max-content` so the body fits on **one line**, `max-width` at the content area's edge; `WifiSlash` at `--size-icon-md` in `--color-status-warning`; title with the reused `stale.asOf` line right-aligned beside it, body underneath; `role="status"`/`aria-live="polite"` set once; a 350 ms entrance and a glyph-only pulse, both behind `prefers-reduced-motion`). An absolute overlay, so it cannot grow the document. Two body variants chosen from **occupancy**: the occupied one directs the guest to their host, the vacant one addresses staff. Rendered in both hosts (shell + standby); spec: `docs/design/screens-inhouse/connection.md` |
| `HttpPreferenceGateway` | `frontend/src/app/core/preferences/` | The real preference transport (D8): `save()` = whole-record `PUT /api/guest/preferences` (400/`409 room-vacant` silent-normal, 401 → kiosk-wide wipe, 5xx → the service's bounded retry); `load()` = handed over from the session payload, **no second GET**; `clear()` = client no-op (stay-anchored server-side). `SessionPreferenceGateway` and `example-property.data.ts` are **deleted** (D6) — no fake-content fallback exists anywhere |
| `DevDataSeeder` | `backend/app/src/main/java/de/gxp/devseed/` | `%dev`-only convenience (D2): example content, an active stay, one device (`sha256("gxp-dev-device-token")`, hash-only). `@IfBuildProfile("dev")` = **build-time** prod inertness — the *bean* is not in the `%prod`/`%test` container (re-verified at review: no `DevDataSeeder_Bean`/observer is generated; the compiled class ships in the jar but nothing instantiates it); idempotent per the dev device row. The token literal is documented as public/dev-only in `backend/README.md` |
| Guest-app serving | `backend/app/pom.xml` (profile `bundle-guest-app`) + `de.gxp.infrastructure.web.SpaRoutingHandler` | **The serving/path layout (D7, decided 2026-08-20):** `/` + hashed assets = the guest kiosk app (plain Maven copy of the prebuilt Angular bundle into `META-INF/resources`, auto-activated on the dist's existence — backend-only builds stay Node-free); `/api/**` and `/q/**` untouched; **`/manage/**` reserved for `manager-frontend`** (own `index.html` under `META-INF/resources/manage/`, base-href `/manage/` — the guest SPA fallback already excludes it, zero rework needed). The fallback reroutes dot-less, html-accepting GETs to `/index.html`, is inert without a bundle, and static-resource cache headers are disabled so `/` always serves the deployed app |

Still designed but not implemented: nothing — the Home screen's designed
components are now all covered by the row above. The **widget column**
remains undesigned (no content built), but its layout space is reserved
(`flex: 1`, empty) rather than collapsed — see "Open Structural Questions"
below.

Added by `docs/features/update-channel/` (2026-08-26) — the box updates
itself from a signed manifest (constitution v1.8.0; `backend/README.md` →
"Updates"):

| Component | Location | Responsibility |
|---|---|---|
| Update contract | `api/openapi.yaml` (tag `manager-updates`) | `GET /api/manager/updates` (settings, last check outcome, available version, the updater's last state), `PUT …/settings` (`autoUpdateEnabled`, `applyAt HH:mm`), `POST …/apply` (202, or 409 with nothing available), `POST …/check` — all `admin`, all audited (`update`/`update-settings`, `update-apply`, `update-check`). Guest and pairing paths untouched |
| `de.gxp.update` | `.../de/gxp/update/` | `UpdateControl` (the hourly check sequence: `https://` only, 64 KiB cap, verify **before** parse, `SemanticVersion` D6 compare, ≤ running version = no update; the reconcile algorithm that writes/moves/removes `desired-update.json`; manual apply/check), package-private `ManifestClient` (JDK `HttpClient`, `User-Agent: gxp-update/1`, no identity, class-name-only logging), `ManifestVerifier` (JDK `Signature("Ed25519")` against the **trust set** `update/signing-public-keys.pem` in the image — two keys, any verifies; a verified newer manifest's `trustedKeys` line rotates the set, persisted in V9 `trusted_keys`/`trusted_keys_version`), `ManifestParser`, `ExchangeFiles` (hand-built fixed-layout writer/reader, temp + rename, 64 KiB read cap), `UpdateStateStore` (`@Transactional`, instants truncated to seconds), entity `UpdateChannel` (V8, single row: settings, last check, the raw verified manifest bytes, the manual request) |
| `backend/updater/` | host, not a container | `gxp-updater.sh` (root, `flock`, snapshots the hostile request file once, OpenSSL re-verifies — **the trust root** —, `pg_dump`, pull by registry digest, `.env` `GXP_IMAGE=` swap, one restart, ready + Flyway health, rollback ladder image → image + restore → `rollback-failed`), `gxp-updater.service`/`.timer` (every 5 min; `ProtectSystem=full`, `ProtectHome`, `PrivateTmp`, `PrivateDevices`, `ProtectKernelTunables/Modules`, `ProtectControlGroups`, `NoNewPrivileges`, `RestrictSUIDSGID`, `LockPersonality`, `RestrictRealtime`, `SystemCallArchitectures=native`, `TimeoutStartSec=3h`, above the sum of the script's own per-step `timeout` bounds — all executed once on the dev box), `install.sh` (installs the **key set** `/etc/gxp/update-signing-public-keys.pem`, refuses a changed set without `--replace-key` and a single-key resource; `--current-version` seeds the shadow so the downgrade guard is armed from day one; `--uninstall`), `updater.conf.example`. Its **own memory** is `/var/lib/gxp-updater/state.json` (root 0600 — replay guard + downgrade refusal + the rotated trust set); `state.json` in the volume is a mirror for the app, written by `rename(2)` from the volume's root-owned parent — root never opens a path inside the app-owned directory for writing. Rung 2 of the rollback wipes `public` before the restore. Tests: `test/parse.sh` (shared fixtures with `ExchangeFilesTest`), `test/smoke-apply.sh` (incl. seed, downgrade refusal, rotation), `test/rehearse-rollback.sh A|B`, `test/rehearse-registry.sh` (scenario C: the production digest-pull branch against a local `registry:2`) against a throwaway compose project |
| `tools/update/` | repo root | `gen-signing-key.sh [dir] [suffix]` (Ed25519 pairs outside any git tree, `$HOME/.gxp/keys`; key 1 and key 2), `sign-manifest.sh` (fixed-layout manifest + detached raw signature, self-verified; `--sha256` = the registry manifest digest from `docker buildx imagetools inspect … '{{println .Manifest.Digest}}'`; `--trusted-keys <pem>` writes a rotation) |
| Volume `gxp-updates` | `backend/compose.yaml` → `/updates` in `app` (`185:root`, 0700) | The app ↔ updater exchange: `desired-update.json` (app → updater, hostile to the updater) and `state.json` (updater → app, mirror). No secrets, not part of the backup |
| `UpdateScheduler` | `.../de/gxp/infrastructure/scheduler/` | The two `@Scheduled` triggers (hourly check at minute 17, reconcile every 15 min) — see "Scheduled housekeeping" |

**Decided 2026-08-26, not yet built — `docs/features/instance-identity-and-certs/`**
(remote-operations Part B; gate PASS, implementation pending). Listed here so
the next feature (Part D, the relay) plans against it; the rows become "real
code" rows when `dev` lands them:

| Component | Location | Responsibility |
|---|---|---|
| `de.gxp.instance` | `.../de/gxp/instance/` | Instance identity (Ed25519 pair generated at first start onto `gxp-tls`: `instance-key.pem` 0600 / `.pub`), manager-triggered enrollment against our enrollment service (`/api/manager/instance`: status, enroll with a token, unenroll, renew-now; V10 `instance_enrollment`, no token/key column), the signed-request envelope `GXP-SIGNED-REQUEST/1`, and the served-certificate reconciler (merges the ACME material into `server.p12` alias `public`, SNI, reload without restart, self-signed fallback on expiry) |
| `backend/acme/` | host, beside `backend/updater/` | `lego` (distribution package) under a hardened hourly `systemd` timer; DNS-01 through an `exec` hook that asks our challenge endpoint (authenticated by the instance key) to publish `_acme-challenge.<fqdn>`; delivers `public.crt`/`public.key` into `gxp-tls` by staged rename; two more exchange files (`instance.json`, `acme-state.json`) in `gxp-updates` |
| `api/enrollment-service.yaml` | repo `api/` | The contract of the enrollment/challenge service **we** operate (built outside this repo); `EnrollmentServiceDouble` (test scope) is its executable spec and the host rehearsal's stand-in, with Pebble + `challtestsrv` as the ACME CA |

Binding facts recorded there: **the kiosk path is unchanged** — no public
certificate can secure a connection to a private LAN address without a
resolver we do not control or an uplink the guest screens must not depend
on, so §5's "kiosks trust natively" claim is withdrawn and `GET
/api/pairing/ca` keeps its meaning in both modes; the public certificate
exists for the instance name only (Part D's need). Sixth scheduled job:
`InstanceScheduler#reconcileServedCertificate` every 15 min at minute 5.

Added by [`docs/features/sidebar-catalogue-overflow/`](features/sidebar-catalogue-overflow/02-design.md)
(2026-08-27). **No new component** — the rare feature that adds capability by
changing four existing rows, which is itself why it was split out of
`modular-menu-catalogue`. Those four rows (`SidebarComponent`,
`layout/sidebar/menu-split.ts`, `SectionPlaceholderComponent`,
`PropertyContentService`) are folded into the real-code tables above; what
follows is only what has no row of its own.

Binding facts: `GuestMenu.items` carries a declared `maxItems: 50` (payload
sanity, enforced at the kiosk boundary by `guest-content.mapper.ts` — **not** a
design limit; the popover has no capacity ceiling and the sidebar splits at 5
regardless); a Flyway `V10` adds eight **disposable placeholder** catalogue rows
whose only job is to exercise overflow, promotion and umlaut collation, and
which arrive **property-disabled** so a migration delivered by the v1.8.1 update
channel changes nothing a guest sees on a running property (`%dev` overrides
them to enabled, so overflow is the default developer and review experience);
and `menu_item_definition` stays release data, not writable through any API
(`docs/backend/specification.md` §3 decision 5, confirmed by the owner
2026-08-27). Only three properties of the placeholder set may be relied on
anywhere — count ≥ 12, one umlaut-initial label, alphabetical order ≠
`default_sort_index` order — and removing it is that migration reversed plus one
edit to `core/icons/registered-icons.ts` and one edit to
`frontend/tools/measure.mjs`'s `ROUTES` list, which walks every route the `%dev`
catalogue can name. Those are the four sanctioned sites; the migration's own
header predates the fourth and names three.

Known limitation recorded with it: `PUT /api/manager/menu-config` is a
**whole-set replace**, so a partial submit deletes those opt-out rows and
absence resolves back to enabled — a future manager UI must round-trip the whole
set. Pinned by `CatalogueExpansionTest.partialConfigReplaceReEnablesTheFixtures`
and, for the shared test database, by
`MenuFixtures.restoreCatalogueOptOut()` in an `@AfterEach` of every class that
replaces the property scope.

The popover's keyboard model is `role="menu"`'s **linear** contract, kept
deliberately: `CdkMenu.orientation` is `'vertical'`, so ←/→ are inert, and
REQ-919's row-major grid fill is what makes ↑/↓ walk the tiles in reading order.
Faking spatial traversal under `role="menu"` would make the announced semantics
and the behaviour disagree.

## Cross-Cutting Concerns

- **Scheduled housekeeping:** the only work this box does unattended. Core
  `quarkus-scheduler` (`@Scheduled`, in-JVM, no job store, no cluster
  coordination — Quartz deliberately not approved, constitution v1.7.0).
  **Scheduling is centrally managed in `de.gxp.infrastructure.scheduler`**
  (owner decision 2026-08-25), one class per domain holding only the
  annotation and a single delegating call into the feature's public
  `*Control`; the logic stays in the feature. **Five jobs today**, all
  `concurrentExecution = SKIP`. Three **daily at 02:00** (`cron = "0 0 2 * * ?"`):
  `DeviceScheduler` sweeps expired `pairing_request` rows, `AuthScheduler`
  purges expired admin bearer-token hashes from the in-memory store, and
  `AuditScheduler` (auth-hardening, 2026-08-26) deletes `audit_entry` rows
  older than 365 days (`AuditControl.pruneOlderThan`). Two from
  `update-channel` (2026-08-26) in `UpdateScheduler`: `checkForUpdate`
  **hourly at minute 17** (`"0 17 * * * ?"`, plus 0–600 s jitter inside the
  Control — an off-the-hour minute and jitter so a fleet never hits the
  manifest server in step) and `reconcileDesiredUpdate` **every 15 min**
  (`"0 */15 * * * ?"`, keeps the request file in the exchange volume
  consistent with the settings and the next apply window). Cron, not
  `every = …`, because a relative interval restarts with the process and an
  on-premise box reboots with the building; the accepted residual is that a
  machine never powered on at 02:00 never sweeps, which is harmless since the
  rows are already functionally inert. The pairing table keeps its
  *opportunistic* delete on create as well — that is what bounds it during a
  flood of anonymous `POST /api/pairing/requests`; the sweep only clears the
  residue a flood leaves behind. **The authoritative answer to "what runs on a
  timer, and how often" is `ScheduledJobInventoryTest`**, which pins each job
  and its cron as a literal list and fails the build when one is added,
  removed or re-timed — this bullet is prose, that test is the source of
  truth. Jobs never fire during the suite
  (`%test.quarkus.scheduler.start-mode=halted`); tests call the Control
  directly. Principle 3 applies with teeth: Quarkus logs an uncaught job
  failure with a full stack trace and a `PSQLException` quotes statement text,
  so every job catches `RuntimeException` and logs the exception's class name
  only, and success logs counts only, and only when the count > 0.
- **Update channel** (`update-channel`, 2026-08-26): the one outbound call
  this box makes on its own — `GET <GXP_UPDATE_BASE_URL>/manifest/<channel>.json`
  + `.sig`, hourly, `https://` only, carrying no identity and no telemetry
  (`User-Agent: gxp-update/1`, `Accept`); unset base URL = channel off. The
  manifest is verified twice (JDK in the app to decide "available", OpenSSL
  in the root updater to decide "apply") against the same two-key trust set; the
  updater treats everything the app wrote as hostile (snapshot, size cap,
  regex per field, verified-manifest-only, no `eval`) and keeps its own
  root-owned memory. Updates apply at night (04:00 local by default, 2 h
  window, manager-changeable), one restart, dump first, automatic rollback;
  a failed version is held until a manager acts. Key custody: two Ed25519
  keys on the owner's machine (key 1 active, key 2 dormant), public halves
  in the image; a signed newer manifest rotates the set on every box
  (architect review T-20, 2026-08-26). Custody practice (passphrase, offline
  backup, second medium) is an open operator task deferred by the owner to
  the first customer; the updater's own update is a tracked follow-up.
- **Design tokens:** CSS custom properties in two layers (primitive → semantic).
  Components reference semantic roles only. Full rationale and shape in
  `docs/design/tokens.md` → "Implementation". No Tailwind. As of
  `home-screen-prototype`, this moved from spec to real code:
  `frontend/src/styles/tokens.css`.
- **Theming:** two independent axes.
  - *Appearance* (`data-theme` on `<html>`) overrides **semantic** roles.
  - *Property branding* overrides **primitives**, written at runtime from a
    per-property config. Adding a customer must never require a code change.
  - **Why the separation is load-bearing, not tidiness:** branding primitives
    are written **inline on `:root`** at runtime, and an inline style beats any
    `[data-theme='dark']` stylesheet rule. So the moment the theme axis
    touches a primitive, a *branded* property silently gets the wrong value —
    a defect visible only to paying customers.
  - **The mesh gradient is therefore a semantic role, not a primitive
    (2026-08-18).** `--mesh-*` became `--color-mesh-1…16` + `--color-mesh-base`,
    and `shell.component.css` reads only the roles. That removed the last
    "components may read a primitive" exception, so the rule is now absolute:
    `grep` for `var(--mesh-|--ink-|--brand-|--night-` in `frontend/src` matches
    **only** `styles/tokens.css`.
  - **A dark palette exists as of 2026-08-18** — designed in-house, no Figma
    counterpart, documented in **`docs/design/dark-theme.md`** and realised in
    `frontend/src/styles/tokens.css`. It is deliberately **not** in
    `docs/design/tokens.md`, which records only what exists in Figma, nor in
    `docs/design/design-sheet.md`. A Figma re-import will not reproduce it;
    its absence from a fresh extraction is not evidence that it is wrong. It
    changes **colour only** — same roles, same geometry, same glassmorphism
    (the surfaces stay translucent and blurred).
- **Appearance & language (added 2026-08-18).** Both are switchable at
  runtime, from two places each: the status-bar dropdowns and the Settings
  screen, all bound to the same signals. Three points bind future work:
  - **Default ⊕ choice.** The *property* configures which options are offered
    and what the defaults are (`settingsConfig()`); the *guest* may override
    per setting. An override that is `null` means "still following the
    property". A value outside the configured option list is discarded, never
    applied — including one that arrives from storage.
  - **`<html>` always carries a resolved value.** `data-theme` is `light` or
    `dark`, never absent, and `lang` is always set. Exactly one place in
    `src/` writes them: the effects in `App` (`app.component.ts`). `prefers-color-scheme` is
    read **only** when a property configures the `system` option — it is never
    the default.
  - **Nothing is fetched to render either one.** Both catalogues ship in the
    bundle and the theme is CSS custom properties, so switching is a signal
    write and a repaint — no request, no reload, no route change. The one
    request in the area is the *persistence* write below, which is outside the
    render path.
- **Icons:** Phosphor Icons via `@ng-icons/core` + `@ng-icons/phosphor-icons`.
  Figma glyph names already match the set, so icons are not exported
  individually. Note there is no Angular-specific icon set — `mat-icon` renders
  Google's Material Symbols, which is a style choice, not a framework fit.
  `@ng-icons` is a multi-set wrapper, so switching sets later does not change
  component code.
- **Overlay behaviour:** `@angular/cdk` — Overlay, Dialog, Menu, FocusTrap,
  Portal, A11y. Unstyled; all visuals come from the project's own CSS.
- **External runtime dependencies: none permitted.** Fonts are self-hosted,
  icons bundled, images embedded. The most likely accidental breach is a
  Google Fonts `<link>` for Plus Jakarta Sans — check for it in the very first
  scaffold. Features needing internet (weather, maps, live recommendations) must
  be designed to degrade when it is absent.
- **Testing:** unit tests on **Vitest + jsdom** — the Angular CLI default since
  v21, so `ng new` sets it up and no separate "install a test runner" TODO step
  is needed. Karma/Jasmine are deliberately not used. **No end-to-end framework
  is chosen** (Angular ships no default; Playwright and Cypress are the usual
  candidates) — an open, approval-gated decision for when one is first needed.
  **`playwright` is nonetheless an approved devDependency since
  `docs/constitution.md` v1.3.0 — as a *verification tool only*:** it drives a
  real browser at review time to measure geometry, wrapping and scroll
  dimensions, which jsdom cannot compute. `frontend/tools/measure.mjs` is that
  instrument; it reports numbers and asserts nothing. Approving it explicitly
  did **not** decide the E2E question above — committed specs, a browser test
  runner or anything wired into `npm test` still needs that separate approval.
- **Viewport & scaling (added 2026-08-18).** Design size **1280 × 832**
  logical px; supported floor **1024 × 768, landscape**; portrait explicitly
  out of scope. Between floor and design size the UI **scales proportionally**
  — no breakpoints, no second layout, no reflow. **No screen ever shows a
  scrollbar**, on any axis, on any route: the user extended this to every
  future screen, so treat it as binding when planning a new one. It is met by
  construction (nothing overflows) and verified by measuring
  `scrollWidth`/`scrollHeight` against `clientWidth`/`clientHeight` — never by
  `overflow: hidden`, which would hide regressions instead of preventing them.
  Full contract in `docs/design/tokens.md` → "Minimum supported width".
  The scaling **mechanism** — an uncapped viewport-proportional root font size,
  `html { font-size: min(1.25vw, 1.9231vh) }` — was **approved by the user on
  2026-08-18** and is recorded in `docs/constitution.md` v1.2.0.

  **The content area's top-right corner is reserved (added 2026-08-18).**
  `StatusBarComponent` overlays it on every route, absolutely positioned at the
  shell's own padding offsets rather than in the flow — a header row would have
  pushed Home's signed-off geometry down. Anything placed there in future has
  to clear it, and the margin is not generous: at 1280 × 832 the clearance to
  Home's card row is 48px horizontally and 20px vertically to the heading; at
  the 1024 × 768 floor it is ~38px and 16px. Verify by measuring, not by
  looking.
- **Component host elements are part of the layout (added 2026-08-18).** With
  Angular's default emulated encapsulation, `<app-foo>` — not the template's
  root element — is what the parent lays out, and it is unstyled
  (`display: inline`, `flex-shrink: 1`, `height: auto`). A component that takes
  part in its parent's flex/grid/height chain must declare it on its own
  `:host` (`display: contents` when the template root should be the item;
  `display: none` when the component renders nothing). Four separate visual
  bugs in the first feature came from this one gap. Not applicable to hosts
  that carry a role/label/landmark, since `display: contents` removes them from
  the accessibility tree.
- **Icon glyphs cannot be styled from a component stylesheet.** `@ng-icons`
  appends its `<svg>` via `Renderer2`, outside the Angular template, so the
  node carries no `_ngcontent-*` attribute and **no scoped selector can ever
  match it**. Anything that must reach the glyph itself (e.g. the
  gradient-filled active nav icon) needs a document-level rule in
  `global.css`, keyed on a purpose-named class. Inheritable properties
  (`color` → `currentColor`) still work normally and should be preferred.
- **Logging:** `<not yet decided>`
- **Persistence:** ~~exactly one thing is persisted~~ — **revised 2026-08-20
  (`guest-frontend-integration`): the kiosk browser now persists exactly
  three things**, all in `localStorage`, all non-sensitive by the guest-API
  v1 pin:
  - `gxp.device-token` — the device's own credential (by the 4xx PRD's
    classification not guest data; kiosk lockdown remains the operational
    control),
  - `gxp.cache.content` — the last-known-good guest payload (+ ETag +
    `storedAt`), the constitution's kiosk-cache clause realised: rendered with
    the staleness pill — and, after an hour, the escalated banner — when the
    backend is unreachable, cleared on 401. One record since
    `guest-payload-consolidation`; the two legacy keys (`gxp.cache.session`,
    `gxp.cache.home`) are evicted once when `GuestDataService` is constructed.
    **This boundary must be re-checked whenever a new field is cached** — the
    top-level key allow-list is asserted in `guest-data.service.spec.ts`, and
    the first sensitive screen needs the spec §11 item 9 field-level rules
    first.
  - The guest's language/appearance preference is **server-owned** (the
    stay row) and no longer stored client-side at all.

  The original decision record (kept for history): all *content* was static
  JSON / TS and nothing else was written.
  - **It is server-owned state, by the user's decision of 2026-08-18** — not
    UI state, and not a client-side cache. It is written when the guest
    changes it, delivered back on load, and the server always supplies the
    defaults (`light`, `de` for the example property).
  - **The app reaches it through one contract**, `PreferenceGateway`
    (`load`/`save`/`clear`, all async, whole-record writes) behind a single
    injection token, with all policy above it in `PreferencesService`. Today
    that token is bound to `SessionPreferenceGateway`, a **`sessionStorage`
    stand-in**, which is *deleted* — not adapted — when the real endpoint
    lands. Swapping it is one provider line and, by design, changes no
    behaviour.
  - **The record carries no identifier of any kind** — no guest, stay, session
    or device id. It is scoped to *the screen*. This deliberately does not
    pre-empt the open guest-authentication question above; scoping the record
    later is a change inside the gateway and the server, nowhere else.
  - **Where the server keeps it — decided 2026-08-19: PostgreSQL**, behind
    the Quarkus backend (constitution v1.4.0). In the decided domain model the
    language/theme override hangs off the **`Stay`** entity, resolving
    Stay → Room → Property. The endpoint's shape, its server-side validation,
    its security (device token — see above, resolved) and what clears the
    record at check-out belong to the backend features now in flight —
    background in `docs/features/appearance-and-language/02-design.md` §6.7
    and `docs/backend/specification.md` §5.
- **Messaging / async:** two boundaries, both about the preference above and
  nothing else. Everything else in the app is synchronous.
  - **One bootstrap-gated read.** An app initializer applies the property
    defaults and then `await`s the stored preference behind a **bounded
    timeout** before the first paint, so the screen never renders light-then-
    dark or German-then-English. The read never rejects: a failure, a timeout
    and "nothing stored" are indistinguishable and all mean "use the
    defaults". A late answer only fills a setting whose override is still
    unset, so it can never overwrite a choice the guest just made.
  - **One fire-and-forget write.** A change updates the UI optimistically and
    schedules a write that **coalesces** (rapid changes collapse to one) and
    retries a bounded number of times, then stops. Failure is normal, not
    exceptional: the guest's choice stays on screen, no error UI appears, and
    **nothing is logged** — the app has no logging convention yet and a failed
    preference write is not the place to invent one (constitution principle 3
    also applies: none of this may reach a log).
- **Auth:** **implemented for the manager (2026-08-19, `backend-domain-config`;
  hardened 2026-08-26, `auth-hardening`)** — **named manager accounts**
  (`manager_user`, every one an admin; first account seeded from
  `GXP_ADMIN_USERNAME`/`GXP_ADMIN_PASSWORD` on first start only, a pre-V5
  instance's shared password carries over as user `admin`) log in with
  username + password at `/api/manager/login` only (PBKDF2-hashed in
  Postgres, own password rotated via `PUT /api/manager/password`, every
  account change drops that account's tokens), opaque 256-bit bearer token
  with a 10-minute sliding idle expiry in an in-memory store
  (`GXP-Token-Expires` on every authenticated response), fail-closed
  `@RolesAllowed("admin")` on every manager Boundary (class-level where the
  whole surface is admin) since v1.6.0, 401 + `Bearer` challenge for anyone
  else on the LAN. **Login is throttled per attributed client address**
  (5 failures / 15 min → 60 s block doubling to 900 s, `429` before any
  PBKDF2, never global or per account — REQ-326 reopened). **A second factor
  (TOTP + recovery codes) is required only for requests attributed to the
  trusted proxy** (remote operations); on the LAN it is never asked for.
  Every login outcome after the throttle and every manager mutation is
  written to the **append-only audit trail** (no values, 365-day retention,
  admin-readable at `GET /api/manager/audit`). Nothing is logged on the
  login path.
  **Device tokens are live (2026-08-19, `device-pairing-and-guest-api`):**
  the SAME bearer path resolves a presented token first against the manager
  store, then against the device table by hash — role `device`, long-lived,
  no idle expiry, revocable with next-request effect, the room derived
  exclusively from it (principle 4's core property, proven by the
  two-rooms scoping tests). HTTPS with the instance-generated CA is live too:
  the compose unit is **production-shaped, no longer dev-only** — `%prod`
  serves HTTPS only.
  **The kiosk side is live (2026-08-20, `guest-frontend-integration`):** the
  device token persists in `localStorage` (D4 — reboot ≠ re-pair; revocation
  is server-side, next-request effective), travels only as the bearer of
  same-origin `/api/**` calls via the generated client's credential fn, and
  any 401 wipes token **and** content cache before the kiosk drops to the
  pairing screen. The poll secret exists only in a component-scoped service
  field while the pairing screen is on.
- **Auth hardening — built (2026-08-26, `auth-hardening`, T-01…T-17):** the
  component rows above (`de.gxp.auth`, `de.gxp.user`, `de.gxp.audit`,
  `ClientAttribution`, the three schedulers) describe the code on disk;
  decisions D1–D25 and the recorded deviations live in
  `docs/features/auth-hardening/02-design.md` and `handoff.md`.
- **Persistence (backend, 2026-08-19):** the domain schema is real — property
  (single row), rooms, menu catalogue/config, action cards, stays, assets
  (BYTEA) and the admin credential, all via Flyway V1–V3 with the seeds that
  make a fresh instance usable (REQ-334). Manager writes go through the CRUD
  API above; the kiosk still renders static TS until
  `device-pairing-and-guest-api`/`guest-frontend-integration`.
- **Hand-off notes from `backend-domain-config`:**
  - **`einstellungen` is fixed app chrome, not a catalogue item** — a manager
    cannot disable the settings entry; the guest API must serve it as fixed
    chrome (design decision, recorded in the PRD/design).
  - **`/q/openapi` is disabled in `%prod`** (user decision 2026-08-19,
    closes foundation review 🟡 #1): no dev-time surface runs in prod —
    `%prod.quarkus.smallrye-openapi.enabled=false` (404 in prod; dev/test
    still serve the document), Swagger UI and Dev Services explicitly
    guarded, Dev UI dev-mode-only by construction. See the commented block
    in `backend/app/src/main/resources/application.properties`.
- **Hand-off notes from `device-pairing-and-guest-api` (for
  `guest-frontend-integration`):**
  - **`ngVersion` ceiling check on first client consumption** — still open
    from `backend-foundation`; the generated client is consumed for the first
    time in that feature.
  - **Kiosk-side token/CA handling is that feature's to design:** where the
    device token persists on the device, and the (human) CA-install step in
    the provisioning flow — the backend serves the CA at
    `GET /api/pairing/ca` and the README documents the workflow.
  - **Images must be fetched with the `Authorization` header — no bare
    `<img src>`:** `GET /api/guest/assets/{id}` requires the device token.
    Fetch as blob/object-URL (or equivalent). Responses carry the immutable
    per-id ETag and `Cache-Control: private, max-age=2592000`.
  - **Guest JSON polling is ETag-based:** every `/api/guest/**` JSON response
    carries a strong ETag with `Cache-Control: no-cache`; steady-state kiosk
    polling should send `If-None-Match` and treat `304` as "render what you
    hold". Occupancy flips and manager edits change the ETag naturally.
  - **The kiosk cache still holds nothing sensitive:** nothing served by the
    v1 guest API is guest-sensitive (no codes, no passwords). The first
    sensitive screen (WiFi, door codes) must first bring the spec §11 item 9
    field-level sensitivity list and per-field kiosk-cache rules. *(Constitution
    v1.9.0 draws the line at credentials reaching past the device, not at
    field names: guest-facing content the manager deliberately displays is
    permitted in the cache, a device or session token never is.)*
  - **Vacancy contract:** the content resource answers 200 with standby texts
    when vacant (`occupancy: VACANT`, no name, no preference, **no `home`**);
    only the preference write still answers the named `409 room-vacant` — the
    kiosk's fire-and-forget write treats that as normal. *(Updated by
    `guest-payload-consolidation`: `screens/home` no longer exists, so the
    two-call race it used to create is gone.)*
- **Hand-off notes from `guest-frontend-integration` (2026-08-20, for
  `manager-frontend` and operations):**
  - **`/manage/**` is reserved** for the manager app: its own `index.html`
    under `META-INF/resources/manage/`, Angular `base-href /manage/`. The
    guest SPA fallback (`de.gxp.infrastructure.web.SpaRoutingHandler`) already excludes the
    prefix — the manager app slots in with zero rework of the serving layout.
  - **Manager-side pairing/device UI is still curl-only:** pairing confirm,
    device list/rename/revoke exist as API operations; `manager-frontend`
    builds their UI.
  - **Kiosk CA install is now the ONLY missing link** between a fresh box and
    a rendering production kiosk: the app is served at `/`, pairing is
    self-service on the kiosk — a human still installs the instance CA
    (`GET /api/pairing/ca`) into the device's trust store once per device.
  - **The pairing code's group-of-four presentation** (`XXXX XXXX`) is the
    kiosk spec's own call; if the manager UI renders codes differently, align
    both then (`docs/design/screens-inhouse/pairing.md`).

## Integration Points

| System | Direction | Notes |
|--------|-----------|-------|
| Figma (`AirBnB-Screen`) | inbound, manual | Design source. Only the `design-import` skill may query it, and the plan is metered — see `docs/design/extraction-log.md` |
| `api/openapi.yaml` → `backend/app` + `packages/api-client/` | build-time, both directions of the repo | The contract toolchain (real since 2026-08-19): `./mvnw verify` regenerates the JAX-RS interfaces/DTOs into `target/` (uncommitted — drift is a compile failure) **and** the committed TS client. Contract, backend code and client move in one commit — workflow in `backend/README.md` |
| PostgreSQL | outbound from `backend/app` | The only datastore (constitution v1.4.0). Dev/test: Quarkus Dev Services container, zero manual setup; prod: the `db` service in `backend/compose.yaml`. Schema exclusively via Flyway versioned SQL |

Potential future integrations implied by the design's widget column (weather,
nearby recommendations) would each be an approval-gated infrastructure decision.
None are committed.

## Open Structural Questions

- ~~Viewport contradiction~~ — **resolved 2026-08-17, revised 2026-08-18.** The
  MVP still targets large touchscreens and still has no breakpoints and no
  second layout, but "the fixed 1280 × 832 layout" is no longer accurate: a
  supported range now exists (1024 × 768 landscape → 1280 × 832 → larger),
  covered by proportional scaling, with a hard no-scrollbar rule. See
  "Cross-Cutting Concerns → Viewport & scaling" above and
  `docs/design/tokens.md` → "Minimum supported width". Phone support is still
  deferred and still a layout project. The type-size-at-viewing-distance
  sub-question is now sharper, not resolved: at the floor, body text renders at
  11.2px and sidebar labels at 9.6px, below the edge `design-sheet.md` already
  warns about — the pending hardware check must happen at the floor size, not
  only at the design size.
- **Widget column** (`docs/design/screens/home.md`) is intentionally undesigned;
  `README.md` treats the widgets as hypotheses to be validated or dropped.
  **Planning resolution (`home-screen-prototype`, 2026-08-18):** no *content*
  built in the first prototype — no Figma design exists for any widget, and
  weather/recommendations would each need an external data source (an
  infrastructure decision gated under `docs/constitution.md`). **User
  confirmed 2026-08-18:** unlike the content, the column's *space* is
  reserved (`flex: 1`, empty) in the Home layout, matching the designed
  proportions, so this stays a content/future-feature decision rather than
  a layout one to revisit later. Selecting and designing the widget set
  itself remains deferred to its own future feature.
