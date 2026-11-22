# PRD: Authentication Hardening (remote-operations Part A)
**Feature:** [index.md](../index.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Status:** In Review (planned 2026-08-25 under the owner's delegated authority — see `docs/backend/remote-operations.md` §12)

## Problem statement

The manager login was built for a LAN with a handful of devices: one shared
admin password, a minimum of eight characters and — since 2026-08-24 (REQ-326
as amended) — **no throttling of any kind**. `docs/backend/remote-operations.md`
makes the manager UI reachable from the internet through a relay (Part D).
Exposing today's posture there would be a regression, not a feature: Part A is
the precondition for Parts B–D (§3), and every part of it improves a LAN-only
deployment as well (§11).

Four gaps, in dependency order (spec §4):

- **A1** — an unauthenticated caller can spend the box's CPU at will through
  PBKDF2 (2.2 s per attempt on the Raspberry Pi 4B; four concurrent attempts
  pin all cores) and guess without limit. The two global mechanisms tried on
  2026-08-24 were themselves denial-of-service vectors; the shape that is not
  is a **per-source-address budget** that never locks the account.
- **A2** — one shared password means one blast radius and an audit trail that
  can attribute nothing. The owner, a co-host and a cleaner must be
  distinguishable.
- **A3** — with a password reachable from the internet, a **second factor**
  carries the security, not the password policy.
- **A4** — changes to door codes, WiFi passwords and stays are neither
  detectable nor disputable without an **append-only audit trail** — one that
  must never contain the values it records changes of (principle 3).

This feature is Part A (A1–A4) exactly as cut in §11 as `auth-hardening`.
Every owner decision is already taken in §12 and in constitution v1.8.0; this
PRD does not reopen them.

## Goals / Non-goals

- **Goals:**
  - Login attempts are budgeted **per attributed source address**, rejected
    *before* any PBKDF2 derivation, never globally, never locking an account
    (reopens REQ-326 — see REQ-606).
  - The attributed address is the socket peer unless the request arrived from
    the **configured trusted proxy**, whose forwarded address is then honoured
    — and from nobody else. With no proxy configured (the LAN-only default) a
    forwarded header is ignored.
  - The single `admin_credential` row becomes **named user accounts**, all
    role `admin`; any admin may list, create, deactivate, reactivate other
    accounts and reset their password or second factor. First-start seeding
    keeps working and still refuses to start without a password. An upgraded
    instance keeps its existing password under the user `admin`.
  - **RFC 6238 TOTP** with the JDK's `javax.crypto.Mac` only, `otpauth://`
    URI returned as text, recovery codes shown once and stored hashed. The
    factor is required for every login that arrives through the trusted proxy
    and never asked on the LAN (§12 item 3); the LAN path is the documented
    recovery route (§12 item 4).
  - An **append-only audit trail** — who, when, attributed address, entity,
    entity id, field name, **never a value of any field** — over every manager
    mutation, every login outcome and every user-management action; paged
    read endpoint; 365-day retention pruned by the daily scheduler (§12 item 5).
- **Non-goals:**
  - Anything under `/api/guest/**` or `/api/pairing/**` — kiosks and their
    device tokens are untouched (spec §1). The pairing request endpoint is
    **not** throttled by this feature.
  - Roles beyond `admin` (§12 item 2). The role attribute stays so a later
    role costs no mechanism change.
  - The manager UI screens for any of this — the whole remote-operations work
    is backend-only; screens are separate later features.
  - QR rendering of the `otpauth://` URI (§12 derived clarification: the
    frontend renders it client-side, or shows the text).
  - The relay itself, PROXY-protocol support and the relay's transport limits
    (Part D). This feature delivers the application half of A1.4 — the
    trusted-proxy attribution — so that Part D has nothing to change inside
    the application (D5: no `if (remote)`).
  - Any dependency. Spec §9 records that none is needed for A1–A4; the design
    confirms it (REQ-625).

## Assumptions

- Quarkus 3.33.3.1's `quarkus.http.proxy.*` platform support is used for
  proxy attribution rather than hand-parsed headers. Verified against the
  artifact in `~/.m2` on 2026-08-25: `proxy-address-forwarding`,
  `trusted-proxies`, `allow-forwarded`, `allow-x-forwarded`,
  `enable-trusted-proxy-header` (adds `X-Forwarded-Trusted-Proxy: true` for
  requests forwarded by a trusted proxy and strips a forged one) and
  `use-proxy-protocol` all exist under `quarkus.http.proxy`.
- One box, one manager population: the throttle and token stores stay in
  memory (a restart clears them — acceptable, as for tokens today).
- A single `manager_user` row per person; usernames are unique, lower-case,
  1–64 characters of `[a-z0-9._-]`.
- The existing 244-test suite calls `POST /api/manager/login` with
  `{"password": …}` in ~21 test files; the contract change (username becomes
  mandatory) means every one of them is touched — planned as one explicit
  step, not left to chance.
- Part D will forward the originating address with the standard `Forwarded`
  header from a fixed relay address; if Part D chooses the PROXY protocol
  instead, only `application.properties` changes (`use-proxy-protocol`), no
  code — the application consumes only "attributed address + came through the
  trusted proxy", exactly as constitution v1.8.0 allows.

## User stories

- As the **property owner**, I want a flood of wrong passwords from someone
  else's device to slow *their* device down and never mine, so that an
  attacker cannot lock me out by guessing.
- As the **owner**, I want each co-host and cleaner to have their own account
  that I can switch off in one click, so that a departed cleaner's login is
  dead on the next request.
- As a **manager logging in from outside the building**, I want to be asked
  for my authenticator code, so that a leaked password alone does not open the
  property; on the LAN I want to log in as today.
- As a **manager who lost the authenticator**, I want to use a recovery code,
  or log in on site and re-enrol, so that a lost phone is not a site visit.
- As the **owner**, I want to see who changed which field of which room and
  when — without the audit record itself holding the door code — so that I
  can dispute a change without creating a new secret store.
- As an **operator upgrading an existing instance**, I want the existing
  password to keep working as user `admin`, so that an update never locks me
  out.

## Requirements

### A1 — Per-address login throttling (reopens REQ-326)

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-601 | Failed login attempts are counted **per attributed source address**, never globally and never per account. A correct password from an address with no recent failures is never refused, whatever any other address is doing. There is no account lockout of any kind. | Must |
| REQ-602 | An address that has exhausted its budget (5 failures inside a 15-minute window) is answered `429` with a `Retry-After` header and the shared `ApiError` shape (code `too-many-attempts`) **before any PBKDF2 derivation and before any database read**. The penalty escalates per further failure (60 s doubling, capped at 15 min) and a successful login clears that address's bucket. Every other address is unaffected. | Must |
| REQ-603 | The attributed address is the **socket peer**, unless the request arrived from the **configured trusted proxy**, in which case the address the proxy forwarded is used and the request is flagged as *via proxy*. With no trusted proxy configured — the default, LAN-only posture — every `Forwarded`/`X-Forwarded-*`/`X-Forwarded-Trusted-Proxy` header is ignored: the peer stays the attributed address and *via proxy* is false. Implemented with Quarkus' `quarkus.http.proxy.*` platform support (no hand-parsed headers), driven by one operator setting. | Must |
| REQ-604 | The budget covers **every password-bearing manager operation**: `managerLogin` (including its second-factor / recovery-code verification, which is part of the same attempt) and `changeManagerPassword` (its current-password check). A failed second-factor code counts as a failed attempt for that address. Pairing and guest endpoints are untouched. *Amendment 2026-08-26 (review of T-08…T-10, finding #2 — accepted):* the two `/me` factor operations (`disableOwnSecondFactor`, `regenerateOwnRecoveryCodes`) verify the current password **unthrottled**: the caller holds a session, each miss costs one PBKDF2 derivation, and `UserControl` must not depend on `de.gxp.auth`. | Must |
| REQ-605 | Throttle state lives in memory, is bounded (a fixed cap on tracked addresses with stale-bucket eviction, so an attacker with many addresses cannot grow it without limit), and **logs nothing** — no address, no username, no count (principle 3 posture). | Must |
| REQ-606 | REQ-326 of `backend-domain-config` is **reopened, not silently overridden**: it receives a dated amendment note pointing at REQ-601…REQ-605 (constitution v1.8.0 records the reopening; principle 7). Its former positive criterion "six wrong passwords arm nothing" is withdrawn there and replaced by the criteria below. | Must |

### A2 — Per-user accounts

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-607 | A `manager_user` table replaces the single-row `admin_credential`. A Flyway migration **carries the existing credential row over** as an active user named `admin` with its hash unchanged, then drops `admin_credential` — an upgraded instance logs in with the same password. A fresh instance has zero rows until seeded. | Must |
| REQ-608 | First-start seeding: when **no user exists**, the first user is created from `GXP_ADMIN_USERNAME` (optional, default `admin` — acceptable because the password is still mandatory and the username is not a secret) and `GXP_ADMIN_PASSWORD` (mandatory, ≥ 8 characters). Unset/blank/too short password with no user → **refuse to start**, naming the variable, never a value. Once any user exists both variables are ignored forever. | Must |
| REQ-609 | `managerLogin` takes **`username` + `password`** (contract change; the generated TS client is regenerated, never hand-edited). Unknown username, deactivated user and wrong password all answer the **same generic `401`** (`invalid-credentials`) and cost the same PBKDF2 work (a fixed dummy hash is verified when no active user matches) — no username oracle by wording or timing. | Must |
| REQ-610 | Admin-only user management under a new `manager-users` tag: list users (never any hash or secret), create (username + initial password ≥ 8), deactivate, reactivate, reset another user's password, reset another user's second factor (clears secret and recovery codes). A user cannot deactivate **themselves** (the one guard that keeps a box from having zero active admins by accident). Username uniqueness violation → `409`. | Must |
| REQ-611 | **Deactivation drops that user's bearer tokens immediately** — effective on the next request, like device revocation. An admin password reset and the user's own password change drop **that user's** tokens (the old credential never survives into another idle window — v1.5.1's intent, now scoped to the credential's owner). | Must |
| REQ-612 | The bearer identity **carries the username** (principal name) and role `admin`, so the audit trail can attribute and logout/deactivation can target one user's tokens (the token store is keyed by user). `LoginResponse` additionally returns the `username`. | Must |
| REQ-613 | Usernames are unique, stored lower-case, `^[a-z0-9][a-z0-9._-]{0,63}$` (contract-validated). No user DTO ever contains password material, the TOTP secret or a recovery code. | Must |

### A3 — Second factor (TOTP)

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-614 | RFC 6238 TOTP: HMAC-SHA1, 6 digits, 30-second step, 160-bit `SecureRandom` secret, implemented with `javax.crypto.Mac` — **no dependency**. Time-step drift tolerance **±1 step**; **replay protection** — a code for a time step ≤ the last step that user already used is refused (last used step stored per user). | Must |
| REQ-615 | Self-service under `manager-users`: **status** (enrolled/pending/none, remaining recovery codes — never the secret), **enroll** (generates a *pending* secret and returns it once as base32 plus the `otpauth://totp/…` URI; re-enrolling replaces a pending secret, and is refused with `409` while a confirmed factor exists), **confirm** (one valid code turns pending into confirmed and returns the first batch of recovery codes), **disable** (requires the current password; clears secret and codes). | Must |
| REQ-616 | **Recovery codes**: a batch of 10 single-use codes (10 characters `[a-z0-9]`, ≥ 50 bits each), shown **once** (on confirm and on explicit regenerate), stored **SHA-256 hashed**, each usable exactly once in place of a TOTP code. Regenerate invalidates the previous batch. | Must |
| REQ-617 | **Enforcement (§12 item 3):** there is ONE login endpoint. `LoginRequest` gains optional `totpCode` and `recoveryCode`. For a request flagged *via proxy* (REQ-603): if neither is present → `401` code `second-factor-required` **before** the password is verified (so it reveals nothing about the password); if one is present it is verified together with the password and **every** failure — wrong password, wrong code, replayed code, spent recovery code, or a user with no confirmed factor — answers the identical generic `invalid-credentials` (no oracle; a user without a factor cannot log in through the proxy at all and enrolls on the LAN). For a LAN request the factor is **never asked** and any supplied code is ignored. | Must |
| REQ-618 | A user's second factor can be **reset by another admin** (REQ-610) — the LAN path plus a colleague is the documented recovery route (§12 item 4); a site visit is only ever needed when nobody can reach the LAN. | Must |
| REQ-619 | Secrets, codes and password material never reach a log, an exception message or any DTO except the one-time enroll/confirm/regenerate responses, in every profile (principle 3, REQ-333 carried forward). | Must |

### A4 — Audit trail

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-620 | An **append-only** `audit_entry` table: occurred-at, actor username (null for a failed login; *amended 2026-08-26, review of T-11…T-14:* the entity id of a failed login is the **matched canonical username only** — an account row exists for the submitted name, active or not — otherwise null; the submitted text itself is **never** stored, because a caller who swaps the two fields would land a password in a 365-day, admin-readable table; `second-factor-required` rows carry no entity id at all; *amended 2026-08-26, 06_review 🟡 #1, lead decision:* the code-less via-proxy `second-factor-required` 401 is **not recorded at all** — it is unthrottled and unauthenticated, and such a path must not write to the database), attributed client address, via-proxy flag, action, entity, entity id (nullable), field name (nullable). **No column for a value exists** — the simplest rule that satisfies principle 3 is "no values at all, for any field". No update or delete endpoint; the only deletion is retention (REQ-623). | Must |
| REQ-621 | **Every manager mutation is recorded**, by an explicit call in the owning Control (no interceptor magic — every call site is greppable): property update; room create/update/delete; menu-config and action-card replace (both scopes); stay create/update/delete; asset upload/delete; device confirm/rename/revoke; every user-management action (REQ-610), own password change, every second-factor action (REQ-615/616); **login success and login failure** (a throttled `429` is *not* recorded — it must stay free of database work, REQ-602). Field-level entries are one row per changed field for update operations; create/delete/replace record one row with no field. | Must |
| REQ-622 | `GET /api/manager/audit` (tag `manager-audit`, admin): paged, newest first, `page`/`size` query (size 1–200, default 50), returns entries plus total count. | Must |
| REQ-623 | **Retention 365 days**: a daily job in `de.gxp.infrastructure.scheduler` (existing 02:00 pattern, `concurrentExecution = SKIP`) deletes older entries via the audit Control; `ScheduledJobInventoryTest`'s pinned list is **edited on purpose** to name it. No size cap (§12 item 5). | Must |
| REQ-624 | An audit row for a mutation is written **in the mutation's transaction** — a rolled-back change leaves no trace claiming it happened. Login outcomes are committed **independently** of the failing request (a failed login's row survives the `401`). | Must |

### Cross-cutting

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-625 | Contract-first: every new/changed operation and schema lives in `api/openapi.yaml`; `packages/api-client/` is regenerated (a second regeneration is diff-clean); every new operation except `managerLogin` declares `bearerAuth`. **`pom.xml` is unchanged** — the design proves no dependency is needed. | Must |
| REQ-626 | Every implemented behaviour has a test (principle 6): per-address budget (other address unaffected, correct password from a clean address, `429` + `Retry-After` before PBKDF2, escalation, success reset, bounded map), proxy attribution (default ignores forwarded headers; a trusted-proxy profile honours them and flags via-proxy), migration of the existing row, seeding (both variables, refuse-to-start cases), generic 401 for unknown/inactive/wrong, user CRUD incl. self-deactivation guard and token drop on deactivation/reset, TOTP vector test against RFC 6238 Appendix B, drift ±1, replay, enroll/confirm/disable/status, recovery-code single use and regenerate, proxy enforcement matrix, audit rows for every mutation class incl. rollback and login outcomes, audit paging order, retention prune, the edited job inventory, and the five architecture guards green with the new packages. | Must |
| REQ-627 | Docs move with the code: `backend/README.md` (login with username, first user seeding, user management, second factor and recovery, trusted-proxy setting, audit), `backend/compose.yaml` (new optional env vars), `application.properties` comments (the REQ-326 comment block is rewritten), `docs/system-context.md` (component rows for `de.gxp.user`, `de.gxp.audit`, the `de.gxp.auth` row, the Auth and Scheduled-housekeeping bullets), `docs/features/index.md`. | Must |
| REQ-628 | **Kiosks untouched:** no operation under the `pairing` or `guest` tags changes, the device-token path in `TokenIdentityProvider` is byte-for-byte equivalent in behaviour, and `RoomScopingTest` plus the guest suite pass unchanged. | Must |

## Acceptance criteria (testable)

- **REQ-601/602:** five wrong passwords from address A → the sixth from A is
  `429` with `Retry-After` **and** the PBKDF2 hasher was not invoked for it
  (observable through a test-profile counting hasher or elapsed time ≪ one
  derivation); the correct password from address B logs in at once; after
  `Retry-After` elapses A is admitted again; a success from A clears A's
  bucket; the penalty doubles on the next exhaustion and caps at 900 s.
  *(Amended 2026-08-26, review finding #2, decided by the lead under
  delegated authority:)* an IPv4 address is its own bucket; an **IPv6 address
  is keyed by its /64 prefix** — two addresses in the same /64 share one
  bucket, two addresses in different /64s do not; an IPv4-mapped IPv6 address
  (`::ffff:a.b.c.d`) shares the bucket of `a.b.c.d`. Unit-tested.
- **REQ-603:** default profile — a request carrying `Forwarded: for=203.0.113.9`
  and `X-Forwarded-Trusted-Proxy: true` is attributed to `127.0.0.1` and is
  not via-proxy (no `second-factor-required`; the bucket keyed
  `127.0.0.1` grows). Trusted-proxy test profile (`trusted-proxies=127.0.0.1`)
  — the same request is attributed to `203.0.113.9`, is via-proxy, and a
  second forwarded address has its own bucket. *(Added 2026-08-26, review
  finding #1/#3:)* in that profile the **first** `for=` element of a
  multi-hop `Forwarded` header is the attributed address (this pins why the
  relay must replace, never append); a non-IP `for=` token (`_hidden`) is
  answered `400 unattributable-address` without throttle state and without
  a derivation; and a second profile (`trusted-proxies=192.0.2.1/32`) proves
  the switch-on negative — an untrusted peer's `Forwarded` and
  `X-Forwarded-Trusted-Proxy: true` are ignored and the flag is `false`.
- **REQ-604:** a wrong current password on `changeManagerPassword` increments
  the caller's address bucket; a wrong TOTP code via proxy increments it.
- **REQ-605:** the map never exceeds its cap under a synthetic 20 000-address
  test; nothing is logged at any level during the throttle tests (log sweep).
- **REQ-606:** `backend-domain-config/01-prd.md` REQ-326 carries the dated
  note; `AuthResourceTest.sixWrongPasswordsInARowArmNothing…` is deleted.
- **REQ-607:** `MigrationTest` head is V7; a test that inserts a legacy-style
  `admin_credential` row before migration is not possible on a fresh DB, so
  the migration SQL is proven by a dedicated test that runs V5's `INSERT …
  SELECT` semantics against a seeded copy (or by asserting the seeded `admin`
  user exists with a `pbkdf2-sha256$` hash after boot in `%test`) — the
  design pins the method.
- **REQ-608:** seeder unit tests: no user + unset/blank/short password →
  `IllegalStateException` naming `GXP_ADMIN_PASSWORD` only; no user + valid
  password + no username → user `admin`; with `GXP_ADMIN_USERNAME=owner` →
  user `owner`; an existing user → both variables ignored.
- **REQ-609:** unknown user, deactivated user and wrong password produce
  byte-identical `401` bodies; the hasher runs for all three (counting hasher).
- **REQ-610/611/613:** create → `201` and appears in list; duplicate → `409`;
  invalid username → `400`; deactivate another → their existing token is `401`
  on its next request while the caller's still works; deactivate self →
  `409`; reactivate → they can log in; admin reset password → old password
  fails, new works, target's tokens dead; list body contains no `hash`,
  `secret`, `code` keys (JSON key sweep).
- **REQ-612:** after login, an audit row's actor equals the login username;
  logout kills exactly the presented token; `LoginResponse.username` is set.
- **REQ-614:** the RFC 6238 Appendix B vectors for SHA-1 (`12345678901234567890`,
  T = 59 → `287082`, 1111111109 → `081804`, 1234567890 → `005924`) pass with
  an injectable clock; a code from step −1 and +1 is accepted, ±2 refused; the
  same code twice is refused the second time.
- **REQ-615/616:** enroll returns `secret` + `otpauthUri` matching
  `^otpauth://totp/.*secret=[A-Z2-7]+.*$`; status is `pending`; confirm with a
  wrong code → `400`, with the right code → `200` + 10 codes, status
  `confirmed`; a second enroll while confirmed → `409`; disable with wrong
  password → `400`; each recovery code logs in once through the proxy
  profile and fails the second time; regenerate returns 10 new codes and an
  old one no longer works.
- **REQ-617:** proxy profile: login without code → `401 second-factor-required`
  **and the hasher did not run**; with a wrong code → `401 invalid-credentials`;
  user without a factor, correct password, any code → `401
  invalid-credentials`; correct password + valid code → `200`. Default
  profile: the same user without a factor logs in with password alone.
- **REQ-619:** log sweep during the whole auth/user suite finds no secret, no
  code, no password, no token; the `enroll` DTO is the only place `secret`
  appears in the contract.
- **REQ-620/621/624:** after a property update changing two fields, exactly two
  rows (`entity=property`, `field` ∈ the two names) exist and no column holds
  the new value (schema assertion: `audit_entry` has no value-like column);
  a room update that fails validation leaves no row; a failed login leaves a
  `login-failure` row with the submitted username and address; a `429` leaves
  none; one row for each of create/delete/replace/confirm/rename/revoke/
  upload/delete-asset and each user/second-factor action.
- **REQ-622:** with 60 entries, page 0 size 50 returns the 50 newest in
  descending `occurredAt`, `total=60`; size 201 → `400`.
- **REQ-623:** `AuditControl.pruneOlderThan(365 d)` deletes a row aged 366
  days and keeps one aged 364 days; `ScheduledJobInventoryTest` lists
  `AuditScheduler#pruneExpiredEntries cron="0 0 2 * * ?"`.
- **REQ-625:** `git diff --stat pom.xml` empty; two consecutive regenerations
  leave `packages/api-client/` diff-clean; `ContractSecuredOperationsDeniedTest`
  picks the new operations up and every one answers `401` anonymously.
- **REQ-628:** `git diff api/openapi.yaml` touches nothing under
  `/api/guest/**` or `/api/pairing/**`; guest and device suites green.

## Technical constraints

Anchored to `docs/constitution.md` v1.8.0:

- Backend Java 21 + Quarkus, BCE with `boundary`/`control`/`entity`
  sub-packages, one-line Boundaries, `@Transactional` on Controls, security
  annotations on the Boundary, the five architecture guards green
  (`backend/CLAUDE.md`).
- Closed dependency allow-list — **nothing enters `pom.xml`**. PBKDF2, HMAC,
  `SecureRandom` and base32 are JDK or hand-written.
- Flyway versioned SQL only; PostgreSQL only; `pg_dump` remains the entire
  backup (the audit table and user table are in it).
- Principle 3 with teeth: no secret, code, password, token or address in any
  log; audit rows hold names, never values.
- Principle 4 as amended: the application consumes exactly one transport
  fact — attributed address + via-trusted-proxy — and branches on nothing
  else. No second login path, no second token type.
- Scheduled work only in `de.gxp.infrastructure.scheduler`, cron + `SKIP`,
  pinned inventory.
- Contract-first; generated client never hand-edited.
- Branch discipline: work stays on `refactor/bce-subpackages-and-boundary-security`; no commit by agents.

## Risks & open questions

- **The trusted-proxy attribution is the single most likely defect (spec
  A1.4).** Mitigation: platform support only, one operator switch that leaves
  every proxy option off by default, and a test profile that proves both
  postures. Residual: an operator who sets `trusted-proxies` to a LAN range
  turns the throttle into a weapon on that range — documented in the README.
- **PBKDF2 amplification remains for the first five attempts per address**
  and for a distributed attacker (spec §8 residual). Part D's transport limits
  are defence in depth, not relied on here.
- **Every test that logs in changes** (~21 files). Risk of a long red window;
  mitigated by a shared `ManagerFixtures.login()` introduced first, then the
  contract flip in one step.
- **Test-profile cost on the Pi:** the trusted-proxy profile restarts Quarkus
  once (~40–60 s). Kept to one profile class.
- **`enable-trusted-proxy-header` semantics** when a trusted peer sends no
  `Forwarded` header at all: the design defines via-proxy as "Quarkus stamped
  `X-Forwarded-Trusted-Proxy: true`"; the dev verifies at the proxy-profile
  step whether Quarkus stamps it without a forwarded header (harmless either
  way in production — only the relay is the trusted peer — but the test
  expectations must match; recorded as a verification note in the TODO, not
  an open decision).
- No `[NEEDS CLARIFICATION]` markers remain: every decision this feature
  needed was either taken in spec §12 or delegated to the planner (recorded
  in `02-design.md`).
