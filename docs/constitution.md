# Constitution: Guest Experience Platform
**Status:** Ratified
**Version:** 1.5.1 | **Last amended:** 2026-08-19

> **1.5.1** — name correction, not a new approval: `quarkus-junit5` was renamed
> **`quarkus-junit`** upstream in Quarkus 3.31 (the old id is only a relocation
> pom), verified by `dev` at scaffold time against Quarkus 3.33 per the
> "versions verified at scaffold time" clause. Same artifact, same intent —
> recorded so the manifest and this file agree (the v1.3.0 lesson).
>
> **1.5.0** — resolved two of `docs/backend/specification.md` §11's open items
> with the owner (2026-08-19): (item 4) the manager UI enters the artifact via
> a **plain Maven copy step** into `META-INF/resources` — Quinoa rejected as an
> avoidable community extension on unpatchable boxes; (item 5) the admin
> password is **seeded from the environment variable on first start only**,
> thereafter stored hashed in Postgres and **changeable by the manager in the
> UI**; every password change immediately drops all active bearer tokens.
> Additive — the env-var-on-first-start rule stands.
>
> **1.4.0** — **the backend exists now.** Amended with the project owner's
> explicit permission of 2026-08-19, implementing §10 of
> `docs/backend/specification.md` (the owner's decision interview of the same
> day). A **major-feeling minor**: nothing previously *approved* is contradicted,
> but the constraint *"No backend, auth or admin area in the first step"*
> described the MVP and is now false — it is rewritten, not just extended.
> Changes: (a) principle 4's tenant-isolation claim narrowed to *between
> buildings*, with mandatory room-level scoping inside one; (b) frontend/backend
> stack constraints split, backend stack recorded (Java 21 + Quarkus + Maven,
> imperative, BCE, Panache repositories, contract-first OpenAPI); (c) the
> infrastructure decisions approved 2026-08-19 recorded (PostgreSQL, Flyway,
> Docker Compose, assets in the DB); (d) backend dependency allow-list added
> with the same force as the frontend one; (e) auth & transport model recorded
> (device tokens via pairing, short-lived sliding admin bearer token, HTTPS
> with an instance-generated CA); (f) the kiosk caching constraint added
> (last-known-good never holds guest-sensitive values); (g) the second Angular
> frontend + shared library recorded, including the twice-made rejection of LIT.
>
> **1.3.0** — approved `playwright` as a **verification tool**, and recorded that
> this is deliberately not the still-open end-to-end framework decision. It had
> already been installed ahead of this approval; the amendment makes the
> manifest and this file agree again rather than pretending otherwise.
>
> **1.2.0** — added the "nothing scrolls" constraint and the supported viewport
> range it applies to (1024 x 768 landscape minimum, 1280 x 832 design size,
> portrait out of scope), and made the `rem` root font size viewport-
> proportional rather than a constant 16px. The scroll rule is additive; the
> root-size change rewrites the second sentence of the styling constraint, so
> tokens authored against a fixed 16px root keep their authored values but no
> longer render at a fixed physical size. Open risk recorded with it: at the
> 1024 x 768 floor the 12px label token renders at 9.6px, which is pending a
> hardware legibility check -- if it fails, the floor rises and this bumps
> again.
>
> **1.1.0** — added principle 4 (self-hosted on premises, untrusted local
> network, no central patching) and the two security constraints that follow
> from it. Additive, nothing contradicted.
>
> **1.0.0** — **ratified.** Reviewed and set in force, so the `04_analyze` gate
> now checks compliance with every principle and constraint below, not only with
> the approval gates. A major bump because enforcement itself changed, which is
> the kind of change that can make previously acceptable work non-compliant.
>
> **0.3.0** — recorded Vitest as the unit-test runner and noted that no
> end-to-end framework is chosen yet.
>
> **0.2.0** — filled in the project name, the stack baseline and the first
> cross-cutting conventions, following the design import and the token
> architecture decisions of 2026-08-17. Nothing pre-existing was contradicted,
> hence a minor bump.

> Project-level governance. Created once via `/constitution` and amended rarely.
> Every feature phase (`01_prd` … `07_improve`) must respect it; the `04_analyze`
> gate checks compliance before implementation. A `Draft` constitution is treated
> as "not yet enforced" — set `Status: Ratified` once you've reviewed it.
>
> **Exception:** "Decisions Requiring Your Approval" below is in force from day
> one, in `Draft` just as much as in `Ratified`. A brand-new project is exactly
> when the biggest decisions (stack, datastore, first dependencies) get made —
> `Draft` never licenses an agent to decide those alone.

