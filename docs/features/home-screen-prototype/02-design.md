# Design: Home Screen Prototype
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **TODO:** [03-todo.md](03-todo.md)
**Status:** In Review

## Architecture decision

### Project location

`frontend/` at the repo root, sibling to a future `backend/` — anticipated
already by `CLAUDE.md`'s note about nested `CLAUDE.md` files
(`backend/CLAUDE.md`, `frontend/CLAUDE.md`). Scaffolded via the Angular CLI
default (`ng new`), which per `docs/constitution.md` already installs Vitest +
jsdom — no separate test-runner setup task. *(REQ-001)*

### Module layout

> **Revised 2026-08-18** — restructured for the shared app-shell/placeholder
> architecture (see "Routing & the shared shell" below). `home.component.*`
> is renamed `home-content.component.*` (it no longer owns the sidebar or
> the background); `sidebar/` moves out of `features/home/` into a new
> `layout/` area, alongside the new `ShellComponent`, since the Sidebar is
> now shared by every route, not a Home-only concern; `nav-items.ts` is
> removed (its content is now config, REQ-017/REQ-018); `features/placeholder/`
> is renamed `features/section-placeholder/` to match its narrower,
> section-aware role (REQ-021).

```
frontend/
  src/
    app/
      app.config.ts          — providers (Router, …)
      app.routes.ts           — route table: Shell as parent, Home + 5 sections as children (REQ-012, REQ-020)
      app.ts                  — root: <router-outlet> only
      core/
        content/
          property.model.ts           — PropertyConfig, MenuItem, ActionCard, PropertyBranding interfaces (REQ-006, REQ-017, REQ-018, REQ-019)
          property-content.service.ts — injectable, DI-provided; getProperty() + getSectionRoute() (REQ-005, REQ-019)
          example-property.data.ts    — static data for the one example property (REQ-006)
      layout/
        shell/
          shell.component.ts/.html/.css   — background (REQ-004/REQ-020) + Sidebar + <router-outlet> for main content, on every route
        sidebar/
          sidebar.component.ts/.html/.css — REQ-007, REQ-017, REQ-018; renders `menuItems` + the fixed `settings` slot
      features/
        home/
          home-content.component.ts/.html/.css — heading block + card row + reserved widget column + hero artwork (REQ-008, REQ-009, REQ-014); rendered by the Shell's router-outlet for `''`
          hero-artwork/
            hero-artwork.component.ts/.html/.css — REQ-010 (no longer paints the background itself — see below)
          heading-block/
            heading-block.component.ts/.html/.css — REQ-008
          action-card/
            action-card.component.ts/.html/.css   — REQ-009, REQ-019 (Glass Panel)
        section-placeholder/
          section-placeholder.component.ts/.html/.css — REQ-021, one reusable component, rendered by the Shell's router-outlet for the 5 non-Home sections
    styles/
      tokens.css   — primitive + semantic custom properties (REQ-003)
      global.css   — @font-face, resets, base html/body, data-theme default
    assets/
      fonts/plus-jakarta-sans/*.woff2   — self-hosted (REQ-002)
      branding/logo-placeholder.svg     — swappable slot's default (REQ-011)
```

Rationale for splitting `hero-artwork`/`heading-block`/`action-card` out of
`home/`, and `sidebar`/`shell` into their own `layout/` area: each maps 1:1
to a component already named and specified in `docs/design/screens/home.md`
(`Menu Bar`/`MenuIcons`, hero bitmap, heading block, `Glass Panel`) and each
is independently testable (REQ-015). `sidebar` and `shell` sit outside
`features/home/` specifically because, after this revision, they are no
longer Home-specific — every route renders them identically (REQ-020) — so a
`features/home/` import path for a component `features/section-placeholder/`
also depends on would be a backwards/cross-feature dependency. `layout/` is
the shared, route-agnostic area both features sit alongside, not under.

### Sidebar config & the Settings decision

> **New 2026-08-18**, resolving the user's explicit "decide this yourself"
> question. This is an ordinary design decision — not on
> `docs/constitution.md` → "Decisions Requiring Your Approval" (no new
> dependency, no infra, no token-mechanism change, no nested `CLAUDE.md`
> entry) — so it's decided and documented here rather than escalated.

