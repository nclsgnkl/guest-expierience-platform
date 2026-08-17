# Design Tokens

> Extracted once via the `design-import` skill — **not** re-queried from the
> design tool during implementation. If a value is missing here, add it via
> `/design-import` (or ask the user), never by spending an unplanned
> design-tool request.
>
> This file is a **specification**. How these tokens are realised in code was
> an approval-gated decision under `docs/constitution.md` — **it has now been
> made**; see "Implementation" at the end of this document.

**Source:** Figma `AirBnB-Screen`, file key `JGH2ZQOXCoL0KNLM4uXtCN`,
frame `1:2891` "Welcome Screen" (2560 × 1664)
**Last updated:** 2026-08-17

> **Scope.** This file records what **exists** in Figma. The wider design system
> — motion, status colours, voice and tone, and components not yet drawn — lives
> in [`design-sheet.md`](design-sheet.md). Where the two disagree, **this file
> wins**; the design sheet was reconciled against it on 2026-08-17 and carries a
> deviation log.

---

## Scale: the Figma canvas is 2x

The frame is **2560 × 1664**, which is the native panel resolution of a
**MacBook Air 13" (M2/M3)** at 224 ppi — the physical pixel count, not a CSS
size. The design was therefore drawn on a **2x canvas**.

| | Value |
|---|---|
| Figma canvas | 2560 × 1664 (device px) |
| **Logical / CSS viewport** | **1280 × 832** |
| Ratio | 2× |

**Every token below is given in CSS px (Figma value ÷ 2).** The raw Figma value
is kept in a separate column so the design file stays traceable.

A caveat worth knowing: the design was not drawn on a strict 2x grid, so a few
values land on half-pixels when halved (sidebar `199`, menu item `146`, widget
column `443`, layout padding `65`, gap `68`). Those get a **recommended
normalised value** below. Spacing and radii, by contrast, halve perfectly onto a
4px scale — those need no adjustment at all.

> 1280 × 832 is a *laptop* viewport. It is a fine reference size, but it is not
> a breakpoint system. See "Breakpoints".

### Units: rem, not px

**Decided 2026-08-17.** Every size token is expressed in **`rem`** against a
`16px` root, not in fixed pixels.

The MVP targets 11–13" tablets, but the platform must scale to other display
sizes later. `rem` is what makes that a one-line change: a larger panel gets a
different root font size and *everything* — type, spacing, radii, component
dimensions — scales proportionally. Fixed px would require revisiting every
token instead.

```css
html { font-size: 16px; }          /* 11–13" tablet — the design baseline */
/* later, per device class: */
/* html { font-size: 20px; }       →  entire UI scales 1.25× */
```

Two deliberate exceptions, which must **not** scale:

- **Hairlines** — the 1px glass panel border stays `1px`. Scaled borders look
  blurry, not bigger.
- **Blur radii** — `backdrop-filter: blur(8px)` is an optical effect, not a
  measurement.

Each table below gives `rem` as the value to implement, with CSS px at the
baseline root size for reference, and the raw Figma value for traceability.

---

## Colors

### Figma variables

| Token | Figma variable | Value | Role |
|-------|----------------|-------|------|
| `--color-brand-primary` | `Brand_Primary-Pink` | `#f45aa8` | Brand pink; icon gradient start, logo, accents |
| `--color-brand-secondary` | `Brand_Secondary-Purple` | `#8a5cf6` | Brand purple |
| `--color-text-primary` | `Text_Primary` | `#2c1f4a` | Headings, card titles, menu labels — default text color |
| `--color-text-secondary` | `Text_Secondary` | `#6e6486` | Card descriptions, supporting copy |
| `--color-text-on-dark` | `Text_On-Dark` | `#ffffff` | Text on dark/saturated surfaces |

> ✅ **Done 2026-08-17:** was `Test_Primary` (a typo); renamed to `Text_Primary`
> in Figma. Spec and design file agree.

`--color-brand-secondary` is defined but **not visibly applied** in this frame.
Its intended use (host-contact buttons) lives in the not-yet-built widget column.

### Raw values (not yet variables)

Used in the design but not bound to a Figma variable.

| Token | Value | Role |
|-------|-------|------|
| `--color-screen-bg` | `#f5c7e2` ⚠️ | **Not the real background** — see the warning below |
| `--color-surface-glass` | `rgba(255, 246, 240, 0.6)` | Glass panel fill (the four action cards) |
| `--color-surface-glass-border` | `rgba(255, 255, 255, 0.4)` | 1px border on glass panels |
| `--color-surface-menu` | `rgba(255, 246, 240, 0.4)` | Sidebar fill |
| `--color-brand-primary-deep` | `#cc49af` | End stop of the icon gradient |

