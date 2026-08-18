# Analyze: Home Screen Prototype
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Result:** **PASS** — cross-artifact consistency is clean for REQ-001…REQ-025.
Two approval gates are **open by design** (raised, not decided by an agent) and
block exactly one task, R7; see "Approval gates" below. Nothing is blocked on a
missing or contradictory artifact.

> **Re-run 2026-08-18 — third pass (round 3, visual-defect correction).** The
> feature was implemented, reviewed PASS, then run and looked at; six visual
> defects were found and a seventh while verifying them. `01-prd.md`,
> `02-design.md` and `03-todo.md` were amended (REQ-022…REQ-025 added; REQ-014
> and REQ-016 amended in place; acceptance criteria strengthened on REQ-007,
> REQ-009, REQ-010, REQ-011), and `docs/design/tokens.md` and
> `docs/design/screens/home.md` were corrected. This gate was re-run against
> the amended artifacts. The second-pass matrix below is kept and extended
> rather than replaced.
>
> Superseded by round 3 in the matrix below: REQ-014's and REQ-016's rows.

> Re-run 2026-08-18 after the user reviewed the first PASS and answered the
> flagged open questions — several answers changed the design (shared
> app-shell placeholders, config-driven sidebar, card→section routing,
> reserved widget-column space), so PRD/Design/TODO were amended (see each
> doc's inline "Revised 2026-08-18" notes) before this gate was re-run
> against the amended artifacts. Superseded: the 2026-08-17 PASS result below
> this revision no longer reflects what will be built.

## Coverage matrix

| REQ-ID | Design section | TODO task(s) | Status |
|--------|----------------|--------------|--------|
| REQ-001 | Architecture decision → Project location | Setup: scaffold project; verify scaffold runs | ✅ |
| REQ-002 | Module layout (`assets/fonts/`) | Setup: self-host Plus Jakarta Sans | ✅ |
| REQ-003 | Module layout (`tokens.css`); Runtime branding hook | Token layer: create `tokens.css`; wire `data-theme` | ✅ |
| REQ-004 | Mesh gradient background (now painted at `ShellComponent`'s root, REQ-020) | Token layer: implement 16-layer mesh gradient on `shell.component.css`; Shell & sidebar: build `ShellComponent` | ✅ |
| REQ-005 | Content service contract | Content service: create `PropertyContentService` | ✅ |
| REQ-006 | Content service contract (`PropertyConfig`) | Content service: create model + `example-property.data.ts` | ✅ |
| REQ-007 | Sidebar config & the Settings decision; Module layout (`SidebarComponent`) | Shell & sidebar: build `SidebarComponent`; build `ShellComponent`; dependency task (`@ng-icons`) | ✅ |
| REQ-008 | Module layout (`HeadingBlockComponent`) | Home content: `HeadingBlockComponent`; compose `HomeContentComponent` | ✅ |
| REQ-009 | Module layout (`ActionCardComponent`); Card → section resolution | Home content: `ActionCardComponent`; compose `HomeContentComponent`; dependency task (`@ng-icons`) | ✅ |
| REQ-010 | Module layout (`HeroArtworkComponent`); Mesh gradient background (background moved out of it) | Home content: `HeroArtworkComponent` | ✅ |
| REQ-011 | Module layout (logo asset); Content service (`logoAsset`) | Shell & sidebar: logo slot in `SidebarComponent` | ✅ |
| REQ-012 | Routing & the shared shell | Routing & section placeholders: `SectionPlaceholderComponent`; wire `app.routes.ts` | ✅ |
| REQ-013 | Interaction states | Shell & sidebar: focus-visible/active states | ✅ |
| REQ-014 | Home content layout & the widget-column reservation | Home content: compose `HomeContentComponent` (reserved `flex:1` widget column) | ✅ |
| REQ-015 | Test strategy | Tests section (11 tasks, one per behavior) | ✅ |
| REQ-016 | Viewport guard rail | Routing & section placeholders: `min-width` guard rail | ✅ |
| REQ-017 | Sidebar config & the Settings decision; Content service contract (`menuItems`) | Content service: model + example data; Shell & sidebar: `SidebarComponent` | ✅ |
| REQ-018 | Sidebar config & the Settings decision; Content service contract (`settings`) | Content service: model + example data; Shell & sidebar: `SidebarComponent` | ✅ |
| REQ-019 | Content service contract (`targetSectionId`, `getSectionRoute()`); Card → section resolution | Content service: model, example data, `PropertyContentService`; Home content: `ActionCardComponent` | ✅ |
| REQ-020 | Routing & the shared shell; Module layout (`ShellComponent`) | Shell & sidebar: build `ShellComponent`; Routing: wire `app.routes.ts` | ✅ |
| REQ-021 | Routing & the shared shell (`SectionPlaceholderComponent`) | Routing & section placeholders: build `SectionPlaceholderComponent` | ✅ |
| REQ-022 | Round 3 → The host element is the layout; Full-height sidebar and group distribution | R1 (`:host` convention), R2 (full-height sidebar) | ✅ |
| REQ-023 | Round 3 → Gradient-filled icon glyph | R6 (three tasks: tokens, `<defs>` + stops, global rule + template class) | ✅ |
| REQ-024 | Round 3 → No scrollbars (REQ-024) and responsive scaling (REQ-025) | R7 (⛔ blocked on approval), R8 (verification) | ✅ |
| REQ-025 | Round 3 → Responsive scaling (REQ-025) — approval-gated | R7 (⛔ blocked on approval), R8 (verification) | ✅ |

**Round-3 re-coverage of amended requirements** (these already had rows above;
the round-3 tasks that now also serve them):

| REQ-ID | What changed | Round-3 task(s) |
|--------|--------------|-----------------|
| REQ-007 | Acceptance strengthened: sidebar labels must not wrap | R2 (item width makes the label fit), R5 (`nowrap`) |
| REQ-009 | Acceptance strengthened: card titles must not wrap; card text baselines must align | R5 |
| REQ-010 | Acceptance strengthened: the empty hero component must occupy no layout space at all | R1 (`:host { display: none }`) |
| REQ-011 | Acceptance strengthened: the logo asset must actually decode | R4 (asset fix + parse test) |
| REQ-014 | **Amended:** full-height content column, heading top-anchored, cards bottom-anchored, no spurious 32px offset | R1 (D7 offset), R3 (vertical composition) |
| REQ-016 | **Superseded by REQ-025**, guard rail moves 1280px → 1024px | R7 |

## Checks

- [x] **Coverage** — every REQ-ID (REQ-001…REQ-021) maps to ≥1 TODO task, see
      matrix above. REQ-017/018/019/020/021 are new since the 2026-08-17
      PASS; REQ-012/014 were substantially rewritten; all are covered by
      tasks added or amended in this revision of `03-todo.md`.
- [x] **No orphans** — every TODO task carries a `_REQ-ID_` tag that exists
      in the PRD's requirements table. The renamed/added tasks (Shell &
      sidebar section, Home content section, `SectionPlaceholderComponent`)
      all carry REQ-IDs from the amended PRD; no task references a
      superseded ID (`nav-items.ts` and the old `PlaceholderComponent`/
      per-card-route tasks were removed, not left dangling with stale tags).
      Cleanup tasks remain process hygiene, intentionally without a REQ-ID,
      consistent with the template's own "Cleanup" section.
- [x] **Design fidelity** — `02-design.md` addresses every requirement
      explicitly, including all five new ones:
      - REQ-017/REQ-018 → "Sidebar config & the Settings decision" (full
        rationale for the membership/content split) and the
        `MenuItem`/`PropertyConfig.menuItems`/`.settings` shapes in
        "Content service contract"
      - REQ-019 → `ActionCard.targetSectionId` in "Content service
        contract" plus `getSectionRoute()`'s resolve-or-throw behaviour,
        and the confirmed mapping table under "Card → section resolution"
      - REQ-020/REQ-021 → "Routing & the shared shell" (`ShellComponent`,
        `SectionPlaceholderComponent`, the child-route table)
      - REQ-014 → "Home content layout & the widget-column reservation"
        (new subsection, resolves the "reserve, don't remove" requirement
        with a concrete `flex: 1` implementation)
      No contradictions found between PRD and Design: every "revised
      2026-08-18" note in the PRD has a matching, consistent elaboration in
      the Design doc (same component names, same field names, same routes,
      same Settings rationale). Checked specifically for the two places a
      contradiction was most likely — (a) PRD's Assumptions says Home is
      included in the config-driven `menuItems` array (5 entries) matching
      Design's `PropertyConfig.menuItems` comment ("exactly 5, in order:
      Home, Info, Umgebung, Smart Home, Services"); (b) PRD REQ-014's
      acceptance criterion ("empty `flex: 1` widget-column element exists")
      matches Design's `.home-content__widgets { flex: 1; }` and its note
      that this is scoped to Home only, not section placeholders — both
      consistent.
- [x] **Constitution compliance** — reviewed against every principle and
      constraint in `docs/constitution.md`, focused on what changed:
      - **Principle 1** (property data, not code): strengthened by this
        revision, not weakened — `menuItems` and `settings` move from a
        code constant to `PropertyContentService`-sourced config
        (REQ-017/REQ-018), closing the one place the original design had
        carved out an exception. The single remaining code-side fact is
        Settings' *position* (always last, own group) — reviewed and judged
        structural, not content (Figma itself draws it as a separate group;
        principle 1 covers "content and branding," not component
        composition/layout structure), consistent with how principle 1 is
        already applied to the fixed 4-card / 5-item counts elsewhere in
        this doc.
      - **Principle 2** (content via service, never imported): still
        enforced structurally — `SidebarComponent` and
        `SectionPlaceholderComponent` receive their data via
        `PropertyContentService` (through `ShellComponent`'s injection or
        their own `inject()`, per Design), never a direct import; the new
        `getSectionRoute()` method is itself a service-level function,
        keeping the "must resolve to a real id" logic out of components.
      - **Principle 3** (no sensitive data logged): not applicable — still
        no guest-sensitive data in this feature.
      - **Principle 4** (self-hosted, no internet dependency): unaffected by
        this revision — no new dependency, no external asset.
      - **Principle 5** (semantic tokens only): unaffected — `ShellComponent`
        and `SectionPlaceholderComponent` are specified to use existing
        semantic tokens only (Design's Module layout / Interaction states),
        no raw values introduced.
      - **Principle 6** (tests accompany code): REQ-015 amended to name the
        new/changed behaviors explicitly; TODO's Tests section grew from 9
        to 11 tasks, one per behavior including the two new components and
        the `getSectionRoute()` resolution/throw case.
      - **Principle 7** (traceability): this matrix; every amended/added
        REQ-ID is traced end-to-end PRD → Design → TODO.
      - **Constraints**: no new dependency, datastore/infra choice, or
        token-mechanism change introduced by this revision — the shared
        shell is Angular Router's ordinary parent/child-route feature
        (already covered by the pre-approved "Angular Router" constraint),
        not a new architectural pattern; PRD's Technical constraints section
        says this explicitly. No dependency-manifest edit beyond the three
        already-approved packages. Branch discipline unaffected
        (`feature/home-screen-prototype`). No external runtime requests
        introduced.
      - **Decisions Requiring Your Approval**: reviewed line by line — no
        new dependency, no datastore/infra, no metered design-tool request,
        no token-mechanism change, no new architectural pattern, no nested
        `CLAUDE.md` entry written. The one decision explicitly delegated by
        the user (Settings hardcode vs. config) is confirmed **not** on this
        list per the user's own instruction and this agent's independent
        check against the list's five bullet points — it is an ordinary
        config-modeling call within the already-approved content-service
        architecture, decided and documented in `02-design.md` rather than
        escalated.
- [x] **No unresolved markers** that block implementation. Every
      `[NEEDS CLARIFICATION: …]` marker still present in `01-prd.md` →
      Risks & open questions is explicitly resolved for this feature with a
      stated recommendation or a recorded user confirmation, and marked
      non-blocking (widget-column *content* scope, background-tint source —
      both now marked "User confirmed 2026-08-18"; the 5 undesigned
      section-placeholder destinations — recommendation to `/design-import`
      each once ready; final logo asset; dark-mode palette; the touchscreen
      font-size hardware-validation note). What remains open in each case is
      a *future* decision, not an ambiguity inside this feature's own scope,
      and none of the TODO's tasks depend on an unresolved answer to reach
      done. The Settings hardcode-vs-config question, which *was* an open
      question at the start of this session, is recorded as **resolved**
      (not a marker) with its rationale in `02-design.md`.
- [x] **No approval-gated decision still unconfirmed** — see the
      constitution-compliance bullet above; nothing on
      `docs/constitution.md` → "Decisions Requiring Your Approval" was
      touched by this revision.

## Round-3 checks

- [x] **Coverage** — REQ-022…REQ-025 each map to ≥1 round-3 task (matrix
      above). Every amended requirement (REQ-007, REQ-009, REQ-010, REQ-011,
      REQ-014, REQ-016) picks up at least one round-3 task that satisfies its
      strengthened acceptance criteria. No requirement is left served only by
      work that has already shipped and demonstrably did not satisfy it.
- [x] **No orphans** — every round-3 task carries a `REQ-ID` except R0
      (baseline install/build/test) and R8's out-of-band-regression re-check,
      both tagged `_no REQ-ID (process)_` explicitly, consistent with how the
      existing Cleanup section is treated.
- [x] **Every defect traces to a requirement** — checked in the other
      direction too, which is the check that would have caught this round's
      real failure. All seven defects in `01-prd.md` → "Defect trace (round 3)"
      map to a requirement: D1→REQ-022, D2→REQ-014, D3→REQ-023, D4→REQ-011,
      D5→REQ-007/REQ-009, D6→REQ-024/REQ-025, D7→REQ-010/REQ-014. Three of them
      (D4, D5, D7) violated requirements that **already existed and were marked
      ✅ in the second-pass matrix above** — coverage of a REQ-ID by a task is
      not evidence the requirement is met, only that someone was told to meet
      it. Noted here because the matrix format invites exactly that
      misreading.
- [x] **Design fidelity** — `02-design.md` → "Round 3" addresses each new
      requirement with a concrete mechanism, not a restatement: REQ-022 (the
      `:host` convention + `align-items: stretch` + derived item size, with the
      space-between arithmetic checked against the reference render), REQ-023
      (four candidate techniques compared, one chosen, with the
      `@ng-icons`-injects-outside-encapsulation constraint that dictates where
      each piece lives), REQ-024 (met by construction, explicitly *not* by
      `overflow: hidden`, with the enforcing measurement named), REQ-025 (the
      mechanism is proposed with alternatives and marked approval-gated rather
      than settled).
- [x] **No contradictions introduced** — checked the four places this revision
      was most likely to create one:
      - PRD Non-goals said "responsive/phone layouts" were out of scope, which
        REQ-025 would have contradicted. Amended in place: phone and portrait
        stay out; a landscape *scaling contract* is in.
      - REQ-016 ("layout is fixed at 1280×832 … min-width guard rail") directly
        contradicted REQ-025. Marked superseded, guard rail value updated, kept
        for traceability rather than deleted.
      - `docs/design/tokens.md` → "Minimum supported width" said `1280px`.
        Revised, with the old value struck through, not silently overwritten.
      - `docs/design/screens/home.md` explicitly *rejected* an active-icon
        fill state, which REQ-023 overrides. The override is recorded there
        with its date and the old rationale intact — the file no longer
        contradicts the PRD in either direction.
      One contradiction is left standing **deliberately and visibly**:
      `tokens.md` still lists `--size-sidebar-item` at 72px while the sidebar
      renders 84px. Both are true (component box vs. instance in the frame) and
      the note explains which governs; collapsing it to one number would lose
      the traceability to Figma.
- [x] **Constitution compliance (v1.1.0)** — re-reviewed for what round 3
      changes:
      - **Principle 1** (property content is data): unaffected. The card-title
        `text-overflow: ellipsis` is the one place round 3 touches
        property-authored content, and it *preserves* the principle (any
        configured title renders without breaking the layout) at the cost of a
        documented authoring constraint. No content moves into code.
      - **Principle 2** (content via service): untouched — no component gains a
        data import.
      - **Principle 3** (no sensitive data logged): not applicable.
      - **Principle 4** (self-hosted, no internet): unaffected. No new
        dependency: the gradient uses inline SVG and CSS, the scaling proposal
        uses only CSS. `playwright` is already in `package.json` and is **not**
        being turned into a shipped or standing test dependency by this round —
        see `02-design.md` → Test strategy, which explicitly defers the
        still-open E2E-framework decision instead of quietly making it.
        (Since 2026-08-18 `docs/constitution.md` v1.3.0 lists `playwright` as an
        approved dependency, scoped to review-time verification; the
        E2E-framework decision it does **not** make is still open.)
      - **Principle 5** (semantic tokens only, never a raw hex or px):
        satisfied and slightly *strengthened*. The new gradient is added at both
        layers with the end stop mapped onto the existing `--brand-secondary`
        rather than a duplicate literal; the sidebar item's 84px is derived from
        `--size-sidebar` and `--space-3` rather than hardcoded; the one new
        `px`-ish value (`min-width: 1024px`) is a guard rail, matching the
        existing exception. `aspect-ratio: 1` and `white-space: nowrap` are
        unitless.
      - **Principle 6** (tests accompany code): R4's parse test and R6's class
        test are new; R8 records the measurements that jsdom cannot make. The
        honest limit — that most of this round is only verifiable in a real
        engine — is stated in the design doc rather than papered over.
      - **Principle 7** (traceability): this matrix, plus the reverse
        defect→REQ trace above.
      - **Constraints**: no dependency-manifest edit; branch discipline
        unchanged (`feature/home-screen-prototype`); no external runtime
        request added; **no design-tool request spent** — every measurement in
        this round came from the committed reference PNG and from the running
        app, which is the behaviour the metering constraint asks for.
      - **Decisions Requiring Your Approval**: two items hit this list and both
        are *raised, not taken* — see below. A third (the constitution
        amendment itself) is user-approved in substance, with only its wording
        outstanding; `docs/constitution.md` is deliberately not edited here.

## Approval gates (open — this is the correct state, not a gap)

| # | Item | Blocks | Status |
|---|------|--------|--------|
| 1 | **Responsive scaling mechanism** — viewport-proportional root font size. A new cross-cutting convention *and* a change to what the constitution's "`rem` against a 16px root" means | Task **R7** only | Proposed with recommendation, two sub-questions (cap at 16px?; the accessibility trade). Awaiting the user |
| 2 | **`frontend/CLAUDE.md` entry** — proposed by `dev` during implementation, never written or approved; round 3 adds three conventions to it | Nothing (documentation only) | Proposed. Should be confirmed as one file, not in two passes |
| 3 | **Constitution amendment** making "no scrollbars" a project constraint, 1.1.0 → 1.2.0 | Nothing in this feature (REQ-024 binds it regardless); binds *future* features | User has approved the substance; exact wording proposed separately for confirmation |

None of these is a cross-artifact inconsistency, which is what this gate
checks. Per `docs/constitution.md`, an agent may not decide them, so "open" is
the only correct state for them to be in — and R1–R6 and R8 do not depend on
any of them.

## Gaps

None. R1–R6 and R8 are implementable now; R7 waits on approval gate 1.