The user asked for the sidebar's 5 non-Home items to become config-driven
data (constitution principle 1), and separately flagged uncertainty about
whether **Settings** should follow the same rule or stay hardcoded, noting
Figma already structures it as a separate group.

**Decision: split membership from content.**

| | `menuItems` | `settings` |
|---|---|---|
| Shape | `MenuItem[]`, exactly 5 (Home, Info, Umgebung, Smart Home, Services) | one `MenuItem` |
| Membership / position | Config-driven — the array *is* the sidebar's non-Settings content, in order (REQ-017) | **Structurally fixed** in `SidebarComponent`'s template: always present, always rendered last, in its own group (REQ-018) |
| Label / icon / route | Config-driven | Config-driven — same as every other item |

Rationale:
- **Figma already draws this split.** `docs/design/screens/home.md` →
  Sidebar lists three groups — Logo / **MenuItems** (five `MenuIcons`
  instances) / **Settings** (one `MenuIcons`-shaped item) — and
  `docs/design/design-sheet.md` independently confirms "Logo oben,
  Menüpunkte mittig, **Einstellungen unten**" (Settings at the bottom, its
  own row). Iterating Settings inside the same array as the other five would
  already be layout-inaccurate, before the content-vs-code question even
  applies.
- **Settings' presence is a safe app-wide invariant; the other four aren't.**
  There's no plausible property configuration that omits a settings
  destination — every deployment needs one. "Services" is a more realistic
  candidate for a property to not offer (e.g. no concierge), which is
  exactly the kind of per-property variation principle 1 exists for.
  Fixing Settings' *position* costs nothing today and avoids a
  property config accidentally hiding or duplicating it.
