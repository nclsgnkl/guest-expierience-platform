# Handoff: backend-bce-refactor

**Last session:** 2026-08-24 (`lead`) — **D1–D3 approved and the governed
docs are written; D4 parked by the owner. Block A delegated to `dev`.**
Branch: `refactor/bce-subpackages-and-boundary-security`. Nothing committed —
the user commits.

## Origin

Owner request (2026-08-24, in German), four items:

1. Introduce `boundary` / `control` / `entity` **sub-packages** inside each
   feature package. "Not 100 % BCE any more, but much clearer. If you have to
   tear open package structures — go for it."
2. **Security annotations belong on the Boundary**, not the Control.
3. 3.1 **Bug:** the login lockout is an admin lockout → unauthenticated DoS.
   Owner's proposal: remove it completely.
   3.2 **Question:** device token is valid forever — rotate it per
   check-in/check-out? Does that work **automatically**, without a user/admin
   doing anything? Fallback he'd accept *only if automation is impossible*:
   the guest gets a code at check-in and types it at the kiosk.

Items 1 and 2 contradict ratified text in `docs/constitution.md` v1.5.1 and
`backend/CLAUDE.md` → approval-gated, exact wording must be confirmed before
being written.

## `architect` findings (verified against Quarkus **3.33.3.1** artifacts in
`~/.m2` — config model JSON + `javap` on deployment/runtime bytecode)

**Could not execute `@QuarkusTest`:** this box's user is not in the `docker`
group, so Dev Services cannot start Postgres. All Quarkus claims are
bytecode-verified but not runtime-verified — hence step 0 of the
implementation order. The PBKDF2 timing **was** measured.

### Item 1 — sub-packages

- **The cost nobody had costed:** Controls mutate entity fields directly all
  over the codebase (`stay.language = …`, `device.revokedAt = …`,
  `credential.passwordHash = …`). Java has no sub-package visibility (JPMS
  exports at package granularity too), so splitting Control away from Entity
  forces **17 entity classes and ~97 fields to `public`** — or ~194
  accessors. No layout avoids it.
- **Better layout than the owner's sketch (Option A):** `control` holds the
  Control **plus every non-JPA collaborator** (Repository, `PairingCodes`,
  `PairingRateLimiter`, `TokenStore`, `Pbkdf2PasswordHasher`,
  `ImageProcessor`, `AssetMetaRow`, auth SPI adapters, `GuestEtagFilter`, …)
  → those all **stay package-private**. `entity` holds `@Entity` + `*Mapper`.
  Only Entity + Mapper become public. The owner's sketch (Repository in
  `control`, Mapper in `entity`) is strictly worse — it publicises the
  Repository for nothing.
- **Enforcement replacement, dependency-free**, in the style of the existing
  `SecurityAnnotationAuditTest` (~60 lines each):
  - *Test A — source-import scan:* no file outside `de.gxp.<f>` may reference
    `de.gxp.<f>.entity.*` / `de.gxp.<f>.control.*` except the Control.
    (Zero wildcard imports in the codebase — verified.) Misses leaks via
    inference (`var r = roomControl.…()`).
  - *Test B — reflection signature scan:* no public field/method/ctor outside
    `.entity` may expose an entity type in its signature (incl. generics,
    arrays). Closes Test A's hole; upholds "Entities never leave their
    feature".
  - **ArchUnit rejected** — new dependency, approval-gated, against
    principle 4 (unpatchable boxes).
- **Blast radius:** 70 main + 39 test files; ~65 move. Only **16**
  cross-feature `de.gxp` imports change (everything else imports the
  *generated* `de.gxp.api.*`). **Nothing in the framework cares** — verified:
  no `beans.xml`, no jandex plugin, no `quarkus.hibernate-orm.packages`, no
  `quarkus.index-dependency`, no `@RegisterForReflection`; generator options
  are fixed (`de.gxp.api`, `de.gxp.api.model`). Six tests must move with
  their target (they touch package-private types): `TemplateControlTest`,
  `PairingFlowTest`, `DeviceLastSeenTest`, `AdminPasswordSeederTest`,
  `Pbkdf2PasswordHasherTest`, `TokenStoreTest`.
- **Excluded, by name not by accident:** `de.gxp.api`, `de.gxp.web`,
  `de.gxp.devseed`, `de.gxp.testsupport`. `de.gxp.text` **is** a feature
  package (Control only) → apply the scheme anyway.
