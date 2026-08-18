# Analyze: Home Screen Prototype
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Result:** PASS

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

## Gaps

None.
