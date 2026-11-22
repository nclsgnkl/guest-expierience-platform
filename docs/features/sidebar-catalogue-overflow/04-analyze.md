# Analyze: Sidebar & Catalogue Overflow
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Result:** PASS

Run 2026-08-27 against `docs/constitution.md` **v1.8.3**, `frontend/CLAUDE.md`,
`backend/CLAUDE.md`, `docs/backend/specification.md` §3 decision 5 / §5, and the
working tree at `refactor/bce-subpackages-and-boundary-security`.

> **What `Status: ✅` means in the table below:** *a task exists that covers this
> requirement*. It does **not** mean the requirement works. The 2026-08-18
> lesson is pinned in this repo's own gate instructions: three shipped defects
> once violated `REQ-ID`s already marked ✅ in a matrix exactly like this one.
> First round for this feature, so there is no defect table.

---

## Coverage — every `REQ-ID` maps to ≥ 1 task

| REQ-ID | Design section | TODO task(s) | Status |
|---|---|---|---|
| REQ-901 | §2, §5 | T-01, T-08 | ✅ |
| REQ-902 | §2 | T-01, T-20 | ✅ |
| REQ-903 | §3.1, §3.5 | T-02, T-03, T-04, T-21 | ✅ |
| REQ-904 | §3.5 | T-07 | ✅ |
| REQ-905 | §3.1 | T-02 | ✅ |
| REQ-906 | §3.4 | T-05 | ✅ |
| REQ-907 | §3.1 (data), §9 (spec case) | T-02, T-13 | ✅ |
| REQ-908 | §3.1, §3.2, §3.3 | T-02, T-03, T-04 | ✅ |
| REQ-909 | §3.2 | T-02, T-04 | ✅ |
| REQ-910 | §3.1 | T-02, T-04 | ✅ |
| REQ-911 | §9 | T-06, T-20 | ✅ |
| REQ-912 | §4.1, §4.2 | T-11, T-12 | ✅ |
| REQ-913 | §4.2 | T-12, T-19 | ✅ |
| REQ-914 | §4.1 | T-11 | ✅ |
| REQ-915 | §6.1, §6.2 | T-13, T-14 | ✅ |
| REQ-916 | §7.2 | T-15 | ✅ |
| REQ-917 | §7.2, §7.4 | T-15, T-16 | ✅ |
| REQ-918 | §7.4 | T-16, T-19 | ✅ |
| REQ-919 | §6.1, §7.4 | T-13, T-16 | ✅ |
| REQ-920 | §6.1 | T-13, T-14 | ✅ |
| REQ-921 | §6.2, §7.1 | T-14, T-17 | ✅ |
| REQ-922 | §7.2, §7.3 | T-15 | ✅ |
| REQ-923 | §6.1, §6.2 | T-13, T-14, T-17 | ✅ |
| REQ-924 | §6.1 | T-13 | ✅ |
| REQ-925 | §5, §6.1 | T-09, T-13 | ✅ |
| REQ-926 | §5, §6.1 | T-09, T-13 | ✅ |
| REQ-927 | §6.1 | T-13 | ✅ |
| REQ-928 | §6.1 | T-13 | ✅ |
| REQ-929 | §6.2 | T-14, T-17 | ✅ |
| REQ-930 | §6.1 | T-13 | ✅ |
| REQ-931 | §6.1 | T-13, T-17 | ✅ |
| REQ-932 | §6.1 | T-13, T-14 | ✅ |
| REQ-933 | §5, §7.1 | T-08, T-15 | ✅ |
| REQ-934 | §8 | T-10, T-15, T-17 | ✅ |
| REQ-935 | §9 | T-18, T-19 | ✅ |

**35 of 35 requirements covered. No gaps.**

## No orphans — every task references an existing `REQ-ID`

