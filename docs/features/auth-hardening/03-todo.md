# TODO: Authentication Hardening (remote-operations Part A)
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md)
**Status:** Done

> Each task traces to a REQ-ID and references a design decision (D1–D25 in
> `02-design.md`) instead of re-deciding. Tasks are ordered A1 → A2 → A3 → A4
> → docs, and **each is independently `./mvnw verify` green** — the suite is
> the gate between tasks, not the end of the feature.
>
> **Environment (binding):** branch `refactor/bce-subpackages-and-boundary-security`,
> no new branch, no commit. Every Maven command:
> `JAVA_HOME=/tmp/gxp-jdk/jdk-21.0.12+8 sg docker -c "cd <repo>/backend/app && ./mvnw verify"`
> (no `java` on PATH; Docker only via `sg docker`). 4-core Pi, ~2.6 GB free,
> 244 tests today: run the suite in a quiet window and distinguish
> environment failures (timeouts, Dev Services boot) from assertion failures
> before concluding anything. `pom.xml` is never touched (D25, REQ-625).
> Contract workflow: edit `api/openapi.yaml` → `./mvnw verify` regenerates
> the JAX-RS interfaces **and** `packages/api-client/` (never hand-edit it).

## Setup

- [x] **T-01 Shared manager login fixture** — _REQ-626, REQ-609_
  - Add `de.gxp.infrastructure.testsupport.ManagerFixtures` (test scope):
    `login()` (seeded test credentials) and `login(username, password)` →
    token. Replace the private `login()` helpers in all ~21 test files that
    post to `/api/manager/login` (list: `grep -rl "api/manager/login" src/test`)
    so the later contract flip (T-05) touches one file. No behaviour change.
  - Files: `src/test/java/de/gxp/infrastructure/testsupport/ManagerFixtures.java`, the ~21 test classes.
  - Validate: `./mvnw verify` green, test count unchanged (244).

## A1 — per-address throttling

- [x] **T-02 `ClientAttribution` + proxy configuration (D4, D5)** — _REQ-603_
  - `de.gxp.infrastructure.http.ClientAttribution` (`@RequestScoped`):
    `Attribution(address, viaTrustedProxy)` from `CurrentVertxRequest`
    (`remoteAddress().hostAddress()`) and the `X-Forwarded-Trusted-Proxy`
    header — honoured only when `quarkus.http.proxy.enable-trusted-proxy-header`
    is `true` (injected config, default `false`). The four
    `quarkus.http.proxy.*` lines from the design in `application.properties`
    with the env expansions `GXP_TRUSTED_PROXY_ENABLED` / `GXP_TRUSTED_PROXY`;
    `compose.yaml` gains both as optional (`:-` defaults), commented.
  - Test: a test-scope `@PermitAll` probe resource (precedent:
    `de.gxp.security.FailClosedResource`) returning the attribution; default
    profile: `Forwarded: for=203.0.113.9` + `X-Forwarded-Trusted-Proxy: true`
    → `127.0.0.1`, `viaTrustedProxy=false`.
  - Files: `infrastructure/http/ClientAttribution.java`, `application.properties`, `backend/compose.yaml`, `src/test/java/de/gxp/infrastructure/http/ClientAttributionTest.java` (+ probe resource).
  - Validate: `./mvnw verify` green; `BoundaryAnnotationAuditTest` still green (the probe is annotated).

