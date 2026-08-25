# Design: Backend Domain & Configuration
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **TODO:** [03-todo.md](03-todo.md)
**Status:** Draft

> HOW for [01-prd.md](01-prd.md). Settled ground referenced, not re-made:
> spec §2/§4/§5/§6, constitution v1.5.1, `backend/CLAUDE.md`, and the
> foundation's pinned patterns (one-line Boundary, non-200 thrown from the
> Control, verify-at-implementation for version-sensitive names).

## Scope decisions made in planning (with rationale)

| Decision | Call | Why |
|---|---|---|
| Manager auth placement | **In this feature** (lead's routing call, confirmed) | Fail-closed is already on; a CRUD API without login is either unusable or must be shipped `@PermitAll` on a shared LAN — both unacceptable. Auth lives in one isolated package (`de.gxp.auth`) so the later per-user swap touches no Boundary/Control (spec decision 4). |
| §6 preview endpoint | **Deferred to `manager-frontend`** | Its request body *is* the manager UI's draft model, which has no design (§11 item 3). Contract-first makes it an additive later change; the server-side building blocks (resolver, resolution rules) ship here. |
| `instance_info` fate | **Kept unchanged** | It backs `/api/system/info` (REQ-211) with *platform* metadata; `Property.name` is the property's — merging them would conflate principle-1 data with release data. Dropping it would break a reviewed, passing feature for zero gain. |
| REQ-ID range | **Fresh 3xx block (REQ-301…336)** | Matches the established per-feature-block pattern. |
| `einstellungen` not in catalogue | **Out** | Spec decision 5 says a catalogue item is manager-configurable (enable/disable/reorder). The settings entry is structurally fixed app chrome (frontend renders it outside `menuItems`; guests must always be able to switch language/theme) — a manager must not be able to disable it. Hand-off note for the guest API: serve it as fixed chrome. |
| Stay `status` | `BOOKED`/`CANCELLED` stored; **"active" derived** from the clock | Nobody flips a state machine at 15:00 on a box in a building. With non-overlap (invariant 5) over non-cancelled stays, "at most one active per room" holds by construction. |
| Overlap enforcement | **Control-level under a per-room `SELECT … FOR UPDATE`**, no DB exclusion constraint | The DB alternative (`EXCLUDE USING gist` + `btree_gist`) would mean enabling a Postgres extension — avoidable governance surface. The repository is package-private, so the Control is provably the only write path (**still true after 2026-08-25**: `StayRepository` moved into `de.gxp.stay.control` precisely so it could keep that visibility, and `ControlPackageVisibilityTest` now enforces it mechanically); the room-row lock serializes concurrent stay writes per room, making the range check race-free at any isolation level. |
| Asset upload transport | **Raw binary body** (`image/png` / `image/jpeg`), `kind` as query param — no multipart | Streams naturally, generator-friendlier, one less moving part. Multipart (built into `quarkus-rest`) is the fallback if the generator fights back — either way no new dependency. |
| MIME allow-list | **PNG + JPEG only** | The JDK's ImageIO can decode/verify/downscale both. SVG is rejected: not raster-verifiable, and a stored-XSS vector when served (`image/svg+xml` executes script on direct navigation). WebP: no JDK decoder → cannot content-verify → out. |
| No new dependencies | Confirmed line by line | ImageIO, `MessageDigest`, `SecureRandom`, PBKDF2 (`SecretKeyFactory`) are JDK; the auth mechanism implements the SPI of approved `quarkus-security` (+ transitive `quarkus-vertx-http`); `UNIQUE NULLS NOT DISTINCT` is core Postgres 15+ syntax (image is 17.11). Anything found missing at implementation → ⏸ stop. |

## Pinned parameters

| What | Pin |
|---|---|
| PBKDF2 | `PBKDF2WithHmacSHA256`, **600 000 iterations** (OWASP 2023), 16-byte random salt, 256-bit key; stored self-describing in one text column: `pbkdf2-sha256$<iter>$<salt-b64>$<hash-b64>` — a later parameter bump rehashes on next successful login |
| Bearer token | 32 bytes `SecureRandom` → base64url (no padding); server stores **SHA-256 hex** of it, never the raw value |
| Idle expiry | **600 s**, slid by every authenticated request; refreshed expiry on every authenticated response as header `GXP-Token-Expires` (ISO-8601 instant); also in the login body (`idleTimeoutSeconds`, `expiresAt`) |
| Login throttling | **None — deliberately (REQ-326, owner decision 2026-08-24).** No lockout, no rate limit, no concurrency gate on `login` or `changePassword`; no `429` on either. Two mechanisms were tried and withdrawn the same day: the 5-failure / 30 s global lockout (an unauthenticated DoS — one global counter checked *before* verification, so the correct password got `429`) and the fair single-permit gate that replaced it (which still shed arriving callers for the whole duration of a flood). The question is deferred until the production hardware is known. **Accepted and recorded:** an unauthenticated LAN caller can spend the box's CPU through PBKDF2, degrading the whole instance, kiosks included. |
| Password policy | ≥ 8 characters at **both** entry points — the change endpoint and, **since 2026-08-24**, the first-start `GXP_ADMIN_PASSWORD` seed (a short value refuses the start, extending the existing refuse-to-start posture for the unset variable). The earlier "not policy-checked, it would brick a box remotely" call is superseded: with **no throttle of any kind** left, this is the *only* thing carrying brute-force resistance (a throttle was worth ~10×, the policy ~10⁸×), and the box has not started yet, so nothing is bricked — it refuses the *first* boot, with a message naming the rule, never the value. |
| Asset cap | 10 MiB (app-enforced while reading → `413`); `quarkus.http.limits.max-body-size=12M` set explicitly so the app cap, not the HTTP layer, produces the error |
| Downscale | longest edge > **2560 px** → scaled to 2560 (bilinear, `Graphics2D`), re-encoded in the source format (JPEG q≈0.85, PNG lossless, alpha preserved); sha/size/dimensions computed over the **stored** bytes |
| Seed defaults | main language `de`; offered languages `de` (`DE`/`Deutsch`/`de-DE`), `en` (`EN`/`English`/`en-GB`); appearances `light`+`dark` (icons `phosphorSun`/`phosphorMoon`), default `light`; timezone `Europe/Berlin`; one room `code='1'`, name `Zimmer 1`; neutral greeting/standby copy (placeholder prose, manager edits it) |
| Language codes (v1) | `de`, `en` — DB `CHECK`, matching the kiosk's compiled catalogues |

*Verify-at-implementation* (names/mechanics, not intent): `jaxrs-spec` binary
request-body parameter type; `useBeanValidation=true` behavior in generator
7.24.0; package-private visibility acceptance for CDI beans implementing the
auth SPI (if ArC demands `public`, the class stays in `de.gxp.auth` with a
comment — it is an infrastructure adapter like the Boundary, not a Control).
**Resolved, and re-verified 2026-08-25:** ArC accepts package-private for all
three adapters, and they survived the move to `de.gxp.auth.control`
package-private too — Quarkus discovers them by implemented interface and
`@Provider`, never by package (proven by `DeviceAuthTest`, `AuthResourceTest`
and the pairing suites passing after the move, not by compilation alone).

## Architecture — BCE packages

> **Amended 2026-08-25 (owner decision D1, constitution v1.6.0) — read the
> block below as the FEATURE map, not the package map.** Every package named
> here has since been split into three sub-packages: the Boundary moves to
> `de.gxp.<f>.boundary`, the Control and all its non-JPA collaborators
> (Repository, `ImageProcessor`, `TokenStore`, `Pbkdf2PasswordHasher`,
> `PairingCodes`, the auth SPI adapters, …) to `de.gxp.<f>.control`, and the
> `@Entity` types plus their hand-written `*Mapper` to `de.gxp.<f>.entity`.
> So `AuthResource` is `de.gxp.auth.boundary.AuthResource`, `AuthControl` is
> `de.gxp.auth.control.AuthControl`, `AdminCredential` is
> `de.gxp.auth.entity.AdminCredential`, and so on for every row.
>
> **What that costs, stated plainly:** Java has no sub-package visibility, so
> every Entity and Mapper had to become `public`, and so did the individual
> entity FIELDS a Control writes across the split. The "(all
> package-private)" note on the `de.gxp.auth` row, and the same claim
> wherever it appears below, therefore no longer describes the *entities* —
> it still holds exactly for the collaborators, which is why they moved into
> `.control` rather than beside the entity. Fields were widened one at a
> time, only where a Control genuinely touches them; the rest stayed
> package-private.
>
> **What replaces the compiler** is three dependency-free architecture tests
> in `de.gxp.architecture`, which the constitution makes part of the
> definition of done: `FeatureInternalsImportTest` (nothing outside a feature
> may name its `.entity`/`.control` types, the feature's own `*Control`
> excepted), `EntityExposureTest` (no public signature outside `.entity` may
> mention an entity type) and `ControlPackageVisibilityTest` (only a
> `*Control` may be public inside a `.control` package). ArchUnit was
> rejected — a new dependency on an unpatchable box, principle 4.