- **Its content still shouldn't be hardcoded**, or the sidebar would have an
  inexplicable asymmetry — five items relabel/re-icon per property, the
  sixth doesn't, for no user-facing reason. Sourcing `settings` from
  `PropertyContentService` like everything else avoids that inconsistency
  and costs nothing extra (it's one more field on the same config object).

Trade-off, flagged for future revisit (not blocking): if a future
requirement needs a property to *omit* Settings entirely (unlikely, but
conceivable for a fully kiosk-locked deployment), this split will need
revisiting — noted in Risks & trade-offs below.

### Content service contract

> **Revised 2026-08-18** — `ActionCard.route` is replaced by
> `targetSectionId` (REQ-019); `MenuItem` and `PropertyConfig.menuItems` /
> `.settings` are new (REQ-017, REQ-018); the service gains
> `getSectionRoute()` to resolve an id to a route in one place.

```ts
// core/content/property.model.ts
export interface PropertyBranding {
  primary: string;       // --brand-primary override
  primaryDeep: string;   // --brand-primary-deep override
  secondary: string;     // --brand-secondary override
}

export interface MenuItem {
  id: string;      // e.g. 'home' | 'info' | 'umgebung' | 'smart-home' | 'services' | 'einstellungen'
  label: string;
  icon: string;     // Phosphor glyph name
  route: string;    // e.g. '' for Home, '/info', '/umgebung', …
}

export interface ActionCard {
  id: string;
  icon: string;              // Phosphor glyph name, e.g. 'Key'
  header: string;
  description: string;
  targetSectionId: string;   // must resolve to a MenuItem.id in menuItems or settings.id — REQ-019
}

export interface PropertyConfig {
  guestName: string;
  heroSubtitle: string;    // "Schön, dass ihr da seid"
  headline: string;        // "Willkommen im Airbnb von {guestName}!" — pre-interpolated
  paragraph: string;
  logoAsset: string;       // path under assets/branding/
  hasArtwork: boolean;     // false for the example property → REQ-010 fallback
  branding: PropertyBranding;
  menuItems: MenuItem[];   // exactly 5, in order: Home, Info, Umgebung, Smart Home, Services — REQ-017
  settings: MenuItem;      // exactly 1; position fixed in SidebarComponent, content from config — REQ-018
  cards: ActionCard[];     // exactly 4, per REQ-009
}
```

Note: `heroSubtitle`, `headline`, `paragraph`, `guestName` are, like every
other field here, **property-level** configuration — authored once by the
property owner/host, not generated or looked up per guest/stay. `guestName`
being part of this object is what makes the headline read as personal; it
does not imply a guest-level data model (see PRD → Assumptions).

```ts
// core/content/property-content.service.ts
@Injectable({ providedIn: 'root' })
export class PropertyContentService {
  // Synchronous today (static data). The signature is intentionally the
  // shape an async version (HTTP → future Quarkus backend) can grow into —
  // e.g. returning a signal fed by httpResource() later — without changing
  // any component's call site beyond how it reads the value.
  getProperty(): PropertyConfig { /* returns example-property.data.ts */ }

  // Resolves a card's (or, in principle, any) targetSectionId to the route
  // that section's own MenuItem carries — the single place REQ-019's
  // "must resolve to a real id" invariant is enforced and tested. Throws on
  // an unknown id rather than silently navigating nowhere: a dangling
  // reference is a content-authoring bug that should fail loudly in tests,
  // not at runtime for a guest.
  getSectionRoute(targetSectionId: string): string {
    /* looks up targetSectionId across [...menuItems, settings] and
       returns the matching MenuItem.route, or throws */
  }
}
```

Components inject the service (`private content = inject(PropertyContentService)`)
and read `this.content.getProperty()` once at construction into a field —
never `import` `example-property.data.ts` directly. This is what makes REQ-005
enforceable and keeps the future backend swap a one-file change.
`ActionCardComponent` additionally calls `getSectionRoute()` to resolve where
it navigates, rather than holding a route of its own — see "Routing & the
shared shell" below.

### Routing & the shared shell (REQ-012, REQ-020, REQ-021)

> **Rewritten 2026-08-18.** Superseded: the previous design gave the 5 nav
> items *and* the 4 cards independent routes to one bare placeholder (9
> routes total, no shell). The user corrected this on two counts — cards
> target *sections*, not their own destinations, and every destination keeps
> the full app shell — which drops the total to 5 non-Home routes and adds a
> parent/child route structure.

`ShellComponent` is the parent for every route. It renders the shared
background (REQ-004) and `SidebarComponent` once, and a `<router-outlet>`
for whichever main content the active route supplies:

```ts
// app.routes.ts (sketch)
export const routes: Routes = [
  {
    path: '',
    component: ShellComponent,
    children: [
      { path: '', component: HomeContentComponent },
      { path: 'info', component: SectionPlaceholderComponent, data: { sectionId: 'info' } },
      { path: 'umgebung', component: SectionPlaceholderComponent, data: { sectionId: 'umgebung' } },
      { path: 'smart-home', component: SectionPlaceholderComponent, data: { sectionId: 'smart-home' } },
      { path: 'services', component: SectionPlaceholderComponent, data: { sectionId: 'services' } },
      { path: 'einstellungen', component: SectionPlaceholderComponent, data: { sectionId: 'einstellungen' } },
    ],
  },
];
```

Note there are no `check-out` / `haus-und-funktionen` / `entdecken` /
`wichtige-infos` routes any more — those 4 cards each resolve to one of the
5 routes above via `targetSectionId` (see "Card → section resolution"
below), rather than owning routes of their own.

**`ShellComponent`** (`layout/shell/`):
- Renders the mesh-gradient background at its own root element — this is
  the one place REQ-004 is painted, so it's identical on every route
  (REQ-020), which is also why `HeroArtworkComponent` no longer paints it
  (see "Mesh gradient background" below).
- Renders `SidebarComponent`, passing `menuItems`, `settings`, and the
  current route (via Angular Router, e.g. `toSignal(router.events…)` or
  reading `router.url`) so exactly one item is active, on every route —
  including every section-placeholder route, not just Home (REQ-007).
- Renders `<router-outlet>` for the main-content region — the only thing
  that changes between Home and a section placeholder.

**`SectionPlaceholderComponent`** (`features/section-placeholder/`, REQ-021):
- Reads `route.snapshot.data['sectionId']`, looks up the matching
  `MenuItem` via `PropertyContentService` (across `menuItems` + `settings`),
  and renders a minimal, token-styled "· label · — coming soon" message
  using that item's `label` — never a hardcoded per-route title string, so
  it stays consistent with principle 2 and with the config being the single
  source of the label.
- Renders only the main-content region — no background, no sidebar; those
  are the Shell's job, already present around it.
- Deliberately not designed beyond that — per this agent's own rule, a
  screen without a spec under `docs/design/screens/` gets flagged for a
  future `/design-import`, not guessed at here.

**`HomeContentComponent`** (`features/home/`, renamed from `HomeComponent`):
- Composes `HeadingBlockComponent` + the 4-card row + `HeroArtworkComponent`
  + the reserved, empty widget-column element, per
  `docs/design/screens/home.md` → Layout's `Welcome` row (`Screen` 780px +
  gap 68 + `Widgets` flex:1) — see "Home content layout & the widget-column
  reservation" below (REQ-014).