All 21 tasks (T-01…T-21) carry at least one `REQ-9xx` from this PRD's block, and
every ID they name exists in it. Machine-checked: the set of IDs appearing in
`03-todo.md` is exactly `{REQ-901…REQ-935}` plus one cross-reference to Part C's
**REQ-962** (T-08, "leave the `sortIndex` re-sort alone — that is Part C's
REQ-962"), which is a scope *exclusion*, not a claim of coverage. That is the
only foreign ID and it is correct.

## Design fidelity

Checked requirement by requirement; the four that are easy to satisfy on paper
and hard to satisfy in fact:

| Check | Verdict |
|---|---|
| **REQ-913 (cold/offline deep links)** — the classic trap of any route-resolution design | **Satisfied structurally, not by a code path.** D4's route table is a static constant, so it exists before the first navigation; `shellGuard` already diverts to `/connection` when no content is held, so `SectionPlaceholderComponent` never runs against a throwing `property()`. There is nothing to get wrong at runtime. Re-checked live in T-19. |
| **REQ-929 (promotion never persisted)** | **Satisfied by construction.** `chosen` is a `protected` component signal in a component that injects no gateway; the shell (and with it the sidebar) is destroyed on the `/standby`, `/pairing` and `/connection` flips because those are top-level routes. There is no code path that could write it out. T-17 asserts the negative anyway. |
| **REQ-931 (no overflow entry in the DOM at ≤ n)** | **Satisfied.** `splitMenu`'s early return plus `@if (split().hasOverflow)`. Not `display: none` — the assertion is about the DOM and the design meets it literally. |
| **REQ-917 "no capacity ceiling"** vs. the contract's `maxItems: 50` | **Not a contradiction.** `maxItems` is an envelope-sanity bound at the wire boundary; the *panel* contains no count, no `slice` and no "and N more". Design §7.4 states it and §2 states why 50 is not a design limit. The two live in different layers on purpose. |
| **REQ-922's named gap** (`CdkMenu` is 1-D, the grid is 2-D) | **Resolved in §7.3, and resolved with evidence** — the installed CDK source was read (`orientation = 'vertical'`, `FocusKeyManager … withVerticalOrientation()`, Left/Right inert). The resolution is the ARIA contract (`role="menu"` ⇒ linear), reinforced by REQ-919's row-major fill. **Nothing beyond CSS and existing public APIs is required.** |
| **REQ-932's two permitted answers** | The design **picks one and states it** (promote on any navigation), as the requirement demands, and derives the corollary — a popover tile can never be active, so the panel needs no active variant. "Nothing looks active" is unreachable. |
| **REQ-903's disposability** (new, from the 2026-08-27 reframing) | **Satisfied and mechanically checkable.** No fixture key appears in any design decision, component, style or test; T-21 turns that into a grep. |
| **PRD ↔ Design contradictions** | **None found.** The one place they could disagree — REQ-918's "decided by measurement" versus §7.4's choice of 5 — is handled honestly: measurement admits both, so the design states the tie-break as a design choice rather than dressing a preference as a measurement. |

## Constitution compliance (v1.8.3)

| Clause | Verdict |
|---|---|
| **No new dependency** | ✅ `@angular/cdk/menu` is already approved and already used (`StatusBarMenuComponent`). T-20 verifies `pom.xml` and `package.json` are untouched. |
| **No new architectural pattern / cross-cutting convention** | ✅ Re-checked at design time (§4.1, §6.1, §7.1): a parameterised route and a wildcard are ordinary Router config; `menu-split.ts` is exported pure functions (the `resolveProperty` precedent); the popover lives in an existing component; `MenuFixtures` sits in `de.gxp.infrastructure.testsupport`, which `backend/CLAUDE.md` → Tests already names. **No nested `CLAUDE.md` entry and no constitution amendment is proposed.** |
| **Nothing scrolls** (document scope, v1.8.0) | ✅ The panel is a bounded sub-component in the CDK overlay container (`position: fixed`), so it cannot grow the document; it bounds itself with `max-height` + `overflow-y: auto`. No `overflow: hidden` at screen level anywhere. Verified by T-19, panel open **and** closed, 14 × 2 × 2 × 3. |
| **Semantic tokens only, `rem`, no breakpoints** | ✅ Every value in §7.4 and the screen spec is a token or derived from tokens (the `sidebar-item` precedent). Column count and slot count are fixed on every viewport — §7.4 and the screen spec both say why a measured slot count would be a second layout. |
| **Append-only audit trail** (v1.8.1) | ✅ No `@Transactional public` Control method is added. T-06 verifies `AuditCoverageTest` stays green with **its assertions and its `NOT_A_MANAGER_MUTATION` exemption list untouched**, and treats a real edit as a stop signal. (T-03 adds an `@AfterEach` to it for database hygiene — §3.3 — which changes nothing it asserts.) |
| **Guest-sensitive data never logged; cache holds none** | ✅ Nothing new is logged; the cache's contents are unchanged by this feature (catalogue labels, icons and routes only). D12's pressure is Part C's, and this feature does not touch the write path. |
| **Automatic update channel** (v1.8.1) | ✅ This is the clause that made D5 necessary, and D5 answers it: a migration delivered by the update channel changes nothing a guest sees on a running property. T-04 asserts it directly rather than inferring it. |
| **No external runtime requests** | ✅ Icons are bundled (T-07 registers them into the same map `provideIcons()` consumes); no fetch is added. |
| **Contract-first, generated client committed** | ✅ T-01 and T-20: two `verify` runs, clean `git status` under `packages/api-client/`. |
| **Room-level scoping** (principle 4) | ✅ Resolution is untouched (REQ-909); the ceiling rule keeps its only implementation, and T-04 re-asserts it for the new keys. |
| **`docs/backend/specification.md` §3 decision 5** | ✅ `menu_item_definition` stays release data, seeded by Flyway, not writable through any API. The owner confirmed the decision stands on 2026-08-27. |
| **Branch discipline** | ✅ Stays on `refactor/bce-subpackages-and-boundary-security` at the owner's explicit instruction; no branch is created or switched. |
| **Design-tool requests are metered** | ✅ None spent. The panel is designed in-repo per D6 (`docs/design/screens-inhouse/menu-overflow.md`), on the precedent of `/pairing`, `/standby` and `/connection`. |

## No unresolved markers

A grep for the clarification marker over `01-prd.md`, `02-design.md`, `03-todo.md` and
`docs/design/screens-inhouse/menu-overflow.md`: **0 hits.** Every one of D1–D6 is
settled with a date and the owner's reasoning; the design's "Open decisions"
section reads *None*.

## Findings that are not gaps — carried into implementation

Recorded so `05_implement` and `06_review` see them as known rather than
discovering them:

1. **`PUT /api/manager/menu-config` is a whole-set replace**, so D5's opt-out
   rows are deletable by a partial submit (design §3.3). Accepted, documented,
   and pinned by `CatalogueExpansionTest.partialConfigReplaceReEnablesTheFixtures`.
   The structural alternative is D5's rejected one.
2. **The same fact is a test-isolation hazard, across five classes.**
   `MenuCatalogueAndConfigTest`, `MenuDisableGuardTest`, `ActionCardTest`,
   `GuestHomeScreenTest` and `AuditCoverageTest` all replace the property-scope
   config — several with a deliberately partial body and a `finally` that clears
   it entirely — so each deletes the eight opt-out rows and would make
   `GuestSessionTest`'s `menu.items hasSize(5)`/`hasSize(4)` depend on class
   order. T-03 fixes it with an `@AfterEach` fixture in all five plus a
   `@BeforeEach` in the asserting class, and validates by running the suite in
   reversed class order. *(This was found by reading the tests during the gate,
   not during design — the first pass named only one of the five.)*
3. **Exactly two existing tests are invalidated, both by the row count:**
   `MenuCatalogueAndConfigTest.catalogueEqualsTheFiveFrontendTuples` and
   `FirstRunSeedTest` step 3 (which asserts `hasSize(5)` **and** the exact
   five-key list) — `hasSize(5)` → `hasSize(13)` in both. **No guest-facing
   assertion changes anywhere**, which is itself the evidence that D5 is the
   low-risk choice: the migration is invisible to every test that looks at what
   a guest receives.
4. **`V10` is contested by a deferred plan** (`instance-identity-and-certs`,
   never started, explicitly "to be re-validated when picked up"). Part A takes
   `V10`; that plan renumbers.
5. **A pre-existing `%dev` database keeps the fixtures disabled** until the Dev
   Services volume is dropped, because the seeder's early return is deliberately
   respected (design §3.4).
6. **Recorded follow-up, out of scope:** `StalenessPillComponent` and
   `StatusBarComponent` still resolve the locale inline and could move onto the
   new `PropertyContentService.locale` at their next touch.

## Result

**PASS** — proceed to `05_implement` (the `dev` subagent), starting at T-01.

Two conditions travel with the hand-off, both from the owner:

- **Stay on `refactor/bce-subpackages-and-boundary-security`.** No branch, no
  commit until T-21; the user commits.
- **Stop and report rather than widen.** If any task appears to need a
  `GuestModule` union or a module registry, that is Part C being pulled forward
  and this design says it is not needed. If any task appears to need a new
  dependency, a nested `CLAUDE.md` entry or a constitution amendment, propose the
  exact wording and end the turn.