```
de.gxp.system      (unchanged, foundation)
de.gxp.auth        AuthResource | AuthControl | AdminCredential, AdminCredentialRepository,
                   TokenStore, Pbkdf2PasswordHasher (all package-private),
                   BearerAuthMechanism + TokenIdentityProvider + TokenExpiryResponseFilter
                   (infrastructure adapters, see note), AdminPasswordSeeder (startup)
de.gxp.text        TemplateControl (public; stateless validate/resolve — no Boundary, no Entity)
de.gxp.property    PropertyResource | PropertyControl | Property, PropertyTranslation,
                   PropertyLanguageOption, PropertyAppearanceOption (+Repository, +Mapper)
de.gxp.room        RoomResource | RoomControl | Room (+Repository, +Mapper)
de.gxp.menu        MenuResource | MenuControl | MenuItemDefinition (+Translation),
                   MenuItemConfig (+Translation), ActionCardConfig (+Translation)
                   (+Repositories, +Mappers)
de.gxp.stay        StayResource | StayControl | Stay (+Repository, +Mapper)
de.gxp.asset       AssetResource | AssetControl | Asset (+Repository, +Mapper, ImageProcessor)
de.gxp.api         ApiErrorMappers (ConstraintViolation→400 ApiError; infrastructure adapter)
```

