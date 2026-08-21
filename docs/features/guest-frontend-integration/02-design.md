# Design: Guest Frontend Integration
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **TODO:** [03-todo.md](03-todo.md)
**Status:** Draft — **decision-complete**: batch D1–D9 answered by the user 2026-08-20 (§10 is the binding record; D7 changed to in-scope, designed in §11)

> HOW for [01-prd.md](01-prd.md). Settled ground referenced, not re-made: the
> guest contract as built (`device-pairing-and-guest-api/02-design.md` —
> operations, vacancy `409 room-vacant`, indistinguishable pairing `404`s,
> ETag semantics), `appearance-and-language/02-design.md` §6 (the
> default-⊕-choice preference model, bootstrap gate, fire-and-forget write),
> `home-screen-prototype/02-design.md` (component graph, measured geometry),
> `frontend/CLAUDE.md`, `docs/design/tokens.md` (wins conflicts),
> `docs/design/design-sheet.md` (motion, status colours, voice),
> `docs/design/dark-theme.md`.

## 1. The shape of the change

Principle 2's bet was that this feature touches the **content and transport
layers only**. It mostly holds; the honest list of what changes:

| Layer | Today | After |
|---|---|---|
| `PropertyContentService` | `signal(EXAMPLE_PROPERTY)` | fed by `GuestDataService` (below); same public surface |
| `PreferenceGateway` binding | `SessionPreferenceGateway` (sessionStorage) | `HttpPreferenceGateway` (client `PUT`); stand-in **deleted** |
| App bootstrap | apply defaults → hydrate (1 s timeout) | token check → session fetch (bounded) → defaults/branding/hydrate → paint (§5) |
| Components | read services | unchanged, except: Home's `{guestName}` interpolation removed (server resolves), sidebar takes a variable item list, images bind to object URLs |
| New | — | `core/api/` (client wiring, token store), `core/guest/` (`GuestDataService`: fetch/poll/cache/state machine), `features/pairing/`, `features/standby/`, `features/connection/` |
| Backend | — | the `%dev` seed (§7) **and the D7 packaging/serving** (§11: Maven copy profile + SPA-fallback adapter); **no contract change** |

## 2. New frontend architecture

```
core/api/
  provide-guest-api.ts     provideHttpClient() + provideApi(Configuration{
                             basePath: '' (same-origin / dev proxy),
                             credentials: { bearerAuth: () => tokenStore.token() }})
  device-token.store.ts    DeviceTokenStore — signal<string|null>, persisted per D4
                           (localStorage, key 'gxp.device-token'); set() on claim,
                           clear() on 401; the ONLY reader is the credential fn above
core/guest/
  guest-data.service.ts    GuestDataService — owns ALL wire I/O for content:
                           fetchSession()/fetchHome() via GuestService, ETag
                           revalidation loop, last-known-good cache (D5),
                           connection state machine (§4), occupancy signal
  guest-content.mapper.ts  pure fns GuestSession/GuestHomeScreen → the internal
                           PropertyConfig-shaped model + validation (exported
                           for spec, like resolveProperty/validateConfig today)
  guest-asset.service.ts   authorized blob fetch → object URL signals; revoke-on-
                           replace; per-id memo (ETag/Cache-Control do the rest)
core/preferences/
  http-preference-gateway.ts  save() → putGuestPreferences (whole record);
                              load() → resolved from the session payload handed
                              over by GuestDataService at hydration (no extra GET);
                              clear() → no-op client-side (stay-anchored server-side)
features/pairing/           PairingComponent + pairing.service.ts (state machine §3)
features/standby/           StandbyComponent (standby texts from session)
features/connection/        ConnectionComponent (no-cache disconnected state)
```

Rules preserved: `DeviceTokenStore` and `HttpPreferenceGateway` stay DI
leaves; `PropertyContentService` remains the only content door for
components; nothing outside `core/guest/` imports the generated client's
models except the mappers and `core/api/`; `document.documentElement` is
still written only in `app.component.ts`.

**Client import path:** TS path alias `@gxp/api-client` →
`../packages/api-client` in `frontend/tsconfig.json` (+ `include` for the
app tsconfig). This is the recorded "consumed by path" intent of the
generation setup — no manifest change. The alias keeps every import
greppable and makes accidental deep-editing of generated code visible in
review.

## 3. Pairing state machine (kiosk side)