- No longer composes the Sidebar or the background — both moved to
  `ShellComponent`, since they're shared now.

**Card → section resolution (REQ-019):** `ActionCardComponent` calls
`content.getSectionRoute(card.targetSectionId)` to resolve where it
navigates, and navigates there on click/Enter/Space — the exact mechanism
`SidebarComponent`'s own items use to know their route, and the exact reason
a card tap and the corresponding sidebar tap are indistinguishable in
outcome. Confirmed mapping, from `example-property.data.ts`:

| Card | `targetSectionId` | Resolves to |
|---|---|---|
| Check-Out | `info` | Info's route |
| Haus & Funktionen | `smart-home` | Smart Home's route |
| Entdecken | `umgebung` | Umgebung's route |
| Wichtige Infos | `info` | Info's route (same as Check-Out) |

Check-Out and Wichtige Infos intentionally share a destination — confirmed
by the user, not an error. Whoever eventually designs the Info screen needs
to serve both the "when do I leave" and the "house rules / WiFi / parking"
needs; flagged in Risks & trade-offs below so it isn't forgotten.

### Interaction states (REQ-013)

Since Figma defines none, minimal states are added in CSS only, using
existing tokens — no new tokens introduced:

```css
.sidebar-item:focus-visible,
.action-card:focus-visible {
  outline: 2px solid var(--color-text-primary);
  outline-offset: 2px;
}
.sidebar-item:active,
.action-card:active {
  transform: scale(0.98);
  transition: transform 180ms cubic-bezier(0.16, 1, 0.3, 1); /* per design-sheet.md → Motion */
}
```

Both elements are rendered as native `<button>` (cards) — buttons are
semantically correct, keyboard-focusable by default and get `:focus-visible`
for free; the app avoids `<a>`/`div`-with-click-handler patterns invented
just for navigation.

### Runtime branding hook (REQ-003)

`PropertyConfig.branding` (see "Content service contract" below) is applied
once, at app start, mirroring the `applyBranding()` snippet in
`docs/design/tokens.md` → Implementation exactly — it only ever overrides
**primitives**, never semantic roles, which is what keeps the theme axis
(`data-theme`) and the branding axis independent:

```ts
// app.config.ts (or an APP_INITIALIZER-style bootstrap step)
function applyBranding(branding: PropertyBranding) {
  const root = document.documentElement;
  root.style.setProperty('--brand-primary', branding.primary);
  root.style.setProperty('--brand-primary-deep', branding.primaryDeep);
  root.style.setProperty('--brand-secondary', branding.secondary);
}
```

With one property configured, this is a no-op in practice (the override
equals the default already in `tokens.css`) — it exists so the mechanism is
real and testable now, not deferred to whenever a second property shows up.

### Viewport guard rail (REQ-016)

Per `docs/design/tokens.md` → Breakpoints: no responsive layout, a single
`min-width: 1280px` on the layout root (`global.css`), with normal page
scroll below it. No dedicated fallback screen — out of scope per the token
spec's own recommendation ("a guard rail for development, not a user-facing
state").

### Mesh gradient background (REQ-004)

> **Revised 2026-08-18** — moved from `HeroArtworkComponent`/Home-only to
> `ShellComponent`'s root element, since the shared shell now needs the
> identical background on every route (REQ-020), not just Home. This is a
> component-boundary correction, not a mechanism change: still the same
> layered CSS `radial-gradient`s, same tokens, same "prepared, not built"
> tinting story — just painted at the right shared level now instead of a
> Home-specific one.

