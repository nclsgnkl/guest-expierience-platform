# TODO: Guest Frontend Integration
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md)
**Status:** **Complete (05_implement, 2026-08-20)** — T-01…T-29 all done; ready for 06_review. (History: unblocked 2026-08-20 by the D1–D9 batch; T-01/T-02 by the planner.)

> Every task references its `REQ-ID`(s). Design decisions are referenced from
> [02-design.md](02-design.md) (§ numbers), never re-decided here. Validate =
> how `dev` proves the step before moving on. Vitest specs sit beside their
> units per `frontend/CLAUDE.md`; collaborator doubles via DI, never
> `vi.mock`.

## Phase 0 — Decisions & design specs

- [x] **T-01** *(done 2026-08-20, planner — D1 approved)* The three in-repo
      screen specs are written: `docs/design/screens-inhouse/pairing.md`,
      `standby.md`, `connection.md` (the latter incl. the staleness pill),
      each with the provenance banner; `design-sheet.md` carries the 📋 rows
      and `tokens.md` → "In-house tokens" the `--color-status-offline` row.
      `dev` implements **against those specs**, not against 02-design §9's
      summary. — REQ-521
- [x] **T-02** *(done 2026-08-20 — D2/D3 approved as recommended)* Mechanism
      = the `%dev` `@IfBuildProfile("dev")` seeder bean (02-design §7);
      dev-token literal = `gxp-dev-device-token` (public/dev-only).
      — REQ-503/505

## Phase 1 — Backend dev seed (backend work is exactly this + Phase 4b, nothing else)

- [x] **T-03** *(done 2026-08-20, dev — `DevDataSeeder` + dev-profile `@QuarkusTest`, 2/2 green, seed isolated to its own DB)* Implement the `%dev` seed per 02-design §7 (recommended:
      `de.gxp.devseed.DevDataSeeder`, `@IfBuildProfile("dev")`, idempotent;
      example content + active stay + device `sha256("gxp-dev-device-token")`
      hash-only, reusing existing hashing/write paths; `backend/CLAUDE.md`
      rules apply). — REQ-503
      Files: `backend/app/src/main/java/de/gxp/devseed/**`.
      Validate: `./mvnw quarkus:dev` → `curl -H "Authorization: Bearer
      gxp-dev-device-token" localhost:8080/api/guest/session` → 200,
      `occupancy: OCCUPIED`, pink brand, 4 cards; restart → still exactly one
      device (idempotent).
- [x] **T-04** *(done 2026-08-20, dev — full suite 176/176 green incl. zero-devices assertion (%test DB seedless, dev-profile seed isolated); README token note written; compose-smoke half folded into T-29)* Prove `%prod`/`%test` inertness: full backend suite green
      **unchanged** (incl. the zero-devices migration assertion — `%test`
      gets no seed); extend the compose smoke checklist: fresh `%prod` boot →
      `device` count 0 and the dev token → `401`. Document the token as
      public/dev-only in `backend/README.md`. — REQ-504
      Files: smoke notes/README; no prod code. Validate: `./mvnw test` green;
      smoke evidence recorded in handoff.

## Phase 2 — Client wiring & dev topology (frontend)