```
UNPAIRED (no stored token)
  → CREATING    POST /api/pairing/requests
      201 → WAITING(code, secret in memory, expiresAt)
      429 → COOLDOWN(Retry-After) → CREATING
      network error → OFFLINE_PAIRING (retry backoff 5→30 s) → CREATING
  WAITING: poll GET /api/pairing/requests/{code} + GXP-Pairing-Secret
      every 3 s
      202 → WAITING (show code, calm "waiting for confirmation")
      200 → CLAIMED: tokenStore.set(deviceToken) → boot the guest app (no reload)
      404 → code expired/consumed → CREATING (fresh code, unattended)
      network error → OFFLINE_PAIRING → resume WAITING with the same
        code/secret until expiresAt, then CREATING
```

Pins: poll cadence **3 s**; create-retry backoff **5 s → 30 s** (doubling);
the poll secret lives in a component-scoped field, never in any storage; the
countdown (if shown) derives from server `expiresAt` vs. local clock,
clamped ≥ 0. A `401` can not occur here (pairing ops are unauthenticated).

## 4. Content state machine & polling

```
BOOT: token? ──no──▶ PAIRING (§3)
       │yes
       ▼
  fetch session (timeout 4 s)
       ├─ 200 → ONLINE          render; start poll loop
       ├─ 401 → wipe token+cache → PAIRING          (REQ-509)
       ├─ error/timeout + cache → STALE             render cache + hint
       └─ error/timeout, no cache → DISCONNECTED    connection screen
ONLINE:  every 30 s revalidate session (If-None-Match) and, when
         OCCUPIED, home; 304 → no-op; 200 → re-render + refresh cache
         error → STALE (keep rendering, discreet hint, keep polling)
STALE:   next 200 → ONLINE (hint disappears);  401 → wipe → PAIRING
DISCONNECTED: retry backoff 5→30 s; 200 → ONLINE
occupancy (from session): VACANT → standby route/state; OCCUPIED → app;
         home is fetched only while OCCUPIED (vacant home = 409 by contract,
         never requested, never an error)
```

Pins: session/home poll **30 s** (manager edits and occupancy flips appear
within that; steady state is two `304`s per interval — by design of the 4xx
feature, polling is what the ETags are for); fetch timeout **4 s**; cache =
`localStorage` keys `gxp.cache.session` / `gxp.cache.home` (JSON verbatim +
`etag` + `storedAt`), written on every 200, cleared on 401 (D5). The
staleness hint is chrome (status-bar-adjacent, §9.4), not a screen.

**Vacancy data-sparing on the client too:** entering `VACANT` clears the
cached *home* payload and any rendered guest-derived value; the standby
screen renders only property-level texts. Returning to `OCCUPIED` refetches
home.

## 5. Bootstrap sequence (no-flash guarantee preserved)

Replaces the current initializer's body; the structure (Angular holds
bootstrap until the promise settles) is kept:

```
provideAppInitializer:
  1. token = DeviceTokenStore.load()            // sync
  2. no token → resolve; router lands on /pairing (guard) — pairing screen
     needs no property data (its copy is app chrome via TranslationService,
     its look is token-layer defaults — deliberately brandless)
  3. token → GuestDataService.bootstrap():
       session := fetch (4 s timeout) | cache | null
       session != null →
         content.applyServerConfig(mapped)      // replaces applyPropertyDefaults source
         preferences.applyPropertyDefaults(settings from session)
         preferences.hydrateFrom(session.preference)   // no separate gateway load
         (branding + <html lang/data-theme> effects run before first paint,
          exactly as today — the first painted value is final)
       session == null → resolve; router guard lands on /connection;
         GuestDataService keeps retrying and the app "arrives" when it heals
```

`PreferencesService.hydrate()`'s late-arrival/timeout semantics survive
unchanged — the "stored preference" is simply delivered with the session
instead of by a second read. A preference that later changes server-side
(same stay, other channel) is applied on poll only if the guest has no newer
local choice (the existing never-overwrite-a-fresh-choice rule).

**Routing:** `/pairing`, `/standby`, `/connection` are top-level routes
*outside* `ShellComponent` (full-screen states, no sidebar); guards driven by
`DeviceTokenStore` + `GuestDataService` state redirect in both directions.
Existing shell/child routes are unchanged.

## 6. Contract mapping (client side of the settled contract)

