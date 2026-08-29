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
  `docs/design/screens/home.md` → Layout's `Welcome` row (`Screen` 780px raw =
  **390px CSS** + gap 68 + `Widgets` flex:1) — see "Home content layout & the widget-column
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
| Smart Home (renamed 2026-08-18 from "Haus & Funktionen") | `smart-home` | Smart Home's route |
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
`--size-heading` **390px** — corrected from 780px, which was the raw 2× Figma
value — and `--size-panel`-derived widths per the token spec) plus a `68px`/
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

## Round 3 (2026-08-18): visual-defect correction

> Everything above describes the feature as designed and built. This section
> amends it after the screen was run and compared against
> `docs/design/assets/home-no-artwork.png`, which is the **ground truth for
> this round** (the no-artwork variant was never extracted from Figma, so the
> render is the only concrete source for its geometry — and per
> `docs/constitution.md`, querying the design tool for it is not an option).
> Each defect's verified root cause is in `01-prd.md` → "Defect trace (round
> 3)"; this section is the *how*.

### The host element is the layout, not the template root

Three of the seven defects (D1, D2, D7), plus the `flex-shrink` bug fixed
out-of-band earlier the same day, are the same mistake in four places:

> With Angular's default (`Emulated`) view encapsulation, a component's
> template root is **not** the element its parent lays out. The **host**
> element (`<app-sidebar>`, `<app-home-content>`, `<app-action-card>`,
> `<app-hero-artwork>`) is. It has no styles of its own — `display: inline`,
> `flex-shrink: 1`, `height: auto` — and it sits *between* the parent's
> layout and the template root's, quietly absorbing `align-self`,
> `flex`, percentage heights and gap participation.

`.sidebar { align-self: stretch }` was written against a parent that is not a
flex container. `.home-content { min-height: 100% }` was written against a
host of indefinite height. `<app-hero-artwork>` renders nothing, yet still
takes a flex gap.

**Convention, applied to every component in this codebase:** a component that
participates in its parent's layout must declare that participation on its own
`:host`. Two shapes, and the choice between them is not stylistic:

| Use | When | Effect |
|---|---|---|
| `:host { display: contents; }` | The template root *is* the visual box and should be the parent's flex/grid item (`ActionCardComponent`, `SidebarComponent`, `HomeContentComponent`) | The host box disappears; the template root becomes the real item, and its own `flex-*`/`align-self` finally apply |
| `:host { display: none; }` | The component renders nothing in this variant (`HeroArtworkComponent`'s no-artwork fallback) | It stops being a flex item, so it stops consuming a `gap` — fixes D7 |

`display: contents` is deliberate rather than "set `display: flex` on the host
and duplicate the layout there": duplicating means two elements to keep in
sync, and it is exactly how `.action-card`'s `width`/`min-width` came to be
ignored. It has one accessibility caveat worth knowing — `display: contents`
removes the element from the accessibility tree — which is harmless here
because every host involved is a semantically meaningless wrapper (the
semantics live on the inner `<nav>` / `<button>` / `<main>`). It must **not**
be applied to a host that carries a role, a label, or a landmark.

*(This is a codebase-wide convention. It is proposed for `frontend/CLAUDE.md`
— see "Proposed conventions, not yet written" at the end of this section.)*

### Full-height sidebar and group distribution (REQ-022)

Two changes, in `shell.component.css` and `sidebar.component.css`:

```css
/* shell.component.css — items must stretch, not centre */
.shell {
  align-items: stretch;   /* was: center */
  height: 100dvh;         /* was: min-height — see "No scrollbars" below */
}
```

```css
/* sidebar.component.css */
:host { display: contents; }   /* the <nav> becomes the flex item */

.sidebar {
  /* align-self: stretch is now real, because the parent is the flex container */
  justify-content: space-between;   /* already present; now has height to distribute */
}

.sidebar__menu-items { width: 100%; }

.sidebar-item {
  width: 100%;          /* = --size-sidebar − 2 × --space-3 = 84px, derived */
  aspect-ratio: 1;      /* square, replaces the fixed --size-sidebar-item */
  height: auto;
}
```

The "much larger gap" between the logo and the nav group, and between the nav
group and Settings, is **not** a separate spacing change — it falls out of
`space-between` once the sidebar actually has the height. Arithmetic against
the reference: content box 784 − 2 × 24 padding = 736; groups 56 (logo) + 420
(5 × 84 nav) + 84 (settings) = 560; the remaining 176 splits into two 88px
gaps. Measured on the reference render: logo 48→104, nav 195→615, settings
≈700→784. That is the same layout, so no gap token is introduced.

Why the item stops using `--size-sidebar-item`: the reference render's item is
84px, not the token's 72px, because Figma stretches the `MenuIcons` instance to
the sidebar's inner width. Deriving it (`width: 100%` + `aspect-ratio: 1`)
keeps one source of truth rather than adding a second literal, and it is what
makes the "Smart Home" label fit (D5). Recorded in `docs/design/tokens.md` →
Sizing, with the measurement.

### Home content vertical composition (REQ-014, REQ-010)

```css
.home-content {
  align-items: stretch;      /* was: flex-end — this is what caused D2 */
  height: 100%;              /* was: min-height: 100% against an auto-height host */
}

.home-content__screen {
  /* already had justify-content: space-between and padding: --space-9 0;
     both were inert while the column was content-height */
}
```

`:host { display: contents }` on `HomeContentComponent` is what makes
`height: 100%` resolve at all — otherwise the percentage is measured against
the auto-height host.

The `gap: var(--space-6)` on `.home-content__screen` is removed: with
`space-between` on a full-height column it is dead weight, and a leftover gap
is what would make "top-anchored / bottom-anchored" only approximately true.

`HeroArtworkComponent` gets `:host { display: none }` in the no-artwork
variant (D7). The alternative — dropping `<app-hero-artwork>` from Home's
template until artwork exists — was rejected: REQ-010 wants the seam kept, and
a component that must be *removed* to not break the layout is a worse seam than
one that declares it renders nothing.

Result, measured against the reference: heading top at 24 + 64 = 88, card row
bottom at 808 − 64 = 744, card row left edge at 172.

### Text must not wrap (REQ-007, REQ-009)

Two separate near-misses (see D5). Neither is fixed by "make the box bigger" —
the boxes are the design's.

**Sidebar labels.** Fixed by REQ-022's derived item width (56px label box →
~84px), plus `white-space: nowrap` on `.sidebar-item__label` so a longer
property-configured label degrades predictably instead of silently reflowing
the whole nav column. The reference's own longest label ("Einstellungen")
measures ~84px, i.e. the design is already at the edge of that box.