## Principles

1. **Property-specific content and branding are data, never code.** Anything
   that differs between one property and the next — texts, WiFi details, house
   rules, contacts, recommendations, brand colors, artwork — arrives as
   configuration. Onboarding an additional property must never require a code
   change or a deploy. Components stay presentational and read from a config
   source; they never hardcode one property's values.
2. **Content is reached through a service, never imported into a component.**
   Local UI state uses Angular **signals**; content and configuration are
   provided via Angular DI. This keeps today's static JSON swappable for a real
   backend later without touching components.
3. **Guest-sensitive data is never logged.** This app deliberately holds door
   and key-box codes, alarm codes, WiFi passwords and host contact details.
   None of it may reach a log, an analytics event, or an error report — not even
   at debug level, and not in a stack trace's captured state.
4. **Self-hosted on premises, on a network we do not control.** Every
   **building** (hotel, apartment house, holiday home) runs its **own
   instance** on a local server on site, serving n room kiosks and one manager
   UI; there is no central service holding many properties. Four consequences
   bind every change:
   - **Physical tenant isolation holds *between buildings only*** (amended
     2026-08-19). Inside a building, one backend serves many rooms, so
     **room-level scoping is mandatory** and is the single most important
     security property of the backend: room 101 must never receive room 102's
     door code. The room is derived from an **authenticated device identity**
     (the pairing token) — never from a path, query or header parameter the
     client supplies. Every query touching room-scoped data is
     security-relevant.
   - **It must run without internet.** No runtime dependency on an external
     host — no CDN fonts or stylesheets, no third-party scripts, no analytics or
     telemetry, no remote images. Fonts, icons and assets ship inside the build
     artifact. A feature that only works online must state that dependency
     explicitly and degrade gracefully without it.
   - **The local network is not trusted.** The server shares a LAN with guest
     devices, and possibly with neighbours. Anything reachable over HTTP is
     reachable by anyone on that network — "not linked in the UI" is never a
     protection.
   - **It cannot be patched centrally.** Instances run in buildings we may have
     no access to, so an unpatched vulnerability can persist for a long time.
     Prefer fewer dependencies, and prefer platform features over libraries. A
     dependency is not just code you add, it is code you must keep updating in
     the field.
5. **Components reference semantic design tokens only.** Never a raw hex value,
   never a raw pixel size. Raw values live once, in the token layer
   (`docs/design/tokens.md`), which is what makes theming and per-property
   branding possible at all.
6. **Tests accompany code** — every implemented behavior has a test.
7. **Traceability** — every PRD requirement has a stable `REQ-ID`; every TODO task traces to one.

## Constraints