- [x] **T-03 `LoginThrottle` wired before PBKDF2 (D2, D3, D20, D21)** — _REQ-601, REQ-602, REQ-604, REQ-605, REQ-606_
  - `de.gxp.auth.control.LoginThrottle` per the design (`check` /
    `recordFailure` / `recordSuccess`, 5 per 15 min, 60 s doubling to 900 s,
    cap 10 000 with stale eviction, package-private `reset()`/`failuresOf()`,
    injectable `Clock`, `%test.gxp.auth.throttle-block-seconds=1`).
    `ApiErrors.tooManyRequests(code, message, retryAfterSeconds)` with
    `Retry-After`. `AuthControl.login` and `changePassword`: `throttle.check`
    first, `recordFailure` on a verification miss, `recordSuccess` on login.
    Contract: `managerLogin` and `changeManagerPassword` gain `429`
    (`ApiError`, `Retry-After` header) and the login description drops the
    "deliberately not throttled" text. Delete
    `AuthResourceTest.sixWrongPasswordsInARowArmNothing…`. Rewrite the
    REQ-326 comment block in `application.properties`. (REQ-606's PRD note
    on REQ-326 is already written by the planner — verify it is there.)
  - Tests (`LoginThrottleTest` unit with a fake clock + HTTP in
    `AuthResourceTest`, `@AfterEach reset()`): 5 failures → 6th is `429` +
    `Retry-After` and the hasher was not called (count via a
    package-private counter on the hasher or assert elapsed < 200 ms with
    `%test` iterations 1000 — pick the counter); another address (unit
    level) unaffected; success clears; escalation doubles and caps; 20 000
    addresses never exceed the cap; wrong current password on
    `changeManagerPassword` increments the bucket; log sweep during the
    class finds nothing from the throttle.
  - Files: `auth/control/LoginThrottle.java`, `auth/control/AuthControl.java`, `infrastructure/http/ApiErrors.java`, `api/openapi.yaml`, `application.properties`, `packages/api-client/` (regenerated), tests.
  - Validate: `./mvnw verify` green; `git status packages/api-client` shows only the regenerated 429 change.

- [x] **T-04 Trusted-proxy test profile (D4, D5)** — _REQ-603, REQ-601_
  - `de.gxp.infrastructure.testsupport.TrustedProxyProfile implements QuarkusTestProfile`
    overriding the four proxy properties with `trusted-proxies=127.0.0.1/32`.
    Tests: the probe now reports `203.0.113.9` / `viaTrustedProxy=true`;
    two different `Forwarded: for=` addresses exhaust separate throttle
    buckets (five failures from A → A `429`, B logs in). **Verification note
    (not a decision):** check whether Quarkus stamps
    `X-Forwarded-Trusted-Proxy` for a trusted peer that sends no `Forwarded`
    header; record the answer in the test's javadoc — production semantics
    are unaffected because only the relay is ever the trusted peer.
  - Files: `testsupport/TrustedProxyProfile.java`, `src/test/java/de/gxp/auth/TrustedProxyLoginTest.java`.
  - Validate: `./mvnw verify` green (one extra Quarkus boot — expect +60 s on the Pi).

## A2 — per-user accounts

- [x] **T-05 `de.gxp.user` package, V5 migration, seeder, username on login (D1, D15, D16)** — _REQ-607, REQ-608, REQ-609, REQ-613_
  - `V5__manager_user.sql` exactly as designed (create, `INSERT … SELECT`
    from `admin_credential`, drop). `user/entity/ManagerUser`,
    `user/control/ManagerUserRepository`, `git mv` `Pbkdf2PasswordHasher` (+
    its test) to `de.gxp.user.control`, `FirstUserSeeder` replacing
    `AdminPasswordSeeder` (`gxp.admin.username` default `admin`, pattern
    check, same refuse-to-start messages; delete `AdminCredential`,
    `AdminCredentialRepository`, `AdminPasswordSeeder`, `V3` stays untouched).
    `UserControl.verifyLogin(username, password)` → `Verdict` with the fixed
    dummy-hash path. Contract: `LoginRequest.username` (required, pattern),
    `LoginResponse.username`; `manager-auth` tag description rewritten.
    `AuthControl.login` uses the verdict. `ManagerFixtures` sends the
    username. `%test.gxp.admin.username` unset (default `admin`).
  - Tests: `FirstUserSeederTest` (the eight `AdminPasswordSeederTest` cases
    ported + `GXP_ADMIN_USERNAME=owner` + invalid username refuses start);
    `MigrationTest` head → `5`, `admin_credential` absent, `manager_user`
    row `admin` with `pbkdf2-sha256$` hash; the temp-table carry-over test
    from the design; `AuthResourceTest`: unknown / wrong-password bodies
    byte-identical and the hasher counter increments for both; the guest
    and device suites untouched.
  - Files: `db/migration/V5__manager_user.sql`, `de/gxp/user/**`, `de/gxp/auth/control/AuthControl.java`, `api/openapi.yaml`, `packages/api-client/`, `ManagerFixtures`, tests.
  - Validate: `./mvnw verify` green incl. all five architecture guards (`user` has all three sub-packages).

