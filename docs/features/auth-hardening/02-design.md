# Design: Authentication Hardening (remote-operations Part A)
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **TODO:** [03-todo.md](03-todo.md)
**Status:** In Review (2026-08-25)

Governed by constitution **v1.8.0** (Auth & transport → "Authentication
hardening" paragraph, principle 4's three new bullets), spec §4 + §12 of
`docs/backend/remote-operations.md`, and `backend/CLAUDE.md`. Requirements are
referenced by `REQ-6xx`, never restated.

## Architecture decision

### Package layout — three feature packages, one infrastructure helper

`de.gxp.auth` today mixes sessions, the credential row, the hasher and the
seeder. Splitting accounts out keeps each package one concern and gives each
its own contract tag with exactly one Boundary (the one-tag-one-Boundary rule
in `backend/CLAUDE.md` is then met without invoking the two-auth-surfaces
exception).

| Package | Owns | Boundary (tag) | Public Control |
|---|---|---|---|
| `de.gxp.auth` (existing, slimmed) | sessions: `TokenStore` (now keyed by user), the **per-address throttle**, login orchestration, logout, the SPI adapters (`BearerAuthMechanism`, `TokenIdentityProvider`, `TokenExpiryResponseFilter`) | `AuthResource` (`manager-auth`: login, logout, change own password) | `AuthControl` |
| `de.gxp.user` (**new**) | `manager_user` + `manager_recovery_code`, `Pbkdf2PasswordHasher` (moves here), `FirstUserSeeder` (replaces `AdminPasswordSeeder`), `TotpVerifier`, `RecoveryCodes`, `Base32` | `UserResource` (`manager-users`: user administration + second-factor self-service) | `UserControl` |
| `de.gxp.audit` (**new**) | `audit_entry`, the append-only writer, paging, retention | `AuditResource` (`manager-audit`: read only) | `AuditControl` |
| `de.gxp.infrastructure.http.ClientAttribution` (**new**, request-scoped) | the ONE transport fact — attributed address + via-trusted-proxy — read once per request from the Vert.x `RoutingContext` (`CurrentVertxRequest`, part of the approved `quarkus-vertx-http`) | — | — (infrastructure, referenceable by any Control like `ApiErrors`) |
| `de.gxp.infrastructure.scheduler.AuditScheduler` (**new**) | the 02:00 retention trigger, one delegating call | — | — |

Dependencies between Controls (no cycles): `AuthControl → UserControl`,
`AuthControl → AuditControl`, `UserControl → AuditControl`, every mutating
Control (`property`, `room`, `menu`, `stay`, `asset`, `device`) `→ AuditControl`.
`UserControl` never references `AuthControl`; token invalidation on
deactivation/reset flows back through a **synchronous CDI event**
(`UserControl.CredentialsInvalidated(username)` fired by `UserControl`,
observed by `AuthControl`, which drops that user's tokens) — greppable, no
circular injection, no interceptor magic. `FeatureInternalsImportTest` allows
a nested public record on a `*Control`; `DeviceControl.DeviceIdentity` is the
precedent.

### A1 — `LoginThrottle` (package-private, `de.gxp.auth.control`) — REQ-601…605

- `ConcurrentHashMap<String address, Bucket>`; `Bucket{int failures, Instant
  windowStart, Instant blockedUntil, Instant lastTouched}`.
- `check(address)`: if `blockedUntil` is in the future → throw
  `ApiErrors.tooManyRequests("too-many-attempts", …, retryAfterSeconds)`
  (re-added to `ApiErrors`, with the `Retry-After` header — the one
  header-carrying builder). Called **first** in `AuthControl.login` and in
  `AuthControl.changePassword`, before any repository call and before any
  hashing.
- `recordFailure(address)`: failures in the current 15-minute window; the
  5th failure sets `blockedUntil = now + 60 s × 2^(exhaustions−1)`, capped at
  900 s; the window and the escalation counter live in the bucket.
- `recordSuccess(address)`: removes the bucket.
- Bound: `MAX_TRACKED = 10_000`; on insert above the cap, evict every bucket
  whose `lastTouched` is older than 1 h, then — if still full — the oldest
  `lastTouched`. No scheduler job: the map self-bounds on insert, so nothing
  depends on the 02:00 sweep for correctness or memory.
- Test probes (package-private): `reset()`, `failuresOf(address)`.
- Logs nothing. Never consults the user table.
- **Bucket key (amendment 2026-08-26, review finding #2, decided by the
  lead under delegated authority):** IPv4 → the address; IPv6 → its **/64
  prefix** (the 8 low bytes of the `Inet6Address` masked, plain JDK, no
  dependency, rendered `<prefix>/64`); IPv4-mapped IPv6 → the IPv4 address.
  Rationale: every LAN host owns a /64 and privacy extensions rotate
  addresses inside it for free, so a per-address IPv6 key would give one
  machine an unlimited budget. Residual: an attacker who controls several
  /64s (or several IPv4 addresses) still has one budget per prefix — the
  distributed-attacker residual A3 exists for.
- **Steady-state figure (recorded 2026-08-26, review finding #7):** the
  15-minute window is anchored at the first failure, and a failure past it
  resets both the count and the escalation. One address failing at every
  block expiry gets 5 free → blocks 60/120/240/480/900 s → window reset at
  ~30 min → 5 free again: **≈ 14 guesses per 30 minutes ≈ 670 guesses per
  day per address (per /64 for IPv6)**. Spec-conformant (REQ-602); stated so
  the trade-off is visible when the second factor lands.

Constants pinned in code, one `%test` override
(`gxp.auth.throttle-block-seconds=1`) so the escalation test does not sleep
for a minute.

### A1.4 — `ClientAttribution` (request-scoped, `de.gxp.infrastructure.http`) — REQ-603

```
record Attribution(String address, boolean viaTrustedProxy)
```

- `address` = `routingContext.request().remoteAddress().hostAddress()` — with
  `quarkus.http.proxy.proxy-address-forwarding=true` and the peer inside
  `trusted-proxies`, Quarkus has already replaced it with the forwarded
  address; from any other peer the headers are ignored by Quarkus itself.
- `viaTrustedProxy` = the request header `X-Forwarded-Trusted-Proxy` equals
  `true` **and** the config `quarkus.http.proxy.enable-trusted-proxy-header`
  is `true` (injected `@ConfigProperty`, default `false`). Quarkus only stamps
  that header for requests forwarded by a trusted proxy and strips a forged
  one — but the handler is only installed when address forwarding is on, so
  the config guard is what makes a forged header inert on a LAN-only box.
- Configuration (`application.properties`, property names verified against
  the Quarkus 3.33.3.1 jar 2026-08-25), **one operator switch** for Part D:

```
# Trusted proxy (remote-operations A1.4). Off by default: on a LAN-only box every
# Forwarded/X-Forwarded-* header is ignored and the socket peer is the address.
quarkus.http.proxy.proxy-address-forwarding=${GXP_TRUSTED_PROXY_ENABLED:false}
quarkus.http.proxy.allow-forwarded=${GXP_TRUSTED_PROXY_ENABLED:false}
quarkus.http.proxy.enable-trusted-proxy-header=${GXP_TRUSTED_PROXY_ENABLED:false}
quarkus.http.proxy.trusted-proxies=${GXP_TRUSTED_PROXY:127.0.0.1/32}
```

`allow-x-forwarded` stays **off** (one header format, the standard one;
fewer forgery combinations). `use-proxy-protocol` stays off; if Part D picks
the PROXY protocol, that line flips and `allow-forwarded` goes back off —
no code changes. `GXP_TRUSTED_PROXY` is added to `compose.yaml` as optional.
The `trusted-proxies` default matters: Quarkus' own default is *all proxies
trusted*, which with forwarding enabled would trust every LAN peer — hence
an explicit loopback default even though forwarding is off.

**Amendments 2026-08-26 (review of T-01…T-04, finding #1):**

- *Relay obligation (binding for Part D, recorded in
  `docs/backend/remote-operations.md` §7):* Quarkus honours the **first**
  `for=` element of a multi-hop `Forwarded` header (`ForwardedParser`,
  regex + `find()`, verified in the 3.33.3.1 jar and pinned by
  `TrustedProxyLoginTest`). The relay therefore MUST strip every
  client-supplied `Forwarded` / `X-Forwarded-*` / `X-Forwarded-Trusted-Proxy`
  header and set exactly one `Forwarded: for=<client>` element — it never
  appends.
- *Unparseable attributed address:* a `for=` token that is not an IP literal
  (RFC 7239's obfuscated `_hidden`, or any garbage a mis-configured relay
  lets through) has no `hostAddress()`. `ClientAttribution` answers
  **`400 unattributable-address`** (via `ApiErrors`, no state, no
  derivation). Rejected alternatives: falling back to the socket peer with
  `viaTrustedProxy=false` (silently drops the second-factor requirement of
  D6 — a downgrade a client could trigger), and bucketing under the raw
  token (one shared bucket for every `_hidden` sender — the A1.4 weapon).
- `allow-x-forwarded=false` and `use-proxy-protocol=false` are now explicit
  lines in `application.properties` (finding #4): Quarkus derives
  `allow-x-forwarded` as `!allow-forwarded`, and a derived default is not a
  posture.
- A second test profile `UntrustedPeerProfile` (`trusted-proxies=192.0.2.1/32`)
  proves the switch-on negative (finding #3).

Test profile `TrustedProxyProfile` (`@TestProfile`): the four properties on,
`trusted-proxies=127.0.0.1/32` — rest-assured's loopback origin becomes the
trusted proxy, so a test sends `Forwarded: for=203.0.113.9` to be
attributed there. Verification note for the dev: confirm whether Quarkus
stamps `X-Forwarded-Trusted-Proxy` for a trusted peer that sends **no**
`Forwarded` header; the tests in that profile always send one, so either
answer is fine — the default-profile test proves the spoof case.

### A2 — `de.gxp.user` — REQ-607…613

- `entity/ManagerUser` (`id`, `username`, `passwordHash`, `active`,
  `totpSecret` (nullable), `totpConfirmedAt` (nullable), `totpLastStep`
  (nullable), `createdAt`, `updatedAt`) + `entity/RecoveryCode` (`id`,
  `user`, `codeHash`, `usedAt`) + `entity/ManagerUserMapper` (→ `UserDto`,
  never hash/secret).
- `control/UserControl` (public): `list()`, `create(UserCreateRequest)`,
  `deactivate(id)`, `reactivate(id)`, `resetPassword(id, PasswordResetRequest)`,
  `resetSecondFactor(id)`, `changeOwnPassword(PasswordChangeRequest)`,
  `secondFactorStatus()`, `enrollSecondFactor()`, `confirmSecondFactor(code)`,
  `disableSecondFactor(password)`, `regenerateRecoveryCodes(password)`,
  `verifyLogin(LoginAttempt) → Verdict` (used only by `AuthControl`), plus the
  nested `public record CredentialsInvalidated(String username)`.
- `verifyLogin(username, password, totpCode, recoveryCode, requireSecondFactor)`:
  loads the active user; if none, verifies `password` against a **fixed dummy
  PBKDF2 hash** (same iterations) and returns `FAIL` — equal work, no
  username oracle. Then password → if false `FAIL`. Then, only when
  `requireSecondFactor`: user without confirmed factor → `FAIL`; `totpCode`
  present → `TotpVerifier.verify(secret, code, lastStep)` (±1 step; accepted
  step must be > `totpLastStep`, then stored); else `recoveryCode` → SHA-256
  match against an unused row → mark used; else `FAIL`. Returns `OK(username)`.
  The Verdict never carries a reason — `AuthControl` maps `FAIL` to the one
  generic 401.
- "Own" operations resolve the user from `SecurityIdentity.getPrincipal().getName()`.
- `control/FirstUserSeeder` (`@Observes StartupEvent`, `@Transactional`):
  when `count() == 0` create `GXP_ADMIN_USERNAME` (`gxp.admin.username`,
  default `admin`, contract pattern enforced) with `GXP_ADMIN_PASSWORD`
  (`gxp.admin.password`, ≥ 8, refuse-to-start messages name the variable
  only). Existing `AdminPasswordSeederTest` cases move over.
- `control/Pbkdf2PasswordHasher` — moved verbatim (`git mv`), package-private
  in `de.gxp.user.control`.
- `control/TotpVerifier` — `javax.crypto.Mac("HmacSHA1")`, RFC 6238/4226
  truncation, injectable `Clock`, 30 s, 6 digits, window ±1; `otpauthUri()`
  builds `otpauth://totp/GXP:<username>?secret=<base32>&issuer=GXP&algorithm=SHA1&digits=6&period=30`.
- `control/Base32` — RFC 4648 encoder only (~25 lines; the JDK has none).
  *(Dev 2026-08-26, T-08:)* consequently `totp_secret` is stored **base64**
  (JDK codec) and rendered base32 only for the one-time enrollment response
  and the `otpauth://` URI.
- `control/RecoveryCodes` — 10 codes × 10 chars `[a-z0-9]` from
  `SecureRandom`; SHA-256-hex at rest (high-entropy random secrets get the
  token treatment, not PBKDF2 — ten PBKDF2 derivations per login attempt on
  the Pi would be a 22-second amplifier).
- `TokenStore` (in `de.gxp.auth.control`): map value becomes
  `Session(String username, Instant expiry)`; `issue(username)`;
  `removeAllOf(username)`; `slide` returns the `Session`.
  `TokenIdentityProvider` sets the principal name to the username and keeps
  `ATTRIBUTE_TOKEN_HASH`/`ATTRIBUTE_TOKEN_EXPIRY`; the device branch is
  untouched (REQ-628).
- `AuthControl.login`: `throttle.check(addr)` → `viaProxy && no code` →
  `401 second-factor-required` (no failure counted — nothing was verified, and
  counting it would let a proxy-side client be throttled for a UI round-trip)
  → `userControl.verifyLogin(...)` → on `FAIL`: `throttle.recordFailure`,
  `audit.recordLoginFailure(username, attribution)` (independent
  transaction), `401 invalid-credentials`; on `OK`: `throttle.recordSuccess`,
  `tokenStore.issue(username)`, `audit.recordLoginSuccess(...)`, response with
  `username`.
- `AuthControl.changePassword` keeps its endpoint under `manager-auth`,
  delegates verification to `userControl.changeOwnPassword`, and on success
  drops the caller's tokens (via the event); a mismatch counts as a throttle
  failure for the caller's address.
- `AuthControl.onCredentialsInvalidated(@Observes UserControl.CredentialsInvalidated e)`
  → `tokenStore.removeAllOf(e.username())`.

### A3 — enforcement and error shapes — REQ-614…619

Single login endpoint, single token kind, no `if (remote)` anywhere except
the two consumptions of `Attribution.viaTrustedProxy` that constitution
v1.8.0 allows: the throttle bucket key (address) and the second-factor
requirement (flag). Error codes on `managerLogin`:

| Situation | Status / `code` | PBKDF2 ran? |
|---|---|---|
| address blocked | `429 too-many-attempts` + `Retry-After` | no |
| via proxy, no `totpCode`/`recoveryCode` | `401 second-factor-required` | no |
| everything else that fails (unknown user, inactive, wrong password, wrong/replayed/spent code, no confirmed factor via proxy) | `401 invalid-credentials` | yes (dummy hash when no user) |

Rejected alternative: a distinct `second-factor-not-enrolled` code. It is
only knowable after the password verified, so it is a password oracle for
anyone who obtained a password but not the device — exactly the attacker A3
exists for. The user learns their enrollment state from the LAN
`GET /api/manager/users/me/second-factor` instead.

### A4 — `de.gxp.audit` — REQ-620…624

- `entity/AuditEntry` (`id`, `occurredAt`, `actor` nullable, `address`,
  `viaProxy`, `action`, `entity`, `entityId` nullable, `field` nullable) +
  `AuditEntryMapper` → `AuditEntryDto`. **No value column exists**, by schema.
- `control/AuditControl` (public):
  - `record(String action, String entity, String entityId, List<String> changedFields)`
    — `@Transactional(REQUIRED)`: one row per field, or one field-less row
    when the list is empty/null. Actor = principal name (null when
    anonymous), address/viaProxy from `ClientAttribution`.
  - `recordLoginSuccess(username, attribution)` / `recordLoginFailure(username, attribution)`
    — `@Transactional(REQUIRES_NEW)`, so the row commits although the login
    request ends in `401` (REQ-624). `AuthControl.login` therefore carries
    **no** `@Transactional` of its own any more (the token store is memory;
    the only DB work is in `UserControl.verifyLogin`, which is transactional
    itself — the TOTP last-step / recovery-code-used writes commit with it).
  - `static List<String> changedFields(Object before, Object after)` —
    compares the public getters of two generated DTOs of the same class and
    returns the **property names** whose values differ (JavaBean getter
    reflection, ~20 lines; values are compared, never returned, never
    stringified). Used by `update` operations: `before = current DTO`,
    `after = incoming DTO`.
  - `page(page, size) → AuditPageDto` newest first (`ORDER BY occurred_at DESC, id DESC`).
  - `pruneOlderThan(Duration)` → count, public for the scheduler.
- Actions and entities (strings pinned as constants on `AuditControl`):
  `login-success`/`login-failure` (`user`), `create`/`update`/`delete`
  (`property`, `room`, `stay`, `asset`), `replace` (`menu-config`,
  `action-cards`; entityId = `property` or the room id), `confirm`/`rename`/
  `revoke` (`device`), `user-create`/`user-deactivate`/`user-reactivate`/
  `password-reset`/`password-change`/`second-factor-reset`/
  `second-factor-enroll`/`second-factor-confirm`/`second-factor-disable`/
  `recovery-codes-regenerate` (`user`, entityId = username). Menu items
  inside a replace are not enumerated (whole-set semantics, and a key name
  is not a field of a guest-sensitive value). *Added by dev 2026-08-26
  (T-12, lead instruction), amended after review the same day:*
  ~~`second-factor-required` (`user`, entityId **null**, actor null,
  `REQUIRES_NEW`) for the code-less via-proxy 401~~ — *withdrawn 2026-08-26
  (06_review 🟡 #1, lead decision): not recorded at all; an unthrottled,
  unauthenticated path must not write (D20);* failed-login rows carry the **matched canonical
  username** (`UserControl.Verdict.matchedUsername`, set when
  `byUsername` found a row, active or not, never returned to the client) or
  null as entityId, actor null. The submitted text is never stored: with the
  username pattern `^[a-z0-9][a-z0-9._-]{0,63}$` a password typed into the
  wrong field would otherwise be persisted for 365 days, readable by every
  admin (principle 3). `confirm` (`device`) uses the pairing request id as
  entityId because the device row does not exist until the kiosk polls.
- `AuditScheduler#pruneExpiredEntries` — `cron = "0 0 2 * * ?"`, `SKIP`,
  catches `RuntimeException`, logs class name only, logs the count only when
  > 0. Added to `PINNED_INVENTORY` (and `INVENTORY_FLOOR` stays 2).

### Contract changes — `api/openapi.yaml`

- `manager-auth`: `LoginRequest{username (pattern), password, totpCode?
  (^[0-9]{6}$), recoveryCode? (^[a-z0-9]{10}$)}`; `LoginResponse` + `username`;
  `managerLogin` gains `429` (with `Retry-After` header) and the two 401 codes
  documented; `changeManagerPassword` gains `429`. Tag description rewritten
  (no "shared password").
- **`manager-users`** (new tag → `UserResource`):
  - `GET /api/manager/users` → `UserDto[]`
  - `POST /api/manager/users` (`UserCreateRequest{username, password}`) → `201 UserDto`; `409 username-taken`
  - `POST /api/manager/users/{userId}/deactivate` → `204`; `409 cannot-deactivate-self`; `404`
  - `POST /api/manager/users/{userId}/reactivate` → `204`
  - `PUT /api/manager/users/{userId}/password` (`PasswordResetRequest{newPassword}`) → `204`; `409 cannot-reset-self` (amendment 2026-08-26, see D12)
  - `DELETE /api/manager/users/{userId}/second-factor` → `204` (admin reset of another user); `409 cannot-reset-self` (mirrors the D12 amendment); that user's tokens are dropped (dev decision 2026-08-26 — a lost device may hold a session)
  - `GET /api/manager/users/me/second-factor` → `SecondFactorStatusDto{state: none|pending|confirmed, recoveryCodesRemaining}`
  - `POST /api/manager/users/me/second-factor` → `200 SecondFactorEnrollmentDto{secret, otpauthUri}`; `409 second-factor-already-confirmed`
  - `POST /api/manager/users/me/second-factor/confirm` (`SecondFactorCodeRequest{code}`) → `200 RecoveryCodesDto{codes[]}`; `400 invalid-code`
  - `DELETE /api/manager/users/me/second-factor` (`PasswordConfirmationRequest{password}`) → `204`; `400 current-password-mismatch` — DELETE with a body is legal in OpenAPI 3.1 and generated fine; alternative `POST …/disable` rejected as a non-idempotent spelling of an idempotent removal
  - `POST /api/manager/users/me/recovery-codes` (`PasswordConfirmationRequest`) → `200 RecoveryCodesDto`
  - `UserDto{id, username, active, secondFactorState, createdAt}`
- **`manager-audit`** (new tag → `AuditResource`): `GET /api/manager/audit?page&size`
  → `AuditPageDto{entries: AuditEntryDto[], page, size, total}`;
  `AuditEntryDto{id, occurredAt, actor?, address, viaProxy, action, entity, entityId?, field?}`.
- All new operations `bearerAuth` + `@RolesAllowed("admin")` (class-level on
  `UserResource` and `AuditResource` — whole surface admin).

## Data model

Three migrations, one per TODO block, each leaving a fresh instance usable:

```sql
-- V5__manager_user.sql (A2)
CREATE TABLE manager_user (
    id            bigserial   PRIMARY KEY,
    username      text        NOT NULL UNIQUE CHECK (username ~ '^[a-z0-9][a-z0-9._-]{0,63}$'),
    password_hash text        NOT NULL,
    active        boolean     NOT NULL DEFAULT true,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL
);
INSERT INTO manager_user (username, password_hash, active, created_at, updated_at)
    SELECT 'admin', password_hash, true, updated_at, updated_at FROM admin_credential;
DROP TABLE admin_credential;

-- V6__second_factor.sql (A3)
ALTER TABLE manager_user
    ADD COLUMN totp_secret       text,
    ADD COLUMN totp_confirmed_at timestamptz,
    ADD COLUMN totp_last_step    bigint;
CREATE TABLE manager_recovery_code (
    id        bigserial PRIMARY KEY,
    user_id   bigint    NOT NULL REFERENCES manager_user(id) ON DELETE CASCADE,
    code_hash text      NOT NULL,
    used_at   timestamptz
);
CREATE INDEX manager_recovery_code_user ON manager_recovery_code(user_id);

-- V7__audit_entry.sql (A4)
CREATE TABLE audit_entry (
    id          bigserial   PRIMARY KEY,
    occurred_at timestamptz NOT NULL,
    actor       text,
    address     text        NOT NULL,
    via_proxy   boolean     NOT NULL,
    action      text        NOT NULL,
    entity      text        NOT NULL,
    entity_id   text,
    field       text
);
CREATE INDEX audit_entry_occurred ON audit_entry(occurred_at DESC, id DESC);
```

The TOTP secret is necessarily stored **as the shared secret** (HMAC needs
it); it is not a password and cannot be hashed. Mitigation is the same as
for the device-token table: no DTO, no log, no dump outside `pg_dump`.
`MigrationTest`'s head assertion moves to V7 and gains: `manager_user`
holds the seeded user with a `pbkdf2-sha256$` hash and `admin_credential` is
gone. **How the carry-over is proven** (the one thing an upgrade must get
right — the password survives): a fresh `%test` boot runs V5's
`INSERT … SELECT` against an empty `admin_credential` (must not fail), and a
dedicated repository test recreates the pre-V5 shape as
`CREATE TEMP TABLE admin_credential (…)`, inserts one legacy row, runs V5's
`INSERT … SELECT` statement verbatim (read from the migration file, so the
test cannot drift from the SQL that ships), asserts a `manager_user` row
`admin` with the identical hash, and rolls back. Fifteen lines, no second
database.

## API contract (summary of shapes)

See "Contract changes" above; exact YAML is written in TODO step 6/12/17.
Error `code`s introduced: `too-many-attempts`, `second-factor-required`,
`username-taken`, `cannot-deactivate-self`, `cannot-reset-self`, `second-factor-already-confirmed`,
`invalid-code`, `current-password-mismatch` (existing), `user-not-found`;
*(added by dev 2026-08-26, T-09 — states the design left implicit)*
`no-pending-enrollment` (409, confirm with nothing pending) and
`second-factor-not-confirmed` (409, regenerate without a confirmed factor).

## Sequence — login through the relay

```
client ─▶ relay ─Forwarded: for=<client>─▶ Quarkus (trusted-proxies = relay)
   ClientAttribution: address=<client>, viaTrustedProxy=true
   AuthControl.login
     throttle.check(<client>)            ── 429 before anything else
     viaProxy && no code                 ── 401 second-factor-required (no PBKDF2)
     UserControl.verifyLogin(...)        ── PBKDF2 (dummy hash if no user), TOTP/recovery
       FAIL ─▶ throttle.recordFailure, AuditControl.recordLoginFailure (REQUIRES_NEW) ─▶ 401 invalid-credentials
       OK   ─▶ throttle.recordSuccess, TokenStore.issue(username), AuditControl.recordLoginSuccess ─▶ 200 {token, username}
```

On the LAN the same code runs with `viaTrustedProxy=false`: the second line
is skipped and the factor fields are ignored.

## Risks & trade-offs

- **Per-address throttling on IPv6** — one attacker with a /64 has 2⁶⁴
  buckets. The 10 000-entry cap keeps memory bounded; the residual
  (distributed guessing) is spec §8's accepted residual for A1 and is what
  A3 exists for.
- **The dummy-hash verification costs a PBKDF2 for unknown usernames** —
  deliberate (no username oracle); the throttle bounds it per address.
- **Own password change now drops only the caller's tokens** instead of
  every token (the v1.5.1 wording was written for one shared credential —
  the intent, "the old credential never survives", is preserved for the
  credential's owner; other users' sessions are not that credential).
- **`REQUIRES_NEW` for login audit rows** opens a second connection briefly
  under a failing login; on a 1-manager box that is negligible, and the
  throttle stops it becoming an amplifier.
- **DTO getter-reflection diff** for changed fields is the only reflection
  in the codebase; it never surfaces a value and is unit-tested on its own.
  Alternative rejected: per-Control hand-written diffs (60 lines each,
  drift-prone) and a JPA listener (interceptor magic, and it would see
  values).
- **Three packages instead of one** costs more files and the
  `FeaturePackages` list gains none (all three have `.entity`, `.control`,
  `.boundary`; `de.gxp.audit` and `de.gxp.user` are discovered from disk).

## Open decisions

None. No `[NEEDS CLARIFICATION]` markers — see the table below.

## Decisions taken under delegated authority (owner 2026-08-25)

The owner delegated every remaining decision for this work ("decide as much
as possible yourself"). Each row records the choice and the rejected
alternative; none adds a dependency, contradicts constitution v1.8.0 or
reopens a §12 row.

| # | Decision | Chosen | Rejected alternative |
|---|---|---|---|
| D1 | Package cut | three features `auth` / `user` / `audit` + `ClientAttribution` in `infrastructure.http` | one fat `de.gxp.auth` with a 14-method `ManagerAuthApi`, or a second Boundary in `auth` (would need the two-auth-surfaces exception) |
| D2 | Throttle numbers | 5 failures / 15 min window → block 60 s, doubling per exhaustion, cap 900 s; success clears; cap 10 000 addresses, evict > 1 h stale | fixed 60 s every time (no pressure on a patient attacker); a global cap (the 2026-08-24 defect) |
| D3 | Throttle rejection status | `429` + `Retry-After` + `ApiError too-many-attempts`; `ApiErrors.tooManyRequests` returns | `401` (hides the reason from the manager's own UI) |
| D4 | Proxy attribution | Quarkus `quarkus.http.proxy.*`, standard `Forwarded` only, `allow-x-forwarded` off, `use-proxy-protocol` off, one env switch `GXP_TRUSTED_PROXY_ENABLED` + `GXP_TRUSTED_PROXY`, loopback default for `trusted-proxies` | hand-parsed `X-Forwarded-For` (spec calls this the most likely defect); PROXY protocol now (Part D's choice, one property later) |
| D5 | Via-proxy signal | `X-Forwarded-Trusted-Proxy: true` as stamped by Quarkus, honoured only when `enable-trusted-proxy-header` is on | comparing `remoteAddress` with the raw socket address (not exposed after Quarkus rewrites it) |
| D6 | Second-factor absent via proxy | `401 second-factor-required` **before** password verification, not counted as a failure | after verification (password oracle) |
| D7 | No enrolled factor via proxy | generic `invalid-credentials` with PBKDF2 still run | distinct `second-factor-not-enrolled` (password oracle) |
| D8 | Factor fields on the LAN | ignored, not verified | verified when present (a second path to a failure count for a factor the LAN never asks for) |
| D9 | TOTP parameters | SHA-1 / 6 digits / 30 s (what every authenticator app defaults to), ±1 step, last-step replay guard per user. *Note 2026-08-26 (review finding #4):* TOTP is a function of the box's clock and a Raspberry Pi has no battery-backed RTC — remote login requires the box's clock to be **NTP-synchronised**; a clock that regressed after a reboot refuses every code (fail-closed, LAN unaffected) until it catches up. Goes into the README in T-16. The replay guard and the recovery-code spend are **conditional UPDATEs** requiring one affected row (review finding #1), so a code presented concurrently twice is accepted once | SHA-256 (poorly supported by apps); no replay guard |
| D10 | Recovery codes | 10 × 10 chars `[a-z0-9]`, SHA-256 at rest, regenerate invalidates all | PBKDF2 at rest (10 derivations per attempt = 22 s on the Pi); 8 codes |
| D11 | Disable / regenerate require the current password | yes. *Amendment 2026-08-26 (review finding #2, accepted):* these two `/me` operations verify the current password **unthrottled** — the caller holds a session, each miss costs one derivation, and `UserControl` must not depend on `de.gxp.auth`. If parity is ever wanted, the non-circular route is a `PasswordMismatch(address)` CDI event observed by `AuthControl` (the D14 pattern) | session alone (an idle-but-open session could strip the factor) |
| D12 | Self-deactivation | refused with `409 cannot-deactivate-self`. **Amendment 2026-08-26 (review of T-05…T-07 finding #1, decided by the lead):** self-*reset* through `PUT /api/manager/users/{ownId}/password` is refused the same way with `409 cannot-reset-self` — an own change goes through `PUT /api/manager/password` with the current password, otherwise an idle-but-open session could take the account over without it (D11's reasoning) | allowed (a box with zero admins needs a database fix); self-reset without the current password |
| D13 | Token drop scope on password change/reset | that user's tokens (event-driven) | every token (v1.5.1 wording, written for one shared password) |
| D14 | Deactivation → token drop mechanism | CDI event `UserControl.CredentialsInvalidated` observed by `AuthControl` | circular Control injection; per-request `active` lookup (constitution says "drops tokens") |
| D15 | Username env var | `GXP_ADMIN_USERNAME`, default `admin` | mandatory (adds friction to first start for no security gain) |
| D16 | Migration shape | V5 users (+carry-over, drop old table), V6 factor, V7 audit — one per block | one V5 (each TODO step could not be independently green) |
| D17 | Audit mechanism | explicit `AuditControl.record(...)` calls in every mutating Control; `changedFields` = DTO getter diff | JPA listener / CDI interceptor (invisible call sites, sees values) |
| D18 | Audit granularity | one row per changed field on update; one field-less row on create/delete/replace; login rows (2026-08-26): success = username as actor + entityId, failure = matched canonical username or null as entityId, ~~`second-factor-required` = no entityId~~ (*2026-08-26, 06_review 🟡 #1:* **not recorded** — an unthrottled, unauthenticated path must not write; see D20) | one row per operation with a field list column (harder to query, invites a JSON blob) |
| D19 | Login audit transaction | `REQUIRES_NEW` for login outcomes; `REQUIRED` for mutations | one mode (either loses failed logins or records rolled-back changes) |
| D20 | Throttled `429` not audited; *2026-08-26 (06_review 🟡 #1, lead decision):* the code-less via-proxy `second-factor-required` 401 likewise — uncounted by the throttle, so it too must stay DB-free | correct — it must stay DB-free | audited (the audit write becomes the amplifier) |
| D21 | Throttle bucket purge | self-bounding on insert, no scheduler job | a third 02:00 job (inventory noise for a memory-only map) |
| D22 | Audit page size | default 50, max 200 | unbounded (`/audit` returning a year of rows) |
| D23 | Login response carries `username` | yes | separate `/me` endpoint (one more operation for one string) |
| D24 | `DELETE …/me/second-factor` with a password body | yes | `POST …/disable` |
| D25 | Dependencies | **none** — `Mac`, `SecretKeyFactory`, `SecureRandom`, `MessageDigest`, hand-written base32 | any TOTP/QR library |

## Decisions confirmed by the user
| Decision | Chosen | Date |
|----------|--------|------|
| Roles beyond `admin` | none | 2026-08-25 (§12 item 2) |
| Second factor on the LAN | never asked; required via the trusted proxy; no factor → cannot log in remotely | 2026-08-25 (§12 item 3) |
| Recovery path | recovery codes + LAN re-enrolment + admin reset | 2026-08-25 (§12 item 4) |
| Audit retention | 365 days, daily 02:00 job, no size cap | 2026-08-25 (§12 item 5) |
| Everything else in this feature | delegated to the planner (table above) | 2026-08-25 |
