# Backend Specification Brief

**Status:** Input for the spec pipeline — **not** a PRD
**Created:** 2026-08-19 · from a decision interview with the project owner
**Hand to:** `claude --agent lead`

> This document is written **for `lead` to route into the pipeline**, not to
> replace it. It carries the decisions the project owner has already made and
> the reasoning behind them, so that `planner` starts from settled ground
> instead of re-asking. Every requirement here still needs a `REQ-ID` in a real
> `01-prd.md`, and the `04_analyze` gate still applies.
>
> Written in English to match the rest of `docs/`, even though the interview was
> in German.

---

## 0. Read this first: two blockers

**The constitution currently forbids this work.** `docs/constitution.md` v1.3.0
→ Constraints says *"No backend, auth or admin area in the first step — content
comes from static JSON/TS files"*, and `docs/system-context.md` records
*"Persistence: none"* and *"Auth: none"*. Section 10 below proposes the
amendment text for **v1.4.0**. It must be ratified by the project owner before
any backend feature enters `05_implement`, otherwise `04_analyze` will fail the
work — correctly.

**The physical-isolation argument no longer covers everything.**
`docs/system-context.md` claims *"One instance holds one property's data. No
cross-tenant leakage is even possible, and no tenant-scoping logic is needed in
queries."* With one backend serving **n rooms**, that is now true only *between
buildings*. Inside a building, room-level scoping is mandatory and is the
single most important security property of this backend: room 101 must never
receive room 102's door code. Treat every query that touches room-scoped data
as security-relevant.

---

## 1. What this backend is for

One Quarkus backend per **building** (hotel, apartment house, holiday home),
running on a local server on site. It serves:

1. **n guest frontends** — one Angular kiosk per room, each of which must
   identify *which room it is* and receive room-specific configuration.
2. **one manager frontend** — an Angular admin UI, shipped inside the same
   Quarkus artifact, used by the property manager to maintain everything above.

The workload is overwhelmingly CRUD. There is little business logic today; the
value is in getting the data model, the contract and the security boundary
right, because five more guest screens (WiFi, house rules, surroundings,
appliances, departure) are queued behind this one.

---

## 2. Decision log

