# PRD: Guest Frontend Integration
**Feature:** [index.md](../index.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Status:** Draft — **decision-complete**: the full batch D1–D9 was answered by the user 2026-08-20 (D1–D6, D8, D9 approved as recommended; **D7 changed** — production serving of the guest app is **in scope**, not deferred). Resolutions recorded in [02-design.md → Resolved decisions](02-design.md); no `[NEEDS CLARIFICATION]` remains.

> Fourth feature of the backend cut (`docs/backend/specification.md` §12):
> *"The guest app moves from `example-property.data.ts` to the real API, plus
> the last-known-good cache and the standby screen. `PropertyContentService`
> is the only file that should meaningfully change — that was the point of
> constitution principle 2, and this feature is where the claim gets tested."*
> Settled ground — referenced, never re-opened: constitution **v1.5.1**, the
> guest contract as built by `device-pairing-and-guest-api` (REQ-401…435, all
> PASS), the ceiling rule, the vacancy contract, `frontend/CLAUDE.md`, the
> reviewed behavior of `home-screen-prototype` and `appearance-and-language`
> (incl. the no-scroll constraint and Home's measured geometry).
>
> **REQ-ID block: `REQ-5xx`** (0xx home screen, 1xx appearance/language,
> 2xx foundation, 3xx domain-config, 4xx pairing/guest API).

## Problem statement

The backend serves a complete, room-scoped, ETag-polled guest API over the
device-token bearer path — and the guest frontend still renders
`example-property.data.ts`. Three gaps:

1. **No consumption.** The generated client in `packages/api-client/` has
   never been imported; `PropertyContentService` reads a static TS file; the
   guest's preference is persisted in `sessionStorage` by a stand-in gateway
   that was designed to be *deleted* when the real endpoint exists — which it
   now does.
2. **No kiosk identity on the client.** The backend's pairing flow
   (create → display code → manager confirm → claim-once) has no UI, no token
   storage, and no screen for the unpaired/vacant/disconnected states the API
   contract already defines. These screens have **no Figma design and will
   not get one** — the user has asked for them to be designed in-repo against
   the existing design system (D1).
3. **Development friction.** A fresh Dev-Services database has zero devices,
   so every backend restart would force a manual pair-confirm-claim round
   before the frontend renders anything. The user asked for a dev-only seeded
   device — with a hard requirement that the mechanism is **provably inert in
   `%prod`** (a leaked dev token must never work in production).

## Goals / Non-goals

**Goals**

1. The guest app renders entirely from `GET /api/guest/session` +
   `GET /api/guest/screens/home` + `GET /api/guest/assets/{id}` through the
   generated client — property content, branding, menu (ceiling-resolved
   server-side), settings config, occupancy, standby texts, home content,
   assets. Components keep reading `PropertyContentService`/
   `PreferencesService`; the swap happens underneath them (principle 2's
   promise, tested here).
2. Kiosk pairing UX: an unpaired kiosk walks the pairing flow unattended on
   its side (show code, poll, claim, enter the app), with honest states for
   expiry, rate-limit, backend-unreachable and revocation.
3. Occupancy-aware rendering: `VACANT` → the standby screen (server-resolved
   standby texts, no guest-derived value on screen); `OCCUPIED` → the normal
   app; flips arrive via ETag polling without reload.
4. Preference round-trip: hydrate from the session's resolved
   defaults ⊕ stay override; write-back via `PUT /api/guest/preferences`
   (whole record, fire-and-forget, `409 room-vacant` treated as normal). The
   `sessionStorage` stand-in is deleted, as designed.
5. Offline-first degradation per the constitution's kiosk-cache clause:
   last-known-good rendering with a discreet staleness hint when the backend
   is unreachable; a designed connection screen when there is nothing to
   render; automatic recovery.
6. Dev environment without manual pairing: a `%dev`-only seed (device +
   active stay + example content) whose `%prod` inertness is a requirement,
   not a hope; Angular dev server proxying to local Quarkus.
7. In-repo design specs for the new screens (pairing, standby, connection)
   under a clearly-marked "designed in-repo" convention that can never be
   mistaken for a Figma extraction (D1).
8. **Production serving of the guest app** (D7, user decision 2026-08-20 —
   changed from the recommended deferral: *"include it here so we will not
   forget it"*): the Angular production build is bundled into the Quarkus
   jar via a plain Maven copy into `META-INF/resources` (mirroring the
   already-decided manager-UI mechanism, constitution v1.5.0), so kiosks
   load the app at `/` on the same HTTPS origin as `/api` — with a path
   layout that a future manager app at `/manage/` slots into without rework.

**Non-goals** (explicit, with owners)

- **Manager-side pairing UI** (confirm, device list, revoke) →
  `manager-frontend`. Dev/testing uses the manager API directly (curl / dev
  seed).
- **New guest screens beyond home** (WiFi, house rules, …) — the four section
  placeholders stay placeholders. The first *sensitive* screen must first
  bring the spec §11 item 9 field-level sensitivity list (kiosk-cache rules).
- **Kiosk device lockdown / CA installation automation** — operational. The
  CA install (`GET /api/pairing/ca`) stays the documented human provisioning
  step; a browser cannot install a trust anchor, and this feature does not
  pretend otherwise.
- **Widget column content** — still reserved space, still a future feature.
- **E2E test framework** — still an open, approval-gated decision;
  `playwright` remains review-time measurement only.
- **Contract changes.** None are expected; the contract mirrors the
  frontend's shapes by design. If implementation finds one is genuinely
  needed, it stops with ⏸ (a contract change regenerates the client and
  touches the backend feature's reviewed surface).

## Assumptions

- Work happens on a feature branch off `feature/backend` (or that branch
  itself, per the user's instruction at implementation time); nothing on
  `main`.
- **The `ngVersion` ceiling check is resolved (2026-08-20, planner):** the
  generated client (openapi-generator 7.24.0, `typescript-angular`, plain
  sources without an npm manifest) **compiles with zero errors** under the
  frontend's exact strict TypeScript settings against Angular 22.1's
  `@angular/core`/`@angular/common/http`/`rxjs` (`tsc` run against
  `frontend/node_modules`). It exposes the standalone-friendly
  `provideApi()` (`EnvironmentProviders`) and per-tag `@Injectable` services;
  the legacy `ApiModule` is ignored. The full `ng build`/`ng test`
  integration is re-verified at implementation, but no generator ceiling
  blocks Angular 22.
- The generated client is consumed **by path** (the recorded intent of the
  no-npm-manifest generation setup) via a TypeScript path alias — no
  `package.json` change anywhere.
- `@angular/common/http` (`provideHttpClient`) is part of the already-approved
  Angular platform — wiring it up is not a dependency addition.
- The guest API v1 payload contains **nothing guest-sensitive** (no codes, no
  passwords — pinned by the 4xx feature), which is what makes the
  last-known-good cache constitution-compliant at all.
- Dev backend runs `./mvnw quarkus:dev` (plain HTTP, :8080, Dev Services
  Postgres); `%prod` stays HTTPS-only — this feature's only backend changes
  are the `%dev` seed (D2) and the D7 packaging/serving (copy step + SPA
  fallback); nothing else in `backend/` moves.
- A fresh instance seeds one room (`Zimmer 1`) and the 5-item menu catalogue
  (V2) — the dev seed builds on that, it does not replace prod seeding.

## User stories

- As a **guest**, I see exactly the screens I see today — same layout, same
  languages, same theming — but the content is my room's real configuration,
  and my language/appearance choice survives because the server keeps it for
  my stay.
- As a **guest in an unoccupied room** (or: as the empty room itself), the
  kiosk shows a calm standby screen with the property's standby texts and
  no previous guest's name — ever.
- As the **property manager**, when I pair a new kiosk it shows me a short
  code, waits patiently while I confirm it, and then simply becomes the guest
  screen; when I revoke a device it drops to the pairing screen on its next
  request and holds no cached content.
- As a **guest during a WiFi/LAN hiccup**, the screen keeps showing what it
  last knew, with a discreet hint, instead of an error page; it heals itself
  when the network returns.
- As the **developer**, `./mvnw quarkus:dev` + `ng serve` gives me a fully
  rendering, paired, occupied kiosk with the familiar example content — no
  manual pairing, ever — and nothing about that convenience can leak into a
  production instance.

## Requirements

### Client consumption & dev topology

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-501 | The frontend consumes `packages/api-client/` by path (TS path alias; no npm manifest change; the client stays generated-only, never edited). The generated `provideApi()` + per-tag services are wired via `provideHttpClient` and a `Configuration` whose `bearerAuth` credential resolves the stored device token at call time. The client's Angular-22 compatibility is pinned by the resolved ceiling check (Assumptions) and re-verified by `ng build`/`ng test` green. | Must |
| REQ-502 | Dev topology: `ng serve` proxies `/api` to the local Quarkus dev instance (plain HTTP, `%dev` only). **CORS is never enabled on the backend** — same-origin in prod, proxy in dev; no backend HTTP-surface change for development. | Must |
| REQ-503 | `%dev`-only backend seeding: a mechanism per D2 seeds (a) one device for room `1` with the SHA-256 hash of a fixed, documented dev token, (b) an active stay on that room (so the dev kiosk is `OCCUPIED`), and (c) the example-property content (branding, translations, 4 action cards — fidelity with the previous `example-property.data.ts` visuals, so reviewed geometry stays comparable). Mechanism per **D2 (approved 2026-08-20)**: the `%dev` `@IfBuildProfile("dev")` seeder bean of 02-design §7. | Must |
| REQ-504 | **`%prod` inertness of the dev seed is proven, not assumed:** the seeding code/SQL is absent from the `%prod` runtime by construction (build-profile exclusion or profile-scoped Flyway location per D2); the existing zero-devices-on-fresh-migration test stays green; the compose smoke additionally asserts a fresh `%prod` instance has zero devices and that the documented dev token answers `401`. The dev token value is treated as public knowledge — its safety derives from its hash never existing in a production database, never from secrecy. | Must |
| REQ-505 | The dev device token reaches the frontend **only** through a dev-scoped mechanism per D3 (Angular `environment.development.ts` file replacement): a production build contains no token literal (checkable: grep over `dist/`), and the dev fallback is consulted only when no stored token exists (**D3 approved 2026-08-20**: `environments/` + `fileReplacements`, the committed literal declared public/dev-only). | Must |

### Pairing & device identity (kiosk side)

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-506 | An unpaired kiosk (no stored token) shows the **pairing screen**: it creates a pairing request, displays the 8-char code prominently for the manager, and polls with the poll secret (held in memory only, never displayed, never persisted) until claimed. On claim it stores the device token and enters the app without a reload. | Must |
| REQ-507 | Pairing resilience: pending (`202`) → keep polling at the pinned cadence; expiry/`404` → automatically request a fresh code and display it; ~~`429` → respect `Retry-After`~~ (**amended 2026-08-24, owner decision:** pairing-request creation is deliberately unthrottled, so the backend emits no `429` on it at all — the `429` response and its `Retry-After` header are gone from `createPairingRequest` in `api/openapi.yaml`, and the kiosk's `cooldown` state went with them; see `06-review.md` → *Addendum 2026-08-24 (third)*. Every create failure now takes the one offline-with-backoff path, which is what this requirement asks for anyway); backend unreachable → retry with backoff and say so honestly on screen. No token, secret or code ever reaches a log or error report (principle 3 applied to the client). | Must |
| REQ-508 | The device token persists on the kiosk in `localStorage` (**D4 approved 2026-08-20**) so a reboot does not re-pair; it is sent only as the `Authorization` bearer of same-origin `/api/**` calls, never rendered after pairing, never logged. | Must |
| REQ-509 | Any `401` on a guest call (revoked/deleted device, wiped DB) drops the kiosk to the pairing screen: stored token **and** last-known-good cache are cleared first — a revoked device renders nothing it learned before. | Must |

### Data layer (the principle-2 swap)

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-510 | `PropertyContentService` is backed by `GET /api/guest/session` instead of the static import; its component-facing surface (`property()`, `settingsConfig()`, `getSectionRoute()`) keeps its semantics so components change minimally. The static `example-property.data.ts` is **deleted** per **D6 (approved 2026-08-20** — delete, no fake-content fallback; its content moves to the dev seed); server payloads are validated at the boundary with the same construction-time strictness the static config enjoyed. | Must |
| REQ-511 | The home screen renders from `GET /api/guest/screens/home`: headline/subtitle/paragraph and card texts arrive **server-resolved** (templates, `{stay.displayName}`, `|time`/`|date` — already resolved per language); the client renders them verbatim and its own `{guestName}` interpolation is removed. 0–4 cards are supported (the static "exactly 4" invariant relaxes to the contract's 0–4) without breaking the reviewed layout. | Must |
| REQ-512 | Branding arrives from the session (`brand.primary/primaryDeep/secondary`) and is validated (hex-colour shape) before being written to `:root` — invalid values are rejected and the token-layer defaults stay in force (input validated at every system boundary; an inline style is an injection surface). | Must |
| REQ-513 | Assets (logo, artwork) are fetched through the generated client **with the `Authorization` header — never a bare `<img src>`** — and rendered from blob/object URLs, honoring the per-id immutable ETag/`Cache-Control`; object URLs are revoked when replaced. A missing/`404` asset renders the existing no-artwork/placeholder fallback, never a broken image. | Must |
| REQ-514 | All localized fields are language-keyed maps over exactly the offered languages: a language switch renders from data already held and **fetches nothing** (existing cross-cutting rule, now proven against the wire). | Must |
| REQ-515 | The sidebar renders the session menu: 0–5 resolved-enabled items in resolved order plus the `settingsEntry` as **fixed chrome** (the server serves it outside `items`; the client renders it structurally, unremovable). Ceiling-resolved enablement is respected by construction — the client never re-derives enablement, it renders what the server resolved. A section absent from the menu is not reachable from any UI affordance. | Must |
| REQ-516 | Steady-state freshness by ETag polling: session and home are revalidated with `If-None-Match` at the pinned cadence; `304` → keep rendering what is held; a changed body → re-render. Manager edits and occupancy flips appear without reload within the pinned interval. | Must |
| REQ-517 | Occupancy: `VACANT` → the **standby screen** (server-resolved standby headline/message; no guest name, no stay-derived value on screen; `screens/home`'s `409 room-vacant` is an expected state, not an error); `OCCUPIED` → the normal app. The flip both ways is driven by the session poll. | Must |
| REQ-518 | Preferences round-trip: `PreferencesService` hydrates from the session (resolved defaults ⊕ the stay's raw override — same default-⊕-choice model, REQ-119/121 behavior preserved incl. the no-flash bootstrap gate and the discard-invalid-values rule); writes go through a new HTTP `PreferenceGateway` implementation calling `PUT /api/guest/preferences` (whole record, optimistic UI, coalesced fire-and-forget, bounded retries; `400`/`409 room-vacant` are silent-normal). `SessionPreferenceGateway` is **deleted**, as its design promised. | Must |

### Offline & degraded operation

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-519 | Last-known-good: the most recent successful session+home payloads are cached on the kiosk per D5; when the backend is unreachable the kiosk keeps rendering them with a **discreet staleness hint** (constitution's kiosk-cache clause, verbatim). The cache stores only guest-API v1 payloads — which contain nothing guest-sensitive — and this boundary is re-checked whenever a new field is cached. (**D5 approved 2026-08-20**: `localStorage`, session+home JSON only, cleared on 401 and home cleared on VACANT.) | Must |
| REQ-520 | Unreachable with **nothing to render** (first boot, or cache cleared): the **connection screen** — honest, guest-presentable, auto-retrying with backoff; recovery is automatic and requires no touch. A paired kiosk never falls back to the pairing screen just because the network flapped (only `401` does that, REQ-509). | Must |

### Production serving (D7 — user decision 2026-08-20, in scope)

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-526 | The Angular **production** build is packaged into the Quarkus jar under `META-INF/resources` via a **plain Maven copy step** (the mechanism the constitution v1.5.0 already decided for the manager UI — mirrored, not reinvented; no new dependency, no Quinoa). Kiosks load the guest app at `/` on the same HTTPS origin as `/api`. A **SPA fallback** serves `index.html` for guest deep links (e.g. a reload on `/einstellungen`) — and **never** rewrites `/api/**`, `/q/**`, or the reserved future manager path `/manage/**` (a wrong or missing API path must keep answering as the API does today, not as HTML). The guest app owns `/`; a future manager app at `/manage/` slots in with zero rework of this layout. | Must |
| REQ-527 | Build ordering is pinned and honest: the copy step consumes a **prebuilt** `frontend/dist/frontend/browser` (built by `ng build` before `./mvnw package`) via a Maven profile **auto-activated on the existence of that directory** — a plain backend-only `./mvnw test`/`package` without a frontend build stays green and simply ships no app (dev loops don't acquire a Node dependency). The shippable-artifact workflow (frontend build → `./mvnw package` → image build) is documented in `backend/README.md`, and the packaged jar is bit-identical in its backend halves whether or not the app is bundled. | Must |
| REQ-528 | The compose smoke asserts the serving end to end: `GET /` over HTTPS returns the kiosk app (index + hashed assets), a guest deep link returns `index.html` (SPA fallback), `/api/**` behavior is byte-for-byte unchanged (anon 401s, pairing ops, health), and `/q/openapi` stays 404 in `%prod`. The served bundle contains no dev token (REQ-505's grep applies to the copied artifact too). | Must |

### Design, quality, docs

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-521 | The three new screens (pairing, standby, connection) are designed in-repo against the existing system — semantic tokens only, glassmorphism, type scale, touch minimums, de+en copy in the app voice — and specified under the D1 convention **before** implementation; they must never be mistakable for Figma extractions. No design-tool (MCP) request is spent. (**D1 approved 2026-08-20**: `docs/design/screens-inhouse/` + provenance banner; specs written 2026-08-20 — `pairing.md`, `standby.md`, `connection.md` — with the `design-sheet.md` 📋 rows and the `--color-status-offline` row in `tokens.md` → In-house tokens.) | Must |
| REQ-522 | Every screen — existing and new — holds the no-scroll rule (`scrollWidth ≤ clientWidth ∧ scrollHeight ≤ clientHeight` at 1024×768 and 1280×832, measured in a real browser at review); Home's signed-off geometry is unchanged with server-fed content (re-measured against the recorded baselines). Status-bar clearance holds on the new screens. | Must |
| REQ-523 | Offline principle preserved: no external runtime request anywhere; all new UI strings (pairing instructions, staleness hint, connection screen, standby chrome) resolve through `TranslationService` in both catalogues — no literal in a template; new screens work identically in both themes. | Must |
| REQ-524 | Every implemented behavior has a Vitest test (pairing state machine incl. expiry/retry/claim, token store, session service ETag/304/poll handling, 401 wipe, cache read/write + staleness, content mapping/validation incl. brand-hex rejection, HTTP preference gateway incl. 409-normal, occupancy flip, asset blob handling); collaborators are DI doubles per `frontend/CLAUDE.md`; `ng build` and `ng test` green. | Must |
| REQ-525 | Docs move with the code: `docs/system-context.md` (components, persistence — the kiosk now persists a device token and a content cache; seams updated: the two backend seams are now *closed*), `docs/features/index.md`, the D1 screen specs, `frontend/README.md` dev workflow (backend + proxy + seeded device), and the backend README's dev-seed note. Any new `frontend/CLAUDE.md` entry is proposed as exact text and written only after approval. | Must |

## Acceptance criteria (testable)

- **REQ-501/502:** `ng build` + `ng test` green with the client imported via
  the alias; `git diff packages/api-client/` empty; `ng serve` +
  `quarkus:dev` renders the app end to end over the proxy with no CORS
  config anywhere in `backend/`.
- **REQ-503/504:** fresh `quarkus:dev` boot → the seeded device
  authenticates, session says `OCCUPIED`, home shows the example content;
  `%test` suite (incl. zero-devices migration assertion) green **unchanged**;
  compose smoke: fresh `%prod` boot → `SELECT count(*) FROM device` = 0 and
  the dev token → `401`.
- **REQ-505:** `grep -r <dev-token> frontend/dist/` after a production build
  → no match; dev build boots straight into content with empty storage.
- **REQ-506/507:** with no stored token the pairing screen appears, shows a
  code, polls; confirming via the manager API turns the kiosk into the guest
  app without reload; letting the code expire yields a fresh code unattended;
  stopping the backend shows the honest retry state; nothing token-shaped in
  any console/log output.
- **REQ-508/509:** reload/reboot keeps the kiosk paired; revoking the device
  → next poll drops to pairing with storage (token + cache) verifiably empty.
- **REQ-510–515:** components render server content: seeded dev data matches
  the previous static visuals; disabling a menu item at property scope
  removes it from the sidebar within one poll interval; settings entry
  remains regardless; card count 0 and 4 both lay out without overflow;
  brand `#zz0000` → defaults stay; language switch produces zero network
  entries in devtools.
- **REQ-516/517:** manager edit appears without reload ≤ the pinned
  interval; deleting the active stay flips the kiosk to standby (and back on
  re-create); steady state shows `304`s in the network log.
- **REQ-518:** changing language/appearance on the kiosk lands on the stay
  (visible in the next session read); vacant-room write → `409` and the UI
  keeps the choice silently; `SessionPreferenceGateway` no longer exists in
  `src/`.
- **REQ-519/520:** kill the backend mid-session → the app keeps rendering
  with the staleness hint; restart → hint disappears unaided; empty cache +
  dead backend → connection screen, which recovers unaided.
- **REQ-526/527:** jar built after `ng build` contains
  `META-INF/resources/index.html`; `./mvnw package` without `frontend/dist`
  is green and contains none; in a served instance `GET /einstellungen` →
  `index.html`, `GET /api/nonsense` → API-shaped 401/404 (never HTML),
  `GET /manage/x` → not rewritten to the guest app.
- **REQ-528:** compose smoke: `curl --cacert <CA> https://<host>/` → the app;
  deep link → `index.html`; the existing API/TLS smoke assertions unchanged;
  `grep <dev-token>` over the image's `META-INF/resources` → empty.
- **REQ-521–523:** the three screen specs exist under the approved location
  with provenance banners; measure run: every route × theme × language ×
  {1024×768, 1280×832} scroll-clean; Home baselines unchanged; `grep` for
  external hosts in `dist/` clean; no German literal in any template.
- **REQ-524/525:** `ng test` green with the new suites; system-context/index/
  READMEs updated in the same change.

## Technical constraints

Anchored to `docs/constitution.md` v1.5.1: principle 1 (all content from the
API is per-property data; the dev seed is data, not code paths), principle 2
(the swap happens in services; components stay presentational), principle 3 +
the kiosk-cache constraint (nothing guest-sensitive cached or logged; the
token never logged), principle 4 (offline-first: bundle-complete fonts/icons,
degrade without network; untrusted LAN: the token is the only key, HTTPS in
prod), principle 5 (semantic tokens only on the new screens), principles 6/7
(tests, traceability). Frontend stack constraints verbatim (standalone,
zoneless, signals, plain CSS, Vitest; no new dependency — everything here
rides Angular platform features + the already-approved packages). No
dependency-manifest edits. `frontend/CLAUDE.md` in full (host-as-flex-item,
no-scroll by construction, i18n rules, one place writing
`document.documentElement`, DI-double testing). Backend touches are limited
to the D2 seed mechanism and the D7 packaging (the Maven copy profile +
the SPA-fallback infrastructure adapter — BCE/`backend/CLAUDE.md` rules
apply to any Java code either adds); no contract change. The D7 copy step
is the constitution-v1.5.0-decided mechanism mirrored, not a new one; the
`pom.xml` change it needs is build-plugin configuration of an already-used
core plugin, **not** a dependency addition — if implementation finds an
actual new dependency is needed, it stops with ⏸.

## Risks & open questions

~~The decision batch (D1–D9)~~ — **fully answered by the user 2026-08-20.**
D1–D6, D8, D9 approved exactly as recommended; **D7 changed**: production
serving is **in scope** (*"include it here so we will not forget it"*) — the
separate-static-server alternative was discussed and rejected (an extra
container and a second TLS surface on a one-box appliance, for no benefit),
and the reverse-proxy variant was rejected because it would force the
reviewed TLS bootstrap to move. Binding record incl. verbatim resolutions:
[02-design.md → Resolved decisions](02-design.md). REQ-526–528 carry the D7
scope; no `[NEEDS CLARIFICATION]` remains in this document.

**Risks**

- **The variable-content layout risk:** the reviewed Home layout was built
  against exactly 4 cards and 5 menu items; the contract allows 0–4 and 0–5.
  Mitigated by design rules + measurement, but it is the likeliest place a
  regression hides.
- **Bootstrap ordering gets harder:** branding, defaults and preference now
  all arrive from one async fetch. The no-flash guarantee (REQ-126's
  structure) must be preserved with a bounded wait and honest fallback
  states — designed explicitly in 02-design §5.
- **Object-URL lifecycle** (leaks if not revoked) and **blob caching vs. the
  browser HTTP cache** — the client fetch carries auth headers, so caching
  behavior must be verified at implementation (pinned as
  verify-at-implementation in the design).
- **Clock skew** between kiosk and server affects nothing here directly
  (occupancy is server-derived), but the pairing screen's countdown (if any)
  must use server-supplied `expiresAt` defensively.
- **The kiosk browser must trust the instance CA in prod** — outside this
  feature's code, documented as the provisioning step; dev is HTTP and
  unaffected. Carried, not solved. (With D7 in scope this is now the *only*
  missing link between a fresh box and a rendering kiosk.)
- **SPA fallback correctness (D7)** — a rewrite that is too eager turns API
  errors into HTML (breaking every client's error handling) or swallows the
  future `/manage/` path. Mitigated by the pinned exclusion rules
  (02-design §11) and dedicated `@QuarkusTest`s on both the rewrite and the
  never-rewrite paths.
- **Build-ordering ergonomics (D7)** — the auto-activated Maven profile
  means "jar without app" is a silent possibility; mitigated by the compose
  smoke asserting `/` serves the app, and by the documented workflow.
- **`%dev` seed drift vs. frontend expectations:** the dev seed duplicates
  the example content; if the design tokens/copy evolve, the seed must move
  with them. Accepted — it is dev-only convenience, and the fidelity
  criterion pins it at review time.