| Server (generated model) | Frontend |
|---|---|
| `GuestSession.property.name/brand/logoAssetId` | branding primitives (validated `#rrggbb` else rejected), logo object URL |
| `.settings.languages[]/appearances[]/default*` | `PropertySettingsConfig` 1:1 (shapes match by design) |
| `.preference {language?, appearance?}` | `StoredPreferences` for hydration; values outside offered sets discarded (existing rule) |
| `.menu.items[] {key,label,icon,route,sortIndex}` | sidebar items (0–5, server order); `key` = section id for `getSectionRoute()` |
| `.menu.settingsEntry` | the fixed Einstellungen entry (structural, unremovable) |
| `.occupancy / .guestDisplayName / .standby` | state machine §4 / standby screen |
| `GuestHomeScreen.headline/subtitle/paragraph` | heading block — rendered verbatim (server-resolved; client interpolation removed) |
| `.artworkAssetId` | hero artwork object URL; absent → existing no-artwork fallback |
| `.actionCards[] {icon,title,description,targetKey,targetRoute}` | action cards 0–4; `targetRoute` used directly (route resolution already server-side) |
| `PUT /api/guest/preferences` | `HttpPreferenceGateway.save()`; `400`/`409` swallowed as designed |
| `GET /api/guest/assets/{id}` | `guest-asset.service` blob fetch |

Validation at the boundary (mapper): required fields present, localized maps
non-empty for the default language, brand hex shape, icon names looked up
against the registered icon map (unknown icon → item rendered with a neutral
fallback glyph, never a crash — the server's catalogue names are already the
registered Phosphor names). Unlike the static config, a *server* payload
failing validation must not throw the app away: it counts as a failed fetch
(→ STALE/DISCONNECTED path) and is surfaced to no log that could carry
content (principle 3).

**Verify-at-implementation (pinned):** (a) `HttpClient` blob responses +
`If-None-Match` interplay with the browser HTTP cache for authorized asset
GETs (expected: browser cache handles 304 transparently; memoized object
URLs make it moot in steady state); (b) `provideApi`'s `Configuration`
credential fn is consulted per request (code-read says yes); (c) proxy
behavior for the `202` poll response body-less passthrough.

## 7. Dev environment (D2/D3) — evaluated

**Requirementss:** no manual pairing; dev-visual parity with the previous
static app; **provable `%prod` inertness** (REQ-503–505).

**Recommended: Option A — `%dev` build-profile-gated seeder bean.**
`de.gxp.devseed.DevDataSeeder` (test-support-adjacent, but `src/main/java`),
annotated `@IfBuildProfile("dev")`, `StartupEvent` observer, idempotent
(seed only when absent). Seeds, reusing the existing package APIs/hashing
(never a second hash implementation):

1. the example-property content: brand `#f45aa8/#cc49af/#8a5cf6`,
   translations (de+en hero/headline/paragraph verbatim from the old
   `example-property.data.ts`), 4 action cards — via the owning Controls'
   existing write paths where public, else a small package-private seed path;
2. an active stay on room `1` (`checkIn = now−1d`, `checkOut = now+30d`,
   display name `Anna & Tom` as today);
3. one device for room `1`: `sha256("gxp-dev-device-token")` inserted
   hash-only (the literal is documented in the README as *public, dev-only*).

*Why over Option B (the user's Flyway suggestion —
`%dev.quarkus.flyway.locations=db/migration,db/dev` + seed SQL):*

| | A: `%dev` seeder bean | B: `%dev` Flyway location |
|---|---|---|
| `%prod` inertness | **Build-time**: `@IfBuildProfile("dev")` beans are removed from the container at augmentation — the seeder does not exist in the prod artifact | Runtime config: rests on the `%dev.`-prefixed locations line never being widened |
| Token hash | computed by the existing code path | a precomputed hex literal pasted into SQL (drift-prone, copy-paste hazard into a real migration) |
| Flyway history | untouched | dev DBs carry an extra migration row; a dev-only version number to keep clear of real `V5+` forever (repeatable `R__` mitigates but still splits history per profile) |
| Content seed | plain Java against existing invariant-enforcing paths | raw SQL that must re-state invariants by hand |