- **Guest frontend: Angular + TypeScript** (strict mode), standalone
  components, Angular Router, signals for local UI state. Content is reached
  through services (principle 2); static TS/JSON is the stand-in until the
  corresponding backend endpoint exists. *(Rewritten in v1.4.0 — this
  constraint previously read "No backend, auth or admin area in the first
  step", which described the MVP and is now false.)*
- **Backend: Java 21 (LTS) + Quarkus, built with Maven** (`backend/app/`) —
  one instance per building. **Imperative/blocking**, not reactive:
  `quarkus-rest` + Hibernate ORM with **Panache repositories** (entities carry
  no persistence methods). Packages follow **BCE (Boundary–Control–Entity),
  by feature**, under the strict rules of `docs/backend/specification.md` §4 —
  binding highlights: a Boundary method is **one delegating line** (no
  validation, mapping, branching or transactions — not even for plain CRUD);
  `@Transactional` sits on the Control; the Control is the package's public
  type and speaks generated API DTOs; Entities, Repositories and Mappers stay
  package-private; mappers are hand-written, no mapping framework.
- **Contract-first API.** `api/openapi.yaml` is the source of truth. The Maven
  build generates the JAX-RS interfaces + DTOs (implementations hand-written)
  **and** the TypeScript client in `packages/api-client/` — which is generated
  output, never edited by hand; a diff there means the contract changed. A
  contract breach fails the build, not the runtime.
- **Backend infrastructure (approved by the owner 2026-08-19):**
  **PostgreSQL** is the only datastore — binary assets (logo, artwork) live in
  it as `BYTEA` so one `pg_dump` is the entire backup, with the mandatory
  mitigations (hard size cap, server-side downscaling on upload, streaming
  delivery, strong SHA-256 ETags). **Flyway** migrations run at startup
  (versioned SQL in the repo; also the home of menu-catalogue seeding; no
  Hibernate schema generation). **Docker Compose** (app + Postgres, one named
  volume) is the deployment unit. First run yields a usable instance with no
  manual database work, and refuses to start if the admin password environment
  variable is unset.
- **Auth & transport (approved by the owner 2026-08-19):** kiosks authenticate
  with a **long-lived opaque device token** obtained through the manager-
  confirmed pairing flow — the token is identity *and* authentication, stored
  **hashed**, revocable from the manager UI, and the room is always derived
  from it (see principle 4). The manager sends the **one shared admin
  password** only to `/api/manager/login` and receives a short-lived opaque
  bearer token (**10-minute idle expiry, sliding**, in-memory store on the
  server, in-memory only on the client, 256-bit `SecureRandom`, stored
  hashed). Password hashing is **JDK PBKDF2** — no Elytron. Authorization
  hangs off `@RolesAllowed` on Control methods with `deny-unannotated-members`
  so a new endpoint **fails closed**. All LAN traffic is **HTTPS** with an
  instance-generated CA distributed to kiosks at pairing — non-negotiable,
  since a shared password over plain HTTP is readable by the guest WiFi.
  Accepted and recorded: no per-user audit trail until a user table replaces
  the shared password; auth is isolated so that swap touches no Boundary or
  Control. **Password lifecycle (v1.5.0):** the environment variable seeds the
  password on **first start only**; from then on it lives hashed in Postgres
  and the manager changes it through an authenticated manager-UI endpoint. A
  password change **immediately invalidates every active bearer token** — the
  old credential must never survive into another idle window.
- **The manager UI enters the Quarkus artifact via a plain Maven copy step**
  of the built Angular `dist` into `META-INF/resources` (v1.5.0, owner
  decision). Quinoa is rejected: a Quarkiverse community extension is one more
  thing to keep patched on boxes nobody can reach, for a job a build step does.
- **Approved backend dependencies** (same force as the frontend list — nothing
  beyond this without a fresh approval; versions verified against the chosen
  Quarkus release at scaffold time): `quarkus-rest`, `quarkus-rest-jackson`,
  `quarkus-hibernate-orm-panache`, `quarkus-jdbc-postgresql`,
  `quarkus-hibernate-validator`, `quarkus-smallrye-openapi`, `quarkus-flyway`,
  `quarkus-security`; build/test-time: `openapi-generator-maven-plugin`,
  `quarkus-junit` (named `quarkus-junit5` before Quarkus 3.31 — same artifact,
  see v1.5.1), `rest-assured`, Quarkus Dev Services (no separate
  Testcontainers dependency). **Deliberate non-additions**, with replacements
  named so they are not re-added by reflex: `quarkus-elytron-security-common`
  (→ JDK PBKDF2), `quarkus-smallrye-jwt` (→ opaque in-memory token), any
  mapping framework / Lombok / utility libraries (→ plain hand-written Java).
- **The kiosk's last-known-good cache never holds guest-sensitive values** —
  no door codes, key-box codes, alarm codes or WiFi passwords, ever, because
  the kiosk stands in a guest-accessible room (principle 3 applied to
  persistence, not just logs). The kiosk may render its last successful
  response when the backend is unreachable, with a discreet staleness hint.
- **Second frontend: the manager UI is a second Angular app**
  (`backend/manager-ui/`), shipped inside the Quarkus artifact,
  dependency-lean: no component library, plain CSS, native form controls. The
  handful of components a manager preview shares with the guest screen live in
  a **plain Angular library** (`packages/ui/`, ng-packagr), which consumes the
  same primitive token layer; the manager's own chrome gets its **own semantic
  layer** and must not borrow the guest screen's roles. **LIT was rejected for
  the second time on 2026-08-19** (first: `docs/design/tokens.md`) — reasoning
  recorded in `docs/backend/specification.md` §7; do not re-litigate.
- **Styling is plain CSS with custom properties.** No Tailwind, no SCSS, no
  component library as a visual base. Sizes are expressed in `rem`, never in
  fixed pixels. The root font size is **viewport-proportional**, not a
  constant: it equals 16px at the 1280 x 832 design size and scales with the
  viewport, which is what makes the whole UI scale without breakpoints. Author
  every token as though the root were 16px; the root does the scaling. It is
  deliberately **not capped** at the top end, so a larger display renders a
  proportionally larger UI. Exceptions: hairline borders and blur radii stay in
  `px`.
- **Nothing scrolls.** Every screen fits its viewport on both axes — no
  horizontal scrollbar, no vertical one, on any route. This holds across the
  whole supported viewport range: **1024 x 768 landscape (minimum) up to the
  1280 x 832 design size and beyond**. Portrait orientation, and anything below
  the minimum, are out of scope and carry no guarantee. Between those sizes the
  UI scales proportionally — there are no breakpoints and no second layout. The
  rule is met **by construction** (a layout that would overflow is a bug to
  fix), never by `overflow: hidden`, which hides the regression instead of
  preventing it. Checkable at review time: on every route,
  `scrollWidth <= clientWidth` and `scrollHeight <= clientHeight`.
- **Approved dependencies:** `@angular/cdk` (overlay, dialog, menu, focus trap,
  a11y — used unstyled), `@ng-icons/core` + `@ng-icons/phosphor-icons`
  (Phosphor icon set), **`vitest` + `jsdom`** (unit tests — the Angular CLI
  default since v21, installed by `ng new` rather than added by hand), and
  **`playwright`** (a devDependency used as a *verification tool* — the
  `code-review` visual check drives a real browser to screenshot each route and
  measure geometry, wrapping and scroll dimensions, because jsdom computes no
  layout and decodes no images). Anything beyond these needs approval as below.
- **Tests run on Vitest.** Karma/Jasmine are not used; Karma has been deprecated
  since 2023 and Angular's default moved to Vitest. **No end-to-end framework is
  chosen yet — that decision is still open**, and approving `playwright` above
  did not quietly make it. Playwright is sanctioned for *measurement during
  review*, not as a test suite: no committed E2E specs, nothing wired into
  `npm test`, no assertions living in a browser runner. Standing up a real E2E
  suite — in Playwright or anything else — remains a separate approval.
- **No dependency-manifest edits** (`package.json`, `pom.xml`, `requirements.txt`, …) without explicit approval.
- **Branch discipline** — no write work on `main`/`master`; use a feature branch.
- Secrets never hardcoded, never logged.
- Input validated at every system boundary.
- **No external runtime requests.** Self-host fonts (Plus Jakarta Sans), bundle
  icons, embed images. Never a `<link>` or `fetch` to Google Fonts, a CDN, or
  any third-party host in shipped code. This is checkable at review time and is
  the most common way the on-premise principle gets broken by accident.
- **Security is a review category, not a phase.** Every change is assessed for
  what it exposes on a shared network: what the server serves without
  authentication, what ends up in the client bundle, and what an unauthenticated
  device on the same LAN can reach. The `reviewer` flags this like any other
  defect; `architect` is escalated to when a change alters the exposure model.
- **Design-tool (MCP) requests are metered and scarce.** Only the
  `design-import` skill may issue one, only on explicit user invocation
  (`/design-import`), and only after confirming the cost. Every other agent
  reads the extracted specs under `docs/design/**` instead. Treat a missing
  detail as a question for the user, not a reason to query.

## Decisions Requiring Your Approval

Never decided by an agent alone — always proposed with a recommendation and
held until you confirm, no matter how far into the project you are (this is
not just a greenfield/`00_constitution` rule):

- A new dependency, or replacing/removing an existing one
- A datastore, messaging system, hosting choice, or other infrastructure
- Spending a metered design-tool request
- **Changing** how design tokens are realised in code. The original choice was
  made on 2026-08-17 (CSS custom properties, two layers — see Constraints and
  `docs/design/tokens.md` → "Implementation"); revisiting it is a fresh
  approval, not an implementation detail
- A new architectural pattern or cross-cutting convention not already covered
  by this constitution or an existing nested `CLAUDE.md`
- Any new entry a subagent wants to add to a nested `CLAUDE.md` — the exact
  text is proposed first, written only after you confirm it
- Anything that would contradict or replace an existing nested `CLAUDE.md` entry

**How this works technically:** a subagent (`dev`, `planner`, `architect`)
cannot pause mid-task and wait for your answer — it stops, states its
recommendation, and ends its turn without implementing or writing anything
for that decision. `lead` relays the question and recommendation to you, then
re-delegates to the same subagent once you've answered. Outside the `lead`
workflow (e.g. a direct `/build`), the session you're talking to relays it
directly.

## Quality bar ("done")

- Builds and all tests green.
- Conventions from nested `CLAUDE.md` files followed.
- No sensitive data in logs.
- `06-review.md` result is PASS with every `REQ-ID` implemented.

## Governance

- Amendments require your own review and a version bump (semver).
- Record each amendment date above and the rationale in a CHANGELOG, if you keep one.