- The two-Boundary exception fits well:
  `de.gxp.device.boundary.{PairingResource, DeviceAdminResource}`.
- Option D (entity behaviour methods + getters → fields stay private) exists
  as a **later, optional** quality feature. Not now.

### Item 2 — security annotations on the Boundary

- **`deny-unannotated-members` is class-scoped** (verified, extension config
  javadoc): *"access to all methods of beans that have any security
  annotations on other members will be denied by default."* A bean with
  **zero** annotations is untouched. → **Moving the annotations alone makes
  Controls annotation-free and the flag inert. Strictly weaker than today.**
- **The fix exists and is better than today:**
  `quarkus.security.jaxrs.deny-unannotated-endpoints=true`
  (`JaxRsSecurityConfig.denyJaxRs`, BUILD_TIME, default false) is **global** —
  verified in `ResteasyReactiveProcessor.integrateEagerSecurity`. It closes a
  hole that exists **today**: a new Boundary over a new Control with no
  annotation anywhere is currently wide open, and the audit test only catches
  it if the class name ends in `Control`.
- **Annotations on the implementing Boundary method ARE honoured** — this
  needed checking, because `de.gxp.api.ResponseStatusFilter` exists precisely
  because `@ResponseStatus` on an override is invisible to the build-time
  scanner. The security path differs:
  `EagerSecurityHandler…getSecurityCheckInfo` resolves `invokedMethodDesc`
  (**implementation**) first, `fallbackMethodDesc` (**generated interface**)
  second, then the deny-default. **Impl wins; the interface can only add.**
- Generated interfaces carry **no** security annotations today
  (`jaxrs-spec`, `useSwaggerAnnotations=false`) — `bearerAuth` in
  `api/openapi.yaml` is documentation-only on the Java side. Risk to pin with
  a test: a generator upgrade that starts emitting them.
- **The owner's request removes real warts.** Five `@PermitAll`-on-internal
  annotations exist *solely* so internal calls pass — sharpest:
  `TokenIdentityProvider` → `DeviceControl.verifyToken`, called **during
  authentication, before any identity exists**. Worse, the design has already
  been bent: `StayControl.writePreference` deliberately skips `validate()`
  because it reaches `PropertyControl.requireOffered` (`@RolesAllowed("admin")`)
  which would blow up under a device identity — so the validation was
  **duplicated** in `GuestControl.writePreferences`. Also latent: several
  admin→admin internal calls work only because the caller *happens* to be
  admin.
- `de.gxp.device` gets materially cleaner: one class-level `@PermitAll` on
  `PairingResource`, one class-level `@RolesAllowed("admin")` on
  `DeviceAdminResource`, instead of 8 scattered annotations on one Control.
- **`denyJaxRs=true` affects JAX-RS resources only** — verified not affected:
  `SpaRoutingHandler` (Vert.x route), static `META-INF/resources` (the
  bundled kiosk app), exception mappers, filters, `/q/**`.
- Replacement tests: (1) every public `*Resource` method resolves to exactly
  one effective annotation (method-level else class-level), sweep pinned by
  name; (2) no generated `*Api` interface method carries one; (3)
  **contract-driven**: every `bearerAuth` operation in `api/openapi.yaml`
  answers 401 anonymously (extends `ManagerSurfaceDeniedTest`). Test-only
  `FailClosedResource` needs `@PermitAll` or it 401s under `denyJaxRs`.
- **Verdict: better than today — conditional on the config flag landing in the
  same change.**

### Item 3.1 — login lockout

- **DoS confirmed, total.** `@ApplicationScoped` singleton, **one global
  counter, one global `lockedUntil`**, checked **before** verification → the
  correct password gets 429 during the lock. Trivially re-armed (5 requests
  per 30 s, forever). `LoginLockoutTest` asserts exactly this behaviour.
  Denies the *incident-response* function: locked out, the manager cannot
  revoke a stolen device token.
- **But removing it wholesale is worse.** Measured on this box (Raspberry
  Pi 4B, JDK 21.0.12): PBKDF2-HMAC-SHA256, 600 000 iterations =
  **2216 ms single-thread**. Today's lockout caps login CPU at ~9 % of the
  box. Unthrottled, **4 concurrent unauthenticated requests pin all 4 cores
  indefinitely** — and that's the same worker pool serving blocking JDBC for
  the kiosks. "Manager can't log in" becomes "**the instance stops serving
  kiosks**".