Both are inert-by-profile; A's inertness is *stronger to prove* (artifact
inspection + the bean simply absent) and it reuses invariants instead of
bypassing them. Option C (compose-level provisioning) is rejected: dev runs
Dev Services, not compose. **Proof obligations either way (REQ-504):** the
existing `%test` zero-devices migration assertion stays green (`%test` gets
no seed — the suites' device counts must not move); compose smoke asserts
zero devices + dev-token `401` on a fresh `%prod` boot.

**Frontend side (D3):** `frontend/proxy.conf.json` (`/api` →
`http://localhost:8080`) wired via `angular.json` serve options; new
`src/environments/environment.ts` (`devDeviceToken: null`) +
`environment.development.ts` (`devDeviceToken: 'gxp-dev-device-token'`) with
`fileReplacements` on the development configuration (the `ng serve`
default). `DeviceTokenStore` consults the environment value **only when
storage is empty**. Prod build: token literal grep-absent from `dist/`.

## 8. What gets deleted / relaxed

- `example-property.data.ts` (content → dev seed), `SessionPreferenceGateway`
  (+ its provider line), the `{guestName}` interpolation, the
  "exactly 5 / exactly 4" validation arms (→ "0–5 / 0–4, server-ordered").
- `property-content.service.spec.ts`'s deliberate data-file import goes with
  the data file; the mapper spec takes over that duty against fixture
  payloads (mirroring the seeded shape).

## 9. New screens — designed in-repo (D1)

**Specs written 2026-08-20** under the approved location:
[`docs/design/screens-inhouse/pairing.md`](../../design/screens-inhouse/pairing.md),
[`standby.md`](../../design/screens-inhouse/standby.md),
[`connection.md`](../../design/screens-inhouse/connection.md) (the connection
spec also carries the staleness pill) — those files are the binding specs;
this section is the summary intent. One refinement made at spec-writing
time, recorded as a deviation from the sketch below: **the status colour is
used for decorative indicators only, never for text** — `#A79DBA` measures
~2.2 : 1 on the light surfaces, so status *text* stays on
`--color-text-secondary` and the offline colour lives in dots/glyphs beside
it (see the `--color-status-offline` row in `tokens.md` → In-house tokens).

**9.1 Pairing** — deliberately **brandless** (no property data yet):
token-layer default mesh background, one centered glass panel
(`--color-surface-glass`, `--radius-2xl`, `--border-glass`, `--blur-glass`,
`--shadow-panel`), max-width ≈ 30rem. Content top-down: `--text-h1` title
("Gerät koppeln" / "Pair this device"), `--text-body` instruction line, the
**code** as the hero — 8 chars, `--text-display` (3rem) ExtraBold,
`letter-spacing 0.15em`, `--color-text-primary`, grouped `XXXX XXXX` for
readability; beneath it a `--text-body` `--color-text-secondary` status line
(waiting / expired-renewing / retry state) with a subtle pulse
(`180 ms`-class motion per design-sheet, `prefers-reduced-motion`
respected). No buttons — the flow is unattended by design. States: WAITING /
COOLDOWN / OFFLINE_PAIRING swap only the status line and never move the code
block (no-jump rule). Status colours: first use of design-sheet's status
palette as the **new in-house semantic role** `--color-status-offline`
(`#A79DBA`) — on the **status dot only** (decorative; the line's text stays
`--color-text-secondary` — see the §9 intro refinement). Recorded under the
D1 convention.

**9.2 Standby** — property-branded, calm: mesh background, centered block —
property logo (when set) at `--size-icon-tile` scale, standby **headline**
(`{property.name}` resolved server-side) at `--text-display`, standby
**message** at `--text-body-lg` `--color-text-secondary`; the status bar
**stays** (clock, language, appearance — all still functional; a preference
write while vacant fails silently as designed). No sidebar, no cards,
nothing touchable except the status bar; any tap elsewhere does nothing
(vacant rooms expose no navigation — data-sparing extends to UI surface).
Both themes; no-scroll by construction (single centered column, capped
widths).

**9.3 Connection** — honest but guest-presentable: mesh background, one
small glass panel, `WifiHigh`-family glyph slashed variant (registered
Phosphor `phosphorWifiSlash`) at `--size-icon-lg` in
`--color-status-offline`, `--text-h1` "Keine Verbindung" / "No connection",
`--text-body` explanation ("Die Anzeige verbindet sich automatisch neu." /
auto-reconnect copy). Auto-retry only; no button.

**9.4 Staleness hint** (chrome, not a screen): a small pill under the
status-bar cluster, `--text-status` on `--color-surface-glass`,
`--radius-md`, text "Zuletzt aktualisiert HH:MM" / "Last updated HH:MM" from
the cache's `storedAt` — discreet per the constitution's wording; appears in
STALE only. Must clear the reserved top-right geometry (measured, both
viewports).

All copy goes through `TranslationService` (de baseline + en), voice per
design-sheet (warm, short, informal *ihr/euch* where addressing guests;
pairing copy addresses the *manager* — neutral Sie-free imperative).

## 10. Resolved decisions — answered by the user, 2026-08-20

The full batch was answered in one round. **D1–D6, D8, D9 were approved
exactly as recommended**; **D7 was changed**. This table is the binding
record; the resolutions are now the design:

| # | Decision | Resolution (user, 2026-08-20) |
|---|---|---|
| D1 | Location/convention for in-repo screen designs | **Approved as recommended:** `docs/design/screens-inhouse/` (sibling of `screens/`, which stays Figma-only), template = `screens/_template.md` + the mandatory provenance banner ("Designed in-repo — no Figma counterpart; a re-import will not produce this; a later Figma extraction supersedes it and the file becomes the deviation log" — the `dark-theme.md` precedent applied to screens). `design-sheet.md` carries 📋 rows for these screens; new tokens they need land in `tokens.md` → "In-house tokens". **Executed 2026-08-20:** the three specs, the 📋 rows and the `--color-status-offline` row are written |
| D2 | Dev-seed mechanism | **Approved as recommended:** the `%dev` `@IfBuildProfile("dev")` seeder bean (§7) — build-time exclusion as the inertness proof, existing hashing/write paths, Flyway history untouched. The Flyway-location alternative (the user's original suggestion) is recorded in §7 as evaluated and not chosen |
| D3 | Dev token → frontend | **Approved as recommended:** Angular `environments/` + `fileReplacements`; the committed literal `gxp-dev-device-token` is declared public/dev-only; the prod bundle is grep-proven token-free |
| D4 | Kiosk token persistence | **Approved as recommended:** `localStorage` (survives reboot — the point of pairing; revocation is server-side, next-request effective; physical access is the open kiosk-lockdown item) |
| D5 | Last-known-good cache | **Approved as recommended:** `localStorage`, session+home JSON only (non-sensitive by the 4xx pin), cleared on 401, home cleared on `VACANT`; staleness pill per the connection spec; boundary re-checked whenever a new field is cached |
| D6 | Static content fate | **Approved as recommended:** delete `example-property.data.ts` + `SessionPreferenceGateway`; no fake-content fallback; the content moves to the dev seed |
| D7 | Prod serving of the guest app | **CHANGED — in scope, this feature** (*"include it here so we will not forget it"*). The recommended deferral was rejected after discussing the alternatives: a **separate static server** on the same box was rejected (an extra container and a **second TLS surface** on a one-box appliance, for no benefit), and the **reverse-proxy variant** was rejected because it would force the reviewed TLS bootstrap (`entrypoint-jvm.sh` + TLS-registry config, smoke-proven) to move. Decided: the Angular production build enters the Quarkus jar via a **plain Maven copy into `META-INF/resources`** — the mechanism constitution v1.5.0 already decided for the manager UI, mirrored — served at `/` on the same HTTPS origin as `/api`, with the future manager app reserved at `/manage/`. Design in **§11**; REQ-526–528; the minimal-backend ground rule is relaxed exactly this far |
| D8 | Preference write-back | **Approved as recommended:** `HttpPreferenceGateway` (§2/§6) — load-from-session, whole-record PUT, silent 400/`409 room-vacant`; stand-in deleted |
| D9 | New-screen inventory | **Approved as recommended:** pairing, standby, connection + the staleness pill as chrome — nothing more (no dedicated "revoked" screen; revocation lands on pairing) |

**No approval gate remains open.** No new dependency anywhere (the D7 copy
step configures an already-used core Maven plugin; the SPA fallback is
hand-written platform code on the Vert.x router Quarkus already ships); no
datastore/infra change beyond the recorded decisions; no token-realisation
change; `docs/design/**` edited only per the approved D1 convention. Should
implementation contradict the no-new-dependency claim, `dev` stops with ⏸
per the constitution.

## 11. Production serving of the guest app (D7 — decided 2026-08-20)

**Path layout (pinned):**

| Path | Serves | Owner |
|---|---|---|
| `/` + hashed asset files | the guest kiosk app (`index.html`, `*.js`, `*.css`, `assets/`, fonts) | this feature |
| `/api/**` | the REST API — untouched | existing |
| `/q/**` | Quarkus management (health/ready; openapi 404 in `%prod`) — untouched | existing |
| `/manage/**` | **reserved** for the future manager app (its own `index.html` under `META-INF/resources/manage/`, Angular `base-href /manage/`) — nothing served there today, and the guest SPA fallback must never claim it | `manager-frontend` |

**Copy step (REQ-526/527):** a `maven-resources-plugin` execution in
`backend/app/pom.xml` (an already-bound core plugin — configuration, not a
dependency), inside a profile `bundle-guest-app` **auto-activated by
`<file><exists>` on `../../frontend/dist/frontend/browser/index.html`**
(the Angular application-builder output path, verified 2026-08-20). It
copies that directory into `${project.build.outputDirectory}/META-INF/
resources` during `process-resources`, so the app rides `target/quarkus-app`
into the existing `Dockerfile.jvm` **unchanged**. Without a frontend build
the profile stays inactive: backend-only `./mvnw test`/`package` remain
Node-free and green. Shippable-artifact workflow, documented in
`backend/README.md`: `ng build` (production) → `./mvnw package` → image
build → compose. **Quinoa stays rejected** (constitution v1.5.0 rationale,
unchanged).

**SPA fallback (REQ-526):** Quarkus serves `META-INF/resources` statically,
but a guest deep link (`/einstellungen` on reload) needs `index.html`.
Zero-dependency mechanism: a small infrastructure adapter
(`de.gxp.web.SpaRoutingHandler`, beside the `ApiErrorMappers`/filter
precedent — not a Boundary, no business logic) observing the Vert.x
`Router` (`@Observes Router`, quarkus-vertx-http is already on the
classpath via `quarkus-rest`) that registers a **late-priority** GET
handler rerouting to `/index.html` **only** when all of: method GET; path
starts with none of `/api/`, `/q/`, `/manage/`; path contains no `.`
(file requests fall through to static resources / real 404s); request
accepts `text/html`. Everything else is untouched — an unknown `/api/x`
keeps answering exactly as today (the API's own 401/404, never HTML).
When no app is bundled (`%dev` backend-only, `%test`), the reroute target
does not exist and the handler must **not** loop — pinned: reroute only if
the bundle is present (an index-resource existence check at startup), else
the handler is inert. Both halves get `@QuarkusTest`s (with a test-classpath
`META-INF/resources/index.html` fixture for the rewrite half).

*Verify-at-implementation (pinned):* the exact route-order/priority API for
"after REST routes, before/instead-of the default 404" on Quarkus 3.33's
router (route `order()` semantics), and that the reroute preserves the
`Cache-Control` posture (`index.html` must not be immutable-cached; hashed
assets may — set `no-cache` on the rerouted response if the default
differs). Findings go to handoff.

**Smoke (REQ-528):** extend the compose smoke: `GET /` over HTTPS (with the
fetched CA) → the app shell; `GET /einstellungen` → `index.html`;
`GET /api/system/info` anon → 401 JSON (not HTML); `/q/openapi` → 404;
`grep` the image's `META-INF/resources` for the dev token → empty.

**Exposure note (security review category):** this adds one unauthenticated
surface — the static app bundle. It contains no secret by construction
(REQ-505's grep proves the dev token's absence; property data still arrives
only via the authenticated API). Anyone on the LAN could already fetch the
bundle's equivalent from any kiosk; serving it changes room-scoping not at
all.

## 12. Risks & trade-offs

- **One fetch feeds everything at boot** — a single point of failure,
  mitigated by cache/connection states; the 4 s bound keeps worst-case boot
  honest.
- **Polling at 30 s costs two `304`s/interval/kiosk** — the 4xx design
  explicitly budgeted this; no client-side scheduler cleverness.
- **`localStorage` on a guest-accessible device** holds: device token (the
  device's own credential — by the 4xx PRD's own classification not guest
  data), non-sensitive content JSON, and the existing preference record is
  *gone* (server-owned now). Kiosk lockdown remains the operational control.
- **Object URLs + zoneless**: asset arrival must be a signal write; leaked
  URLs are a slow memory leak on a 24/7 kiosk — revoke-on-replace is a rule,
  not a nicety.
- **The dev seed duplicates example content** — accepted drift risk, pinned
  by the review-time fidelity check.
- **Icon names from the wire** — defended by the fallback-glyph rule (§6);
  the seeded catalogue already uses registered names.