> **Superseded 2026-08-28 (owner decision) — `nowrap` is gone.** "Degrades
> predictably" turned out to mean "runs out of the item and keeps going":
> "Einstellungen" needs 79px against a 68px content box at 1280 and simply
> painted over the item's edge. The label now reserves **two lines for every
> item** and is clamped with an ellipsis past that, which is what actually
> removes this section's problem — the item's height no longer depends on its
> text at all, so no label can move an icon. See
> `01-prd.md` → REQ-007 and the D5 note under Defects for the re-diagnosis,
> and `sidebar.component.css` for the rule. The heading above ("Text must not
> wrap") is now true of card titles only.

**Card titles.** The title box is **162px** (196 − 2 × 1px glass border − 2 ×
16px padding; the "164px" this originally read forgot the border) and "Haus &
Funktionen" needs **167px** — Figma does not re-wrap a fixed text frame, a
browser does. Fix:

```css
.action-card__header {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
```

and, so that a long title can never desynchronise the row again:

```css
.action-card__text-block { justify-content: flex-end; }  /* description row pinned to the bottom */
```

The `text-overflow: ellipsis` is a deliberate trade: card copy is
property-authored config (principle 1), so *something* has to happen when a
host writes a 30-character title. The options were wrap (reflows the row —
this is the defect), shrink the font (invents a size not in the token layer),
or truncate. Truncation is the only one that keeps four cards visually
identical, and it fails visibly rather than silently. **This makes "keep card
titles short enough to fit ~162px at `--text-title`" a content-authoring
constraint** (measured 2026-08-18: ~162px), which belongs in whatever
admin/authoring surface eventually
edits this config — noted in Risks & trade-offs.

### Gradient-filled icon glyph (REQ-023)

The requirement is "paint an SVG glyph with a gradient". This needs a real
technique decision, because the obvious ones do not work:

| Candidate | Verdict |
|---|---|
| `background-image: var(--gradient-icon-active); background-clip: text` | **No.** `background-clip: text` clips to rendered *text* glyphs. The icon is an `<svg>`, not a font glyph |
| `fill: linear-gradient(…)` directly | **No.** SVG `fill` takes a paint server (`url(#…)`), not a CSS gradient function. Not supported in any browser |
| `mask-image: url(icon.svg)` over a gradient box | **Works, but needs the glyph as a fetchable URL.** `@ng-icons` inlines its SVG as a string; there is no URL. Would mean shipping a second copy of the icon as an asset — a duplicate that can drift from the one the sidebar renders |
| **SVG `<linearGradient>` + `fill: url(#…)`** | **Chosen.** Verified against the actual package payload: `@ng-icons/phosphor-icons`' `regular` set is **fill-based** — `<svg viewBox="0 0 256 256" fill="currentColor"><path d="…"/></svg>` — so a `fill` paint server recolours the glyph exactly. (A stroke-based set would need `stroke: url(#…)` instead; Phosphor `regular` is not one) |

**Where the pieces live, and why it is not all in one component stylesheet:**

`@ng-icons` does not render its SVG from the component template — `NgIcon`
builds the element with `Renderer2` and appends it (`setSvg()` in
`@ng-icons/core`). Nodes created that way carry **no `_ngcontent-*`
attribute**, so under emulated encapsulation *no component-scoped selector can
ever match the injected `<svg>`*. That is a property of the library, not a
style preference, and it dictates the split:

1. **The gradient definition** goes in `shell.component.html` — an inline,
   zero-size `<svg><defs><linearGradient>`. It is part of the template, so its
   `<stop>` elements *are* encapsulation-tagged and `shell.component.css` can
   style them normally. The Shell renders once on every route, which is exactly
   the lifetime the sidebar needs.

   ```html
   <svg class="shell__defs" aria-hidden="true" focusable="false" width="0" height="0">
     <defs>
       <linearGradient id="icon-gradient-active" gradientTransform="rotate(49 0.5 0.5)">
         <stop class="shell__defs-stop--start" offset="0%" />
         <stop class="shell__defs-stop--end"   offset="100%" />
       </linearGradient>
     </defs>
   </svg>
   ```

   `rotate(49 0.5 0.5)` is the 139° CSS angle: SVG's default gradient axis runs
   left→right, which equals CSS `90deg`, so the rotation is `139 − 90` about
   the object bounding box's centre.

2. **The stop colours** come from the token layer, as semantic roles, in
   `shell.component.css` — never primitives, per constitution principle 5:

   ```css
   .shell__defs { position: absolute; width: 0; height: 0; }
   .shell__defs-stop--start { stop-color: var(--color-icon-active-start); }
   .shell__defs-stop--end   { stop-color: var(--color-icon-active-end); }
   ```

   `stop-color` must be a CSS *declaration*, not a presentation attribute — a
   presentation attribute cannot hold `var()`. Because the stops resolve custom
   properties at render time, the runtime branding hook (REQ-003) reaches the
   glyph for free.

3. **The one rule that must escape encapsulation** — pointing the injected
   `<svg>`'s fill at that gradient — goes in `global.css`, keyed on a
   purpose-named class rather than a component's BEM class, so it stays a
   reusable primitive instead of a leak:

   ```css
   /* global.css — see the note above: @ng-icons injects its <svg> outside the
      Angular template, so it carries no encapsulation attribute and no
      component-scoped selector can reach it. */
   .icon-gradient-active svg {
     fill: url('#icon-gradient-active') currentColor;
   }
   ```

   The trailing `currentColor` is SVG's paint fallback: if the referenced
   gradient is ever missing (a stray `<base href>` interaction, a stripped
   `<defs>`, a test environment without the Shell), the glyph falls back to the
   inherited colour instead of disappearing.

   Rejected alternatives: `::ng-deep` (deprecated, and it would put the same
   rule in a component file while still compiling to a global selector —
   the leak with worse ergonomics) and `ViewEncapsulation.None` on
   `SidebarComponent` (globalises *all* of the sidebar's CSS to solve one line).

4. **The class is applied in the sidebar template**, on the active item's
   `<ng-icon>` only:

   ```html
   <ng-icon [class.icon-gradient-active]="isActive(item)" … />
   ```

**Verification `dev` must actually perform, not assume:** the document carries
`<base href="/">`, and historically some browsers resolved a bare `url(#id)`
against the *base* URL rather than the current document, breaking fragment
references on any non-root route. Modern engines resolve against the document,
but this must be confirmed by loading a **non-Home route** (where a different
sidebar item is active) and checking the glyph is still gradient-filled — not
only the Home route.

### No scrollbars (REQ-024) and responsive scaling (REQ-025)

**No scrollbars is met by construction, not by hiding overflow.** `overflow:
hidden` on `html`/`body` would satisfy the letter of the rule while turning
every future layout regression into invisible clipping — content that exists,
is unreachable, and no test notices. Instead:

- `.shell` becomes `height: 100dvh` (not `min-height`), so the shell is exactly
  the viewport and any child that would overflow does so *visibly* in
  development;
- the guard rail moves from `min-width: 1280px` to `min-width: 1024px`
  (REQ-016/REQ-025 — below the supported floor, scrolling is the documented,
  out-of-scope behaviour);
- an automated check asserts `scrollWidth ≤ clientWidth && scrollHeight ≤
  clientHeight` at 1024×768, 1280×832 and 1440×900, on every route. That check
  is the actual enforcement mechanism, and it is what makes the rule survive
  into future screens.

Note this is why REQ-024 and REQ-025 are one problem: at 1280×832 the built app
*already* fits (verified: `scrollWidth === clientWidth === 1280`,
`scrollHeight === clientHeight === 832`). The only reason a scrollbar appears
at all is that the layout does not scale below the design size.

#### Responsive scaling (REQ-025) — approval-gated, not decided here

The layout must go from 1280×832 to 1024×768 — a 0.8× width factor — with no
reflow, no wrapping and no scrollbar. Note the aspect ratios differ (1.54 vs
1.33), so the binding constraint is `min(width-ratio, height-ratio)`; at the
floor, width binds (0.80 vs 0.92).

Every size token in this project is already `rem` against a 16px root, and
`docs/design/tokens.md` → "Units" was written **anticipating exactly this
move** ("a larger panel gets a different root font size and *everything*
scales proportionally"). So the mechanism that fits the existing architecture
is a viewport-proportional root font size:

```css
html { font-size: min(1.25vw, 1.9231vh); }   /* 1280/16 = 80 → 1.25vw; 832/16 = 52 → 1.9231vh */
```

= exactly `16px` at 1280×832, `12.8px` at 1024×768, and larger on bigger
displays. Nothing else changes: no breakpoints, no second layout, no component
edits — provided nothing in the codebase is sized in `px` (audited: only the
hairline border and the blur radius are, both intentional per the
constitution, plus the guard rail's `min-width`).

**This is a new cross-cutting convention affecting every future screen and
component, and `docs/constitution.md` mandates `rem` against a *16px* root —
so changing what the root means is on that file's "Decisions Requiring Your
Approval" list. It is therefore proposed, not adopted.** Alternatives
considered, with why they lose:

| Alternative | Why not |
|---|---|
| `zoom: min(1, 100vw/1280, 100vh/832)` on `:root` | Genuinely re-lays out (unlike `transform`), and needs no `rem` discipline. But `zoom` was only recently standardised, interacts oddly with fixed positioning and media queries, and would establish a *second*, competing scaling mechanism next to the `rem`/root-font-size one the token layer already assumes |
| `transform: scale()` on a fixed 1280×832 canvas | Pixel-exact, but rasterises: text is scaled bitmap output, not re-hinted. On a text-dense screen at 0.8× that is visibly soft. Also breaks `position: fixed` descendants and complicates hit-testing |
| Per-component `clamp()` on each size | No single decision to approve, but it must be repeated in every component forever, and it defeats the token layer's purpose |
| Do nothing below 1280 (status quo) | Contradicts the user's decision |

Two sub-decisions inside the recommendation, both for the user:

- **Cap at 16px or not?** Uncapped (`min(1.25vw, 1.9231vh)`) means a 1920×1080
  display renders everything ~1.3× larger, which is what `tokens.md` → "Target
  device" already says should happen ("larger displays … the layout keeps its
  proportions and simply gets bigger"). Capped (`min(1.25vw, 1.9231vh, 16px)`)
  keeps the design size as a ceiling and leaves bigger displays with more empty
  space. **Recommended: uncapped**, for consistency with the existing spec.
- **Accessibility note:** a viewport-derived root font size overrides the user's
  browser font-size preference. On a fixed-purpose kiosk that is acceptable and
  is *already* the case (`html { font-size: 16px }` today is equally absolute) —
  but it is a real trade and should be a conscious one.

Until this is answered, the REQ-025 task in `03-todo.md` stays blocked. Every
other round-3 task is independent of it.

### Logo asset (REQ-011, defect D4)

`logo-placeholder.svg` is not well-formed XML: its comment contains `--`,
which XML forbids inside a comment, so Chromium's SVG parser rejects the whole
document and `<img>` fires `error`. Fix is one character-level edit (rewrite
the comment without `--`), plus two guards so it cannot recur silently:

- give the SVG explicit `width`/`height` attributes alongside its `viewBox`, so
  it has an intrinsic size and a failure is distinguishable from "renders at
  0×0";
- add a test that the shipped asset **parses** — `DOMParser.parseFromString(…,
  'image/svg+xml')` produces no `<parsererror>` — rather than only asserting the
  `src` attribute came from config, which is what the existing REQ-011 test
  does and why it stayed green through this bug.

The class of error is worth naming: an SVG that is invalid XML is *silently*
valid-looking in an editor, in `git diff`, and to `file(1)` (which reported it
as "SVG Scalable Vector Graphics image"), and it 200s over HTTP. Only a parser
disagrees.

### Proposed conventions, not yet written

Two items here are **proposals awaiting the user's confirmation**, per
`docs/constitution.md` → "Decisions Requiring Your Approval". Neither has been
written to any file:

1. **The root-font-size scaling pattern** (above) — a new cross-cutting
   convention, and a change to what the constitution's "16px root" means.
2. **A `frontend/CLAUDE.md` entry.** One was already proposed by `dev` during
   implementation (2026-08-18) and has still never been written or approved.
   This round adds to it rather than replacing it — specifically the `:host`
   layout convention above, the "no scrollbars, verify by measurement" rule,
   and the `@ng-icons`-injects-outside-encapsulation gotcha. The two proposals
   should be confirmed together as one file rather than in two passes.

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
asserting the resolved component renders inside `ShellComponent`.

**Round 3 addition — what unit tests could not have caught, and what replaces
them.** Every defect in this round was a *geometry* or *rendering* defect, and
the existing 29 tests stayed green through all of them: jsdom computes no
layout, so "the sidebar is 536px tall instead of 784" and "the logo failed to
decode" are both invisible to it. Two things therefore change:

- **Assert what jsdom *can* decide.** The logo asset's well-formedness is a
  parser question, not a layout one: a test that runs `DOMParser` over the
  shipped SVG and asserts no `<parsererror>` would have failed on D4 in jsdom,
  today. Same for "the active `<ng-icon>` carries the gradient class" and "the
  `<defs>` gradient element exists in the Shell's DOM".
- **Everything else needs a real engine.** The no-scroll assertions (REQ-024),
  the measured geometry (REQ-022, REQ-014) and the no-wrap assertions (REQ-007,
  REQ-009) all require layout. Playwright is already present in
  `frontend/package.json` (added 2026-08-18 for the `code-review` skill's visual
  check) and `docs/constitution.md` **v1.3.0 approves it — as a verification
  tool only**: review-time measurement, no committed specs, nothing wired into
  `npm test`. That approval deliberately did **not** decide the end-to-end
  framework, which stays an open, approval-gated question in
  `docs/constitution.md` and `docs/system-context.md`. So this round does
  **not** introduce an E2E suite; the repeatable instrument it does carry is
  `frontend/tools/measure.mjs`, which reports numbers and asserts nothing.
  The measurements are performed as part of the `code-review` visual-check step,
  against the numbers recorded in `01-prd.md`'s acceptance criteria, and the
  results are recorded in `06-review.md`. Turning them into a standing automated
  suite is the natural trigger for finally making the E2E decision — flagged,
  not smuggled in. A
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
- **Round 3: card titles truncate rather than wrap.** `text-overflow: ellipsis`
  on `.action-card__header` keeps the four cards aligned no matter what a
  property author writes, but it means a long configured title is *silently
  shortened on screen*. **It already happened to the shipped copy:** "Haus &
  Funktionen" needed 167px of advance width in the real 162px title box
  (`--size-panel` 196 − 2 × `--border-glass` 1px − 2 × `--space-5` 16px — the
  earlier "164px" here and in `action-card.component.css` missed the 1px glass
  border on each side) and rendered ellipsized in a browser for the whole of
  round 3. Resolved 2026-08-18 by the user renaming that card to "Smart Home"
  (measured: 111px ink, 51px of headroom), not by changing the box — so the
  constraint stands unchanged and the next over-long title will truncate the
  same way. The constraint ("≤ ~162px at `--text-title`, roughly 16–17
  characters") is currently written down nowhere a content author would see
  it. Whatever admin/authoring surface eventually edits
  this config should carry it as a field hint. Flagged, not solved.
- **Round 3: the sidebar item size is now derived, not tokenised.**
  `--size-sidebar-item` (72px) stays in the token layer for traceability to the
  Figma component but is no longer what the sidebar renders (84px, derived from
  `--size-sidebar` − 2 × `--space-3`). A token that exists and is deliberately
  not used is a trap for the next person; `docs/design/tokens.md` carries the
  explanation, but this is worth revisiting the next time the token layer is
  tidied.
- **Round 3: one CSS rule lives in `global.css` that logically belongs to the
  sidebar** (`.icon-gradient-active svg { fill: … }`). This is forced by
  `@ng-icons` injecting its SVG outside the Angular template; there is no
  encapsulation-respecting alternative that does not either deprecate
  (`::ng-deep`) or globalise more (`ViewEncapsulation.None`). If the icon
  library is ever swapped, check whether this constraint still applies.
- **Round 3: `--gradient-icon-active` has no Figma source.** It was measured
  from a render. A future `/design-import` will not reproduce it and could
  quietly drop it. Both design files carry the provenance; the durable fix is
  adding the gradient to the Figma component.
- **Card target-section resolution can throw at runtime** if a future config
  edit introduces a `targetSectionId` typo. This is intentional (see
  `getSectionRoute()` above) — failing loudly during development/tests is
  preferred over a card that silently does nothing on a touch kiosk — but it
  does mean a bad config edit is a build/test-time failure, not a graceful
  runtime fallback. Acceptable for a single-property MVP with no live config
  editing UI; worth revisiting if/when property owners edit config directly.

## Open decisions

> **Reopened 2026-08-18 (round 3).** The statement below was true for rounds 1
> and 2. Round 3 introduces **two** approval-gated items, both stated as
> proposals and neither written to any file:
>
> 1. **The responsive scaling mechanism** (viewport-proportional root font
>    size) — a new cross-cutting convention, and a change to what the
>    constitution's "`rem` against a 16px root" means. See "Responsive scaling
>    (REQ-025) — approval-gated" above. **Blocks the REQ-025 task only.**
> 2. **The `frontend/CLAUDE.md` entry** — still un-written from the
>    implementation session, now with round-3 additions. See "Proposed
>    conventions, not yet written".
>
> A third item is user-approved in substance but not in wording: the
> **constitution amendment** making "no scrollbars" a project constraint
> (REQ-024) and bumping 1.1.0 → 1.2.0. The exact text is proposed to the user
> separately; `docs/constitution.md` is not edited by this pipeline.

Original text, still accurate for everything except the three items above:
None remaining that are hard-gated under `docs/constitution.md` → "Decisions
Requiring Your Approval" — no new dependency, no datastore/infra choice, no
change to how tokens are realised in code, and no new architectural pattern
beyond what the constitution already establishes (principles 1 & 2 already
mandate the content-service split used here; the shell/child-route structure
is ordinary Angular Router usage, not a new pattern).

Note the round-3 token additions (`--gradient-icon-active` and its two stop
roles) are **not** a change to *how* tokens are realised in code — the
mechanism (CSS custom properties, primitive → semantic) is untouched; only new
values are added inside it. That is ordinary work, not a fresh approval.

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
| Active sidebar icon glyph carries a gradient (139°, `#f45aa8` → `#8b5cf6`), overriding `screens/home.md`'s 2026-08-17 "icon does not participate" decision | Confirmed (values measured from the reference render and approved verbatim; end stop recorded as the existing `--brand-secondary`, which it matches within 1/255 per channel) | 2026-08-18 |
| No scrollbars, on this and **every future** screen | Confirmed — wording of the constitution amendment still to be approved | 2026-08-18 |
| Minimum supported size 1024 × 768, landscape; portrait out of scope | Confirmed (floor value delegated to this agent — "nimm eine typische Tablet-Größe" — and set to classic iPad landscape) | 2026-08-18 |

> Approval-gated decisions move from "Open decisions" to this table only once
> confirmed. This feature introduces none new — the rows above record
> pre-existing constitution decisions this design relies on, plus the
> product/design-level decisions the user has since confirmed, for
> traceability.