- **Brute force, with numbers:** lockout = 0.17 guesses/s; unthrottled =
  1.8/s; single-permit gate = 0.45/s. At 1.8/s: 8 random chars (62-alphabet)
  = ~3.8 M years; a 10⁶ dictionary = 6.4 days; a 10⁴ dictionary = 1.5 hours.
  **The lockout buys ~10×; the password policy buys ~10⁸×.**
- **Adjacent, now dominant:** `AdminPasswordSeeder` enforces **no policy at
  all** on the first-start `GXP_ADMIN_PASSWORD` (only `changePassword`
  enforces ≥ 8) — so the seeded password can be `a`.
- **Same bug, second instance:** `PairingRateLimiter` (5 creates / 60 s,
  global) — an attacker can keep a real kiosk from ever getting a pairing
  code. Already observed self-inflicted via clock skew
  (`guest-frontend-integration/06-review.md` 🟡-3).
- **Recommendation — `LoginConcurrencyGate`:** `new Semaphore(1, true)`,
  bounded wait (~5–10 s), bounded queue (~4); over capacity → `429` +
  `Retry-After: 1` **without hashing**. Fair FIFO → manager's worst case
  < ~10 s, and **no state an attacker can push into a denying mode**. Put
  `changePassword` behind it too (closes `backend-domain-config` 🟢 #5).
  Same pattern for `PairingRateLimiter`. Options (c) per-IP and (d)
  exponential delay rejected — spoofable/NAT-prone, and sleeping holds worker
  threads, which *is* the DoS.
- Noted, **not** recommended as part of the fix: 600 000 iterations is OWASP's
  x86 figure (~0.5 s); on this box it's a 4× DoS amplifier and poor UX. The
  stored format is self-describing, so lowering it rehashes on next login.

### Item 3.2 — device token rotation

**Direct answer: YES, full automation is possible — no manager, no guest, no
code typed anywhere. The fallback should not be built.**

- **What a stolen token buys today: LOW.** Room-scoped, no cross-room access
  (verified in `GuestControl`, `RoomScopingTest`, and on the contract — no
  guest operation takes a room/stay/device/property parameter). Reachable:
  own-room session/home/assets + preference write + `/api/system/info`. Only
  PII is the guest display name. **No door/key-box/alarm codes or WiFi
  passwords exist in the domain model yet** — they arrive with the five queued
  screens. The real defect is **temporal**: the token outlives the stay, so
  last week's guest keeps reading this week's data — principle 4's rule
  shifted from space to time. **The owner's instinct is right, just early.**
- **Storage is not the fixable part.** Token lives in `localStorage`
  (`gxp.device-token`). `sessionStorage`/memory would unpair the kiosk on
  every power cut → manager walks to the room. You cannot keep a secret on an
  unattended device in a guest's room; the answer is bounding *how long a copy
  is useful*, i.e. rotation.
- **There is no check-in/check-out event.** `Stay` stores only
  `BOOKED`/`CANCELLED`; "active" is **derived from the clock**
  (`StayRepository.activeOf(roomId, Instant.now())`). No scheduler is on the
  allow-list. → rotation must be **poll-driven**, and the hook is free: the
  kiosk already polls `GET /api/guest/session` every 30 s and each poll
  already resolves the active stay. `GuestSession` must **not** start exposing
  a stay id — the comparison stays server-side.
- **Promote-on-first-use (never bricks a kiosk):** `device` gains
  `bound_stay_id`, `next_token_hash` (UNIQUE), `rotation_started_at`. On an
  authenticated guest request: if the presented hash is the *old* one and the
  active stay differs from `bound_stay_id`, mint a successor and attach it —
  **re-minted on every such request**. If the presented hash is
  `next_token_hash`, promote it and commit `bound_stay_id`. A crash/power cut
  at any point just re-issues on the next poll. No timers, no grace clock.
- **Honest limit:** an attacker who keeps polling rotates along with the
  kiosk. Rotation bounds a *passive* copy; it does **not** evict an active
  attacker. If the attacker promotes first, the kiosk 401s and falls back to
  pairing — a support call, but also a **loud detection signal that does not
  exist today**. Full eviction needs manager revoke, which has an API but
  **no UI yet**.
- **Delivery must be a response header `GXP-Device-Token`, not a body field** —
  `GuestDataService` caches the **raw `GuestSession` payload** into
  `localStorage` (`SESSION_CACHE_KEY`), so a body field would write a
  credential into the last-known-good cache the constitution restricts, and
  would churn the ETag. Mirrors the existing `GXP-Token-Expires` precedent;
  the service already reads `response.headers.get('etag')`. Wrinkle: Angular
  surfaces **304 as `HttpErrorResponse`** — read `error.headers` too, or
  suppress the 304 while a rotation is pending (needed anyway: an unchanged
  body would 304).
- **Contract/DB:** `GXP-Device-Token` response header on guest 200/304 in
  `api/openapi.yaml`; `typescript-angular` does not model response headers →
  regenerated client diff empty/trivial. One Flyway migration
  `V5__device_token_rotation.sql`.
- **Add age-based rotation to the same machinery** (`stay changed OR age >
  24 h`) — nearly free, and it also bounds a 3-week stay, which stay-based
  rotation alone does not.
- **Fallback (guest types a code) rejected:** kiosk unusable until someone
  types (contradicts the standby/vacancy design — `StandbyComponent` has no
  navigation surface at all), puts a person back in the loop at check-in, and
  doesn't even solve the threat (the guest who types the code is the one who
  can then read `localStorage`).