Every row was decided by the project owner on 2026-08-19. The rationale is
recorded because the pipeline's later phases will be tempted to re-open them.

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | **`Stay` is an entity; `Guest` is not** | Guest name, stay period, check-out time and language preference hang off a `Stay`. No separate person aggregate and no contact details — minimal PII on a box that stands in a physically accessible building. Gives "dynamic check-out time" and "derive language from guest data" a real anchor without collecting people |
| 2 | **Structural configuration resolves Property → Room; preferences resolve Stay → Room → Property** | One rule to remember: *configuration lives on the house and the room, data lives on the stay*. A suite may deviate from the house default; the manager UI shows the house standard plus a few explicit room overrides rather than n rooms to maintain individually |
| 3 | **Device pairing with an opaque token** | The kiosk is paired once by the manager and stores a long-lived token. The token is identity **and** authentication: no valid token, no room data, and a device cannot change which room it claims to be. Resolves the open `NEEDS DECISION` in `system-context.md` → "does the app authenticate the guest at all?" |
| 4 | **One shared admin password, verified statelessly (v1)** | Deliberately chosen over per-user accounts. Consequence accepted and recorded: there is **no** "who changed this" audit trail and no revoking an individual person's access. It follows that **no session is needed either** — see §6: HTTP Basic over HTTPS, no login endpoint, no cookie, no server-side auth state. Auth is to be isolated so a user table and real sessions can replace it later without touching a Boundary or a Control |
| 5 | **The menu catalogue is release data** | A menu item *is* a route with an implemented screen, so the manager cannot invent one. The backend seeds a catalogue per release (stable `key`, default label, default icon, frontend route); the manager only enables/disables, reorders, and overrides label and icon. A new menu item becomes a seed row plus a frontend screen — not a feature |
| 6 | **i18n: structure now, one language maintained** | Every manager-authored text carries a language key from day one, but the manager UI maintains only the house's main language in v1. Avoids the expensive migration across every content type that WiFi/house-rules/surroundings will add, without paying for translation workflows yet |
| 7 | **Guest API is split: shell vs. screen** | `GET /api/guest/session` for everything route-independent, `GET /api/guest/screens/{key}` for screen content. Two calls at boot, one per navigation. New screens are a new `screens/{key}` without touching the existing contract, and a screen holding sensitive data can be secured separately instead of riding in a shared payload |
| 8 | **Contract-first OpenAPI with code generation** | `api/openapi.yaml` is the source of truth. JAX-RS interfaces + DTOs are generated for the backend (interfaces only; implementations are hand-written), and a typed TypeScript client for the frontends. The contract becomes a constraint, not documentation: a breach fails the build instead of surfacing at runtime |
| 9 | **Vacant rooms get their own standby screen** | Between check-out and the next check-in the kiosk shows a dedicated, reduced screen — not the previous guest's data, and not a half-filled normal screen. Maximally data-sparing, and it makes an unoccupied room unmistakable to cleaning staff. **Needs a Figma design that does not exist yet** (see §11) |
| 10 | **Binary assets live in Postgres** | Renderings and the logo are stored in the database, so the whole instance is exactly **one** backup artifact — a real argument for a box nobody maintains. Mitigations that follow are mandatory, not optional: hard size cap, server-side downscaling on upload, streaming rather than full buffering, and strong-ETag delivery so each kiosk fetches an image once |
| 11 | **Kiosk keeps a last-known-good cache** | The kiosk renders its last successful response when the backend is unreachable (reboot, update, power), with a discreet staleness hint. **Sensitive values are never cached** — door codes, key-box codes, WiFi passwords — because the kiosk stands in a guest-accessible room (constitution principle 3) |
| 12 | **Maven + Java 21 LTS** | Maven is Quarkus' default and the best-documented path for extensions, native builds and the OpenAPI generator plugin. Java 21 has the longest track record in the Quarkus LTS streams and in Mandrel. On a box nobody can service, a gap in the native toolchain costs more than a shorter support window |
| 13 | **Imperative/blocking, not reactive** | `quarkus-rest` + Hibernate ORM, ordinary transactions and control flow. At this load — one building, a few dozen kiosks, mostly cached reads — reactive buys capacity nobody will draw and costs complexity everybody carries. Also the better fit for BCE, where the boundary is a plain transactional façade |
| 14 | **Panache Repository** | Entities stay free of persistence methods; data access lives in repository classes that read cleanly as BCE *Control*. Chosen over plain `EntityManager` for the boilerplate saving across many CRUD endpoints, and over Active Record because that would fuse Entity and data access — the very split BCE opens |
| 15 | **Flyway, migrating at startup** | Versioned SQL in the repo, applied when the instance boots. Plain SQL is reviewable without an intermediate language, an instance update brings its own schema, and it is the natural home for seeding and extending the menu catalogue per release. Hibernate schema generation was rejected outright for an instance holding real guest data |
| 16 | **Action cards: 0–4, hard cap at 4** | The backend validates `<= 4`. A small apartment is not forced to invent a fourth quick action. **Consequence:** the frontend now needs a rule for 1–3 cards, which the design does not have (see §11) |
| 17 | **Monorepo; manager UI ships inside the backend artifact** | `api/`, `frontend/`, `backend/` and the shared UI library side by side. A contract change is one commit that moves both sides — which is the whole point of contract-first |
| 18 | **HTTPS on the LAN, certificate distributed at pairing** | The instance generates its own CA and server certificate on first start; the pairing flow installs the CA on the kiosk. No internet, no ACME, no manual certificate work on site. Resolves the second open `NEEDS DECISION` in `system-context.md`. Non-negotiable given decision 4: a shared admin password over plain HTTP is readable by anything on the guest WiFi |
| 19 | **Docker Compose: app + Postgres** | One `compose.yaml`, one named volume. An update is an image tag change plus a restart, and Flyway pulls the schema forward. Reproducible identically in every building |
| 20 | **Manager UI is Angular; shared preview components are a plain Angular library. LIT rejected (again)** | See §7 |

---

## 3. Repository layout

```
api/
  openapi.yaml                  # the contract — source of truth
backend/
  app/                          # Quarkus, Maven, Java 21
  manager-ui/                   # Angular admin app, bundled into the artifact
  compose.yaml
packages/
  ui/                           # shared Angular library (ng-packagr)
  api-client/                   # GENERATED TypeScript client — not hand-edited
frontend/                       # existing Angular guest kiosk
docs/
```

Two rules that follow and should go into the nested `CLAUDE.md` files:

- **The shared UI library never lives under `backend/`.** Both apps consume it;
  the guest app must not need the backend tree to build.