- [x] **T-05** *(done 2026-08-20, dev — alias + `packages/node_modules` symlink for the client's own bare imports; prod build green, `git diff packages/api-client` empty)* TS path alias `@gxp/api-client` → `../packages/api-client`
      (tsconfig only; no manifest change); import smoke (one model type) —
      `ng build` green. — REQ-501
      Files: `frontend/tsconfig.json`, `frontend/tsconfig.app.json`,
      `frontend/tsconfig.spec.json`. Validate: build + `git diff
      packages/api-client` empty.
- [x] **T-06** *(done 2026-08-20, dev — factory provider variant, spec proves per-request bearer)* `core/api/provide-guest-api.ts`: `provideHttpClient()` +
      `provideApi(Configuration{ basePath:'', credentials.bearerAuth: () =>
      DeviceTokenStore.token() })`; register in `app.config.ts`. — REQ-501
      Validate: unit spec — a `GuestService` call carries `Authorization:
      Bearer <token>` (HttpTestingController).
- [x] **T-07** *(done 2026-08-20, dev — 6 spec cases green)* `core/api/device-token.store.ts`: signal-backed store,
      `localStorage` key `gxp.device-token`; `set/clear/load`; environment
      fallback consulted only when storage is empty (see T-08). Never logged.
      — REQ-505/508
      Validate: spec — persistence round-trip, clear, fallback precedence.
- [x] **T-08** *(done 2026-08-20, dev — prod-build token grep empty; `ng serve` end-to-end check deferred to T-22)* `src/environments/environment.ts` (`devDeviceToken:
      null`) + `environment.development.ts` (the literal);
      `fileReplacements` in `angular.json` (development configuration);
      `frontend/proxy.conf.json` (`/api` → `http://localhost:8080`) wired to
      `ng serve`. — REQ-502/505
      Validate: `ng serve` against `quarkus:dev` renders content with empty
      storage; `ng build` (prod) then `grep -r gxp-dev-device-token dist/` →
      empty; no CORS config anywhere under `backend/`.

## Phase 3 — Guest data layer

- [x] **T-09** *(done 2026-08-20, dev — 11 mapper spec cases green)* `core/guest/guest-content.mapper.ts`: pure mapping + boundary
      validation per 02-design §6 (brand-hex check, localized-map checks,
      icon fallback); exported for spec. — REQ-510/512
      Validate: spec with fixture payloads mirroring the dev seed: happy
      path, bad hex rejected (defaults survive), unknown icon → fallback
      glyph, 0-card/0-item payloads accepted.
- [x] **T-10** *(done 2026-08-20, dev — 10 state-machine spec cases green; If-None-Match sent via the generated service's public `defaultHeaders`, recorded in handoff)* `core/guest/guest-data.service.ts`: bootstrap fetch
      (4 s bound), ETag revalidation loop (30 s; `If-None-Match`, 304 no-op),
      state machine ONLINE/STALE/DISCONNECTED per 02-design §4, occupancy
      signal, `localStorage` cache (`gxp.cache.session`/`gxp.cache.home`,
      written on 200, cleared on 401; home also cleared on VACANT), 401 →
      token+cache wipe. Zoneless-safe timers (signal writes; `DestroyRef`
      teardown — the `ClockService` pattern). — REQ-509/516/517/519
      Validate: spec — 200/304/error/401 transitions, cache round-trip,
      vacancy clears home, poll issues conditional requests
      (HttpTestingController asserts the header).
- [x] **T-11** *(done 2026-08-20, dev — 4 spec cases green; cache findings in handoff)* `core/guest/guest-asset.service.ts`: authorized blob fetch
      via `GuestService.getGuestAsset`, object-URL signal per asset id,
      revoke-on-replace, 404 → null (components fall back). Resolve
      verify-at-implementation (a): blob + browser-cache interplay — record
      findings in handoff. — REQ-513
      Validate: spec — object URL lifecycle (create/revoke), 404 path;
      manual devtools check for cache behavior recorded.
- [x] **T-12** *(done 2026-08-20, dev — service fed by GuestDataService signal; S29 rewritten against doubles, green)* Rework `PropertyContentService`: raw config fed by
      `GuestDataService` (signal), static import removed; keep `property()`,
      `settingsConfig()`, `getSectionRoute()` semantics (menu source = server
      items + settingsEntry; `targetRoute` from the wire). Relax 5/4-exact
      validation to 0–5/0–4 per 02-design §8. — REQ-510/514/515
      Validate: existing component specs still green with a service double;
      new spec: language resolution across server maps, route lookup incl.
      settings entry, unknown `targetSectionId` still throws.
- [x] **T-13** *(done 2026-08-20, dev — both files deleted, HttpPreferenceGateway + 7 spec cases green, policy suite untouched and green)* Delete `example-property.data.ts` +
      `SessionPreferenceGateway`; add
      `core/preferences/http-preference-gateway.ts` (save → whole-record
      `putGuestPreferences`, silent 400/409; load → session-handoff per
      02-design §2; clear → no-op) and swap the one provider line.
      `PreferencesService` policy untouched. — REQ-518
      Validate: grep — no import of the deleted files anywhere; preference
      spec suite green against the new gateway double; spec: 409 does not
      surface, retries bounded, coalescing preserved.
- [x] **T-14** *(done 2026-08-20, dev — GuestBootstrapService initializer; REQ-126 no-flash suite re-run green against the wire-shaped boot)* Bootstrap rework per 02-design §5: initializer branches
      (no token → pairing; session → defaults/branding/hydrate before first
      paint; failure → cache/connection). — REQ-518/509/520
      Validate: spec-level: initializer resolves in all four branches; no
      light→dark/de→en flash (existing REQ-126 spec pattern re-run); app
      boots with token+dead backend into cache or connection, never a blank
      screen.

## Phase 4 — Screens & chrome

- [x] **T-15** *(done 2026-08-20, dev — guards + GuestStateRoutingService effect; 8 route/guard spec cases green)* Routing: top-level `/pairing`, `/standby`, `/connection`
      outside the shell + guards (token/state driven, both directions).
      — REQ-506/517/520
      Validate: route specs — unpaired → `/pairing`; VACANT → `/standby`;
      OCCUPIED → previous route restored; DISCONNECTED+no-cache →
      `/connection`.
- [x] **T-16** *(done 2026-08-20, dev — 8 machine + 5 component spec cases green; status-dot colour deviation recorded in handoff)* `features/pairing/`: `pairing.service.ts` (state machine
      02-design §3 — create/poll/claim, 3 s cadence, ~~429 `Retry-After`~~
      (**amended 2026-08-24:** removed with the backend's throttle — any create
      failure is now the single offline path), expiry auto-renew, offline
      backoff 5→30 s, secret in memory only) +
      `PairingComponent` per the T-01 spec (brandless, code hero, status
      line, reduced-motion respected). i18n keys de+en. — REQ-506/507
      Validate: service spec covers every transition incl. claim-once
      (token stored, navigation triggered) and that no storage write ever
      contains code/secret; component spec: states render, code grouping.
- [x] **T-17** *(done 2026-08-20, dev — 4 spec cases green)* `features/standby/`: standby screen per the T-01 spec
      (logo/headline/message from session, status bar functional, no nav
      surface). — REQ-517
      Validate: component spec — renders standby texts for the active
      language, no guest-derived value in DOM while vacant.
- [x] **T-18** *(done 2026-08-20, dev — connection + pill; pill visibility proven in shell spec; geometry check at T-23)* `features/connection/` + staleness pill (layout chrome per
      02-design §9.4; visible in STALE only, `storedAt` timestamp, clears
      status-bar geometry). Register `phosphorWifiSlash`. — REQ-519/520
      Validate: component specs; pill hidden in ONLINE, shown in STALE.
- [x] **T-19** *(done 2026-08-20, dev — verbatim heading, 0/4-card cases, object-URL logo/artwork with fallbacks; specs green)* Home/sidebar adjustments: heading block renders server
      strings verbatim (interpolation removed); action cards/menu take
      variable counts; hero artwork + sidebar logo consume object-URL
      signals with existing fallbacks. — REQ-511/513/515
      Validate: component specs with 0/4-card and 3/5-item doubles; no
      overflow logic change (flex handles fewer items).
- [x] **T-20** *(done 2026-08-20, dev — 9 new keys in both catalogues, german-literals net green over the new components; both-themes check lands with the T-23 measure run)* i18n sweep: all new strings in both catalogues; no template
      literal (existing german-literals spec extended to the new
      components); both themes verified on the three new screens.
      — REQ-523
      Validate: `ng test` catalog completeness (typed keys), grep sweep.

## Phase 4b — Production serving (D7, decided 2026-08-20; tasks numbered by addition)

- [x] **T-27** *(done 2026-08-20, dev — with dist: jar carries index + hashed assets; `clean package` without dist: green, 0 bundle files; `clean` caveat documented in README; no dependency change)* Maven copy step per 02-design §11: profile `bundle-guest-app`
      in `backend/app/pom.xml`, auto-activated on
      `../../frontend/dist/frontend/browser/index.html`, copying that
      directory into `${project.build.outputDirectory}/META-INF/resources`
      at `process-resources` (`maven-resources-plugin` configuration — no
      new dependency; **any** new artifact need → stop ⏸). Document the
      workflow (`ng build` → `./mvnw package` → image) in
      `backend/README.md`. — REQ-526/527
      Files: `backend/app/pom.xml`, `backend/README.md`.
      Validate: with `dist/` present → `unzip -l` the jar shows
      `META-INF/resources/index.html` + hashed assets; after
      `rm -rf frontend/dist` → `./mvnw package` green, jar contains no
      `META-INF/resources/index.html`; `dependency:tree` unchanged.
- [x] **T-28** *(done 2026-08-20, dev — 8+2 tests green; pins resolved: order = ROUTE_ORDER_AFTER_DEFAULT (20000), cache = static caching-enabled=false + headers-end no-cache on reroute — findings in handoff)* SPA fallback per 02-design §11: `de.gxp.web.SpaRoutingHandler`
      (`@Observes Router`, late-priority GET handler; reroute to
      `/index.html` **only** when path starts with none of `/api/`, `/q/`,
      `/manage/`, contains no `.`, and the request accepts `text/html`;
      inert when no bundle is present — startup existence check). Resolve
      the two verify-at-implementation pins (route ordering API,
      `Cache-Control` on the rerouted response) and record findings in
      handoff. — REQ-526
      Files: `backend/app/src/main/java/de/gxp/web/**`, tests under
      `backend/app/src/test/java/de/gxp/web/**` (test-classpath
      `META-INF/resources/index.html` fixture).
      Validate: `@QuarkusTest`s — `/einstellungen` → index content;
      `/api/nonsense` → API-shaped response (never HTML); `/q/health`
      untouched; `/manage/x` NOT rewritten to the guest app; `/logo.png`
      (dot-path) falls through; without the fixture the handler is inert
      (no reroute loop).
- [x] **T-29** *(done 2026-08-20, dev — 11/11 assertions over verified TLS on a fresh %prod compose: `/` = kiosk app, deep link = index + no-cache, hashed asset 200, anon info 401-JSON (html-accept → 406, never HTML), `/q/openapi` 404, `/manage/x` 404-not-the-app, dev token 401, device count 0, image bundle token-grep 0, pairing create 201, plain HTTP refused; volumes+image removed after)* Compose smoke extension per REQ-528 (one smoke session,
      covering this and T-04's assertions): image built from a jar **with**
      the bundle; over HTTPS with
      the fetched CA: `GET /` → the kiosk app, `GET /einstellungen` →
      `index.html`, `GET /api/system/info` anon → 401 JSON, `/q/openapi` →
      404; `grep` the image's `META-INF/resources` for
      `gxp-dev-device-token` → empty; all pre-existing smoke assertions
      unchanged. — REQ-528
      Validate: evidence recorded in handoff.

## Phase 5 — Verification, measurement, docs

- [x] **T-21** *(done 2026-08-20, dev — `ng build` prod green (443 kB initial); `ng test` 235/235 (2 load-flaked timeouts in the final re-run passed 21/21 in isolation); backend 176/176 green on the final code state)* Full suites: `ng build` (prod) + `ng test` green; backend
      `./mvnw test` green unchanged. — REQ-501/524
- [x] **T-22** *(done 2026-08-20, dev — scripted: 22/22 + 5/6 + 2/2; the one part-2 miss (heal window) was a cold-backend artifact re-proven green in part 3; evidence + geometry in handoff)* End-to-end dev smoke (manual, recorded in handoff):
      `quarkus:dev` + `ng serve` → seeded content renders; wipe storage →
      pairing screen → confirm via manager API (curl) → app enters without
      reload; revoke device → pairing + storage empty; delete stay →
      standby; stop backend → staleness hint; clear cache + dead backend →
      connection screen → heals on restart. — REQ-506–509/516/517/519/520
- [x] **T-23** *(done 2026-08-20, dev — light grid 72/72 no-scroll 0-truncated, Home baseline y 88 / y 744 EXACT; dark: pass-1 both reference viewports clean (dev+prod builds), pass-4 contrast within Δ0.02 of §3.4, 0 findings; states run: pairing/connection/stale-pill/standby all no-scroll, pill clears the corner at both viewports; instrument extended for server-side prefs + prod-build token seeding — findings (e) in handoff)* Measure run (`node tools/measure.mjs`, extended to the new
      routes/states): every route × theme × language × {1024×768, 1280×832}
      scroll-clean; Home baselines (y 88 / y 744 etc.) unchanged with
      server-fed dev content; staleness pill clears the reserved corner.
      — REQ-522
      Validate: numbers recorded in handoff; deltas justified or fixed.
- [x] **T-24** *(done 2026-08-20, dev — dist token-grep 0 (build AND packaged image), external hosts = XML namespaces/framework strings only, zero console.* in src, cache = session+home JSON only per the non-sensitive pin)* Security/offline sweep: `grep -r <dev-token> dist/` (prod
      build) empty; no external host in `dist/`; no token/code/secret in any
      console/log path (code review + spec assertions); cache content
      reviewed against the non-sensitive pin. — REQ-504/505/508/519/523
- [x] **T-25** *(done 2026-08-20, dev — system-context (new table, seams closed, persistence trio, serving/path layout incl. /manage reservation, manager-frontend hand-off), index.md row, frontend/README dev workflow, backend/README serving+dev-seed; `frontend/CLAUDE.md` additions PROPOSED as exact text in handoff — ⏸ awaiting the user)* Docs: `docs/system-context.md` (new components table rows,
      persistence — device token + content cache; the two backend seams
      marked closed; the serving/path-layout row incl. the `/manage/`
      reservation; hand-off notes for `manager-frontend`),
      `docs/features/index.md`, `frontend/README.md`
      dev workflow, `backend/README.md` dev-seed note. Any
      `frontend/CLAUDE.md` addition proposed as exact text ⏸, written only
      after approval. — REQ-525
- [x] **T-26** *(done 2026-08-20, dev — handoff.md carries the full implementation record: per-package summary, 8 recorded deviations, all verify-at-implementation findings (a)–(e), carried-forward items)* Handoff: carried-forward items — kiosk CA install (prod
      provisioning; now the only missing link to a rendering prod kiosk),
      `/manage/` reservation for `manager-frontend`, first-sensitive-screen
      cache rules; record all verify-at-implementation findings (02-design
      §6 and §11). — REQ-525