- **Verdict: own feature `device-token-rotation`, sequenced AFTER the manager
  UI's device-list/revoke and BEFORE the first screen carrying a door code or
  WiFi password.** Not now.

## Status 2026-08-25 — all three blocks implemented, NOT committed

**Suites: 230 backend / 251 frontend green.** Branch
`refactor/bce-subpackages-and-boundary-security`.

| Block | State |
|---|---|
| **A** — throttling removed | ✅ done, reviewed, fix round applied |
| **B** — security annotations → Boundary + `deny-unannotated-endpoints=true` | ✅ done, reviewed, fix round applied |
| **C** — `boundary`/`control`/`entity` migration + architecture guards | ✅ done, reviewed, fix round applied |

### Block C outcome

All ten feature packages migrated via `git mv` (69 renames, all R100). **58 of
the 17 entities' 97 fields widened to `public`; 39 stayed package-private** —
the rule applied is *widen only where a Control outside `.entity` actually
touches the field*, which is narrower than the constitution's blanket "(and
their fields)". The reviewer **proved this minimal**: it stripped `public` from
all 58 and compiled, and javac emitted exactly the 58 matching errors — nothing
widened unnecessarily, nothing required left out.

Three dependency-free guards in `de.gxp.architecture` replace the compiler:
- **A** `FeatureInternalsImportTest` — source scan; nothing outside
  `de.gxp.<f>` may reference that feature's `.entity.`, `.control.` or
  `.boundary.` types, except the feature's own `*Control`. Plus a
  no-wildcard-imports assertion and a layout-present assertion.
- **B** `EntityExposureTest` — reflection scan; no public field/method/ctor
  outside `.entity` may expose an entity type, including generic arguments,
  array components, and (since the fix round) supertypes and interfaces.
- **C** `ControlPackageVisibilityTest` — only `*Control` may be public in
  `.control`, only `*Resource` in `.boundary`.

`FeaturePackages` derives the feature list from disk, excluding `api`, `web`,
`devseed`, `testsupport`, and re-checks its own exemptions against disk every
run so a stale exemption fails loudly.

