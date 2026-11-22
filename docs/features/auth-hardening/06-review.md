# Review: Authentication Hardening (remote-operations Part A)
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Result:** PASS ✅ (2026-08-26, `reviewer`, against constitution **v1.8.0**, `docs/backend/remote-operations.md` §4/§7/§12, `backend/CLAUDE.md`)

**Counts:** 🔴 0 · 🟠 0 · 🟡 3 · 🟢 9

> Final 06_review of the whole feature — T-01…T-17 plus the four in-flight
> block reviews and their fix rounds (all recorded in `handoff.md`). Scope
> is the uncommitted working tree on
> `refactor/bce-subpackages-and-boundary-security` (`git diff HEAD --stat`:
> 64 tracked files + the new `de.gxp.user`, `de.gxp.audit`, `LoginThrottle`,
> `ClientAttribution`, `AuditScheduler`, V5–V7 and 22 new test classes);
> `docs/features/update-channel/` is another feature's planning and was
> ignored. Nothing was fixed in-review; no `git checkout`/`restore`.
> Visual check (code-review step 0): **not applicable** — backend, contract,
> config and docs only, no rendered surface.

## Suite run (honest count)

`target/surefire-reports` wiped, then one full
`JAVA_HOME=/tmp/gxp-jdk/jdk-21.0.12+8 sg docker -c "… ./mvnw -q verify"`
in the background (started 07:06, ended 07:28; load average 9–12 on the
4-core Pi with Immich/Home Assistant running, the first Quarkus boot alone
took 145 s).

- **Full run: 65 classes, `EXIT=1`; 341 tests reported, 1 failure, 4 errors,
  3 skipped.** 60 classes green. The five non-green classes are exactly the
  load-sensitive set every earlier run in `handoff.md` also lost, and none
  is an assertion of this feature's logic:
  - `TrustedProxyLoginTest`, `UntrustedPeerAttributionTest`,
    `DevDataSeederTest`, `OpenApiDisabledTest` — each needs its own Quarkus
    boot and lost its Postgres Dev Services container to
    `ContainerLaunchException: Container startup failed for image
    postgres:17.11` (Testcontainers start timeout under load; 1 error +
    the rest skipped per class).
  - `PairingFlowTest.aConcurrentFloodIsFullyServedAndArmsNothing` — a 500
    where 201 was expected; the run log carries 45 Agroal `Acquisition
    timeout while waiting for new connection` lines in that window (pool
    starved while the box stalled) — the pre-existing kiosk flood test, not
    touched by this feature beyond the T-01 fixture swap.
- **Isolated re-run** of those five classes (one `verify -Dtest=…`, five
  boots, load average ~4): see the line below, filled in after the run.
  - all five green, `EXIT=0`: `TrustedProxyLoginTest` 6/6, `PairingFlowTest`
    8/8, `DevDataSeederTest` 2/2, `UntrustedPeerAttributionTest` 2/2,
    `OpenApiDisabledTest` 2/2 (07:33–07:36).
- **Honest total across the 65 report files after the re-run: 346 tests,
  0 failures, 0 errors, 0 skipped** — the same figure dev's T-17 run
  reached the same way (`handoff.md`). Note for the owner: on this box the
  full suite does not pass in one go under load; the drop is always a
  Postgres Dev Services start timeout on a profile boot or the pairing
  flood starving the pool, never an assertion of this feature's code.
- **Green in the full run, relevant to the gate:** the five
  `de.gxp.architecture` guards (`FeatureInternalsImportTest` 3,
  `EntityExposureTest` 1, `ControlPackageVisibilityTest` 2,
  `PersistenceAccessTest` 2, `ScheduledJobInventoryTest` 2),
  `BoundaryAnnotationAuditTest` 3, `ContractSecuredOperationsDeniedTest` 49
  (floors 54 / 48 with the twelve new operations pinned), `MigrationTest` 5
  (head V7), every `de.gxp.audit.*`, `de.gxp.user.*`,
  `de.gxp.auth.control.*` class, `AuthResourceTest` 12, `UserResourceTest`
  8, `SecondFactorTest` 7, `SecondFactorLoginTest` 6 (proxy profile — that
  boot survived), `SecondFactorSingleUseTest` 2, `RoomScopingTest` and the
  whole guest/device suite.