Implemented exactly as `docs/design/tokens.md` → "Background implementation"
specifies: 16 `radial-gradient` layers on one element (`ShellComponent`'s
root — the screen canvas that wraps Sidebar + routed content on every
route), positioned at the edge-aligned `0% / 33.33% / 66.67% / 100%` grid,
each sourcing its colour from `--mesh-1`…`--mesh-16` custom properties set
once in `tokens.css`. No per-property override logic is wired up — the
"prepared, not built" state from tokens.md is preserved as-is.
`HeroArtworkComponent` no longer paints anything itself in the no-artwork
variant; it simply renders nothing, letting the Shell's background show
through underneath it (REQ-010).

### Home content layout & the widget-column reservation (REQ-014)

> **New 2026-08-18**, resolving the user's "leave space for it" instruction.
> The previous design explicitly omitted the widget column entirely
> ("the widget column is not rendered"); the user corrected this — the
> column's *content* stays out of scope (no widgets are built), but its
> *space* must be reserved so the layout already matches the designed
> proportions.

`HomeContentComponent` reproduces `docs/design/screens/home.md` → Layout's
`Welcome` row: the `Screen` column (heading block + card row, fixed at
`780px`/`--size-panel`-derived widths per the token spec) plus a `68px`/
`--space-7` gap, plus a `Widgets` column. That `Widgets` column **is
rendered** — as an empty element with `flex: 1`, taking up all remaining
horizontal space exactly as `docs/design/screens/home.md` specifies ("`flex:
1` — it absorbs all remaining horizontal space") — but nothing is rendered
inside it:

```css
.home-content__widgets {
  flex: 1;
}
```

This is deliberately scoped to `HomeContentComponent` only.
`SectionPlaceholderComponent`'s stub content has no Figma-designed widget
concept to reserve space for — only the `Welcome` row in the Home frame
does — so section placeholders don't replicate this element; they render
their own simpler centred stub content filling the Shell's content region.

Selecting, designing, and building what actually goes in `.home-content__widgets`
remains its own future feature (see PRD → Non-goals) — this task only
ensures the layout doesn't have to be revisited when that feature lands.

## Data model

No datastore. `example-property.data.ts` is a plain static TS object matching
`PropertyConfig` above — this **is** the data model for this feature,
including the new `menuItems` (5), `settings` (1), and each card's
`targetSectionId`. No migrations; static JSON/TS is the constitution's
explicit MVP stance.

## Service contract

(No HTTP API — this feature has no backend.) The closest analog is
`PropertyContentService`'s two methods documented above —
`getProperty(): PropertyConfig` and `getSectionRoute(targetSectionId: string): string`
— which are the seam a future HTTP-backed implementation replaces.

### Test strategy (REQ-015)

Vitest + `TestBed` (Angular's standard harness, CLI-provided). Each
component listed under "Module layout" gets a component test rendering it
with a test double for `PropertyContentService` (`provide: PropertyContentService, useValue: mockService`) rather than the real static
data — this is also what proves REQ-005/REQ-006 are decoupled correctly: if a
component test needs to `import` the data file to make assertions instead of
injecting a mock through DI, that is itself evidence of a principle-2
violation. Routing behaviour is tested via `RouterTestingHarness` (or
equivalent) navigating to Home and each of the 5 section routes and
asserting the resolved component renders inside `ShellComponent`. A
dedicated `PropertyContentService` test asserts `getSectionRoute()` resolves
every one of the example config's 4 `card.targetSectionId` values to a real
route, and throws for an unknown id — this is what makes REQ-019's "must
resolve to a real id" invariant enforced rather than assumed.

## System context updates

`docs/system-context.md` is updated alongside this design (see diff applied
to that file) to reflect the renamed/added components: `ShellComponent` and
`SidebarComponent` move under a shared `layout/` area, `HomeComponent` is
renamed `HomeContentComponent`, and `PlaceholderComponent` is renamed
`SectionPlaceholderComponent` — and to note the token layer moved from spec
to real code.

## Risks & trade-offs

- **Placeholder routes for 5 undesigned destinations** (down from 9 in the
  original design, since cards no longer own independent routes) risk
  looking like "real" navigation that leads nowhere useful. Mitigated by an
  explicit, visibly-labelled "coming soon" placeholder rendered inside the
  full, functional shell (never a blank, broken, or isolated-looking page)
  and by flagging every one of them for `/design-import` once ready, rather
  than silently building more UI than was designed.
- **Two cards intentionally converge on the same destination** (Check-Out
  and Wichtige Infos both → Info). Confirmed by the user, not an error, but
  it means whoever eventually designs the Info screen needs to serve both
  needs from one place. Flagged here so it isn't lost by the time that
  screen is designed.
- **Settings' position is hardcoded** (see "Sidebar config & the Settings
  decision" above) — a judgement call, not dictated verbatim by the
  constitution. If a future requirement needs a property to omit Settings
  entirely, this will need revisiting — flagged here so it isn't forgotten,
  not blocking now.
- **`menuItems`' membership is still fixed at exactly 5** (no per-property
  add/remove of a module, e.g. hiding "Services" for a property without
  concierge). This is now much closer to free than in the original design —
  the content is already data-driven — but the *count/shape* validation
  (exactly 5) is still an assumption baked into `PropertyConfig`. Flagged
  here so it isn't forgotten, not blocking now.
- **Fixed mesh background, no brand-tinting.** Consistent with
  `docs/design/tokens.md`'s own "prepared, not built" framing, but every
  property will look visually identical until a future feature builds the
  derivation rule. Confirmed by the user (see PRD's Risks section) —
  resolved, not open.
- **Card target-section resolution can throw at runtime** if a future config
  edit introduces a `targetSectionId` typo. This is intentional (see
  `getSectionRoute()` above) — failing loudly during development/tests is
  preferred over a card that silently does nothing on a touch kiosk — but it
  does mean a bad config edit is a build/test-time failure, not a graceful
  runtime fallback. Acceptable for a single-property MVP with no live config
  editing UI; worth revisiting if/when property owners edit config directly.

## Open decisions

None remaining that are hard-gated under `docs/constitution.md` → "Decisions
Requiring Your Approval" — no new dependency, no datastore/infra choice, no
change to how tokens are realised in code, and no new architectural pattern
beyond what the constitution already establishes (principles 1 & 2 already
mandate the content-service split used here; the shell/child-route structure
is ordinary Angular Router usage, not a new pattern).

**Resolved during this revision, explicitly delegated by the user rather
than gated:** whether Settings should be hardcoded or config-driven — see
"Sidebar config & the Settings decision" above for the decision and its
rationale.

The softer, product/design-level items below are **not** re-litigated here —
they're already resolved in the PRD (`01-prd.md` → Risks & open questions)
with a stated recommendation, and flagged prominently in this session's
report for your confirmation rather than blocking this document:
widget-column *content* scope (space is now reserved, per REQ-014), final
logo asset, dark-mode palette.

## Decisions confirmed by the user

| Decision | Chosen | Date |
|----------|--------|------|
| Design token mechanism (CSS custom properties, two layers: primitive → semantic) | Confirmed pre-existing (via `docs/constitution.md` / `docs/design/tokens.md` → Implementation) | 2026-08-17 |
| Approved dependency list (`@angular/cdk`, `@ng-icons/core` + `@ng-icons/phosphor-icons`, `vitest` + `jsdom`) | Confirmed pre-existing (`docs/constitution.md` → Constraints) | 2026-08-17 |
| Widget column: reserve `flex: 1` space, no content built | Confirmed | 2026-08-18 |
| Background gradient: fixed default palette, no per-property tinting | Confirmed | 2026-08-18 |
| Placeholder architecture: shared full-shell placeholder per section; cards target sections via `targetSectionId`, not independent routes; example mapping (Check-Out→Info, Haus & Funktionen→Smart Home, Entdecken→Umgebung, Wichtige Infos→Info) | Confirmed | 2026-08-18 |
| Sidebar's 5 non-Home `menuItems` become config-driven data | Confirmed | 2026-08-18 |
| Settings: hardcode vs. config — delegated to this agent | Decided: fixed position, config-sourced content (see rationale above) | 2026-08-18 |
| Logo: placeholder SVG behind a swappable slot | Confirmed | 2026-08-18 |
| Heading content is property-level (host-authored config), not per-guest/per-stay | Confirmed | 2026-08-18 |

> Approval-gated decisions move from "Open decisions" to this table only once
> confirmed. This feature introduces none new — the rows above record
> pre-existing constitution decisions this design relies on, plus the
> product/design-level decisions the user has since confirmed, for
> traceability.
