# PRD: Backend Domain & Configuration
**Feature:** [index.md](../index.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Status:** Draft

> Second feature of the backend cut (`docs/backend/specification.md` §12),
> planned on top of `backend-foundation` (reviewed **PASS** 2026-08-19).
> Settled ground — referenced, never re-opened: spec §2 decision log,
> constitution **v1.5.1** (incl. the password-lifecycle clause), the closed
> dependency allow-list, and `backend/CLAUDE.md`.
>
> **REQ-ID block: `REQ-301+`** — a fresh 3xx block, continuing the established
> per-feature-block pattern (0xx home-screen, 1xx appearance-and-language,
> 2xx backend-foundation).

## Problem statement

The backend scaffold exists and the contract toolchain is proven, but the
backend still knows nothing about the domain: no Property, no Room, no menu,
no cards, no stays, no assets — and no way for a manager to maintain any of
it. Everything the kiosk renders today comes from
`frontend/src/app/core/content/example-property.data.ts`, i.e. from code,
which is exactly what constitution principle 1 forbids as an end state.

This feature gives the instance its data model (spec §5, every invariant
included) and the **secured manager CRUD API** (spec §6) over it. Two features
are queued directly behind it and consume what it builds:
`device-pairing-and-guest-api` (reads this domain into the guest API) and
`manager-frontend` (puts a UI on this CRUD API).

**Scope call made by `lead`, ratified by this PRD: manager auth is in this
feature.** The CRUD API must not exist unsecured on a shared LAN for even one
feature's lifetime — fail-closed is already on, so without login there would
be no way to *use* the API at all. Login/logout, the in-memory sliding token
store, JDK PBKDF2 hashing, first-start password seeding and the
password-change endpoint (constitution v1.5.1) are therefore in scope.
HTTPS/TLS and device tokens stay in `device-pairing-and-guest-api`.

## Goals / Non-goals

**Goals**

1. The domain schema per spec §5 as Flyway migrations: Property (exactly one
   row), Room, MenuItemDefinition (seeded catalogue), MenuItemConfig,
   ActionCardConfig, Stay, Asset, plus typed per-entity translation tables.
2. Every §5 invariant (1–6) enforced **server-side**, including the
   409-with-offending-cards on disabling a targeted menu item.
3. Text templates stored raw, validated at save time (`400` on unknown
   placeholder), with a working server-side resolver (consumed by the guest
   API in the next feature).
4. The manager CRUD API per §6: property, rooms, menu configuration, action
   cards, stays, assets; read-only menu catalogue — all under
   `@RolesAllowed("admin")`.
5. Manager auth: `/api/manager/login` + `/api/manager/logout`, the in-memory
   sliding-expiry bearer-token store (10-min idle, 256-bit `SecureRandom`,
   stored hashed), JDK PBKDF2 password hashing, first-start seeding from
   `GXP_ADMIN_PASSWORD` (refuse to start when unset **and** no hash in the
   DB), and the password-change endpoint that drops all active tokens.
6. First-run usability (spec §9): migrations/seed yield a usable instance —
   property row, menu catalogue covering the five real kiosk sections, one
   default room, admin credential — with zero manual database work.
7. The domain model expresses everything the kiosk already renders from
   `example-property.data.ts`: localized fields, menu items, action cards
   (including several cards targeting the same menu item), and the settings
   config (offered languages/appearances + defaults).

**Non-goals** (explicit, with owners)

- **Device pairing, device tokens, TLS/CA, `/api/guest/**`, ETag-polled guest
  reads, occupancy state** → `device-pairing-and-guest-api`. The compose unit
  stays dev/preview-only (plain HTTP) until then.
- **The §6 preview endpoint for unsaved drafts** → `manager-frontend`
  (planner's call, per the routing brief's delegation). Rationale: its
  request shape *is* the manager UI's draft model, which does not exist yet —
  designing it now would guess that model; contract-first makes adding it
  later a normal, additive contract change, and nothing in this feature
  forecloses it. The building block it needs server-side (the template
  resolver and the resolution rules) ships here.
- **Any frontend change**: `frontend/` untouched; the TS client is
  regenerated and committed but not consumed (`guest-frontend-integration`).
- **Manager UI** (`backend/manager-ui/`, `packages/ui/`, Maven copy step
  wiring) → `manager-frontend`.
- **`/q/openapi` `%prod` exposure decision** — deferred to
  `device-pairing-and-guest-api` per the foundation review (🟡 #1). This
  feature must not foreclose it (no config either way beyond what exists).
- **Per-user manager accounts / audit trail** — deliberately deferred (spec
  decision 4, accepted and recorded). Auth is isolated so that swap later
  touches no Boundary or Control.
- Spec §11 open items 1–3 (standby/1–3-cards/manager designs — Figma) and
  6–9 (clock/NTP, backup ownership, update process, sensitive-field list):
  owner-level, not guessed here. The *data model* provisions standby texts
  because §5 puts them on Property; the standby *screen* is later work.

## Assumptions

- `backend-foundation` is merged conceptually (review PASS); its toolchain,
  BCE reference package, fail-closed config and logging posture are the
  baseline this feature extends. Work continues on branch `feature/backend`.
- The closed dependency allow-list suffices: image processing
  (`javax.imageio`), hashing (`MessageDigest`, `SecretKeyFactory`/PBKDF2),
  randomness (`SecureRandom`) are JDK platform features; the bearer-token
  authentication mechanism is implemented against the SPI of the
  already-approved `quarkus-security` (+ transitive `quarkus-vertx-http`).
  **No `pom.xml` change is expected; if implementation finds one is needed,
  it stops with ⏸ per the constitution.**
- The kiosk's translation catalogues support exactly `de` and `en`
  (`frontend/src/app/core/i18n/`), so v1 constrains language codes to those
  two — offering a language the kiosk cannot render would be a lie in config.
- Timestamps arrive as ISO-8601 with offset; the property's IANA timezone
  governs display formatting (template resolution).

## User stories

- As the **property manager**, I want to log in with the admin password and
  maintain my property, rooms, menu, cards, stays and images through one API
  so that the kiosk content stops being code.
- As the **property manager**, I want to be told *which* action cards block
  disabling a menu item — not have my cards silently dropped, and not ship a
  dead tile to a kiosk.
- As the **next feature** (`device-pairing-and-guest-api`), I want resolution
  rules (Stay → Room → Property; room row → property row → catalogue default)
  and the template resolver implemented and tested server-side so the guest
  API only composes them.
- As the **project owner**, I want the shared-password model implemented
  exactly per constitution v1.5.1 — seeded once from the environment, then
  hashed in Postgres, changeable in the UI, every change dropping all tokens.
- As **anyone on the guest WiFi**, I must not be able to read or write any
  manager data without a valid bearer token — and never learn from an error
  message whether a password guess was close.

## Requirements

### Contract & schema

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-301 | `api/openapi.yaml` gains the `manager` surface (auth + CRUD operations below), a `bearerAuth` security scheme (`http`/`bearer`) applied to every manager operation except login, and the new schemas. Both generators run green; `packages/api-client/` is regenerated and committed with the contract and backend code together; `frontend/` is untouched. | Must |
| REQ-302 | All schema changes are Flyway versioned SQL (`V2+`), applied at startup; no Hibernate schema generation in any profile. **`instance_info` is kept unchanged** (carried-forward decision, resolved here): it is *platform* metadata backing `/api/system/info`, distinct from the property's name (principle 1) — no V2+ migration touches it. | Must |

### Domain model (spec §5)

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-303 | `Property`: **exactly one row**, DB-enforced (`CHECK (id = 1)`), no create/delete API (invariant 4). Fields: name, main language, IANA timezone (validated), the three brand primitives (`#RRGGBB`-validated), default appearance, optional logo asset (must be kind `LOGO`), optional default artwork asset (must be kind `ARTWORK`; the house default a room may override), greeting texts and standby-screen texts (localized templates). | Must |
| REQ-304 | The property's **settings config** is expressible: offered languages (ordered; each with `code`, `shortLabel`, `label` endonym, `locale`) and offered appearance options (ordered; `mode` ∈ light/dark/system with icon), plus defaults. Server rejects (`400`) a default not contained in its offered set, and any language code outside `de`/`en` (v1 kiosk support). | Must |
| REQ-305 | `Room`: unique non-empty code, display name, optional overrides: artwork asset (kind `ARTWORK`), default language (∈ offered), default appearance (∈ offered). | Must |
| REQ-306 | `MenuItemDefinition`: the release catalogue, **seeded by Flyway and writable through no API**. Seed covers exactly the five real kiosk sections, keys/routes/icons matching `frontend/src/app/core/content/example-property.data.ts`: `home`→``, `info`→`/info`, `umgebung`→`/umgebung`, `smart-home`→`/smart-home`, `services`→`/services`, with the Phosphor icon names and `de`+`en` default labels the frontend ships today. The structurally-fixed `einstellungen` entry is **not** in the catalogue (design records why). | Must |
| REQ-307 | `MenuItemConfig`: one table, both scopes via nullable `room_id`; fields `enabled`, `sort_index`, label override (localized), icon override — **each nullable, resolved per-field**: room row → property row → catalogue default. *(Amended by user decision 2026-08-19, resolving review 🟡 #2: for **`enabled`** the property is the global **ceiling** — `enabled(room, k) = enabled(property, k) AND room-override(default true)`; a room can only offer what the property offers, room overrides are pure opt-outs. Guards: room-enable of a property-disabled item → `409 item-disabled-at-property`; property-disable over explicit room enables → `409 menu-item-enabled-by-rooms`; property-scope card PUT validated against inheriting rooms. See 02-design.md → "RESOLVED by user decision".)* Uniqueness per (menu item, scope) enforced in the DB including the `room_id IS NULL` scope. | Must |
| REQ-308 | `ActionCardConfig`: same two-scope shape; `sort_index` `0..3`; icon; title template + description template (localized); `target_menu_item_key` referencing the catalogue. **No unique constraint on the target key** — several cards may target the same item (the shipped frontend data has `check-out` and `wichtige-infos` both → `info`). A room with ≥1 card rows replaces the property set **wholesale** (no per-card merge — cards have no stable cross-scope identity). | Must |
| REQ-309 | `Stay`: room, display name, check-in < check-out (`timestamptz`, DB-checked), optional language and appearance override (∈ property's offered sets), status ∈ `BOOKED`/`CANCELLED`. "Active" is derived: `BOOKED` ∧ check-in ≤ now < check-out — so invariant 5 (REQ-315) makes "at most one active stay per room" hold by construction. | Must |
| REQ-310 | `Asset`: MIME type, byte size, pixel dimensions, SHA-256 (doubles as the strong ETag), `BYTEA` payload, kind ∈ `LOGO`/`ARTWORK`. | Must |
| REQ-311 | Translations are **typed per-entity child tables** (property, menu-item definition, menu-item config, action card), keyed owner + language — no generic key/value table. Language keys constrained to `de`/`en` (v1). Seeded catalogue translations carry `de`+`en`; manager-authored content carries the main language in v1 (structure ready for more). | Must |

### Invariants (spec §5, server-enforced)

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-312 | **Invariant 1:** at most 4 action cards per scope, `sort_index` unique within a scope — enforced by construction (DB: `CHECK sort_index BETWEEN 0 AND 3` + per-scope uniqueness incl. the NULL scope) and answered as `400` with an `ApiError` naming the violation on the whole-set PUT. | Must |
| REQ-313 | **Invariant 2:** a card's target must exist in the catalogue (`400`, unknown key named) **and** be enabled in the resolved menu of the card's scope (`409`, item + scope named). | Must |
| REQ-314 | **Invariant 3:** a menu-config change that would leave any resolved scope with a card targeting a now-disabled item is **rejected `409`**, the `ApiError` naming every offending card and its scope. Evaluated against **all affected scopes** (a property-level disable is checked against the property scope and every room that inherits it), per the algorithm pinned in the design. Nothing is silently dropped or kept. | Must |
| REQ-315 | **Invariant 5:** no overlapping non-`CANCELLED` stays per room. Enforced in the Control under a per-room serialization (row lock) so concurrent writes cannot race; violation → `409` naming the conflicting stay. | Must |
| REQ-316 | **Invariant 6:** asset uploads are capped (10 MiB → `413`), restricted to `image/png` + `image/jpeg` **verified by content** (declared type must match decoded format; mismatch/undecodable → `400`), server-side downscaled (longest edge > 2560 px re-encoded to 2560), delivered with the SHA-256 as strong `ETag` (`304` on `If-None-Match`) and streamed; metadata/list queries never load the payload column. | Must |

### Text templates

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-317 | Template texts (greeting, standby, card title/description) are stored **raw** and validated at save time against the field's allowed placeholder set — initial placeholders `{stay.displayName}`, `{stay.checkOut\|time}`, `{stay.checkOut\|date}`, `{property.name}`; standby fields allow only `{property.name}` (no stay exists when vacant). An unknown or out-of-context placeholder is a `400` naming it — never a surprise on a kiosk. | Must |
| REQ-318 | Server-side resolution is implemented and unit-tested now (checkout time/date formatted in the property's timezone with the language's locale), consumed by the guest API in the next feature. Manager reads return **raw** templates — the manager edits templates, not resolved text. | Must |

### Manager CRUD API (spec §6)

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-319 | `GET`/`PUT /api/manager/property` — read/replace the single property incl. settings options and translations. No `POST`/`DELETE` exists. | Must |
| REQ-320 | Rooms CRUD: list/create/read/update/delete. Deleting a room with any stay is `409` (naming the stays — manager work is never silently destroyed); a room deletion cascades **its own** config-override rows (menu + cards). | Must |
| REQ-321 | `GET /api/manager/menu/catalogue` (read-only definitions) and menu config `GET`/`PUT` for both scopes (property; `rooms/{id}`). PUT replaces the scope's override entries atomically; absent fields inherit (REQ-307). | Must |
| REQ-322 | Action cards `GET`/`PUT` for both scopes, whole-set replace (0–4 entries), validated atomically against REQ-312/313. | Must |
| REQ-323 | Stays CRUD: list (filterable by room), create, read, update, delete — create/update validated against REQ-309/315. | Must |
| REQ-324 | Assets: metadata list (no payloads), binary upload (`kind` stated; no multipart — raw image body), binary get (ETag per REQ-316), delete. Deleting an asset referenced by the property (logo/artwork) or a room is `409` naming the referrer. | Must |
| REQ-325 | Input is validated at the boundary layer (contract constraints + bean validation on generated DTOs) and semantically in the Controls; every non-2xx carries the shared `ApiError` shape, including bean-validation failures (mapped, not the framework default body). | Must |

### Manager auth (constitution v1.5.1)

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-326 | `POST /api/manager/login` is the **only** endpoint that ever sees the password. Success returns an opaque bearer token — 256-bit `SecureRandom` — plus its idle timeout and current expiry. Failure is a generic `401` (no timing/wording oracle). Manager login and password change are **deliberately not throttled** — no lockout, no rate limit, no concurrency gate (owner decision 2026-08-24, superseding the concurrency gate of the same day, which in turn replaced the original failed-attempt lockout). Rationale: the production hardware is not yet chosen, and a throttle sized for the wrong machine is worse than none. Residual brute-force exposure is carried by the password policy — minimum 8 characters, enforced both on change and on the **first-start seed** — which is worth roughly 10⁸ against a random password where any throttle was worth about 10. **Accepted and recorded:** an unauthenticated caller on the LAN can spend the server's CPU at will through PBKDF2, so sustained abuse degrades the whole instance, kiosks included. Revisit once the deployment target is known. **Reopened 2026-08-25** (constitution v1.8.0, `docs/backend/remote-operations.md` §3/§12): the deferral was argued on a LAN threat model that expires once the endpoint is reachable through a relay. Throttling returns as a **per attributed source address** budget — never global, never locking an account, rejecting before PBKDF2 — in `auth-hardening` REQ-601…REQ-605 (`docs/features/auth-hardening/01-prd.md`); the "deliberately not throttled" sentence above is superseded from the moment that feature ships. The recorded CPU-amplification residual stays true for the first five attempts per address. | Must |
| REQ-327 | The token store is **in-memory** (a restart logs everyone out — acceptable and desirable), holds token **hashes** (SHA-256), and slides: 10-minute idle expiry, reset by every authenticated request; the refreshed expiry is exposed on every authenticated response (header) so the client can warn before idle-out. Expired/unknown tokens → `401`. | Must |
| REQ-328 | `POST /api/manager/logout` invalidates the presented token immediately (server-side drop), `204`. | Must |
| REQ-329 | Password hashing is **JDK PBKDF2** (`PBKDF2WithHmacSHA256`, parameters pinned in the design, per-credential random salt, self-describing stored format). No Elytron, no new dependency. | Must |
| REQ-330 | First-start seeding: when no credential row exists, the hash is seeded from `GXP_ADMIN_PASSWORD`; if the variable is unset **or blank** and no hash exists, the app **refuses to start** with a clear message. Once a hash exists the variable is ignored (never re-read, never logged). | Must |
| REQ-331 | `PUT /api/manager/password` (authenticated): verifies the current password, enforces a minimal policy (≥ 8 chars), stores the new hash, and **immediately drops every active bearer token** — including the caller's (v1.5.1: the old credential must never survive into another idle window). | Must |
| REQ-332 | Every public manager Control method carries `@RolesAllowed("admin")` (login: `@PermitAll`); the fail-closed posture stays on. **Amended 2026-08-24 (owner decision D2, constitution v1.6.0):** the annotations moved from the Control to the **Boundary** — every public `*Resource` method now resolves to exactly one of `@PermitAll` / `@RolesAllowed(...)` (method-level or from a single class-level annotation) and the Controls carry none, because an internal call is not a trust boundary. The exactly-one guarantee is unchanged in substance, only relocated: `SecurityAnnotationAuditTest` (the `*Control` sweep) is replaced by `BoundaryAnnotationAuditTest` (the `*Resource` sweep plus a guard that no generated `de.gxp.api.*Api` interface carries a security annotation) and by `ContractSecuredOperationsDeniedTest` (every `bearerAuth` operation in `api/openapi.yaml` answers 401 anonymously). The *Control* sweep could not simply be re-pointed: `deny-unannotated-members` is class-scoped, so annotation-free Controls fall outside it — which is why `quarkus.security.jaxrs.deny-unannotated-endpoints=true` (global, build-time) landed in the same change and now covers a brand-new Boundary nobody annotated. The foundation's `FailClosedTest` 401-revisit lands **here** (the first real authentication mechanism arrives in this feature, earlier than the handoff's guess): with a mechanism installed, anonymous against `@RolesAllowed` must yield `401` + challenge; wrong/expired token `401`; the assertions are updated accordingly. | Must |
| REQ-333 | No password material, token, hash or guest-sensitive value reaches any log or exception message, in any profile — auth errors are generic, the foundation's logging config is not weakened, and no new code logs request payloads. | Must |

### Operations, tests, docs

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-334 | First run (fresh DB + `GXP_ADMIN_PASSWORD`) yields a usable instance with zero manual DB work: neutral property row (placeholder content a manager edits — nothing property-specific hardcoded, principle 1), languages `de`+`en` offered with `de` default, light+dark offered with `light` default, the 5-item menu catalogue, one default room, admin credential seeded. The compose unit from the foundation works unchanged (it already requires `GXP_ADMIN_PASSWORD`). | Must |
| REQ-335 | Every implemented behavior has a test (`quarkus-junit` + `rest-assured` against the real contract paths): every invariant (positive + violating case), template validation/resolution, the full auth lifecycle (login, slide, idle-expiry, logout, password change drops tokens, refuse-to-start seeding logic), asset pipeline (cap, content mismatch, downscale, ETag/304), and CRUD happy paths. | Must |
| REQ-336 | Docs updated with the code: `backend/README.md` (login workflow, first password + change, asset limits, unchanged one-`pg_dump` backup), `docs/system-context.md` (new components, Persistence/Auth updates), `docs/features/index.md`. | Must |

## Acceptance criteria (testable)

- **REQ-301:** `./mvnw verify` green; regenerating twice leaves
  `packages/api-client/` diff-clean; `git status` shows nothing under
  `frontend/`; every manager operation except `login` declares `bearerAuth`.
- **REQ-302:** boot log shows Flyway at the new head version; `V1` file
  unchanged; `instance_info` still seeded; `database.generation=none` still
  in every profile.
- **REQ-303/314:** inserting a second property row fails at the DB; the
  contract has no property create/delete operation; `PUT` with a bad
  timezone/hex → `400`.
- **REQ-304:** `PUT` with `defaultLanguage` not in the offered list → `400`;
  offered language `fr` → `400`.
- **REQ-306:** a test asserts the seeded catalogue equals the five
  key/route/icon/label tuples from the frontend data; the contract contains
  no write operation on the catalogue.
- **REQ-307:** DB rejects a second property-scope row for the same key
  (NULL-scope uniqueness); a room row overriding only the label inherits
  `enabled`/`sort` in the resolved view (asserted via the invariant checks).
- **REQ-308:** two cards targeting `info` in one scope save fine; a fifth
  card or duplicate `sort_index` → `400` (REQ-312).
- **REQ-313:** card targeting unknown key → `400`; targeting an item
  disabled in that scope → `409` naming item + scope.
- **REQ-314:** disabling `info` at property scope while property cards
  target it → `409` whose body names the offending cards; ~~disabling at
  property scope when only an *overriding* room still enables it and only
  that room's own cards target it → allowed~~ *(superseded by the 2026-08-19
  ceiling amendment, REQ-307: the property disable now wins, stranding the
  room's own cards → `409`)*; the design's affected-scope matrix is covered
  by tests.
- **REQ-315:** overlapping stay (`409` naming the conflict); adjacent stays
  (checkout == next check-in) allowed; `CANCELLED` never blocks; concurrent
  create of overlapping stays cannot both succeed.
- **REQ-316:** 10 MiB + 1 byte → `413`; PNG bytes declared `image/jpeg` →
  `400`; > 2560 px upload is stored ≤ 2560 with recomputed sha/size/dims;
  `GET` with matching `If-None-Match` → `304`; the list endpoint's SQL never
  touches the payload column (asserted via projection).
- **REQ-317/318:** saving `{stay.checkout}` (unknown) → `400` naming it;
  `{stay.displayName}` in a standby field → `400` (out of context); resolver
  unit tests cover `de`/`en` formatting of `|time`/`|date` in the property
  timezone.
- **REQ-319–324:** rest-assured CRUD tests per endpoint incl. the named
  `409` deletion guards.
- **REQ-325:** a bean-validation failure returns the `ApiError` shape, not
  the default validation body.
- **REQ-326/327:** wrong password → generic `401`; **amended 2026-08-24** —
  the former criterion *"5 failures → `429` + `Retry-After`"* is withdrawn
  with the throttling itself; the positive replacement is that **six
  consecutive wrong passwords arm nothing** (no `429`, no `Retry-After`) and
  the correct password still logs in immediately afterwards. **Withdrawn 2026-08-25** with the reopening of REQ-326 — the replacement criteria are `auth-hardening` REQ-601/602 (five failures from one address → `429` + `Retry-After` for that address only; another address's correct password logs in at once). A valid token's
  expiry visibly slides on a subsequent request (header value moves forward);
  an idled token → `401`; the store holds no plaintext token (asserted at the
  store's API level).
- **REQ-328/331:** after logout the token is dead; after password change
  **all** tokens (two sessions in the test) are dead and the new password
  logs in.
- **REQ-330:** with empty DB and unset/blank env var, startup fails with a
  clear message (tested at the seeding component level); with a hash present
  and the var unset, startup succeeds.
- **REQ-332:** anonymous → `401` with `WWW-Authenticate` on a manager
  endpoint; `FailClosedTest` updated + green; an audit test asserts every
  public manager Control method carries exactly one security annotation.
  **Amended 2026-08-24:** the audit now sweeps the **Boundaries**
  (`BoundaryAnnotationAuditTest`), and two criteria are added — no generated
  `de.gxp.api.*Api` interface method carries a security annotation, and every
  `bearerAuth` operation in `api/openapi.yaml` answers 401 to an anonymous
  caller (`ContractSecuredOperationsDeniedTest`, contract-derived rather than
  hand-listed). `FailClosedResource` keeps ONE deliberately unannotated
  endpoint: with the global endpoint deny on, that fixture is the only proof
  the flag bites.
- **REQ-333:** grep of committed config/code finds no logging of password,
  token or payload material; boot + test logs show zero SQL/bind values
  (unchanged foundation posture).
- **REQ-334:** fresh Dev-Services boot + the seed test proves property/
  catalogue/room/credential exist; `docker compose up` on a fresh volume
  serves a logging-in manager API.
- **REQ-335/336:** `./mvnw verify` green with the new suites; README/system
  context/index updated.

## Technical constraints

Anchored to `docs/constitution.md` v1.5.1 — all binding: backend stack (BCE
strict per spec §4, one-line Boundaries, `@Transactional` + security
annotations on Controls, package-private Entities/Repositories/Mappers,
hand-written mappers), contract-first workflow, PostgreSQL-only with assets
as `BYTEA` + mandatory mitigations, Flyway-at-startup, closed dependency
allow-list (**this feature adds nothing**), auth & transport model verbatim
(the TLS half lands next feature), principle 3 verbatim for logs, principles
6/7 (tests, traceability), branch discipline (`feature/backend`), input
validated at every boundary, secrets never hardcoded.

**Amended 2026-08-25 (owner decision D1, constitution v1.6.0):** the clause
*"package-private Entities/Repositories/Mappers"* no longer describes what is
on disk and is replaced, not deleted. Each feature package is now split into
`de.gxp.<f>.boundary` / `.control` / `.entity`; Java has no sub-package
visibility, so the **Entity and the Mapper are `public`** (and so are the
individual entity fields a Control writes across the split — widened one at a
time, never wholesale). The **Repository stays package-private**, which is
exactly why it lives in `.control` rather than beside the entity. What the
compiler used to guarantee is now guaranteed by three dependency-free
architecture tests in `de.gxp.architecture` — `FeatureInternalsImportTest`,
`EntityExposureTest` and `ControlPackageVisibilityTest` — which the
constitution makes part of the definition of done. Every other clause of this
paragraph is unchanged.

## Risks & open questions

- **Plain HTTP until `device-pairing-and-guest-api`.** The login password and
  bearer tokens ride unencrypted on the LAN until TLS lands. Accepted only
  because the compose unit is already marked dev/preview-only; the README
  keeps saying so. TLS is the very next feature.
- **Contract enumerability.** With manager operations in `api/openapi.yaml`,
  an unauthenticated `/q/openapi` in `%prod` would enumerate the admin
  surface. The decision stays with `device-pairing-and-guest-api` (foundation
  review 🟡 #1) — this feature changes nothing either way and the risk is
  recorded, not silently accepted forever.
- **`jaxrs-spec` generator vs. binary upload bodies and bean validation** —
  exact generated parameter types (`InputStream`/`File`/`byte[]`) and the
  `useBeanValidation` behavior in 7.24.0 are *verify-at-implementation* items
  (same pattern as the foundation's verify-at-scaffold). Intent is pinned
  (raw binary upload, no multipart, no new dependency); only the mechanics
  may adjust.
- **BYTEA is materialized by the JDBC driver** — true byte-level streaming
  from `BYTEA` is not available; the honest mitigation set (hard cap,
  downscaling, streamed response write, payload-free metadata queries) is
  what REQ-316 pins. Recorded as a bounded-memory argument, not a streaming
  fiction.
- **Neutral seed content wording** (property name/greetings a fresh instance
  shows before the manager edits) is placeholder copy — flagged for the
  manager-frontend feature to surface prominently as "please configure me";
  no design exists for that yet (§11 item 3).
- **`einstellungen` outside the catalogue** means the guest API must serve
  the settings nav entry as fixed chrome — recorded as a hand-off note for
  `device-pairing-and-guest-api`/`guest-frontend-integration`.