> ⚠️ **`--color-screen-bg` is wrong — extraction artefact, corrected 2026-08-17.**
>
> The Figma frame's background is a **mesh gradient** with many colour points:
> pink at the top left, peach/orange top right, violet along the bottom, muted
> mauve through the centre. The MCP's code generation **flattened all of it to a
> single flat `#f5c7e2`**, and that flat value was recorded here in good faith.
> It reproduces none of the actual background.
>
> This is a known class of extraction loss: `get_design_context` returns
> framework code, and complex paint types (mesh gradients, multiple stacked
> fills, blend modes) get approximated to something a single CSS property can
> express. **Treat any single flat colour in this file with suspicion where the
> render clearly shows a gradient.**
>
> **Implementation note:** CSS has no mesh-gradient primitive. The established
> technique is several stacked `radial-gradient` layers on one element, which
> approximates a mesh closely and — importantly — can take its colour stops from
> the brand primitives, so the per-property tinting idea remains viable. It is
> simply more than a one-line token.
>
✅ **The real background: a 4 × 4 mesh gradient.** Supplied by the user
2026-08-17 (read off Figma by hand — cost 0 MCP calls). Sixteen colour points
spread evenly across the frame, row by row from the top left:

| | col 1 | col 2 | col 3 | col 4 |
|---|---|---|---|---|
| **row 1** | `#F5668C` | `#F4779C` | `#FE9B82` | `#FDCFAD` |
| **row 2** | `#EFA2B5` | `#E8B2BC` | `#F3B5AA` | `#FDD6BE` |
| **row 3** | `#D5B3D0` | `#C693A7` | `#A27684` | `#F4B9BF` |
| **row 4** | `#B69ED1` | `#AF99D2` | `#BEA3D6` | `#E4BFDD` |

**Control points sit on the edges, not at cell centres.** Point 1 is at the
top-left *corner* (0,0), so each axis divides into thirds:

| Index | 1 | 2 | 3 | 4 |
|---|---|---|---|---|
| Position | `0%` | `33.33%` | `66.67%` | `100%` |

Confirmed against the render: all four corners carry their grid colour at full
strength (`#F5668C` top left, `#FDCFAD` top right, `#B69ED1` bottom left,
`#E4BFDD` bottom right). Cell-centre placement would leave the corners washed
out instead.

The palette runs warm pink → peach across the top, through mauve in the middle,
to violet along the bottom — which is why a single flat `#f5c7e2` reproduced
none of it. (Described informally as "the pink/orange gradient"; note rows 3–4
are genuinely violet, not a warm hue.)

`[NEEDS CLARIFICATION: should the mesh be tinted per property, or stay one fixed house look? Note this only matters for properties *without* a 3D rendering — those with one already have a per-property visual, making a tinted background redundant. See "Background implementation" below.]`

`[NEEDS CLARIFICATION: promote these five to Figma variables? They repeat across components but are currently hardcoded, so a brand tweak means editing every layer.]`

### Gradients

| Token | Value | Role |
|-------|-------|------|
| `--gradient-icon` | `linear-gradient(223.139deg, var(--color-brand-primary) 55.732%, var(--color-brand-primary-deep) 88.083%)` | Filled icon tiles and the logo |
| `--gradient-menu-active` | `linear-gradient(-74.707deg, rgba(255,243,250,0.9) 7.717%, rgba(255,243,250,0.5) 92.116%)` | Active state of a sidebar item |

### Background implementation

**SVG does not solve this.** SVG's own `<meshgradient>` element was drafted for
SVG 2 but **never shipped in any browser**, so exporting the mesh as SVG yields
one of two things: a bitmap wrapped in an SVG (raster content plus XML
overhead — strictly worse than a plain image), or a stack of blurred shapes
approximating the mesh, which is the same approximation as the CSS route with
less control over it. Neither is a reason to prefer SVG here.

**Decision (2026-08-17): layered CSS `radial-gradient`s — 16 of them.**

Positioned on the edge-aligned grid above (`0% / 33.33% / 66.67% / 100%` on each
axis), stacked as comma-separated layers in one `background-image` over a base
colour. Each layer fades to `transparent` at roughly 50–60% so neighbours blend.
Note that in CSS the **first** listed layer paints on top.

Because the points sit on the edges, the four corner gradients have three
quarters of their circle outside the canvas — expected, but it means they need a
generous radius to carry colour convincingly into the corner they own.