**The reviewer found a real slip-through that both `dev` and `architect` had
missed:** `.boundary` was in no guard's scope, so a `public class StayView
extends Stay` there, referenced from another feature's Control, laundered an
entity's public fields past all three guards. Closed in the fix round — the
same probe now fails three guards independently.

### ⚠️ Incident 2026-08-25 — `MenuControl.java` was clobbered and reconstructed

Reverting a probe, `dev` ran `git checkout` on
`backend/app/src/main/java/de/gxp/menu/control/MenuControl.java`. **Every moved
file is in staged-rename (`RM`) state, and the index content of such a file is
still the pre-refactor original** — so the checkout silently discarded all
uncommitted Block A/B/C edits to it. Not stashed, not a dangling blob.

Recovered against `target/quarkus-app/.../MenuControl.class` from the last
pre-mutation build: `javap -p -c -v` of the rebuilt class is **diff-identical
to that oracle, zero lines**, including annotations and the full
`LineNumberTable`. So code, annotations, signatures and every source line
number match. What the oracle cannot prove is **comment wording** — exactly one
javadoc line was rewritten by hand (the `REQ-417` guest-read block, which lost
its reference to the now-removed "`@PermitAll`-on-internal precedent").
Independently checked afterwards: the file carries no stale annotation
reference, the phrase appears nowhere else in `src/main`, it compiles, and the
suite is green. One cosmetic residue: that line is 97 columns where the
surrounding block wraps at 66–78.

**Standing hazard until the user commits:** all **69** moved files are in the
same `RM` state. `git checkout <file>` on any of them destroys uncommitted
work the same way. Committing the staged rename index removes the hazard.

### ⚠️ Commit strategy — two commits, not one

The reviewer verified: all 69 renames sit in the index as **R100**, but the
content edits are unstaged. Committing both together (`git add -A && git
commit`) drops **8 of 69** below git's default 50 % rename score, and
`git log --follow` loses them — the short files whose javadoc roughly doubled:
`asset/Asset.java`, `asset/AssetMetaRow.java`, `auth/AdminCredential.java`,
the three `menu/*Translation.java`, `system/InstanceInfo.java`,
`system/InstanceInfoMapper.java`.

**Commit the already-staged index first** (a pure rename commit), **then** a
second commit for the content edits. Costs nothing, preserves `--follow` for
all 69.

### ⏸ Open with the owner — wording only, no code blocked

Six items, all in files only the orchestrator edits. Three are corrections to
text the owner already approved, because measurement contradicted it:
`"fails closed at build time"` (the build stays green; the deny is at
runtime), `"(and their fields)"` (58 of 97, not all), and `"two dependency-free
architecture tests"` (there are three, now also covering `.boundary`). Three
are `backend/CLAUDE.md`: drop the stale "rate limiters" example, name the third
guard, and add the rule learned from Block B's hole — **class-level is allowed
only for `@RolesAllowed`; `@PermitAll` must sit on the method**, because a
class-level `@PermitAll` is inherited by any method added later and makes a
forgotten endpoint anonymously reachable with every guard green.

The reviewer's judgement on the constitution's *"held by … architecture tests,
not by the compiler"*: that claim was **stronger than the artefact** while
`.boundary` was unguarded. With the fix round in, it is defensible for every
violation the reviewer could construct. Its recommendation — taken — was to
fix the guard rather than soften the sentence.

## Owner decision REVERSAL — 2026-08-24, later the same day

**D3 is superseded: all throttling is removed from the backend.** After the
Block A review showed the concurrency gate did not deliver its "never denies a
correct password" promise (a sustained 5-connection flood keeps the manager out
with ~99.98 % probability per attempt, ~70 min expected time to get in), the
owner chose **not** to build a better mechanism:

> *"Ich denke wir sollten für Punkt 1 erstmal gar nichts machen. Ich denke wir
> sollten die Sicherheitsmechanismen soweit zurückbauen, dass Bruteforce
> möglich ist und es keine Rate Limits gibt. Ich weiß bisher noch gar nicht,
> auf welcher Maschine in welcher Form das Ganze produktiv eingesetzt wird.
> Über dieses Sicherheitsfeature können wir uns wann anders Gedanken machen."*

Removed: `LoginConcurrencyGate`, `PairingConcurrencyGate`,
`ApiErrors.tooManyRequests`, the `429` responses on manager login and
pairing-request creation, and every test asserting shedding/pacing.
**No lockout, no rate limit, no gate anywhere.**

**Kept, deliberately** (the boundary drawn, and confirmed by the owner's
silence on it after it was stated explicitly): the `AdminPasswordSeeder` ≥ 8
character policy — a password policy is not a rate limit, and it is now the
only thing carrying brute-force resistance (worth ~10⁸ against a random
password, where the throttle was worth ~10). Also kept:
`BoundarySecuritySemanticsTest` and its fixtures, which are Block B's
foundation and unrelated to throttling.

**Recorded consequence, accepted by the owner:** an unauthenticated caller on
the LAN can spend the server's CPU at will through PBKDF2 (~2.2 s per request
on the Pi, ~0.5 s on x86), and that is the same worker pool serving the
kiosks — so sustained abuse degrades the whole instance, not just the manager
login. This is written into REQ-326 so nobody "fixes" it later without knowing
it was deliberate. **Revisit once the production deployment target is known.**

Also withdrawn as moot: `dev`'s proposed `backend/CLAUDE.md` entry *"Throttling
paces, never denies"* — there is no throttling left for it to govern. The
`backend/CLAUDE.md` Tests bullet WAS reworded (owner-approved 2026-08-24): the
`DeviceFixtures` convention now stands on "seeding a device is a fixture
concern, and driving the real flow would couple unrelated suites to pairing's
contract" instead of on a pairing rate limit that no longer exists.

The Block A review's other findings still stand and are being worked:
🟠 #3 (the proof test's claim 2 was not actually proven — the fixture
implemented no interface, so it could not distinguish "impl wins" from "impl is
the only candidate"; **this gates Block B**) and 🟠 #2 (the
`device-pairing-and-guest-api` PRD chain never got its amendment — REQ-409's
acceptance criterion asserts a rate limit that is now doubly gone).

## Owner decisions — 2026-08-24, ANSWERED

- **D1 — sub-packages: APPROVED** in the Option A layout (Repository stays
  package-private in `.control`; Mapper goes to `.entity`) — explicitly *not*
  the owner's original sketch. ~97 public entity fields accepted as the price.
  Two dependency-free architecture tests replace the compiler. **No ArchUnit.**
- **D2 — security annotations on the Boundary: APPROVED**, together with
  `quarkus.security.jaxrs.deny-unannotated-endpoints=true`. The two are a
  package: the move alone would make `deny-unannotated-members` inert on
  annotation-free Controls and leave the backend *less* safe than before.
- **D3 — lockout: APPROVED** as replace-don't-delete
  (`LoginConcurrencyGate`), plus the ≥ 8 policy on the first-start seed and
  the same pace-don't-deny treatment for `PairingRateLimiter`.
- **D4 — device-token rotation: PARKED.** Owner: *"D4 bitte erstmal gar nichts
  machen."* No feature created, no PRD, no constitution change — the
  "long-lived opaque device token" wording stands. The full analysis above is
  preserved so this can be picked up without re-deriving it. **Revisit before
  the first guest screen that carries a door code, key-box code, alarm code or
  WiFi password** — that is the release where the exposure stops being
  theoretical. Also still true and unaddressed: manager revoke has an API but
  no UI, and it is the only control that evicts an active attacker.
- **Semver: v1.6.0**, per the architect's recommendation.

### Governed docs written 2026-08-24 (wording approved literally, then applied)

| Doc | Change |
|---|---|
| `docs/constitution.md` | version → **1.6.0** / last amended 2026-08-24; new v1.6.0 amendment note; "Backend:" constraint rewritten (sub-packages + the architecture-test guarantee + ArchUnit rejection); "Auth & transport" authorization sentence rewritten (Boundary + `deny-unannotated-endpoints`) |
| `backend/CLAUDE.md` | "BCE — binding rules" bullets 1–3 rewritten (three sub-packages + the excluded non-feature packages; Control as the only cross-feature type; Boundary carries exactly one security annotation, on the **implementing** method); "Security & logging" fail-closed bullet rewritten (both flags + the three tests) |
| `docs/backend/specification.md` | §4 layout block replaced (`de.gxp.room/boundary|control|entity`) + the 2026-08-24 rationale paragraph; §4 bullet 3 rewritten; §6 lost the words "and failed-attempt lockout" |
| `docs/features/backend-domain-config/01-prd.md` | REQ-326 rewritten to the concurrency gate (**already done — `dev` must not touch it**) |

### Superseded decisions (do not re-litigate)

- ~~D4 wording for the constitution's "long-lived opaque device token"~~ — not
  applied, D4 is parked.

## Environment note (cost prior sessions real time)

`docker` is in `/etc/group` for this user, but the running processes'
supplementary groups predate the `usermod`, so Docker is **not** reachable
directly and Dev Services cannot start Postgres. **Verified workaround —
prefix every build/test command:**

```
sg docker -c "cd <repo>/backend/app && ./mvnw verify"
```

(`sg docker -c "docker info"` → Docker 28.5.1.) A full OS re-login would also
fix it; restarting only the Claude session does **not**. This box also needs a
quiet window for the suite and may need
`-Dquarkus.datasource.jdbc.acquisition-timeout=60`; distinguish
timeout/boot failures (environment) from assertion failures (real) before
concluding anything.

## Superseded: the original open-decision list

- **D1** — adopt sub-packages in the Option A layout, accept ~97 public entity
  fields, two architecture tests, no ArchUnit?
- **D2** — move security annotations to the Boundary **together with**
  `deny-unannotated-endpoints=true`? (Approving the move without the flag
  would make the backend less safe than today.)
- **D3** — replace the lockout with `LoginConcurrencyGate` (not plain
  removal)? Plus: enforce ≥ 8 on the seeded `GXP_ADMIN_PASSWORD`?
- **D4** — rotation as its own later feature, fallback dropped?
- **Semver:** architect recommends **v1.6.0** (v1.4.0 precedent: "a
  major-feeling minor"); notes a real case for **v2.0.0** since v1.0.0's own
  rationale for a major was *"enforcement itself changed"* — and the
  fail-closed mechanism is genuinely being swapped. Owner's call.

Exact proposed replacement wording for the constitution "Backend:" bullet,
the "Auth & transport" authorization sentence, the v1.6.0 amendment note,
`backend/CLAUDE.md` → "BCE — binding rules" (3 bullets) and → "Security &
logging" (1 bullet), `docs/backend/specification.md` §4 (layout block +
bullet 3), and the REQ-326 replacement sentence — all drafted by `architect`
in its report. **Must be confirmed literally before anything is written**
(constitution → Decisions Requiring Your Approval; `lead` rule 12).

## Proposed implementation order (each step independently `./mvnw verify` green)

| # | Step | Gate |
|---|---|---|
| 0 | **Prove the Quarkus semantics first** — throwaway `@QuarkusTest`: (i) `@PermitAll` on a Boundary whose Control has no annotation → 200; (ii) with `deny-unannotated-endpoints=true`, an unannotated Boundary over a `@PermitAll` Control → 401. Bytecode-verified, **not runtime-verified** (no Docker access). Do not start step 3 until green | — |
| 1 | `LoginConcurrencyGate` replaces `LoginRateLimiter`; `changePassword` behind it; `LoginLockoutTest` → `LoginConcurrencyGateTest`; drop `gxp.auth.lockout-seconds` | D3 |
| 2 | Seeder password policy; same pace-don't-deny treatment for `PairingRateLimiter`; doc updates | D3 |
| 3 | Flip `deny-unannotated-endpoints=true` **and** add Boundary annotations **while leaving them on the Controls** — one commit, both models active, all tests green. Proves the Boundary annotations are effective with no unenforced window | D2 |
| 4 | Remove Control annotations (incl. all `@PermitAll` warts); `SecurityAnnotationAuditTest` → `*Resource` sweep; add generated-interface guard + contract-driven anonymous-401 test; fix `FailClosedResource`/`FailClosedControl` | D2 |
| 5 | Fold `GuestControl.writePreferences`' duplicated offered-set validation back into `StayControl.validate()` | D2 |
| 6 | Migrate `de.gxp.system`; land both architecture tests scoped to a migrated-package list | D1 |
| 7 | `text`, `asset`, `property`, `room` | D1 |
| 8 | `menu`, `stay` | D1 |
| 9 | `device`, `auth` (+ the six package-private-touching tests) | D1 |
| 10 | `guest`; widen the architecture tests to all feature packages; delete the migrated list | D1 |
| 11 | Docs: constitution v1.6.0, `backend/CLAUDE.md`, spec §4/§6, `docs/system-context.md`, `backend-domain-config` PRD/design/todo/analyze amendments, `docs/features/index.md` | D1+D2+D3 |
| 12 | `device-token-rotation` enters the pipeline at `01_prd` | D4 |

## Docs to touch for 3.1 (architect's list)

`backend-domain-config/01-prd.md` (REQ-326, **amend, don't delete** —
principle 7), `02-design.md` (lines 32, 50, 85, 91, 263, 314), `03-todo.md`
(T-10, lines 95–100), `04-analyze.md` (line 39), `06-review.md` + `handoff.md`
(**append a dated note, do not rewrite** — historical records),
`docs/system-context.md` (line 185, `de.gxp.auth` row),
`docs/backend/specification.md` §6 (drop "failed-attempt lockout"),
`application.properties`. Verified: `backend/CLAUDE.md` and
`docs/constitution.md` do **not** mention the lockout.

## Next

Owner answers D1–D4 → `lead` presents the exact wording for literal approval
→ `lead` writes the governed docs → `dev` works the table above step by step
→ `reviewer` after each block. Branch: currently `feature/backend`; a
dedicated branch for this refactor is advisable before step 1.