- **`packages/api-client/` is generated output.** Nothing in it is edited by
  hand, and a diff there is a signal that `api/openapi.yaml` changed.

---

## 4. BCE package structure

Per the project owner's requirement, the backend follows
[BCE](https://bce.design/): package **by feature**, each package holding a
Boundary, a Control and its Entities.

> **The Boundary does nothing but accept a request and hand it to the Control.**
> No validation, no transaction, no mapping, no branching — **not even for plain
> CRUD**. This is a stricter reading than several BCE write-ups take, and it is
> binding here (project owner, 2026-08-19). There is no such thing as a feature
> package without a Control, and "it is only CRUD" is not a reason to skip one.

```
de.<org>.gxp.room/
    RoomResource.java     # Boundary — JAX-RS adapter, one delegating line per endpoint
    RoomControl.java      # Control  — all behaviour, @Transactional, the package's public type
    RoomRepository.java   # Control  — data access only (PanacheRepository), package-private
    Room.java             # Entity   — state + invariants, no persistence methods
    RoomMapper.java       # Entity <-> API DTO, package-private, invoked by the Control
```

Conventions to write down once and then enforce at review:

- **A Boundary method is one line:** take the generated DTO, call one Control
  method, return the result. An `if`, a `try`, a loop or a second call in a
  Boundary method means that logic belongs in the Control. This is mechanically
  checkable and should be checked.
- **`@Transactional` sits on the Control, never on the Boundary.** The
  transaction boundary follows the behaviour, and no behaviour lives in the
  Boundary.
- **The Control is the package's public type; the Boundary is only its HTTP
  adapter.** This follows directly from the rule above: with a pass-through
  Boundary, making *it* the cross-package entry point would drag HTTP concerns
  into internal calls. A feature needing another feature's data therefore
  injects **that feature's Control**. Repository, Mapper and Entity stay
  package-private.
- **Controls speak API DTOs; Entities never leave the package.** Because the
  Boundary may not map, the Control accepts and returns the generated types and
  calls the Mapper itself. The trade-off is deliberate and worth stating: the
  Control is coupled to the API model, and in exchange the Boundary is genuinely
  trivial and no entity can escape its package.
- **Data access is a Control concern, not a fourth layer.** `RoomRepository`
  keeps queries out of `RoomControl` once a feature grows them; a feature with
  two trivial queries may hold them in the Control directly. Both are Control —
  what matters is that neither is reachable from outside the package.
- **No mapping framework.** Mappers are hand-written; another dependency to keep
  patched in the field is not worth the saved keystrokes.
- **The guest read model is a coordinating Control.** `GuestSessionControl`
  composes property, room, stay, menu and preferences by injecting the
  respective Controls. This is the one place where cross-feature composition is
  expected, and it is deliberately read-only.

---

## 5. Domain model

### Entities

**`Property`** — exactly one row per instance, enforced by a check constraint.
Name, main language, timezone, brand primitives (`--brand-primary`,
`--brand-primary-deep`, `--brand-secondary`), default theme, logo asset,
greeting texts, and the standby-screen texts.

**`Room`** — code (`"101"`), display name, plus optional overrides: artwork
asset, default language, default theme.

**`MenuItemDefinition`** — the release catalogue, seeded by Flyway and **not
writable through any API**. Stable `key` (e.g. `smart-home`), default label,
default icon (Phosphor glyph name), the frontend route, a default sort index,
and the release it was introduced in.

**`MenuItemConfig`** — one table carrying both scopes via a nullable `room_id`:
row with `room_id IS NULL` is the house default, row with `room_id` set is the
room override. Fields: enabled, sort index, label override, icon override.
Resolution order: room row → property row → catalogue default.

**`ActionCardConfig`** — same two-scope shape. Sort index `0..3`, icon override,
title template, description template, and `target_menu_item_key` referencing the
catalogue. **Several cards may target the same menu item** — the existing mock
data already does this (`check-out` and `wichtige-infos` both point at `info`),
so do not add a unique constraint.

**`Stay`** — room, display name, check-in and check-out timestamps, optional
language and theme override, status. At most one *active* stay per room at any
instant.

**`Asset`** — MIME type, byte size, dimensions, SHA-256, `BYTEA` payload, kind
(`LOGO` | `ARTWORK`). The SHA-256 doubles as the strong ETag.

**`Device`** — room, name, **token hash only** (the raw token is returned once at
pairing and never again), created/last-seen/revoked timestamps.

**`PairingRequest`** — short-lived code shown on the kiosk, its expiry, and the
room the manager assigned when confirming.

**Translations** — one child table per translatable entity
(`property_translation`, `action_card_translation`, `menu_item_config_translation`,
…), keyed by owner + language. **Not** a generic key/value table: typed columns
keep validation and the manager UI honest. In v1 each parent simply has one
child row.

### Invariants the backend enforces

1. At most **4** action cards per scope; `sort_index` unique within a scope.
2. An action card's `target_menu_item_key` must exist in the catalogue **and**
   be enabled in the resolved menu for that scope.
3. Disabling a menu item that a card still targets is **rejected** (`409`) with a
   message naming the offending cards. Silently dropping the card would lose the
   manager's work; silently keeping it would put a dead tile on a kiosk.
4. Exactly one `Property` row.
5. No overlapping stays for the same room.
6. Asset uploads are capped in size and restricted to an allow-list of image
   MIME types, verified by content rather than by filename.

### Text templates — designed in now, filled later

Card titles and descriptions are stored as templates and resolved **server-side**
at read time:

```
"Bis {stay.checkOut|time} Uhr am Abreisetag"
```

The frontend always receives finished text, so today's flat-string contract is
unchanged. The project owner's "later these should take dynamic values" then
becomes a new placeholder implementation rather than a contract change.
Placeholders are validated **at save time** — an unknown one is a `400`, not a
surprise on a kiosk. Start with `{stay.displayName}`, `{stay.checkOut|time}`,
`{stay.checkOut|date}` and `{property.name}`.

---

## 6. API surfaces

All paths are illustrative; `api/openapi.yaml` is the authority once written.

### Pairing (unauthenticated, rate-limited)

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/pairing/requests` | Kiosk asks for a pairing code; receives code + expiry and displays it |
| `GET` | `/api/pairing/requests/{code}` | Kiosk polls; `202` while pending, `200` with the device token once the manager confirms — **returned exactly once** |
| `POST` | `/api/manager/pairings/{code}/confirm` | Manager assigns the code to a room |

### Guest (device token required, room-scoped)

| Method | Path | Returns |
|---|---|---|
| `GET` | `/api/guest/session` | Room, property, brand primitives, logo URL, resolved preferences (language, theme), **occupancy state**, guest display name when occupied, and the resolved menu |
| `GET` | `/api/guest/screens/home` | Headline, subtitle, paragraph, artwork URL, and 0–4 resolved action cards |
| `GET` | `/api/guest/assets/{id}` | Binary, strong ETag, long `Cache-Control` |

Every guest response carries an `ETag`; the kiosk polls with `If-None-Match` and
normally gets `304`. **The room is derived from the device token — never from a
path, query or header parameter the client supplies.** A room id in the URL would
reintroduce exactly the enumeration hole that decision 3 closes.

### Manager (HTTP Basic over HTTPS — **no session**)

Every manager request carries `Authorization: Basic`, checked against the stored
password hash. **There is no session, no login endpoint, no cookie and no
server-side auth state.**

This is not a shortcut, it is the honest consequence of decision 4: with a
single shared password a session buys almost nothing. It cannot revoke one
person's access, because there is only one credential, and rotating the password
is the only revocation available either way. What a session *would* add is
server-side state to store, expire and invalidate on a box nobody maintains.

Four consequences to build for:

- **The Angular app collects the password in its own form** and sets the header
  itself, holding it in memory only. The browser's native auth dialog never
  appears, because the API does not send a `WWW-Authenticate` challenge — a
  missing or wrong credential is a plain `401` the app handles and renders.
- **"Logout" is discarding the value client-side**, and a page reload means
  re-entering it. On a manager's laptop that is acceptable, and it is also the
  reason not to put the password in `localStorage`.
- **Brute-force protection must be explicit.** Without a login endpoint there is
  nothing single to rate-limit, so the limit belongs on *every* manager path:
  failed-attempt counting per source address with a growing delay, held in
  memory. Required, not optional — the credential sits behind endpoints
  reachable from the entire LAN.
- **TLS is what makes this safe** (decision 18). The password travels on every
  request; over plain HTTP it would be readable by anything on the guest WiFi.

Endpoints: CRUD over property, rooms, menu configuration, action cards, stays,
assets and devices (including **revoking** a device); read-only access to the
menu catalogue; and a preview endpoint returning the guest screen shape for an
unsaved draft configuration.

---

## 7. The manager frontend and the shared UI library

**Decision: a second Angular app, plus a plain Angular library for the handful
of components a manager preview shares with the guest screen. No LIT.**

`docs/design/tokens.md` had already rejected LIT once, reasoning that it solves
framework independence — something this project does not need. The project owner
reopened the question when a genuine use case appeared: the manager must show a
live preview of individual guest UI elements. It was rejected a second time, and
the reasoning belongs in the record because it will come up again:

- **Both consumers are Angular.** A LIT-only manager would not remove Angular
  from the picture; it would push LIT *into the guest app*, which would then have
  to consume the shared custom elements. Two frameworks instead of one.
- **The shared surface is small** — action card, menu item, perhaps the heading
  block. Not a design system. The manager's own UI is forms, tables and sortable
  lists in a completely different visual language, for which no Figma design
  exists at all.
- **Icons would have to be re-solved.** The guest components use `@ng-icons`,
  which injects its SVG via `Renderer2` outside the template
  (`docs/system-context.md` documents this and the styling trap it creates).
  Inside a LIT component `@ng-icons` does not exist.
- **Shadow DOM would be a third variant** of a problem this project has already
  been bitten by twice (the `:host` layout convention and the icon-styling rule).

The manager app is kept deliberately dependency-lean: no component library,
plain CSS, native form controls. That recovers most of the longevity argument
that spoke for LIT, without changing paradigm — which matters because every
existing convention, test setup and review checklist in this repo is
Angular-shaped, and a second paradigm would double the surface every agent has
to respect.

**Token sharing:** the shared library consumes the same primitive layer, so a
preview shows real brand colours. The manager's own chrome needs its *own*
semantic layer — dense admin UI is not soft-glass — and must not borrow the
guest screen's roles.

---

## 8. Approved dependencies (proposed)

To be added to `docs/constitution.md` → Constraints. **Nothing outside this list
without a fresh approval**, exactly as on the frontend side. Version-sensitive
names must be verified against the chosen Quarkus release at scaffold time
rather than assumed.

**Backend runtime**

| Extension | Why it is unavoidable |
|---|---|
| `quarkus-rest`, `quarkus-rest-jackson` | HTTP boundary and JSON |
| `quarkus-hibernate-orm-panache` | Persistence (decision 14) |
| `quarkus-jdbc-postgresql` | The datastore |
| `quarkus-hibernate-validator` | Constitution: *input validated at every system boundary* |
| `quarkus-smallrye-openapi` | Serves the contract and the dev UI |
| `quarkus-flyway` | Migrations and catalogue seeding (decision 15) |
| `quarkus-security` + `quarkus-elytron-security-common` | Basic authentication and `BcryptUtil` for the admin password hash. **No session store and no JWT extension** — the stateless choice in §6 is what keeps this to two small extensions |

**Build-time**

| Tool | Why |
|---|---|
| `openapi-generator-maven-plugin` | Generates JAX-RS interfaces + DTOs **and** the TypeScript client |
| `quarkus-junit5`, `rest-assured` | Tests |
| Quarkus Dev Services | Starts Postgres for dev and test; no separate Testcontainers dependency |

**A deliberate non-addition:** no mapping framework, no Lombok, no utility
library. Constitution principle 4 — every dependency is code that must be kept
patched on machines nobody can reach.

**A pleasant consequence worth protecting:** because the TypeScript client is
generated by the *Maven* build into `packages/api-client/`, **neither Angular app
needs a new npm dependency**. The generated client uses `@angular/common/http`
and `rxjs`, both already present. `frontend/package.json` stays untouched.

---

## 9. Operations

- **Migrations run at startup.** No one on site can apply SQL by hand.
- **First run must yield a usable instance** with no database work: create the
  single `Property` row, seed the menu catalogue, create one default room, and
  set the admin password from an environment variable — refusing to start if it
  is unset rather than shipping a default.
- **Backup is one `pg_dump`.** That is the whole point of decision 10, so the
  documentation must say it plainly and say how to restore.
- **TLS material is generated on first start** and the CA is handed to kiosks
  during pairing.
- **Health and readiness endpoints** exist so a person in the building can tell
  whether the box is the problem.
- **Logging is a security surface here.** Set explicitly and check at review:
  no SQL parameter logging, no request-body logging, and no guest-sensitive
  value in any message — constitution principle 3 applies to the backend
  verbatim, and Hibernate's SQL logging is the most likely accidental breach.

---

## 10. Proposed constitution amendment (v1.4.0)

For the project owner to ratify. Proposed, not written — per
`docs/constitution.md` → Governance.

1. **Rewrite** the constraint *"No backend, auth or admin area in the first step"*.
   It described the MVP and is now false.
2. **Amend principle 4.** Physical tenant isolation holds *between buildings*.
   Inside a building, room-level scoping is mandatory, and the room is derived
   from an authenticated device identity — never from a client-supplied
   parameter.
3. **Close the two open `NEEDS DECISION` items** in `docs/system-context.md`:
   guest-side authentication is the device token; LAN transport is HTTPS with a
   self-generated CA distributed at pairing.
4. **Add the backend dependency allow-list** from §8, with the same "nothing
   beyond this without approval" force as the frontend list.
5. **Record the infrastructure decisions** — PostgreSQL, Docker Compose, Flyway,
   assets in the database — each of which is approval-gated and now approved.
6. **Record the second frontend** and the shared Angular library, including the
   renewed rejection of LIT and its reasoning, so it is not re-litigated.
7. **Add a caching constraint:** the kiosk's last-known-good cache never holds
   guest-sensitive values.
8. **Update `docs/system-context.md`** — *Persistence*, *Auth*, *Logging*, the
   components table and the deployment-model section all now describe a state
   that no longer exists.

---

## 11. Open items — not decided, do not guess

| # | Item | Who |
|---|---|---|
| 1 | **No design exists for the standby screen** (decision 9). Needs a Figma frame before the guest frontend can implement it | Design |
| 2 | **No design exists for 1–3 action cards** (decision 16). The row is specified only at four | Design |
| 3 | **No design exists for the manager frontend at all** — not tokens, not layout, not components | Design |
| 4 | **How the manager UI gets into the artifact:** the Quinoa extension, or a plain Maven step copying the built `dist` into `META-INF/resources`. Quinoa is a Quarkiverse (community) extension; on a system that cannot be patched centrally, the zero-extension copy is the leaner choice. *Recommendation: the copy step* | Owner |
| 5 | **Admin password rotation** — environment variable, or changeable through the UI and stored hashed in the database? One upside of the stateless choice in §6: a rotation takes effect on the next request everywhere, with no sessions to hunt down and invalidate | Owner |
| 6 | **Clock and timezone.** Check-out times and stay dates need a correct clock on a box that may have no internet and therefore no NTP | Owner |
| 7 | **Who backs the instance up, and how often?** Still open from `system-context.md`, and now there is real data to lose | Owner |
| 8 | **How do instances get updated?** Open since 2026-08-17 and now the dominant operational risk, since the backend adds a schema and a database to every update | Owner |
| 9 | **Guest-frontend cache: what exactly counts as sensitive?** Needs an explicit field-level list before the first sensitive screen ships | Owner |

---

## 12. Suggested feature cut for the pipeline

Five features, in dependency order. Each is small enough to survive
`04_analyze` and to be reviewed properly.

| Slug | Scope |
|---|---|
| `backend-foundation` | Quarkus scaffold, Postgres, Flyway, BCE package conventions, the OpenAPI toolchain end to end (spec → generated interfaces → generated TS client), Compose, health endpoints, `backend/CLAUDE.md`. **One trivial endpoint** proves the whole chain |
| `backend-domain-config` | Property, Room, menu catalogue + configuration, action cards, stays, assets, translations — with the manager CRUD API and every invariant in §5 |
| `device-pairing-and-guest-api` | Pairing flow, device tokens, HTTPS with the self-generated CA, `/api/guest/**` including ETags and the occupancy state |
| `guest-frontend-integration` | The guest app moves from `example-property.data.ts` to the real API, plus the last-known-good cache and the standby screen. `PropertyContentService` is the only file that should meaningfully change — that was the point of constitution principle 2, and this feature is where the claim gets tested |
| `manager-frontend` | The Angular admin app, the shared component library, and the preview |

**Start with `backend-foundation`, and do not let it grow.** Its real job is to
prove that a change to `api/openapi.yaml` propagates into both a Java interface
and a TypeScript client in one commit. If that chain does not work, everything
else is built on a contract that is only aspirational.