**The default is the fixed palette above** — the existing pink/peach/violet
mesh, identical for every property. Per-property tinting is **prepared, not
built**: the sixteen colours are authored as their own custom properties
(`--mesh-1` … `--mesh-16`) set once in `:root`, and the gradient references only
those. Tinting later then means overriding sixteen variables, with no change to
the gradient's structure. Nothing derives from brand colours yet, and no
derivation rule is implemented.

This is not an imitation of a different technique — **a mesh gradient is colour
points on a grid with smooth interpolation between them, which is exactly what
the stacked radial layers produce.** The construction is the same; only the
interpolation maths differs slightly (alpha compositing rather than bilinear
surface interpolation), which for a soft pastel mesh is not perceptible.

Why not a raster, which would be pixel-exact and tiny (a smooth mesh needs
almost no resolution — ~640 × 416 as WebP would be tens of kilobytes, never the
4 MB of the current export): **an image cannot be tinted per property.** The
background is a large, permanently visible surface. A property with, say, a teal
brand would otherwise sit in front of this pink-and-violet mesh regardless of its
own colours — and unlike the 3D rendering, the background is *not* per-property
content today. Keeping it CSS keeps that door open.

`[NEEDS CLARIFICATION: how do 16 brand-derived stops get generated from the ~3 brand primitives a property supplies? Deriving a full mesh from one or two colours needs a rule — e.g. hue-rotating the existing 16 towards the brand hue while keeping their lightness and saturation relationships. Worth settling when the background is actually built.]`

**If fidelity ever disappoints**, there is an exact alternative worth knowing:
scaling a literal **4 × 4 pixel image** up to full size makes the browser
interpolate bilinearly — which *is* a mesh gradient, mathematically. Such an
image is around a hundred bytes as a data URI and can be generated at runtime on
a canvas from brand colours, so it stays tintable. More machinery than CSS
layers, so not the default.

## Typography

**Family:** `Plus Jakarta Sans` — the only family in the design.
**Weights:** Regular 400, Bold 700, ExtraBold 800.

| Token | **rem** | px @16 | Figma | Weight | Line height | Used for |
|-------|---------|--------|-------|--------|-------------|----------|
| `--text-display` | `3rem` | 48 | 96 | ExtraBold 800 | `normal` | Page headline |
| `--text-title` | `1.125rem` | 18 | 36 | ExtraBold 800 | `normal` | Glass panel card title |
| `--text-body-lg` | `1.125rem` | 18 | 36 | Regular 400 | `normal` | Hero subtitle and intro paragraph |
| `--text-body` | `0.875rem` | 14 | 28 | Regular 400 | `normal` | Card description |
| `--text-label-active` | `0.75rem` | 12 | 24 | ExtraBold 800 | `normal` | Active sidebar item label |
| `--text-label` | `0.75rem` | 12 | 24 | Bold 700 | `normal` | Inactive sidebar item label |

> **Every text layer uses line-height `normal`** — no explicit value is set
> anywhere in the frame. `normal` resolves from the font's own metrics and will
> not reproduce identically across browsers and platforms.
> `[NEEDS CLARIFICATION: set explicit line-heights. Suggested: 1.1 for --text-display, 1.3 for titles/labels, 1.5 for body.]`

## Spacing

Halves cleanly onto a **4px scale** — no normalisation needed.

| Token | **rem** | px @16 | Figma | Used for |
|-------|---------|--------|-------|----------|
| `--space-1` | `0.25rem` | 4 | 8 | Icon-to-label gap, card title-to-description gap |
| `--space-2` | `0.375rem` | 6 | 12 | Heading block internal gap |
| `--space-3` | `0.5rem` | 8 | 16 | Sidebar horizontal padding |
| `--space-4` | `0.75rem` | 12 | 24 | Gap between glass panels |
| `--space-5` | `1rem` | 16 | 32 | Glass panel horizontal padding |
| `--space-6` | `1.5rem` | 24 | 48 | Glass panel vertical padding, layout vertical padding |
| `--space-7` | `2rem` | 32 | 64 | Layout gutter unit |
| `--space-8` | `2.5rem` | 40 | 80 | Gap between sidebar and content column |
| `--space-9` | `4rem` | 64 | 128 | Content column vertical padding |

Off-scale values, with a recommended normalisation:

| Raw (Figma) | ÷2 | Recommended | Used for |
|---|---|---|---|
| `10px` | 5 | `4px` (`--space-1`) | Icon block horizontal padding |
| `28px` | 14 | `12px` (`--space-4`) | Sidebar item vertical padding |
| `65px` | 32.5 | `32px` (`--space-7`) | Layout horizontal padding |
| `68px` | 34 | `32px` (`--space-7`) | Gap between content and widget column |