- **Menu config and action cards share one package deliberately.** Invariants
  2 and 3 make them a single consistency unit; splitting them would force a
  circular Control injection (menu asks "which cards target k", cards ask "is
  k enabled"). One `MenuControl` owns catalogue reads, config CRUD, card CRUD
  and both invariants, in one transaction.
- **Cross-feature injection (public Controls only** — since 2026-08-25 this
  is no longer merely a convention but the thing
  `FeatureInternalsImportTest` fails on**):** `PropertyControl` →
  `AssetControl` (logo/artwork kind check) and `TemplateControl`;
  `RoomControl` → `AssetControl`, `PropertyControl` (offered-option checks);
  `MenuControl` → `TemplateControl`; `StayControl` → `RoomControl`,
  `PropertyControl`; `AuthControl` → nothing domain-side.
- **Infrastructure adapters** (`BearerAuthMechanism`, `TokenIdentityProvider`,
  `TokenExpiryResponseFilter`, `ApiErrorMappers`, `AdminPasswordSeeder`) are
  platform SPI implementations, not Boundaries/Controls: they carry zero
  business logic (the mechanism delegates token lookup to the package-private
  `TokenStore` — still package-private after 2026-08-25, now in
  `de.gxp.auth.control`; mappers only reshape errors). This mirrors the already-pinned
  precedent that the JAX-RS runtime — not the Boundary — maps exceptions.
- All non-2xx outcomes are thrown from Controls as `jakarta.ws.rs`
  exceptions carrying an `ApiError` entity (`Response`-based constructors) —
  the foundation's pinned pattern, now with bodies: `400` unknown
  placeholder/target, `409` offending cards / stay conflict / delete guards,
  `413` upload cap. (No `429` anywhere: `ApiErrors` has no `tooManyRequests`
  overload — it was added with the concurrency gates on 2026-08-24 and removed
  with them the same day, leaving no dead API surface.)

### Auth flow

```
POST /api/manager/login {password}
  AuthControl: PBKDF2 verify (constant-time compare), unthrottled by design
    → 256-bit token → TokenStore.put(sha256(token), now+600s)
    → LoginResponse {token, idleTimeoutSeconds, expiresAt}

any /api/manager/** request
  BearerAuthMechanism: Authorization: Bearer t → TokenIdentityProvider:
    TokenStore.slide(sha256(t)) → hit: SecurityIdentity(role=admin), miss: 401 challenge
  @RolesAllowed("admin") authorizes   // amended 2026-08-24 (D2): on the
                                      // BOUNDARY method/class, not the Control
  TokenExpiryResponseFilter appends GXP-Token-Expires

PUT /api/manager/password {currentPassword, newPassword}
  verify current → policy check → store new hash → TokenStore.clear()   // v1.5.1
```

`AdminPasswordSeeder` observes `StartupEvent`: no credential row → read
`GXP_ADMIN_PASSWORD`; unset/blank → throw (boot fails, message says which
variable to set; the value itself never appears in any message); present →
hash + insert, then treat the variable as nonexistent forever. Runs before
the HTTP server accepts requests (startup observer semantics).

### Invariant 3 algorithm (pinned)

Definitions: `enabled(scope, k)` = per-field resolution room→property→
catalogue-default (default: enabled) — **amended 2026-08-19 to ceiling
resolution: a property-level disable wins over any room-level enable; see
the resolved 🟡 #2 section at the end of this document**; `cards(scope)` =
room's card rows if ≥1 exist, else property's (wholesale override, REQ-308).

On menu-config `PUT` for scope `S` (applied, then validated in the same
transaction before commit):

1. Affected scopes: `S = property` → {property} ∪ all rooms; `S = room r` → {r}.
2. For each affected scope `T`: for each card `c ∈ cards(T)`,
   if `!enabled(T, c.target)` → collect `(c, T)`.
3. Any collected → rollback, `409` with `ApiError` listing every offending
   card (id, resolved title language-fallback, target key) and its scope.

The symmetric check on card `PUT` for scope `S` (invariant 2): every target
must exist (`400`) and satisfy `enabled(S, target)` (`409`). Checking all
rooms on property-level changes is O(rooms × cards) with tiny bounds (≤ 4
cards/scope, dozens of rooms) — correctness over cleverness.

### Stay overlap (invariant 5)

`StayControl.create/update`, inside `@Transactional`: lock the room row
(`SELECT id FROM room WHERE id = ? FOR UPDATE` via the repository), then
query non-cancelled stays of that room with `check_in < :newOut AND
check_out > :newIn AND id <> :self`; any hit → `409` naming it. Adjacent
stays (checkout == next check-in) pass by the strict inequalities. The lock
makes two concurrent overlapping creates serialize; the second sees the first.

### Asset pipeline

Upload: read the body stream counting bytes (cap+1 → `413`, stop reading) →
`ImageIO` detect format (must match declared content type, else `400`) →
decode (undecodable → `400`) → downscale if needed → re-encode → sha-256,
dimensions, size over stored bytes → persist. Delivery: `If-None-Match`
equals stored sha → `304`; else stream the `byte[]` to the response with
`ETag: "<sha256>"` and `Cache-Control: private, max-age=…`. List/metadata
use a projection query that never selects the payload column (the JPA entity
maps the payload lazily/via dedicated fetch; the honest BYTEA-materialization
bound is the 10 MiB cap — PRD risk).

## Data model (Flyway)

**`V2__domain_config.sql`** — tables + seeds (`V1`/`instance_info` untouched):

```sql
property (id smallint PK CHECK (id=1), name text NOT NULL,
          main_language text NOT NULL CHECK (main_language IN ('de','en')),
          timezone text NOT NULL,               -- ZoneId-validated in Control
          brand_primary/brand_primary_deep/brand_secondary text NOT NULL CHECK (~ '^#[0-9a-fA-F]{6}$'),
          default_appearance text NOT NULL CHECK (IN ('light','dark','system')),
          logo_asset_id bigint NULL REFERENCES asset ON DELETE RESTRICT,
          artwork_asset_id bigint NULL REFERENCES asset ON DELETE RESTRICT)
property_translation (property_id FK, language CHECK (IN ('de','en')),
          hero_subtitle text, headline text, paragraph text,
          standby_headline text, standby_message text,
          PRIMARY KEY (property_id, language))
property_language_option (property_id FK, code CHECK (IN ('de','en')),
          short_label, label, locale, sort_index, PK (property_id, code))
property_appearance_option (property_id FK, mode CHECK (IN ('light','dark','system')),
          icon, sort_index, PK (property_id, mode))
room (id bigserial PK, code text NOT NULL UNIQUE CHECK (code <> ''),
      display_name text NOT NULL,
      artwork_asset_id bigint NULL REFERENCES asset ON DELETE RESTRICT,
      default_language text NULL CHECK, default_appearance text NULL CHECK)
menu_item_definition (key text PK, default_icon text NOT NULL,
      route text NOT NULL, default_sort_index int NOT NULL,
      introduced_release text NOT NULL)
menu_item_definition_translation (key FK, language, label, PK (key, language))
menu_item_config (id bigserial PK, menu_item_key FK, room_id bigint NULL
      REFERENCES room ON DELETE CASCADE,
      enabled boolean NULL, sort_index int NULL, icon_override text NULL,
      UNIQUE NULLS NOT DISTINCT (menu_item_key, room_id))
menu_item_config_translation (config_id FK ON DELETE CASCADE, language,
      label, PK (config_id, language))
action_card_config (id bigserial PK, room_id bigint NULL REFERENCES room
      ON DELETE CASCADE,
      sort_index int NOT NULL CHECK (BETWEEN 0 AND 3),
      icon text NOT NULL,
      target_menu_item_key FK REFERENCES menu_item_definition,  -- deliberately NOT unique
      UNIQUE NULLS NOT DISTINCT (room_id, sort_index))          -- ≤4/scope by construction
action_card_config_translation (card_id FK ON DELETE CASCADE, language,
      title_template text NOT NULL, description_template text NOT NULL,
      PK (card_id, language))
stay (id bigserial PK, room_id FK REFERENCES room ON DELETE RESTRICT,
      display_name text NOT NULL,
      check_in timestamptz NOT NULL, check_out timestamptz NOT NULL,
      CHECK (check_in < check_out),
      language text NULL CHECK, appearance text NULL CHECK,
      status text NOT NULL CHECK (IN ('BOOKED','CANCELLED')))
asset (id bigserial PK, mime_type text CHECK (IN ('image/png','image/jpeg')),
      byte_size int NOT NULL, width int, height int,
      sha256 text NOT NULL, kind text CHECK (IN ('LOGO','ARTWORK')),
      data bytea NOT NULL)
```

Seeds: catalogue 5 rows exactly matching the frontend
(`home`/``/`phosphorHouse`/0, `info`/`/info`/`phosphorInfo`/1,
`umgebung`/`/umgebung`/`phosphorMapPin`/2,
`smart-home`/`/smart-home`/`phosphorLightbulbFilament`/3,
`services`/`/services`/`phosphorShoppingBag`/4; `introduced_release`
`1.0.0`) + definition translations `de`+`en` verbatim from
`example-property.data.ts`; one property row (neutral copy, pinned defaults
above) + `de` translation row; language/appearance options; one room. No
menu-config rows (absence = catalogue defaults, enabled), no cards (0 is
valid).

**`V3__admin_credential.sql`**: `admin_credential (id smallint PK CHECK
(id=1), password_hash text NOT NULL, updated_at timestamptz NOT NULL)` — no
seed row; the seeder writes it (schema and code land together, as the
foundation design promised).

Room deletion semantics (REQ-320): config rows `ON DELETE CASCADE` (the
room's own settings), stays `ON DELETE RESTRICT` + Control pre-check → `409`
naming the stays.

## API contract (added to `api/openapi.yaml`; tag `manager`)

`components.securitySchemes.bearerAuth` (`type: http, scheme: bearer`);
applied per-operation to everything below except `login`.

| Op | Method & path | Success | Named errors |
|---|---|---|---|
| `managerLogin` | `POST /api/manager/login` | 200 `LoginResponse{token,idleTimeoutSeconds,expiresAt}` | 401 generic (the only failure — no `429`, login is deliberately unthrottled since 2026-08-24) |
| `managerLogout` | `POST /api/manager/logout` | 204 | 401 |
| `changeManagerPassword` | `PUT /api/manager/password` | 204 (all tokens dropped) | 400 policy/current-mismatch, 401 |
| `getProperty` / `updateProperty` | `GET`/`PUT /api/manager/property` | 200 `PropertyDto` | 400 (validation, options, templates) |
| rooms CRUD | `GET/POST /api/manager/rooms`, `GET/PUT/DELETE …/rooms/{roomId}` | 200/201/204 | 400, 404, 409 (delete w/ stays) |
| `getMenuCatalogue` | `GET /api/manager/menu/catalogue` | 200 `[MenuItemDefinitionDto]` | — (read-only; no write op exists) |
| menu config | `GET`/`PUT /api/manager/menu-config`, `GET`/`PUT /api/manager/rooms/{roomId}/menu-config` | 200 (scope's override entries) | 400 unknown key, 409 invariant 3 (offending cards) |
| action cards | `GET`/`PUT /api/manager/action-cards`, `GET`/`PUT /api/manager/rooms/{roomId}/action-cards` | 200 (whole set 0–4) | 400 invariant 1/unknown target/template, 409 invariant 2 |
| stays CRUD | `GET/POST /api/manager/stays[?roomId]`, `GET/PUT/DELETE …/stays/{stayId}` | 200/201/204 | 400, 404, 409 overlap |
| assets | `GET /api/manager/assets` (metadata), `POST /api/manager/assets?kind=` (binary body), `GET /api/manager/assets/{assetId}` (binary, ETag), `DELETE …/{assetId}` | 200/201/204/304 | 400 type/content, 404, 409 in use, 413 cap |

DTO conventions: localized values are language-keyed maps
(`{"de": "…", "en": "…"}`) mirroring the frontend's `Localized<T>`; manager
reads return **raw** templates; `ApiError{code,message}` (foundation shape)
gains machine-usable `code` values (e.g. `menu-item-targeted-by-cards`,
`stay-overlap`, `asset-in-use`, `unknown-placeholder`) and, where a list is
needed (offending cards), a structured `details` array — an **additive**
change to the shared schema.

## Risks & trade-offs

- **One `MenuControl` is large.** Accepted: the invariants make menu+cards one
  transactional unit; two Controls would ping-pong. Repositories keep the
  queries out of the control flow.
- **Whole-set card PUT / per-field config inheritance asymmetry** — deliberate:
  config rows have a natural identity (the catalogue key), cards do not.
  Recorded so the manager UI designs against it knowingly.
- **In-memory token state** disappears on restart — an attacker who
  can restart the box already owns it; accepted (spec §6 shape). Since
  2026-08-24 there is no throttling state at all, in memory or otherwise.
- **Derived "active" stay needs a correct clock** — sharpens spec §11 item 6
  (owner-level, open); recorded, not solved here.
- **Neutral seed copy** shows until the manager edits it; flagged to
  `manager-frontend`.
- **Standby text fields ship before the standby screen design** (§11 item 1)
  — schema only; if the design later needs different fields, that is a
  normal V4 migration, not a contract break (guest API doesn't read them yet).

## Open decisions

None left `[NEEDS CLARIFICATION]` — every decision above is either settled
ground (spec §2, constitution v1.5.1), a delegated planner call recorded in
"Scope decisions", or a pinned parameter. **No approval gate is triggered:**
no dependency, no infrastructure change, no new cross-cutting convention
beyond what the constitution already records, no nested `CLAUDE.md` edit
proposed. Should implementation contradict the no-new-dependency claim, `dev`
stops with ⏸ per the constitution.

## Implementation deviations (recorded at 05_implement, ratified at 06_review 2026-08-19)

As-built differences from the text above — the code is the shape described
here, all judged acceptable at review (full judgments in
[06-review.md](06-review.md)):

1. **Tags:** one `manager-*` tag per BCE package
   (`manager-auth|property|rooms|menu|stays|assets`) instead of the single
   `manager` tag written above — one tag = one generated interface = one
   Boundary; a single tag would have forced one Boundary implementing
   everything. Now also a `backend/CLAUDE.md` convention (user-approved
   2026-08-19). Paths/operations are exactly the table above.
2. **Binary GET** (`getAssetBinary`): the generated interface returns `File`
   (jaxrs-spec; `returnResponse` is global-only), which cannot carry
   ETag/Cache-Control/304 — `AssetControl.binary()` therefore always throws
   the fully built 200/304 `Response` (the pinned thrown-outcome channel).
3. **`@ResponseStatus(201)`** sits on the three create Boundary overrides
   (user-approved convention). Because Quarkus resolves that annotation at
   build time against the *generated interface* method,
   `de.gxp.api.ResponseStatusFilter` honors it at runtime via `ResourceInfo`
   (rewrites only a default 200; thrown Responses and errors untouched).
4. **Generator config flag** `<dateLibrary>java8</dateLibrary>` added
   (`OffsetDateTime` instead of legacy `java.util.Date`) — config, not a
   dependency.
5. **Referrer checks are native queries on foreign tables**
   (`AssetRepository.findReferrers` reads `property`/`room`;
   `RoomRepository.staysOf` reads `stay`) — injecting the owning Controls
   would be circular. Parameterized, entities never leave their packages,
   `ON DELETE RESTRICT` FKs as backstop.
6. **Test-profile tuning knobs:** `gxp.auth.pbkdf2-iterations` (default
   600 000, `%test` 1000 — behavior-neutral, self-describing hash format),
   `gxp.auth.idle-timeout-seconds` (600). **No throttling knobs exist:**
   `gxp.auth.lockout-seconds` was removed 2026-08-24 with the lockout, and
   `gxp.auth.gate-*` / `gxp.pairing.gate-*` were added and removed the same
   day with the concurrency gates (owner decision — REQ-326/REQ-409).
7. **`de.gxp.api.ApiErrors`** — shared builder for thrown non-2xx `ApiError`
   outcomes (now a `backend/CLAUDE.md` convention). Also
   `ApiErrorMappers.WrappedJacksonExceptionMapper`: Quarkus' Jackson body
   reader wraps parse failures in an entity-less `WebApplicationException(400)`
   before mappers run, so a dedicated mapper unwraps those into the shared
   shape (entity-less framework WAEs pass through untouched).
8. **Seed brand colors** are neutral slate placeholders
   (`#5b6472`/`#3f4652`/`#8892a0`) — principle 1; `manager-frontend` surfaces
   them as "please configure me".
9. **`GXP-Token-Expires` on the login response** is taken from the
   `LoginResponse` body by the response filter (login is `@PermitAll`, so no
   identity attribute exists there).
10. **Invariant-3 conflict details are deduped per card** (first affected
    scope wins) — "every offending card and its scope" reads per-card: a
    property card inherited by several rooms is one offender, not one per room.

**~~Open design question (review 🟡 #2)~~ — RESOLVED by user decision
(2026-08-19), superseding the original resolution rule.** The user pinned a
hierarchy principle: *the property is the global ceiling — a room can only
offer what the property offers.* This amends the design as follows (menu
items and, via the guards, action cards):

1. **Ceiling resolution.** `enabled(room, k)` is now
   `enabled(property, k) AND room-override(k, default = true)`. A
   property-level disable hides item `k` in **every** room; an explicit
   room-level "enabled" row can never resurrect it. Room overrides remain
   pure opt-outs. (`enabled(property, k)` stays property→catalogue-default.)
2. **Room-enable of a property-disabled item is rejected.** Room-scope
   menu-config `PUT` setting `enabled=true` for an item the property has
   disabled → `409` with the shared `ApiError` naming the item — the hotel
   doesn't offer it, so the room can't.
3. **Property-level disable warns about dependent rooms.** Property-scope
   menu-config `PUT` disabling item `k` while explicit room-level "enabled"
   rows for `k` exist → `409` listing those rooms (same blocking
   409-with-details channel as invariant 3; the manager frontend renders the
   "rooms depend on this — it will be removed there too" warning, clears the
   room overrides on confirm, and retries). Stranded-*card* detection
   (invariant 3) is unchanged and evaluates against the new ceiling
   resolution.
4. **Card-PUT guard extended to inheriting rooms (the original 🟡 #2).** A
   *property-scope* card `PUT` is validated against every room that inherits
   the property's cards (no own card rows): any inheriting room where the
   card's target resolves disabled (a local room opt-out — the only way
   left, given rule 1) → `409` in the invariant-3 conflict shape, listing
   card and room. Reuses `collectStrandedCards`. Room-scope card `PUT`
   validation is unchanged (its own scope only). Bounds unchanged:
   O(rooms × cards), ≤ 4 cards/scope.

Tracked in [06-review.md](06-review.md); implemented post-review as the
🟡 #2 fix round.
