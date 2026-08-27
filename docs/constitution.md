# Constitution: Guest Experience Platform
**Status:** Ratified
**Version:** 1.9.0 | **Last amended:** 2026-08-27

> **1.9.0** — owner decision 2026-08-27, wording approved verbatim before it
> was written. The kiosk cache clause was an absolute prohibition on caching
> door codes, key-box codes, alarm codes and WiFi passwords. It is replaced by
> a threat-model boundary: the cache may hold what the manager deliberately put
> on a guest-facing screen, and must never hold anything that extends reach
> beyond the device. Rationale, in the owner's terms: a value displayed to the
> guest can be photographed or written down, so purging it later removes
> nothing an attacker has — while withholding the WiFi password or the door
> code from the cache denies them to exactly the guest who needs them when the
> backend is unreachable. What we secure is what we control; physical loss of a
> device is disclosed to the operator, not defended against speculatively. The
> same amendment concretises stale rendering: a prolonged loss of contact
> escalates the discreet staleness hint to a prominent banner, for
> **operational** reasons — on a box with no remote access a guest complaint is
> the only alerting channel there is. The threshold is deliberately not fixed
> here; it lives with the feature that implements it.
>
> **1.8.3** — accuracy correction, not a new approval: principle 4 said the
> update signature is verified against *"a signing key compiled into the
> image"*. What shipped (`update-channel`, architect finding A-1) is a
> **rotatable trust set of two keys** — active and dormant — compiled into
> the image and mirrored on the host, with in-band rotation so a lost or
> stolen key never means a site visit. Reworded below; the property it
> protects (nothing unsigned is ever applied) is unchanged.
>
> **1.8.2** — accuracy correction, not a new approval (the v1.7.1 precedent):
> *"A password change **immediately invalidates every active bearer
> token**"* was written for one shared credential. With named accounts
> (`auth-hardening`, REQ-611) a password change, reset or deactivation drops
> **that user's** tokens — every other user's sessions survive, which is the
> whole point of per-user accounts. The sentence below is reworded; the
> intent (the old credential never survives into another idle window) is
> unchanged.
>
> **1.8.1** — **principle 4's fourth consequence turns from a statement of
> fact into a decision** (owner decision 2026-08-25, ratifying
> `docs/backend/remote-operations.md` §10 item 1). *"It cannot be patched
> centrally"* was written as helplessness and the whole
> dependency-minimisation posture was derived from it. It now reads:
> instances **are patched through an outbound, signed update channel** — the
> box asks, nothing ever connects to the box, the signature is verified
> against a key compiled into the image before anything is applied, and the
> component that applies the update lives *beside* the application, not in
> it. **The dependency-minimisation posture stays regardless**: a dependency
> is still code we must keep updating in the field, only now there is a
> channel to do it through, which makes the channel itself the
> highest-value target associated with the product. Two further bullets are
> added under the same principle, both derived from the remote-operations
> specification: the **survival invariant** (the product keeps working when
> everything we operate is gone — guest screens and the LAN manager UI never
> depend on the relay, the update server, or our company) and the rule that
> **remote access is a separately sold, optional capability** the
> application never branches on. Also recorded here: the owner reopened
> **REQ-326** — login throttling returns, per source address, never global
> (the reason for the 2026-08-24 deferral, "a LAN threat model", expires the
> moment the endpoint is internet-reachable). A major-feeling minor, the
> v1.4.0 precedent: a previously approved sentence is rewritten, nothing
> approved is contradicted in spirit.
>
> **1.8.0** — the "nothing scrolls" constraint now states its **scope**
> (owner decision 2026-08-26, wording approved verbatim). A **clarification
> with real consequences**, recorded as a minor rather than a patch because it
> permits something the previous wording read as forbidden: the rule binds the
> *document*, and a bounded sub-component may scroll its own content. It was
> written for full-screen routes and had been read — by me, in planning
> `modular-menu-catalogue` — as a component-level ban, which produced a
> recommendation against a scrolling overflow popover on constitutional
> grounds that did not exist. The owner corrected the reading and chose the
> popover. Nothing about page-level overflow changes, and `overflow: hidden`
> at screen level stays forbidden.
>
> **1.7.1** — accuracy correction, not a new approval: the backend constraint
> said the feature boundary is held by **three** dependency-free architecture
> tests and named three. It has been **five** since the scheduler work of
> 2026-08-25 — `PersistenceAccessTest` landed on 2026-08-25 (`89cd289`,
> owner-approved) without an amendment, and `ScheduledJobInventoryTest` arrived
> with the scheduled-housekeeping change. Both are named now. This is the
> second time this exact sentence has drifted (v1.6.1 (c) corrected "two →
> three"), and it drifted the same way: a guard was approved and shipped while
> the sentence counting the guards was not touched. Recorded rather than
> quietly fixed, per the v1.3.0 lesson.
>
> **1.7.0** — `quarkus-scheduler` added to the approved backend dependencies
> (owner decision 2026-08-25). A **new approval**, not a correction: the
> allow-list previously had no scheduler at all, and the code says so — the
> pairing table is swept opportunistically on every create, and the manager
> token store purges on every issue. Both work, but both tie housekeeping to
> traffic: an instance nobody touches never cleans up. The owner chose the
> **core `quarkus-scheduler`** (a `@Scheduled` annotation, in-JVM, no store, no
> cluster coordination) explicitly **over Quartz**, whose persistence and
> clustering answer problems one self-hosted box does not have. Principle 4
> still applies — it is one more extension to keep patched on machines nobody
> can reach — and it was accepted because the replacement it displaces is
> "hang the cleanup off unrelated traffic", not "do nothing". Existing
> opportunistic cleanups may stay or move; nothing is required to change.
>
> **1.6.1** — accuracy corrections to v1.6.0, not new approvals: three
> statements written on 2026-08-24 were contradicted by measurement once the
> work landed, and the owner approved the corrected wording on 2026-08-25.
> (a) *"fails closed **at build time**"* — the build does not fail; a dropped
> annotation compiles and is denied at runtime. (b) *"the types in `.entity`
> **(and their fields)**"* — only the fields a Control outside `.entity`
> actually touches must be widened: 58 of 97, compiler-proven minimal.
> (c) *"**two** dependency-free architecture tests"* — there are **three**,
> and the third exists because a reviewer walked a real leak through the
> unguarded `.boundary` sub-package that the first two did not cover. Recorded
> rather than quietly fixed, per the v1.3.0 lesson.
>
> **1.6.0** — the project owner's two backend conventions of 2026-08-24,
> approved after an architecture review. (a) **Feature packages gain
> `boundary`/`control`/`entity` sub-packages.** Chosen for readability, in full
> knowledge of the price: Java has no sub-package visibility, so entity types
> and their fields become `public` and the feature boundary is enforced by two
> dependency-free architecture tests instead of by the compiler. ArchUnit was
> considered and rejected — a dependency on an unpatchable box for what 120
> lines of reflection do (principle 4). (b) **Security annotations move from the
> Control to the Boundary**, together with
> `quarkus.security.jaxrs.deny-unannotated-endpoints=true`. This is a net
> *increase* in safety: `deny-unannotated-members` is class-scoped and would
> have gone inert on annotation-free Controls, whereas
> `deny-unannotated-endpoints` denies **every** unannotated JAX-RS endpoint,
> including in a Boundary that does not exist yet — a hole the old model left
> open. It also removes the five `@PermitAll`-on-internal-method warts and the
> latent "this internal call only works because the caller happens to be admin"
> coupling. Both are rewrites of previously approved sentences, hence a
> major-feeling minor (the v1.4.0 precedent).
>
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
   - **Instances are patched through an outbound, signed update channel; the
     dependency-minimisation posture stays regardless** (rewritten in v1.8.1
     — this bullet previously read *"It cannot be patched centrally"*).
     Instances run in buildings we may have no access to, so the only way a
     fix reaches them is a channel the box itself polls: outbound only,
     nothing ever connects to the box, every artifact verified against a
     rotatable trust set of signing keys compiled into the image (v1.8.3)
     *before* it is applied, signing done
     offline so a compromised update server can serve nothing a box accepts,
     and the updater living beside the application (it cannot replace its own
     container) with rollback on a failed health check. Still: prefer fewer
     dependencies, and prefer platform features over libraries. A dependency
     is not just code you add, it is code you must keep updating in the
     field — the channel makes that possible, not free.
   - **The product keeps working when everything we operate is gone** (added
     in v1.8.1). Guest screens and the manager UI on the LAN never depend on
     the relay, the update server, the internet or our company's continued
     existence; what any central component adds is convenience, never
     function. A design that breaks that row is wrong, not the invariant.
   - **Remote access is an optional, separately sold capability** (added in
     v1.8.1). A box must be fully functional having never enrolled, enrollment
     must be possible later without a reinstall and reversible without loss,
     and security updates must never be gated behind it. The application
     never branches on where a request came from — the single transport-level
     fact it may consume is the attributed client address (trusted only from
     the configured proxy), which feeds the per-address login budget and the
     second-factor requirement, and nothing else.
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
  binding highlights: every feature package `de.gxp.<feature>` is split into
  the sub-packages **`boundary` / `control` / `entity`** (owner decision
  2026-08-24); a Boundary method is **one delegating line** (no validation,
  mapping, branching or transactions — not even for plain CRUD);
  `@Transactional` sits on the Control; **the Control is the only type any
  other feature may reference**, and it speaks generated API DTOs; mappers are
  hand-written, no mapping framework. Because Java has no sub-package
  visibility, the types in `.entity` are necessarily `public`, and so is every
  field that a Control outside `.entity` actually touches — but only those:
  the migration widened **58 of 97** fields and left 39 package-private, and
  that minimum is compiler-checkable. **The feature boundary is therefore held
  by five dependency-free architecture tests in `de.gxp.architecture`, not by
  the compiler**: `FeatureInternalsImportTest` (nothing outside
  `de.gxp.<feature>` may reference that feature's `.entity`, `.control` or
  `.boundary` types, except its own `*Control`; no wildcard imports; the
  layout is actually present), `EntityExposureTest` (no public signature
  outside `.entity` may expose an entity type — including generic arguments,
  array components, supertypes and interfaces),
  `ControlPackageVisibilityTest` (only a `*Control` may be public in
  `.control`, only a `*Resource` in `.boundary`), `PersistenceAccessTest`
  (only entities and repositories reach the database) and
  `ScheduledJobInventoryTest` (every `@Scheduled` job and its cron, pinned as
  a literal list, so a job cannot be added, removed or re-timed silently).
  The first four derive the feature list from disk; the job inventory is
  pinned by hand on purpose. Those tests are part of the
  definition of done and must stay green. They are a strong regression net,
  not a proof: each records its known gaps in its own javadoc, and leaving one
  sub-package out of scope is exactly how a real leak survived review once. A library such as ArchUnit is **deliberately not used** (principle 4).
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
  hangs off `@PermitAll` / `@RolesAllowed(...)` on **Boundary** methods or
  Boundary classes — the trust boundary is HTTP, not a Java call — with
  **`quarkus.security.jaxrs.deny-unannotated-endpoints=true`**, so any endpoint
  carrying no annotation, **including one in a brand-new Boundary**, **fails
  closed** — denied at runtime by a decision baked in at build time; the build
  itself does not fail, and `BoundaryAnnotationAuditTest` is the loud, early
  signal. `deny-unannotated-members` stays on as the second
  line for any bean mixing annotated and unannotated members. Controls carry no
  security annotations: internal calls are not a trust boundary, and the room
  is still derived exclusively from the authenticated device identity
  (principle 4). All LAN traffic is **HTTPS** with an
  instance-generated CA distributed to kiosks at pairing — non-negotiable,
  since a shared password over plain HTTP is readable by the guest WiFi.
  Accepted and recorded: no per-user audit trail until a user table replaces
  the shared password; auth is isolated so that swap touches no Boundary or
  Control. **Password lifecycle (v1.5.0):** the environment variable seeds the
  password on **first start only**; from then on it lives hashed in Postgres
  and the manager changes it through an authenticated manager-UI endpoint. A
  password change **immediately invalidates every active bearer token of
  that user** (v1.8.2: per user, not per instance) — the old credential must
  never survive into another idle window.
  **Authentication hardening (v1.8.1, owner decisions 2026-08-25 — supersedes
  the "one shared admin password" and "no per-user audit trail" wording above
  as the `auth-hardening` feature ships):** the single credential row becomes
  **named user accounts**, all with role `admin` for now (no further roles;
  any admin may create, deactivate and reset other accounts; the first-start
  seed creates the first account from the environment and still refuses to
  start without it); deactivation drops that user's tokens and takes effect
  on the next request. **Login is throttled per attributed source address,
  never globally** — an exhausted budget rejects *that address* before any
  PBKDF2 derivation and never locks an account; the attributed address is
  the socket peer unless the request arrives from the configured trusted
  proxy, whose forwarded address is then honoured (and from nobody else).
  **A second factor (RFC 6238 TOTP, JDK `Mac` only, recovery codes stored
  hashed) is required for every login that arrives through the trusted
  proxy and is never asked on the LAN** — a user without an enrolled factor
  cannot log in remotely at all, and enrolls on the LAN; the LAN path is the
  documented recovery route. **An append-only audit trail** records who,
  when, from which address, which entity and which field — and **never a
  value** of a guest-sensitive field (principle 3); retention is 365 days,
  pruned by the daily scheduler.
- **The manager UI enters the Quarkus artifact via a plain Maven copy step**
  of the built Angular `dist` into `META-INF/resources` (v1.5.0, owner
  decision). Quinoa is rejected: a Quarkiverse community extension is one more
  thing to keep patched on boxes nobody can reach, for a job a build step does.
- **Approved backend dependencies** (same force as the frontend list — nothing
  beyond this without a fresh approval; versions verified against the chosen
  Quarkus release at scaffold time): `quarkus-rest`, `quarkus-rest-jackson`,
  `quarkus-hibernate-orm-panache`, `quarkus-jdbc-postgresql`,
  `quarkus-hibernate-validator`, `quarkus-smallrye-openapi`, `quarkus-flyway`,
  `quarkus-security`, `quarkus-scheduler` (core `@Scheduled` only — Quartz
  deliberately not approved, see v1.7.0); build/test-time:
  `openapi-generator-maven-plugin`,
  `quarkus-junit` (named `quarkus-junit5` before Quarkus 3.31 — same artifact,
  see v1.5.1), `rest-assured`, Quarkus Dev Services (no separate
  Testcontainers dependency). **Deliberate non-additions**, with replacements
  named so they are not re-added by reflex: `quarkus-elytron-security-common`
  (→ JDK PBKDF2), `quarkus-smallrye-jwt` (→ opaque in-memory token), `quarkus-quartz`
  (→ core `quarkus-scheduler`; this box needs neither persistence nor
  cluster coordination for its jobs), any mapping framework / Lombok /
  utility libraries (→ plain hand-written Java).
- **The kiosk's last-known-good cache may hold any guest-facing content the
  current guest is entitled to see** (amended 2026-08-27) — the WiFi password,
  door and key-box codes, alarm codes and host contact details included. The
  boundary is **what the manager deliberately chose to display**: a value the
  property put on a guest-facing screen is, by that act, a value the guest may
  read, photograph or write down. Caching it adds no exposure, and purging it
  removes none. The former absolute prohibition bought nothing and contradicted
  principle 3's own statement that the app deliberately holds exactly these
  values. The kiosk may render its last successful response when the backend is
  unreachable, with a discreet staleness hint. After a prolonged loss of
  contact that hint escalates to a prominent banner naming the situation and
  directing the guest to their host; content keeps rendering, because a guest
  whose backend is unreachable is precisely the guest who still needs the door
  code. The escalation is an **operational** requirement, not a security one —
  on a box with no remote access, a guest complaint is the only alerting
  channel that exists. The threshold itself lives with the feature that
  implements it, not here.
- **What the cache must never hold is anything that extends reach beyond the
  device itself** — no device token, session token, key material or any
  credential that could address the backend, another room, or another guest's
  data. That is the line, and it is not negotiable: a stolen kiosk may expose
  what its own room's guest already knew, and must never become a key to
  anything else.
- **Physical loss of a kiosk is an operator responsibility, and the product
  discloses it.** A missing device is assumed to expose every value it last
  held; the operator documentation states that door, key-box and alarm codes
  are to be rotated when a device goes missing. We implement the controls we
  own and disclose the ones we do not — we do not build speculative defences
  against the physical world.
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
  **The scope is the document** (v1.8.0): no route may produce a page-wide
  scrollbar. A bounded sub-component — an overlay panel, a popover, a list
  inside a card — may scroll its own content, as long as it stays inside the
  viewport and therefore never grows the document. `overflow: hidden` at
  screen level remains forbidden.
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