## Radii, borders, shadows

Radii also halve cleanly.

| Token | **rem** | px @16 | Figma | Used for |
|-------|---------|--------|-------|----------|
| `--radius-md` | `1rem` | 16 | 32 | Sidebar item (active background) |
| `--radius-lg` | `1.5rem` | 24 | 48 | Logo tile |
| `--radius-xl` | `1.875rem` | 30 | 60 | Filled icon tile |
| `--radius-2xl` | `2rem` | 32 | 64 | Glass panels, sidebar, screen frame |

| Token | Value | Used for |
|-------|-------|----------|
| `--border-glass` | `1px solid var(--color-surface-glass-border)` | Glass panel edge |
| `--shadow-panel` | `0 10px 40px 0 rgba(44, 31, 74, 0.1)` | Glass panel drop shadow (= `--color-text-primary` at 10%) |
| `--blur-glass` | `backdrop-filter: blur(8px)` | Glass panel and sidebar frosting |

## Sizing

Normalised first (see the half-pixel note under "Scale"), then converted.

| Token | **rem** | px @16 | Figma | Used for |
|-------|---------|--------|-------|----------|
| `--size-icon-tile` | `3.5rem` | 56 | 112 | Filled gradient icon tile |
| `--size-icon-glyph` | `0.75rem` | 12 | 24 | Icon glyph box (1:1) |
| `--size-sidebar` | `6.25rem` | 100 | 199 → 200 | Sidebar width |
| `--size-sidebar-item` | `4.5rem` | 72 | 146 → 144 | Sidebar item square |
| `--size-panel` | `12.25rem` | 196 | 392 | Glass panel (square) |
| `--size-widget-col` | `13.75rem` | 220 | 443 → 440 | Right-hand widget column width |

## Breakpoints

**None — deliberately.** Decided 2026-08-17.

The MVP targets **large touchscreens only**, sized generously enough to render
the layout exactly as designed: permanent sidebar, four cards across, 780px
heading block. There is no responsive behaviour to specify, because there is no
second layout.

Phone support is an acknowledged future need but explicitly **out of scope for
now**. When it arrives it is a layout project (sidebar → bottom bar or drawer,
cards → 2×2 or scroller), not a token change — nothing in this file blocks it.

### Target device

**11–13" tablet**, on a stand or table, viewed from roughly 40–70cm. At that
distance the designed values hold without adjustment: `0.75rem` labels and
`0.875rem` body text read normally, and touch targets are comfortable — sidebar
items are `4.5rem` (72px), well above the 44–48px guideline.

Larger displays are expected later. That is handled by the root font size (see
"Units"), **not** by breakpoints — the layout keeps its proportions and simply
gets bigger.

### Minimum supported width

`1280px`. Below it the layout breaks rather than degrades: the sidebar, the
780px heading block and four 196px cards with gaps consume the full width by
design.

**Recommendation:** treat widths below the minimum as **out of scope rather
than broken** — the app is deployed on known hardware, so this is a guard
rail for development, not a user-facing state. A simple `min-width` on the
layout root with page scrolling is enough; no dedicated fallback screen is
worth building until phone support becomes real.

---

## Implementation

Decided 2026-08-17 with the user via the `grilling` skill. This section is
binding for implementation; the rest of the document stays a pure spec.

### Requirements that drove it

1. **Runtime theming is required.** Dark mode is planned (`Einstellungen →
   Darstellung`), and each property should eventually carry its own brand
   colors. Both must change *in the browser*, which rules out compile-time-only
   approaches (SCSS variables, a TypeScript-only theme object).
2. **Branding must be data, not code.** With 10+ customers, onboarding number
   eleven must not require a code change or a deploy.
3. **The product is far larger than the Home screen** — every panel and icon is
   interactive, with sub-menus, pop-ups and detail pages across all six modules
   in `README.md`.

### Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | **CSS custom properties** are the token layer | The only mechanism that is swappable at runtime *and* reaches through Angular's `ViewEncapsulation` |
| 2 | **No Tailwind** | The design is bespoke; Angular already scopes styles per component; and Tailwind does not help with the genuinely hard part (overlay behaviour). Avoids a build dependency |
| 3 | **Two token layers: primitive → semantic** | Components reference roles only. Theme switching touches roles; property branding touches primitives. The two axes never collide, and derived values (e.g. `--gradient-icon`) update automatically |
| 4 | **Property branding injected at runtime** from a per-property config | Adding a customer = adding a config entry. No per-tenant CSS |
| 5 | **`@angular/cdk`** for overlay behaviour | Unstyled primitives (Overlay, Dialog, Menu, FocusTrap, Portal, A11y) give focus management and ARIA correctness without imposing visual opinions. Angular Material is built on it, so individual Material components remain addable later |
| 6 | **Phosphor Icons**, consumed via `@ng-icons/core` + `@ng-icons/phosphor-icons` | Every glyph name in Figma is already a Phosphor name, so design and code stay aligned. Its six weights supply the sidebar's active/inactive distinction for free, and its rounded terminals match the "warm, nicht technisch" product principle. `@ng-icons` also carries Lucide and Material Symbols, so a later switch is a package swap rather than an API change |

**Rejected:** Tailwind (see 2); Angular Material as a base — its visual
opinions (elevation, ripple, density) would be overridden throughout by this
soft glass design; Lit web components — `README.md` already parks them, and they
solve framework-independence, not multi-tenant theming. Should a Lit widget be
built later, it consumes this same token layer, since custom properties pierce
shadow DOM.

### Shape

```css
/* styles/tokens.css — layer 1: primitives (runtime-overridable per property) */
:root {
  --brand-primary: #f45aa8;
  --brand-primary-deep: #cc49af;
  --brand-secondary: #8a5cf6;
  --ink-900: #2c1f4a;
  --ink-600: #6e6486;
}

/* layer 2: semantic roles — the only thing components reference */
:root {
  --color-text-primary: var(--ink-900);
  --color-text-secondary: var(--ink-600);
  --color-surface-glass: rgb(255 246 240 / 0.6);
  --gradient-icon: linear-gradient(223.139deg,
    var(--brand-primary) 55.732%, var(--brand-primary-deep) 88.083%);
}

/* theme axis — roles only */
[data-theme="dark"] { /* … */ }
```

```ts
// property branding axis — primitives only, written at runtime
applyBranding(brand: PropertyBranding) {
  const root = document.documentElement;
  root.style.setProperty('--brand-primary', brand.primary);
  root.style.setProperty('--brand-primary-deep', brand.primaryDeep);
  root.style.setProperty('--brand-secondary', brand.secondary);
}
```

### Setup decisions

- **Plain CSS**, not SCSS. The custom-property layer already supplies what SCSS
  variables would, nesting is native CSS now, and the token layer must be
  runtime-swappable anyway — which SCSS cannot do. One less build step.
  Reversible later via the Angular CLI, per project.
- **Units in `rem`** against a `16px` root (see "Units").
- **Dark-mode trigger:** a `data-theme` attribute on `<html>`. It defaults to
  the system preference (`prefers-color-scheme`) but an explicit user choice in
  `Einstellungen → Darstellung` must win over it — so the attribute is authored
  in three states: absent (follow system), `light`, `dark`.
- **Line heights (decided 2026-08-17):** implement `1.1` for `--text-display`,
  `1.3` for titles and labels, `1.5` for body text. **These values are
  authoritative and live here, not in Figma** — every Figma text layer is left
  on "Auto", which resolves from the font's own metrics (~1.26 for Plus Jakarta
  Sans) and is not reproducible in CSS.

  Accepted consequence: the app's text blocks will not match Figma's exactly —
  headings slightly tighter, body text slightly looser. Given the fixed layout
  this is visible but not harmful. Setting the three values in Figma later would
  remove the drift; creating Text Styles for it was deliberately rejected as
  disproportionate, since the file has none today.

### Still blocked on design

- **No dark palette exists.** Dark mode is decided and deferred — only light
  values are designed. The dark role values in the snippet above are
  placeholders and must be designed before the feature can ship, including
  whether the glass surfaces stay translucent over a dark background.

## Open questions

- `[NEEDS CLARIFICATION: responsive behaviour — no breakpoints exist]`
- `[NEEDS CLARIFICATION: explicit line-heights are missing everywhere]`
- `[NEEDS CLARIFICATION: promote the five raw surface/gradient colors to Figma variables?]`
- `[NEEDS CLARIFICATION: no dark-mode palette exists — is one wanted?]`

### Resolved

- ✅ Canvas is 2x; logical viewport is 1280 × 832 (2026-08-17)
- ✅ `Test_Primary` → `Text_Primary`, typo confirmed (2026-08-17)
- ✅ Spacing keeps the design's literal values, normalised onto a 4px scale (2026-08-17)