- **Contract hygiene:** `git diff HEAD --stat -- backend/app/pom.xml` empty;
  `git diff HEAD -- api/openapi.yaml | grep -E '^[+-].*/api/(guest|pairing)'`
  empty; the twelve new operations all declare `bearerAuth` (12 `+ … bearerAuth`
  lines in the contract diff); `packages/api-client/` hashed
  `761b469e…` before the run and `761b469e…` after the regenerating build →
  a second generation is diff-clean, the committed client is generator
  output (7 tracked files modified + 13 untracked generated files, all
  shapes of the generator, no hand edit visible).

## REQ coverage — every REQ-601…REQ-628 traced (0 orphans)

| REQ-ID | Implementation | Test(s) | ✔ |
|---|---|---|---|
| REQ-601 | `auth/control/LoginThrottle` (per bucket key, never per account; IPv4 verbatim, IPv6 /64, IPv4-mapped unwrapped), `recordSuccess` removes only the caller's bucket | `LoginThrottleTest` (14, incl. the five key-shape tests), `TrustedProxyLoginTest.twoForwardedAddressesHaveSeparateBuckets` | ✅ |
| REQ-602 | `throttle.check(address)` is the first statement of `AuthControl.login`/`changePassword`; `login` carries no `@Transactional`; `ApiErrors.tooManyRequests` + `Retry-After`; 60 s × 2^(failures−5), cap 900 | `LoginThrottleTest` (block, escalation, cap, Retry-After ≥ 1), `LoginThrottleResourceTest.fiveFailuresThenTheSixthIs429BeforeAnyHashing` (`passwordVerifications()` unchanged, no audit row) | ✅ |
| REQ-603 | `infrastructure/http/ClientAttribution` (Quarkus `quarkus.http.proxy.*`, `X-Forwarded-Trusted-Proxy` honoured only with `enable-trusted-proxy-header`), six explicit lines in `application.properties`, one env switch | `ClientAttributionTest` (default: headers ignored), `UntrustedPeerAttributionTest` (switch on, untrusted peer ignored / stamp overwritten to false), `TrustedProxyLoginTest` (attributed + flagged, first `for=` wins, `_hidden` → 400) | ✅ |
| REQ-604 | `login` counts every verified miss incl. factor misses; `changePassword` checks + counts; `/me` factor ops unthrottled per the dated amendment (D11) | `SecondFactorLoginTest.aWrongCodeIsTheGenericFailureAndCountsAgainstTheAddress` (1 password + 4 code misses → 429), `LoginThrottleResourceTest` (password change +1, blocked address 429 without hashing) | ✅ |
| REQ-605 | `MAX_TRACKED = 10 000`, stale-then-oldest eviction on insert; no `Logger` in `de.gxp.auth`/`user`/`audit`/`infrastructure.http` (grep) | `twentyThousandAddressesNeverExceedTheCap`, `staleBucketsAreEvictedBeforeLiveOnes`; `LogSweep` (`de.gxp` + `io.quarkus.vertx.http`) asserts nothing logged in `LoginThrottleResourceTest`, `SecondFactorLoginTest`, `TrustedProxyLoginTest` | ✅ |
| REQ-606 | `backend-domain-config/01-prd.md` REQ-326 carries the dated reopening note and the withdrawn criterion; `sixWrongPasswordsInARowArmNothing…` deleted; `application.properties` comment block rewritten | `git diff` verified; `AuthResourceTest` no longer contains the test | ✅ |
| REQ-607 | `V5__manager_user.sql` (CREATE, `INSERT … SELECT 'admin', password_hash, …`, DROP) | `ManagerUserCarryOverTest` (statement read verbatim from the file, identical hash, active, `created_at = updated_at`), `MigrationTest.adminCredentialTableIsGoneAndTheFirstUserIsSeededByCode`, `flywayHeadIsV7AuditEntry` | ✅ |
| REQ-608 | `user/control/FirstUserSeeder` (`count() > 0` → return before reading either variable; username pattern; three password refusals naming variable/rule only) | `FirstUserSeederTest` (11: unset/blank/short/invalid-username refuse, value never in the message, default `admin`, `owner`, existing account never re-policed) | ✅ |
| REQ-609 | `LoginRequest{username, password}`; `UserControl.verifyLogin` verifies a construction-time dummy hash for unknown/inactive, real hash otherwise, one `Verdict.fail` shape | `LoginOracleTest` (unknown, wrong, deactivated = one derivation each), `AuthResourceTest.unknownUserAndWrongPasswordAnswerByteIdenticalBodies`, `aContractInvalidUsernameIsA400NotA401` | ✅ |
| REQ-610 | `manager-users` tag → `UserResource` (class-level `@RolesAllowed("admin")`, eleven one-liners); `UserControl.list/create/deactivate/reactivate/resetPassword/resetSecondFactor`; self-deactivate 409; duplicate 409 | `UserResourceTest` (8), `SecondFactorTest.anotherAdminCanResetTheFactorButNobodyTheirOwn` | ✅ |
| REQ-611 | `UserControl.CredentialsInvalidated` fired from deactivate / resetPassword / changeOwnPassword / resetSecondFactor; `AuthControl.onCredentialsInvalidated` → `TokenStore.removeAllOf` | `UserResourceTest.deactivatingAnotherAccountKillsItsTokens…`, `anAdminResetReplacesThePasswordAndDropsTheTargetsTokens`, `OwnPasswordChangeTest`, `AuthResourceTest.passwordChangeDropsTheCallersSessions…`, `TokenStoreTest.removeAllOf…` | ✅ |
| REQ-612 | `TokenStore.Session(username, expiry)`, `TokenIdentityProvider` principal = username, `LoginResponse.username` | `AuditLoginTest.aSuccessfulLoginRecordsTheUsernameAsActor`, `OwnPasswordChangeTest` (principal proven through the other user's mismatch), `AuthResourceTest.loginIssuesASlidingTokenWithExpiry` (`username`) | ✅ |
| REQ-613 | Contract pattern on `LoginRequest.username`/`UserCreateRequest.username`, DB `CHECK`, `ManagerUserMapper` maps neither hash, secret nor step | `UserResourceTest` key sweep `(?i)"…(hash|secret|code|password)…":` on every user response; `SecondFactorTest` sweep on status/enroll bodies | ✅ |
| REQ-614 | `user/control/TotpVerifier` (`Mac("HmacSHA1")`, 6 digits, 30 s, 20-byte `SecureRandom`, ±1, all three candidates evaluated, `MessageDigest.isEqual`); replay guard `totp_last_step` advanced by a conditional UPDATE | `TotpVerifierTest` (six RFC 6238 App. B SHA-1 vectors, drift, replay, malformed), `SecondFactorLoginTest.aValidCodeLogsInOnceAndIsRefusedOnReplay`, `SecondFactorSingleUseTest` | ✅ |
| REQ-615 | `secondFactorStatus/enrollSecondFactor/confirmSecondFactor/disableSecondFactor` (+ `409 no-pending-enrollment`, `409 second-factor-already-confirmed`, `400 invalid-code`, `400 current-password-mismatch`) | `SecondFactorTest` (7) | ✅ |
| REQ-616 | `RecoveryCodes` (10 × 10 `[a-z0-9]` ≈ 51.7 bit, SHA-256-hex at rest), `RecoveryCodeRepository.markUsed` conditional UPDATE, `replaceRecoveryCodes` deletes all | `SecondFactorTest` (stored = hashes, regenerate voids), `SecondFactorLoginTest.eachRecoveryCodeWorksExactlyOnce`, `SecondFactorSingleUseTest.theSameRecoveryCode…` | ✅ |
| REQ-617 | `AuthControl.login`: `viaProxy && no code → 401 second-factor-required` before `verifyLogin`, uncounted; `verifyLogin(…, requireSecondFactor)`: no confirmed factor → fail after the password work; LAN → codes ignored | `SecondFactorLoginTest` (6: code-less = no derivation + budget untouched; wrong code generic + counted; no-factor user generic with one derivation, body byte-identical; valid/replay/±1/±3; recovery once; nothing leaks), `AuthResourceTest.onTheLanTheSecondFactorFieldsAreIgnored` | ✅ |
| REQ-618 | `UserControl.resetSecondFactor(id)` (self 409, clears secret + codes, drops tokens) | `SecondFactorTest.anotherAdminCanResetTheFactorButNobodyTheirOwn` | ✅ |
| REQ-619 | No logger in the three packages; only `SecondFactorEnrollmentDto` carries `secret` (once in the YAML); `ApiErrors`/`IllegalStateException` messages carry names and rules only | `SecondFactorTest.theContractCarriesTheSecretOnlyInTheEnrollmentDto`, `LogSweep` in `SecondFactorTest`/`SecondFactorLoginTest`, `SecondFactorLoginTest.noSecretOrCodeReachesAnyResponseButTheToken`; T-17 sweep of the surefire output recorded in `handoff.md` | ✅ |
| REQ-620 | `V7__audit_entry.sql` (nine columns, no value column), `AuditEntry`, `AuditResource` read-only; failure rows carry `Verdict.matchedUsername` or null, `second-factor-required` rows no entity id | `AuditResourceTest.theTableHasExactlyTheNineDesignedColumnsAndNoValueColumn`, `theSurfaceIsReadOnlyAndAdminOnly` (405/404), `AuditLoginTest` (3), `SecondFactorLoginTest.withoutACode…` (`entity_id is null`) | ✅ |
| REQ-621 | Explicit `auditControl.record(...)` in `PropertyControl.update`, `RoomControl.create/update/delete`, `MenuControl.putConfig/putCards`, `StayControl.create/update/delete`, `AssetControl.upload/delete`, `DeviceControl.confirmPairing/renameDevice/revokeDevice`, ten `UserControl` actions, `AuthControl.login` (25 sites) | `AuditCoverageTest` (derived scanner + `== 25` pin + the four HTTP walks), `AccountAuditTest` (eleven-row actor/action sequence), `AuditLoginTest`, `LoginThrottleResourceTest` (429 → no row) | ✅ |
| REQ-622 | `AuditControl.page` newest-first (`occurredAt DESC, id DESC`), contract `page ≥ 0`, `size 1..200` default 50 | `AuditResourceTest.pagesAreNewestFirstWithTheTotalCount`, `defaultsArePageZeroSizeFiftyAndTheBoundsAreEnforced` (0/200/201/−1) | ✅ |
| REQ-623 | `infrastructure/scheduler/AuditScheduler#pruneExpiredEntries` (`0 0 2 * * ?`, `SKIP`, class-name-only failure log, count only when > 0), `PINNED_INVENTORY` edited on purpose | `AuditRetentionTest` (366 deleted / 364 kept), `ScheduledJobInventoryTest` (three entries), `ScheduledJobExecutionTest.theAuditJobPrunes…` | ✅ |
| REQ-624 | `record` `REQUIRED`; login outcomes `REQUIRES_NEW`; `MenuControl` records before the invariant check so a 409 rolls the row back | `AuditCoverageTest.roomMenuCardsAndStayOperations…` (validation 400 → no row; invariant 409 → no row), `AuditLoginTest` (row survives the 401) | ✅ |
| REQ-625 | Every new operation/schema in `api/openapi.yaml`; twelve new operations all `bearerAuth`; `pom.xml` untouched; client regenerated | `ContractSecuredOperationsDeniedTest` (49 dynamic, new operations pinned); reviewer's `git diff --stat pom.xml` empty and identical client hash before/after the regenerating build | ✅ |
| REQ-626 | 22 new test classes + edits, per the per-REQ column above | — | ✅ |
| REQ-627 | `backend/README.md` (username login, first user, user management, second factor + recovery + NTP, trusted proxy + relay obligation + LAN-range warning, audit), `compose.yaml` (three optional vars), `application.properties` blocks, `docs/system-context.md` (three package rows, `ClientAttribution`, schedulers, Auth bullet, "built" pointer), `docs/backend/specification.md` §6 + decision 4 superseded + §9, `docs/features/index.md` | read-through; nits under 🟢 #5/#6 | ✅ |
| REQ-628 | `git diff` of `TokenIdentityProvider` touches the manager branch only (device branch byte-identical); no `/api/guest/**` or `/api/pairing/**` line in the contract diff; guest/device tests show only the T-01 fixture swap | `RoomScopingTest`, `Guest*Test`, `DeviceAuthTest`, `RoomDeviceGuardTest` green in the full run; `PairingFlowTest` 8/8 in isolation | ✅ |

## The four in-flight reviews — findings closed

| Review | Finding | Closed by | ✔ |
|---|---|---|---|
| T-01…T-04 | 🟠 #1 relay obligation / first `for=` / `_hidden` 500 | `remote-operations.md` §7 "Relay obligation", D4 amendment, `theFirstForwardedElementWinsSoTheRelayMustReplaceNotAppend`, `ClientAttribution` → `400 unattributable-address` (`anUnparseableForwardedAddressIsA400NotA500`) | ✅ |
| T-01…T-04 | 🟡 #2 IPv6 per-address evasion | `LoginThrottle.bucketKey` /64 keying, five unit tests, D2 + REQ-601 amended | ✅ |
| T-01…T-04 | 🟡 #3 switch-on negative untested | `UntrustedPeerProfile` + `UntrustedPeerAttributionTest` (2) | ✅ |
| T-01…T-04 | 🟡 #4 derived `allow-x-forwarded` | explicit `allow-x-forwarded=false`, `use-proxy-protocol=false` lines | ✅ |
| T-01…T-04 | 🟡 #5 narrow log sweep | `LogSweep` on `de.gxp` + `io.quarkus.vertx.http`, used in three classes | ✅ |
| T-01…T-04 | 🟡 #6 nested `CLAUDE.md` bullet | written by the lead 2026-08-26 under delegated authority (recorded in `handoff.md`) | ✅ |
| T-05…T-07 | 🟡 #1 self password reset | `409 cannot-reset-self` in `resetPassword`, D12 amendment, `anAccountCannotResetItsOwnPasswordWithoutTheCurrentOne` | ✅ |
| T-05…T-07 | 🟡 #2 `GXP_ADMIN_USERNAME` not in compose | `compose.yaml` line + comment | ✅ |
| T-05…T-07 | 🟡 #3 deactivated-user derivation count | `LoginOracleTest.aDeactivatedUserCostsOneDerivationToo` | ✅ |
| T-08…T-10 | 🟡 #1 single-use not atomic | `RecoveryCodeRepository.markUsed`, `ManagerUserRepository.advanceTotpStep` (conditional UPDATEs, `== 1`), `SecondFactorSingleUseTest` (2, real race with a `CyclicBarrier`) | ✅ |
| T-08…T-10 | 🟡 #2 REQ-604 wording vs `/me` ops | dated amendment in REQ-604 and D11 | ✅ |
| T-08…T-10 | Info #4 NTP | D9 note + README "The box's clock must be right" | ✅ |
| T-11…T-14 | 🟠 #1 attacker-controlled text in `entity_id` | `Verdict(ok, username, matchedUsername)`; `recordLoginFailure(verdict.matchedUsername())`; `recordSecondFactorRequired(attribution)` writes null; REQ-620/D18/A4 amended; `AuditLoginTest` (3) incl. "submitted strings in no cell"; `SecondFactorLoginTest.auditRows(…, null)` | ✅ verified in code |
| T-11…T-14 | 🟡 #2 hand-typed coverage list | `AuditCoverageTest.everyMutatingControlMethodCallsTheAuditControl` derives every `@Transactional public` method of every `*Control.java` (comments/strings stripped, brace-matched) against `NOT_A_MANAGER_MUTATION` + `EXPLICIT_ENTRIES`, `== 25` pin kept | ✅ (see 🟡 #2 below for its one remaining blind spot) |
| T-11…T-14 | 🟡 #3 stale full-row UPDATE after `advanceTotpStep` | `user.totpLastStep = step` deleted (`UserControl.secondFactorAccepted`), `SecondFactorSingleUseTest` asserts the stored step | ✅ verified: the managed entity is no longer dirtied |
| T-11…T-14 | Info #4 / #8 | comment after `replaceChildren`; `page=-1 → 400` | ✅ |

## Security categories — re-checked end to end

- **Per-address throttle before PBKDF2 and never global** — `throttle.check` precedes every repository call and every `hasher.verify` in both password-bearing operations; `AuthControl.login` has no `@Transactional`, so the 429 path opens no connection; `recordSuccess` removes the caller's bucket only; no account state anywhere. Proven by `verifyCount()` over HTTP. ✅
- **Trusted-proxy attribution off by default, unspoofable when on** — default profile ignores `Forwarded`/`X-Forwarded-For`/`X-Forwarded-Trusted-Proxy` (handler not installed; config guard in `ClientAttribution` makes a forged stamp inert); switch on + untrusted peer → Quarkus skips the headers and stamps `false` (`UntrustedPeerProfile`); switch on + trusted peer → attributed and flagged; loopback `/32` default for `trusted-proxies` (Quarkus' own default trusts everyone); first-`for=` rule pinned; `_hidden` → 400 with no state. The only two consumers of the flag that *branch* are the throttle key and the factor requirement (`AuthControl.login`); `AuditControl` records it, which is not a branch. ✅
- **No username/password oracle** — unknown, inactive and wrong-password all reach one `Verdict.fail` after exactly one derivation (dummy hash for the first two); bodies byte-identical; via proxy the no-factor case returns after the real hash (D7); the code-less 401 precedes any password work (D6); the `matchedUsername` never reaches the response. ✅
- **TOTP required only via proxy, LAN never asks** — `requireSecondFactor = viaTrustedProxy`; on the LAN `secondFactorAccepted` never runs and both fields are ignored (`000000` + garbage recovery code → 200). ✅
- **Recovery codes single-use and hashed** — SHA-256-hex at rest, constant-time match over ≤ 10 rows, conditional UPDATE, regenerate/disable/reset delete all rows; concurrent double-use yields one session. ✅
- **Audit trail without values (schema) and without attacker-controlled text** — nine columns asserted against `information_schema`; `changedFields` returns generated-DTO property names only; entity ids are numeric ids, `property`, canonical usernames or the pairing-request id; failure rows carry the matched canonical name or null, never the submitted text; the pairing code and property values are asserted absent from every cell. ✅
- **Sessions dropped on deactivate/reset** — synchronous CDI event → `removeAllOf(username)`; next request 401; reactivate does not restore sessions. ✅
- **Kiosks / `/api/guest/**` / `/api/pairing/**` untouched** — contract diff clean, device branch of `TokenIdentityProvider` unchanged, guest suite green. ✅
- **No `pom.xml` change; contract-first; generator-only client** — verified (hash identical across a regenerating build). ✅
- **Five `de.gxp.architecture` guards + `ScheduledJobInventoryTest`** — 10/10 green in the full run. ✅
- **Logging** — no `Logger`/`System.out` in `de.gxp.auth`, `de.gxp.user`, `de.gxp.audit`, `de.gxp.infrastructure.http`; `AuditScheduler` logs a count and an exception class name only; seeder messages name variables and rules only. ✅

## Findings

| # | Sev | Category | File | Finding | Fix |
|---|-----|----------|------|---------|-----|
| 1 | 🟡 | Security / D20 consistency (**owner decision**) | `backend/app/src/main/java/de/gxp/auth/control/AuthControl.java:105-108`; `AuditControl.recordSecondFactorRequired`; deviation 21 | The code-less via-proxy `401 second-factor-required` is **uncounted by the throttle (D6) and yet writes one `REQUIRES_NEW` audit row per request**. That is an unauthenticated, unbounded database insert reachable from the internet once Part D ships (`POST /api/manager/login` with `{username, password}` and no code, at whatever rate the relay admits), into a table with no size cap (§12 item 5) and 365-day retention — exactly the "audit write becomes the amplifier" shape D20 kept the 429 free of. The PRD (REQ-620 amendment) and design were updated to describe the row, so spec and code agree; the residual is the decision itself. Unreachable on a LAN-only box (`viaTrustedProxy` is always false there). The relay's transport limits (§7 req. 4) are defence in depth the PRD says Part A must not rely on. | Owner's call, before Part D: (a) drop the row (the T-10 dev recommendation — one line in `AuthControl.login` + the `SecondFactorLoginTest.auditRows("second-factor-required", null)` assertion), or (b) keep it and give code-less attempts a cheap per-address cap without PBKDF2 (a second bucket or counting them at 1/5 weight — D6's "UI round-trip must not eat the budget" survives either). Recommendation: (a); nothing was verified, so the row carries only an address that the `login-failure`/`login-success` rows of the same client will carry anyway. |
| 2 | 🟡 | Testing / guard blind spot | `backend/app/src/test/java/de/gxp/audit/AuditCoverageTest.java:149-150` (`TRANSACTIONAL_PUBLIC`) | The derived scanner recognises `@Transactional` **on the method only**. A Control that puts `@Transactional` at class level (legal, none exists today — verified by grep) would have every public method skipped and its mutations never classified, silently defeating the guard the previous review asked for. The `== 25` pin would still catch a *lost* call, not an unscanned new Control. | Two lines: in the same test, fail if any `*Control.java` matches `@Transactional\b[^\n]*\n\s*(@\w+[^\n]*\n\s*)*public\s+(final\s+)?class` (class-level use), with the message "put `@Transactional` on the methods — the audit scanner reads method annotations". Optional: also add the class-level rule to `backend/CLAUDE.md`'s BCE sentence when it is next touched (approval-gated). |
| 3 | 🟡 | Docs / constitution consistency | `docs/constitution.md` "Auth & transport" → "Password lifecycle (v1.5.0)" | The paragraph still says *"A password change **immediately invalidates every active bearer token** — the old credential must never survive into another idle window."* The v1.8.0 "Authentication hardening" paragraph supersedes the "one shared admin password" and "no per-user audit trail" wording explicitly, but not this sentence — and the as-built behaviour (D13, REQ-611) drops **that user's** tokens, not every token. The intent ("the old credential never survives") is preserved for the credential's owner; the literal sentence is now false, the same way the guard-count sentence was in v1.6.1/v1.7.1. | An accuracy note (v1.8.1, the v1.7.1 precedent — owner's review, no new approval): "…invalidates every active bearer token **of that account** (scoped per user since `auth-hardening`, D13)". Not a code change. |
| 4 | 🟢 | Ops / logging (record) | Quarkus `io.quarkus.vertx.http.runtime.ForwardedParser` | With the switch on, a request from the trusted peer that *carries* a client-sent `X-Forwarded-Trusted-Proxy` makes Quarkus log one value-free `WARN "The header X-Forwarded-Trusted-Proxy was already set. Overwriting it."` per request (seen in the T-17 sweep and in this run). Through a relay that does not strip the header, an internet client can therefore emit one log line per request — no address, no value, log volume only. Already covered: §7's binding relay obligation says the relay **must strip** `X-Forwarded-Trusted-Proxy` (and every `Forwarded`/`X-Forwarded-*`). | None required now. If the relay's stripping is ever in doubt, the application-side belt is lowering that one category (`quarkus.log.category."io.quarkus.vertx.http.runtime.ForwardedParser".level=ERROR`) — lowering, not raising, so `backend/CLAUDE.md`'s logging rule is not touched. Record in Part D's PRD as a test case for the relay ("the box logs nothing per proxied request"). |
| 5 | 🟢 | Docs consistency | `docs/system-context.md:174,186,196`; `docs/backend/specification.md` §6 | (a) The `de.gxp.system` row still says the boundary is enforced by "the **three** `de.gxp.architecture` guards" — it is five (constitution v1.7.1 corrected the same sentence there). (b) The Flyway row names the V6 table `recovery_code`; it is `manager_recovery_code`. (c) §6 says `de.gxp.auth.LoginThrottle`; the class is `de.gxp.auth.control.LoginThrottle`. (d) The `de.gxp.api` row (pre-existing) places `ApiErrors`/`ApiErrorMappers` in `de.gxp.api`; they live in `de.gxp.infrastructure.http`. | Four word-level edits when the file is next touched; (a) and (d) pre-date this feature. |
| 6 | 🟢 | Docs consistency | `backend/CLAUDE.md` Infrastructure sentence (the approved `ClientAttribution` bullet) | Says "`AuthControl` today; `UserControl`/`AuditControl` when they need it" — `AuditControl` injects it now (for `record`). Still true in spirit; the "today" list is one feature old. | Optional wording update at the next approved edit of that bullet (approval-gated; not worth a gate on its own). |
| 7 | 🟢 | Design (record — answers the handoff's question) | `UserControl.Verdict(ok, username, matchedUsername)` | An internal record component that names an account on a failed verdict, consumed only by `AuthControl.login` for the audit row. Judged sound: the alternative (returning the entity) would leak `.entity` across the feature boundary, and a separate lookup in `AuthControl` would be a second query with its own timing. `LoginOracleTest` dropping record-equality for explicit `ok`/`username` equality is the honest consequence, not a weakening (the client-visible half is what it asserts). | None. |
| 8 | 🟢 | Concurrency (record, carried) | `UserControl.deactivate/resetPassword/resetSecondFactor` (event fires inside the transaction) | A login racing the deactivation with the old credential can obtain a token in the microsecond before commit — fail-safe direction, one box, two admins. `UserControl.create` is check-then-insert (a concurrent duplicate → 500 from the UNIQUE constraint, not 409). Both recorded by the T-05…T-07 review; unchanged. | None now. |
| 9 | 🟢 | Testing / environment (record) | `LoginThrottleResourceTest` (1 s test block, `Thread.sleep(1100)`), the five profile-boot classes | The 1-second `%test` block with five `REQUIRES_NEW` inserts inside it is the flake the dev named; it did not flake in this run (the class was green under load average 11). The load-dropped classes are Testcontainers start timeouts on a saturated Pi, not assertions — they re-run green in isolation (above). | If the flake recurs: widen `%test.gxp.auth.throttle-block-seconds` to 3 and the two expectations (`Retry-After: 3`, `sleep(3100)`) — two lines, owner's "minimal first" preference respected by not doing it pre-emptively. |
| 10 | 🟢 | Security (record — checks out) | `LoginThrottle` steady state | ≈ 14 guesses per 30 min ≈ 670/day per address (per /64 for IPv6), stated in D2; a distributed attacker keeps one budget per address — spec §8's accepted residual, and what A3 exists for. | None. |
| 11 | 🟢 | Contract / UX (record, carried) | `LoginRequest.recoveryCode` pattern `^[a-z0-9]{10}$`; `SecondFactorEnrollmentDto.secret` | Upper-case recovery input is `400 validation-failed`, not the generic 401 (public rule, no oracle); the confirming TOTP code cannot immediately log in remotely (its step is stored as the guard). Both for the manager-UI feature to handle. | None. |
| 12 | 🟢 | Docs (record) | `docs/features/auth-hardening/03-todo.md` header | `**Status:** Not Started` while every task is `[x]`. | Flip to "Done" when the feature is committed. |

## What was checked and holds (so it is not re-litigated)

- **BCE / conventions:** `de.gxp.user` and `de.gxp.audit` have all three sub-packages; `UserResource` (eleven one-liners, class-level `@RolesAllowed("admin")` + `@ResponseStatus(201)` on create only) and `AuditResource` (one line) are pure adapters; `AuthResource` unchanged; `@Transactional` on Controls only; every non-Control type in `.control` package-private (`LoginThrottle`, `TokenStore`, `TotpVerifier`, `RecoveryCodes`, `Base32`, `FirstUserSeeder`, the three repositories, the hasher); entities/mappers public only as the guards allow; `ClientAttribution` public in `infrastructure.http` as `backend/CLAUDE.md` now records; every thrown non-2xx goes through `ApiErrors`; scheduled work only in `de.gxp.infrastructure.scheduler` with cron + `SKIP`; `de.gxp.auth` correctly joined `FEATURES_WITHOUT_ENTITIES`.
- **Transactions:** `login` non-transactional → `verifyLogin` (REQUIRED, commits the step/spend at return, *before* the token is issued) → `REQUIRES_NEW` audit writes; one connection at a time, no nested hold; `changePassword` → `changeOwnPassword` (REQUIRED) with the audit joining; `MenuControl` 409 proves rollback on a real `WebApplicationException`; `PropertyRepository.replaceChildren` flush+clear is correct and `PropertyControl.update` only reads the detached row afterwards (comment present).
- **Deviations 1–27 in `handoff.md`:** all accepted by the block reviews; re-read here, none reopens a §12 row or a D1–D25 decision without a dated amendment.
- **Approval gates:** the one nested-`CLAUDE.md` edit is recorded as written by the lead under the owner's delegated authority (2026-08-26); no dependency, datastore or new pattern beyond the ones the design records; `ApiErrors.tooManyRequests` restores a pattern removed on 2026-08-24.
- **Branch discipline:** all work on `refactor/bce-subpackages-and-boundary-security`, nothing committed by agents.

## Spec drift

None left open: every implementation deviation already carries a dated amendment in `01-prd.md` (REQ-601 criteria, REQ-603 criteria, REQ-604, REQ-620) or `02-design.md` (D2, D4, D9, D11, D12, D18, the A2/A4 dev notes, the error-code list). Finding 🟡 #3 is constitution wording, not feature-spec drift.

## Top 3 recommendations

1. **🟡 #1** — decide the `second-factor-required` audit row before Part D is planned: drop it (recommended, one line) or cap it per address; either way record the choice in D20/deviation 21 so the relay's PRD inherits it.
2. **🟡 #2** — the two-line class-level `@Transactional` guard in `AuditCoverageTest`, so the derived scanner cannot be bypassed by a future Control's annotation placement.
3. **🟡 #3** — a v1.8.1 accuracy note on the constitution's "every active bearer token" sentence (owner's review), plus the four wording nits in 🟢 #5 at the next docs touch.

**Next:** the user commits contract + backend + `packages/api-client/` + docs together (nothing is committed yet); `07_improve` optional after this much work (candidates: the `LogSweep`/`ManagerFixtures`/`QuarkusTestProfile` pattern and the "re-run load-dropped classes in isolation" rule for `backend/CLAUDE.md` → Tests, both approval-gated).