- [x] **T-06 User-keyed sessions and credential invalidation (D13, D14)** — _REQ-611, REQ-612_
  - `TokenStore`: `Session(username, expiry)`, `issue(username)`,
    `removeAllOf(username)`; `TokenIdentityProvider` sets the principal name
    to the username (device branch unchanged). `UserControl.changeOwnPassword`
    (verify current, policy, store, fire `CredentialsInvalidated`);
    `AuthControl.changePassword` delegates and counts a mismatch as a
    throttle failure; `AuthControl.onCredentialsInvalidated(@Observes …)`
    drops that user's tokens. `TokenStore.clear()` is deleted.
  - Tests: `TokenStoreTest` (`removeAllOf` leaves other users' sessions);
    `AuthResourceTest.passwordChangeDrops…` rewritten: two sessions of the
    same user die, a second user's session survives; principal name equals
    the login username (probe via an existing admin endpoint that echoes
    nothing — assert through the audit row in T-12 instead; here assert
    `LoginResponse.username`).
  - Files: `auth/control/TokenStore.java`, `TokenIdentityProvider.java`, `AuthControl.java`, `user/control/UserControl.java`, tests.
  - Validate: `./mvnw verify` green.

- [x] **T-07 `manager-users` administration (D1, D12)** — _REQ-610, REQ-611, REQ-613_
  - Contract: `GET/POST /api/manager/users`,
    `POST …/{userId}/deactivate|reactivate`, `PUT …/{userId}/password`;
    `UserDto`, `UserCreateRequest`, `PasswordResetRequest`; errors
    `username-taken` (409), `cannot-deactivate-self` (409), `user-not-found`
    (404). `UserResource` (class-level `@RolesAllowed("admin")`, one line per
    method), `UserControl` methods, `ManagerUserMapper` (never hash/secret);
    deactivate/reset fire `CredentialsInvalidated`.
  - Tests (`UserResourceTest`): create → 201 + listed; duplicate → 409;
    bad username → 400; deactivate other → their token 401 next request,
    caller's works; deactivate self → 409; reactivate → login works; admin
    reset → old password fails, new works, target's tokens dead; JSON key
    sweep of every user response for `hash|secret|code|password`;
    `ContractSecuredOperationsDeniedTest` covers the new operations
    automatically (assert its count grew).
  - Files: `api/openapi.yaml`, `packages/api-client/`, `user/boundary/UserResource.java`, `user/control/UserControl.java`, `user/entity/ManagerUserMapper.java`, tests.
  - Validate: `./mvnw verify` green.

## A3 — second factor

- [x] **T-08 TOTP primitives (D9, D25)** — _REQ-614_
  - `V6__second_factor.sql` (columns + `manager_recovery_code`).
    `user/control/TotpVerifier` (`Mac("HmacSHA1")`, RFC 4226 truncation,
    `Clock`, ±1 step, `lastStep` guard, `otpauthUri(username, secret)`),
    `user/control/Base32` (RFC 4648 encode). `ManagerUser` gains the three
    fields; `RecoveryCode` entity + repository (no endpoints yet).
  - Tests: RFC 6238 Appendix B SHA-1 vectors (T = 59 → `287082`,
    1111111109 → `081804`, 1234567890 → `005924`, 2000000000 → `279037`)
    with a fixed clock; ±1 accepted, ±2 refused; replay refused; base32
    vectors from RFC 4648 §10; `MigrationTest` head → `6`.
  - Files: `db/migration/V6__second_factor.sql`, `user/control/TotpVerifier.java`, `user/control/Base32.java`, `user/entity/ManagerUser.java`, `user/entity/RecoveryCode.java`, `user/control/RecoveryCodeRepository.java`, unit tests.
  - Validate: `./mvnw verify` green; `PersistenceAccessTest` green (repository in `.control`).

- [x] **T-09 Enrollment, recovery codes, admin reset (D10, D11, D24)** — _REQ-615, REQ-616, REQ-618, REQ-619_
  - Contract under `manager-users`: `GET/POST/DELETE /api/manager/users/me/second-factor`,
    `POST …/me/second-factor/confirm`, `POST …/me/recovery-codes`,
    `DELETE /api/manager/users/{userId}/second-factor`; DTOs
    `SecondFactorStatusDto`, `SecondFactorEnrollmentDto`,
    `SecondFactorCodeRequest`, `PasswordConfirmationRequest`,
    `RecoveryCodesDto`; `UserDto.secondFactorState`. `RecoveryCodes`
    generator (10 × 10 `[a-z0-9]`, SHA-256-hex at rest). `UserControl`:
    status / enroll (409 while confirmed; replaces pending) / confirm (400
    `invalid-code`; returns codes) / disable (password) / regenerate
    (password) / admin reset.
  - Tests (`SecondFactorTest`, computing codes with `TotpVerifier` and the
    returned secret): the acceptance matrix of REQ-615/616; the contract
    text has `secret` only in `SecondFactorEnrollmentDto` (grep assertion
    on `api/openapi.yaml`); log sweep of the class finds no secret/code.
  - Files: `api/openapi.yaml`, `packages/api-client/`, `user/**`, tests.
  - Validate: `./mvnw verify` green.

- [x] **T-10 Login enforcement via the trusted proxy (D6, D7, D8)** — _REQ-617, REQ-604_
  - Contract: `LoginRequest.totpCode?` (`^[0-9]{6}$`), `recoveryCode?`
    (`^[a-z0-9]{10}$`); `401` codes documented (`second-factor-required`,
    `invalid-credentials`). `AuthControl.login` per the design sequence;
    `UserControl.verifyLogin(username, password, totpCode, recoveryCode,
    requireSecondFactor)` — no confirmed factor via proxy → `FAIL` after the
    password work; TOTP/recovery consumption inside its transaction.
  - Tests: `TrustedProxyLoginTest` matrix — no code → 401
    `second-factor-required` and hasher counter unchanged; wrong code → 401
    `invalid-credentials` + bucket +1; no factor + correct password + any
    code → `invalid-credentials`; correct password + valid code → 200; each
    recovery code once; default profile: factor fields ignored, user without
    a factor logs in.
  - Files: `api/openapi.yaml`, `packages/api-client/`, `auth/control/AuthControl.java`, `user/control/UserControl.java`, tests.
  - Validate: `./mvnw verify` green.

## A4 — audit trail

- [x] **T-11 `de.gxp.audit` package, V7, read endpoint (D1, D17, D18, D19, D22)** — _REQ-620, REQ-622, REQ-624_
  - `V7__audit_entry.sql`. `audit/entity/AuditEntry` + `AuditEntryMapper`,
    `audit/control/AuditEntryRepository`, `AuditControl` (`record` REQUIRED,
    `recordLoginSuccess/Failure` REQUIRES_NEW, `changedFields` getter diff,
    `page`, `pruneOlderThan`, action/entity constants), `AuditResource`
    (class-level `@RolesAllowed("admin")`). Contract tag `manager-audit`,
    `GET /api/manager/audit?page&size`, `AuditPageDto`, `AuditEntryDto`.
  - Tests: `changedFields` on two `PropertyDto`s (names only, never values —
    assert the returned list contains no value string); schema assertion:
    `audit_entry` columns are exactly the nine designed; paging newest first
    with `total`; `size=201` → 400; `MigrationTest` head → `7`.
  - Files: `db/migration/V7__audit_entry.sql`, `de/gxp/audit/**`, `api/openapi.yaml`, `packages/api-client/`, tests.
  - Validate: `./mvnw verify` green; architecture guards green (`audit` has all three sub-packages).

- [x] **T-12 Audit login outcomes and account actions** — _REQ-621, REQ-624, REQ-612_
  - `AuthControl.login` records `login-success` / `login-failure` (never on
    `429`); `AuthControl.login` loses its own `@Transactional` (design);
    `UserControl` records every user-management and second-factor action;
    `changeOwnPassword` records `password-change`.
  - Tests: a failed login leaves one `login-failure` row (submitted
    username, `127.0.0.1`, `viaProxy=false`) although the response was 401;
    a `429` leaves none; a success row's `actor` equals the login username
    (this is the REQ-612 attribution proof); one row per user/second-factor
    action.
  - Files: `auth/control/AuthControl.java`, `user/control/UserControl.java`, tests.
  - Validate: `./mvnw verify` green.

- [x] **T-13 Audit every domain mutation** — _REQ-621, REQ-624_
  - Explicit `auditControl.record(...)` calls: `PropertyControl.update`
    (`changedFields(get(), dto)`), `RoomControl.create/update/delete`,
    `MenuControl.putConfig/putCards` (`replace`, entityId `property` or
    room id), `StayControl.create/update/delete`,
    `AssetControl.upload/delete`, `DeviceControl.confirmPairing/renameDevice/revokeDevice`.
  - Tests: one class-level test per package (or one `AuditCoverageTest`)
    asserting the row(s) after each operation and **no row after a
    rolled-back update** (a room update failing validation). Grep guard:
    every public mutating Control method named above contains
    `auditControl.record` (a text test in `de.gxp.audit`, listing the
    methods literally — the reviewer's "grep every call site" made
    mechanical).
  - Files: the six Controls, `src/test/java/de/gxp/audit/AuditCoverageTest.java`.
  - Validate: `./mvnw verify` green.

- [x] **T-14 Retention job** — _REQ-623_
  - `de.gxp.infrastructure.scheduler.AuditScheduler#pruneExpiredEntries`
    (`cron = "0 0 2 * * ?"`, `SKIP`, class-name-only failure log, count log
    only when > 0) → `auditControl.pruneOlderThan(Duration.ofDays(365))`.
    Edit `ScheduledJobInventoryTest.PINNED_INVENTORY` **on purpose** to add
    `de.gxp.infrastructure.scheduler.AuditScheduler#pruneExpiredEntries cron="0 0 2 * * ?"`.
  - Tests: prune deletes a 366-day-old row and keeps a 364-day-old one;
    inventory test green with three entries.
  - Files: `infrastructure/scheduler/AuditScheduler.java`, `architecture/ScheduledJobInventoryTest.java`, `src/test/java/de/gxp/audit/AuditRetentionTest.java`.
  - Validate: `./mvnw verify` green.

## Tests and contract hygiene

- [x] **T-15 Contract and boundary hygiene** — _REQ-625, REQ-628, REQ-626_
  - Run `./mvnw verify` twice; `git status packages/api-client` clean after
    the second run; `git diff --stat backend/app/pom.xml` empty;
    `git diff api/openapi.yaml | grep -E '^[+-].*/api/(guest|pairing)'`
    empty; `ContractSecuredOperationsDeniedTest` count = old + 13; all five
    architecture guards + `BoundaryAnnotationAuditTest` green; `RoomScopingTest`
    and every guest/device test unchanged in content (`git diff --stat` on
    those files shows only the fixture swap from T-01).
  - Validate: the listed commands.

## Cleanup and docs

- [x] **T-16 Documentation moves with the code** — _REQ-627_
  - `backend/README.md`: "Manager auth workflow" rewritten (username +
    password, first user via `GXP_ADMIN_USERNAME`/`GXP_ADMIN_PASSWORD`, user
    management, second factor enrol/confirm/recovery/disable, the
    trusted-proxy switch and the warning that a LAN range in
    `GXP_TRUSTED_PROXY` turns the throttle into a weapon, audit read,
    365-day retention); `backend/compose.yaml` comments; `application.properties`
    comment blocks (throttle, proxy, seeding); `docs/system-context.md`
    (component rows for `de.gxp.user`, `de.gxp.audit`, updated `de.gxp.auth`
    row, `ClientAttribution`, `AuditScheduler`; the Auth and Scheduled
    housekeeping bullets; the "No central patching" deployment row already
    superseded by v1.8.0 gets its note); `docs/features/index.md` row →
    06_review.
  - Validate: manual read-through; links resolve.

- [x] **T-17 Final quiet-window run and log sweep** — _REQ-619, REQ-626_
  - Full `./mvnw verify` in a quiet window; capture the surefire output and
    grep it for the test password, any token, any TOTP secret, recovery
    code or `Forwarded` address — expected zero hits outside test names.
    Update `docs/features/auth-hardening/handoff.md` with counts.
  - Validate: BUILD SUCCESS; sweep empty.
