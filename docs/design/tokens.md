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
**Last updated:** 2026-08-29

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
/* As built, 2026-08-18 (user-approved; docs/constitution.md v1.2.0): */
html { font-size: min(1.25vw, 1.9231vh); }
/* = 16px at the 1280 × 832 design size, 12.8px at the 1024 × 768 floor,
   larger above — deliberately uncapped, so a bigger display renders a
   proportionally bigger UI. 1280/16 = 80 → 1.25vw; 832/16 = 52 → 1.9231vh;
   the smaller of the two binds, so both axes always fit. */
```

**Amended 2026-08-18.** The root is no longer the constant `16px` above — it is
viewport-proportional, which is what the paragraph above always anticipated.
Tokens are still authored *as though* the root were 16px; only their rendered
physical size changes. Accepted trade: a viewport-derived root overrides the
browser's font-size preference (acceptable on a fixed-purpose kiosk, and the
previous constant was equally absolute).

Two deliberate exceptions, which must **not** scale:

- **Hairlines** — the 1px glass panel border stays `1px`. Scaled borders look
  blurry, not bigger.
- **Blur radii** — `backdrop-filter: blur(8px)` is an optical effect, not a
  measurement.

Each table below gives `rem` as the value to implement, with CSS px at the
baseline root size for reference, and the raw Figma value for traceability.

---

## Colors

> **Light theme only.** Every colour in this section is the light palette, as
> it exists in Figma. The dark palette has no Figma counterpart and lives in
> [`dark-theme.md`](dark-theme.md) — **no dark value belongs in this file**.
> `--color-divider`, added in-house for the status bar, is recorded under
> "In-house tokens" below rather than here.

### Figma variables

| Token | Figma variable | Value | Role |
|-------|----------------|-------|------|
| `--color-brand-primary` | `Brand_Primary-Pink` | `#f45aa8` | Brand pink; icon gradient start, logo, accents |
| `--color-brand-secondary` | `Brand_Secondary-Purple` | `#8a5cf6` | Brand purple |
| `--color-text-primary` | `Text_Primary` | `#2c1f4a` | Headings, card titles, menu labels — default text color |
| `--color-text-secondary` ⚠️ | `Text_Secondary` | `#6e6486` → shipped as `#5a516e` | Card descriptions, supporting copy — **the shipped value deliberately deviates from Figma**, see the note below |
| `--color-text-on-dark` | `Text_On-Dark` | `#ffffff` | Text on dark/saturated surfaces |

> ✅ **Done 2026-08-17:** was `Test_Primary` (a typo); renamed to `Text_Primary`
> in Figma. Spec and design file agree.

> ⚠️ **`--color-text-secondary` — deliberate deviation from Figma, 2026-08-19.**
>
> Figma's `Text_Secondary` is **`#6e6486`**, and that is what this record says
> the design file contains — it has not changed there. The **implementation
> ships `#5a516e`** instead, as `--ink-600` in `frontend/src/styles/tokens.css`.
>
> Reason: accessibility. `#6e6486` measures **4.01 : 1** on a glass card,
> **3.80 : 1** on the Settings panel and **3.60 : 1** on the sidebar in light
> mode — all below **WCAG AA's 4.5 : 1** for normal-size text, and the
> large-text exemption does not apply (the copy on this role is `--text-body`,
> 0.875rem / 14 px at weight 400). `06-review.md` finding #3 on
> `appearance-and-language`; the user approved darkening the role on
> 2026-08-19.
>
> `#5a516e` is `#6e6486` scaled by **0.65 in linear-light space**, which
> preserves the hue — the same purple-grey, one step deeper — so the ink ramp
> still reads as one family rather than gaining a new colour. It measures
> **5.41 / 5.12 / 4.85** on those same three surfaces, sampled from real pixels
> with `frontend/tools/measure.mjs`. The **dark theme is untouched**: it maps
> the role to `--ink-300`, which already passed.
>
> This is recorded, not overwritten: if Figma is ever brought in line, the
> deviation disappears — until then `#6e6486` is what the design file says and
> `#5a516e` is what ships. Full measurements in
> `docs/features/appearance-and-language/02-design.md` §3.4.

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
| `--gradient-icon-active` ⚠️ | `linear-gradient(139deg, var(--color-brand-primary), var(--color-brand-secondary))` | Active sidebar item's **icon glyph** (the `MenuIcons` `state = true` glyph). Added 2026-08-18 — **measured from the reference render, not extracted from Figma.** See the provenance note below |

> ⚠️ **`--gradient-icon-active` provenance: measured, not extracted (2026-08-18).**
>
> Every other value in this file came out of Figma via `design-import`. This one
> did not. It was measured **pixel-by-pixel from
> [`../assets/home-no-artwork.png`](../assets/home-no-artwork.png)** — a linear
> regression fitted across the active "Home" glyph's stroke pixels — because the
> Figma component carries no such fill and the render clearly does. Recorded here
> rather than in a component so the two-layer architecture still holds, but the
> difference in provenance is real and must not be forgotten:
>
> - **If this file is ever re-imported from Figma, this row will not come back.**
>   Either the gradient gets added to the Figma component, or this row is
>   carried forward by hand. Do not treat its absence from a fresh extraction as
>   evidence it was wrong.
> - The measured axis was **139°** in CSS notation (top-left → bottom-right); the
>   measured stops were **`#f45aa8` → `#8b5cf6`**.
> - **The end stop is not a new colour.** `#8b5cf6` is within one 8-bit unit per
>   channel of `Brand_Secondary-Purple` (`#8a5cf6`), which this file has carried
>   since the first extraction — a difference far below perceptual threshold and
>   well inside PNG-render tolerance. It is therefore recorded as
>   `var(--color-brand-secondary)`, **not** as a new primitive. Introducing a
>   near-duplicate violet primitive would have left a per-property branding
>   override (which rewrites `--brand-secondary`) unable to reach the nav icon —
>   the property would rebrand everywhere except there.
> - This also resolves the older note above that `--color-brand-secondary` is
>   "defined but not visibly applied in this frame". It is applied now.
>
> **Realisation in code is not a CSS `background`.** A CSS gradient cannot paint
> an SVG glyph: `fill` takes a paint server, not a `linear-gradient()`. The
> gradient is therefore realised as an SVG `<linearGradient>` referenced by
> `fill: url(#…)`, which has two consequences for this token layer:
>
> - the `139deg` CSS angle becomes `gradientTransform="rotate(49 0.5 0.5)"` on
>   the default left→right SVG gradient axis (SVG's default axis equals CSS
>   `90deg`, so the rotation is `139 − 90`);
> - an SVG `<stop>` cannot consume a CSS gradient value, so the two stops are
>   also exposed as their own semantic roles — `--color-icon-active-start`
>   (= `var(--brand-primary)`) and `--color-icon-active-end`
>   (= `var(--brand-secondary)`) — and the `--gradient-icon-active` value above
>   is the canonical *specification* of what those two roles compose into.
>
> Full technique, including why the rule cannot live in a component stylesheet,
> is in `docs/features/home-screen-prototype/02-design.md` → "Gradient-filled
> icon glyph".
>
> **This overrides a design decision recorded elsewhere.**
> [`screens/home.md`](screens/home.md) → "Assets to export" decided on
> 2026-08-17 that the icon does *not* participate in the active/inactive
> distinction (a `fill`-weight active icon was proposed and rejected as too
> heavy). The user overrode that on 2026-08-18 after seeing the built screen.
> The old rationale is kept in that file alongside the override, not deleted.

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

Three further type tokens — `--text-status`, `--text-clock`, `--text-h1` — were
designed in-house and are **not** in this table: see "In-house tokens" below.

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

> ⚠️ **Corrected 2026-08-18 — extraction error.** This table previously carried
> a single `--size-icon-glyph` at `0.75rem` (12px), derived by reading Figma's
> `aspect-[24/24]` as a 24px dimension. It is an aspect **ratio** (24:24 = 1:1)
> and carries no size at all. Every icon in the built app rendered at roughly a
> third of its intended size as a direct result. The real sizes come from the
> surrounding Figma boxes and differ by context, so one token could never have
> served all of them — hence the three below. Independently corroborated by
> `design-sheet.md`'s own icon scale (Large 36 / Default 28 / Small 22).
>
> **Watch for this class of error elsewhere in this file:** any value sourced
> from a Tailwind-style `aspect-[a/b]` in the MCP output describes proportion,
> not measurement.

| Token | **rem** | px @16 | Figma | Used for |
|-------|---------|--------|-------|----------|
| `--size-icon-tile` | `3.5rem` | 56 | 112 | Filled gradient icon tile |
| `--size-icon-lg` | `2.25rem` | 36 | 72 | Glyph inside a filled icon tile |
| `--size-icon-md` | `1.625rem` | 26 | ~52 | Sidebar navigation icon |
| `--size-icon-sm` | `1.5rem` | 24 | 48 | Inline icons — card arrow, heading sparkle |
| `--size-sidebar` ⚠️ | `6.25rem` | 100 | 199 → 200 | Sidebar width — **shipped at `6.5rem` since 2026-08-29, see the owner decision below** |
| `--size-sidebar-item` ⚠️ | `4.5rem` | 72 | 146 → 144 | Sidebar item square — **but see the measured correction below** |
| `--size-panel` | `12.25rem` | 196 | 392 | Glass panel (square) |
| `--size-heading` | `24.375rem` | 390 | 780 | Heading block width — **see the correction below** |

> ⚠️ **`--size-widget-col` removed, 2026-08-18.** It used to sit in this table
> at `13.75rem` (220 CSS / 443 raw). The widget column is `flex: 1` in Figma and
> in the implementation, so a fixed width was never referenced by anything and
> the token was deleted from `frontend/src/styles/tokens.css`. The 220px figure
> survives only as the value the column happens to measure at the 1280 × 832
> design size (~~measured: 224px~~ — **measured 220px since 2026-08-29**, see
> the `--size-sidebar` deviation below: the column is what absorbs the rail's
> extra 4px, and 224 → 220 is that whole cost) — not as something to set.
>
> ⚠️ **`--size-heading` — halving correction, 2026-08-18.** It shipped as
> `48.75rem` (780px), which is the **raw Figma value**, not the CSS one. Every
> other size in `docs/design/screens/home.md` → Components is halved on the way
> in (199 → 100, 112 → 56, 392 → 196, 443 → 220, display type 96 → 48); this one
> was not, so the heading block rendered twice as wide as designed and its
> headline stayed on one line where the reference render wraps to two. The CSS
> value is **390px**. Verified in a browser: at 390px the headline breaks
> 359 + 375px and the paragraph 347 + 150px, against the reference render's
> 356 + 369 and 348 + 150.4. Same error family as the `aspect-[24/24]` glyph
> misread above.
>
> ⚠️ **`--size-sidebar-item` — measured correction, 2026-08-18.** The `4.5rem`
> above is the Figma **component's own** box (`MenuIcons`, 146 raw). In the
> exported render the item does not keep that width: it and its active-state
> pill measure **84 × 84 CSS px**, which is exactly `--size-sidebar` (100) minus
> twice the sidebar's `--space-3` (8) horizontal padding — i.e. the item is
> stretched to fill the sidebar's inner width by the sidebar's own auto-layout.
> Measured from [`../assets/home-no-artwork.png`](../assets/home-no-artwork.png)
> by edge scan: at y = 240 the active pill spans x 40 → 124 inside a sidebar
> spanning x 32 → 132; vertically the pill spans y 195 → 279.
>
> **Implement it as derived, not as a second literal:** the item takes the
> sidebar's inner width and is square (`width: 100%` inside the sidebar's
> padding box, `aspect-ratio: 1`). That keeps one source of truth
> (`--size-sidebar` and `--space-3`) instead of a magic 84. The 72px token stays
> recorded here for traceability back to the Figma component, but it is not what
> the screen shows — and taking it literally is what made the "Smart Home" label
> wrap in the built app (the 72px item leaves a 56px label box; the label needs
> ~71px, which the real 84px item provides).
>
> This is the same class of error as the `aspect-[a/b]` one flagged above: a
> value read off a *component definition* rather than off its *instance in the
> frame*.

> ⚠️ **`--size-sidebar` — deviation from Figma, 2026-08-29 (owner decision,
> recorded not corrected).** The extracted value is `6.25rem` (100 CSS px, 199
> raw) and that is what this table records. **What ships is `6.5rem`** (104px
> at the 1280 × 832 design size), so the derived item is **88px** square rather
> than 84px. This is a design change the owner took deliberately, not a
> re-reading of the frame; a re-import will bring `6.25rem` back and must not
> be treated as a correction.
>
> **Why.** `Einstellungen` is the label that puts up the tightest cell at the
> 1024 × 768 floor, and a standard module — it is on screen on every route.
> *(Corrected 2026-08-29: it is **not** the longest label in the catalogue.
> Measured at 1280 × 832, `Hausordnung` is wider — 78px inactive / 79px active
> against `Einstellungen`'s 77/77 — and is therefore that viewport's tightest
> cell at 4.0 / 3.5px per side against 4.5px. At the 1024 × 768 floor the order
> reverses: `Hausordnung` 64/64 in the 68.81px box (2.41px per side) against
> `Einstellungen`'s 63/66 (2.91 inactive, **1.41 active**). The floor is what
> the decision below turns on, so the decision is unchanged — the superlative
> was not.)* At `6.25rem` it needed 77px
> of a 84px item at 1280 × 832 and 66px of a 67.22px item at 1024 × 768 in the
> **active** ExtraBold state, i.e. **0.61px of clearance per side** at the
> supported floor. The owner asked for a visible inset of 2px per side; the
> measurement said that at the shipped width even **1px** put the word back on
> two lines at 1024. The alternatives, per reference cell, all measured:
>
> | inset per side, at `--size-sidebar: 6.25rem` | 1280 inactive | 1280 active | 1024 inactive | 1024 active |
> |---|---|---|---|---|
> | 0px (what shipped 2026-08-29 morning) | fits, 3.5px clear | fits, 3.5px clear | fits, 2.11px clear | fits, **0.61px clear** |
> | 1px | fits | fits | fits | **breaks to two lines** |
> | 2px (the owner's original wish) | fits | fits | fits | **breaks to two lines** |
>
> So 2px was **rejected on measurement**, not on taste: it is carried by three
> of the four reference cells and fails the fourth. The owner then weighed
> `+0.5rem` (`6.75rem`, enough for the full 2px) and **rejected it as too
> expensive for the content area**, and settled on `+0.25rem` with a **1px**
> inset (`--size-sidebar-label-inset`, next section).
>
> **What it costs.** The content area starts 4px further right at 1280 × 832
> (3.19px at 1024 × 768) and is that much narrower.
> Measured after the change, both reference viewports × both themes × both
> languages, `tools/measure.mjs` (168 cells) and `tools/measure-states.mjs`:
> Home's heading block still starts at y = 88 and the card row still ends at
> y = 744 (the signed-off baseline, unmoved — the loss is horizontal only);
> heading block and card row moved 172 → 176 (x) and 992 → 996 (right); the
> status bar is right-anchored and did not move at all (right edge 1248 / 998),
> so its clearance to the card row went 52 → **48px** and its vertical
> clearance to the heading is unchanged at 20px; the staleness pill is
> right-anchored too and did not move. The overflow popover's tiles derive from
> the same expression and grew with it — panel 470 × 200 → **490 × 208** at
> 1280, 376 × 160 → **392 × 167** at 1024, tiles 84 → 88 / 67 → 70, still
> inside the viewport with the grid not scrolling. **0 overflowing, 0
> truncated, 0 clamped labels, 200 × NO-SCROLL OK.**
>
> **Who actually pays the 4px — nobody visible (measured 2026-08-29,
> re-measured after the review of the same day).** "The content area is 4px
> narrower" is true of `.shell__content` and misleading about everything
> inside it, and the owner weighed `+0.5rem` against exactly that sentence.
> Both reference viewports, the two `--size-sidebar` values compared in one
> live render:
>
> | at 1280 × 832 | `6.25rem` | `6.5rem` |
> |---|---|---|
> | `.shell__content` | 1076 | **1072** |
> | `.home-content__screen` (heading + card row) | 820 | **820 — unchanged** |
> | `--size-heading` (the headline column) | 390 | **390 — unchanged** |
> | `.home-content__widgets` (the empty reserved column) | 224 | **220** |
>
> At 1024 × 768 the same comparison reads 860.81 → 857.63, 655.97 → 655.97,
> and the widget column 179.25 → **176.06**. The card row is sized by its four
> cards and the heading column by `--size-heading`; the widget column is
> `flex: 1` and absorbs the whole difference. So the price of `+0.25rem` was
> 4px of a column that is **empty by design** (see `--size-widget-col` above),
> and the price of the rejected `+0.5rem` would have been 8px of it — not 8px
> of anything a guest can see. Recorded because the rejection was made on the
> other reading; **it is not an argument for reopening the decision**, which
> the owner took and which shipped.
>
> **`--size-sidebar` has a third consumer, and it is not in the list above.**
> Besides `.sidebar` and the grid of `.sidebar-overflow`, the escalated
> staleness banner reads it: `staleness-banner.component.css` bounds itself
> with `max-width: calc(100% - var(--size-sidebar) - var(--space-8) - 2 *
> var(--space-7))` so the bar can never reach over the rail. Two effects, both
> measured 2026-08-29 with `tools/measure-states.mjs` against a live backend:
>
> - **First order: none today.** The bound resolves to 1076 → **1072px** at
>   1280 × 832 and 860.8 → **857.59px** at 1024 × 768, against a bar that is at
>   most **694px** wide (English, standby host, 1280) and 553px at 1024. The
>   `max-width` is nowhere near binding, so the bar's own box does not move.
> - **Second order, and this is the one that matters: the bar did not move but
>   the heading column did.** The bar is right-anchored; Home's headline column
>   slid 4px (3.19px at 1024) to the right, so the gap the bar's left edge
>   keeps to the headline's ink shrank by that much. Measured in the worst cell
>   the instrument can produce — the 37-character unbroken display name, five
>   headline lines — the bar's left edge clears the headline ink by **5px at
>   1280 × 832** (bar x 569, ink reaches x 564) and **~6px at 1024 × 768** (bar
>   x 458.83, ink reaches x 453). With the seeded name it is 7px / ~10px. The
>   instrument reported **no COLLISION in any banner cell**, so the clearance
>   is intact — but it is the same margin `docs/features/guest-payload-consolidation/06-review.md`
>   already called *"four and five pixels of luck"*, now ~4px thinner, and the
>   headline is manager-authored (`{stay.displayName}`). Recorded as a fact for
>   whoever next touches either number, not repaired here.

## In-house tokens — no Figma counterpart (2026-08-18, extended 2026-08-20, 2026-08-27 and 2026-08-29)

Seven tokens added by `docs/features/appearance-and-language/` for the status
bar (`02-design.md` §8.3) and the Settings screen (§9), plus one added by
`docs/features/guest-frontend-integration/` (2026-08-20, the D1 convention —
in-repo screen designs live in [`screens-inhouse/`](screens-inhouse/), new
tokens they need land here), one by `docs/features/guest-payload-consolidation/`
(2026-08-27, the escalated staleness banner) and one by
`docs/features/home-screen-prototype/` (2026-08-29, the rail label's inset —
an owner decision, see `--size-sidebar` above). They are kept **in
their own section, deliberately not merged into the extracted tables above**,
for the same reason the dark palette lives in its own file: this document's
value is that its tables are what Figma contains, and one section that says
otherwise is a warning, whereas ten rows sprinkled through five tables is a
broken record.

> ⚠️ **Provenance: designed in-house, not extracted** — same standing as the
> `--gradient-icon-active` note above, and the same consequences.
>
> - **Neither screen exists in Figma.** The status bar was read off
>   [`assets/home-concept-ai.png`](assets/home-concept-ai.png), which
>   is an **AI concept mock-up**, not a design file — it supplies composition
>   and rough proportion, never token-accurate values. The Settings screen has
>   no render at all; it is the feature's own design (`02-design.md` §9, which
>   states that gap openly).
> - **A re-import will not bring these back.** Their absence from a fresh
>   extraction is not evidence that they are wrong.
> - **Where an existing system rule covered a value, it was used rather than
>   invented** — see the corroboration column. The one exception is
>   `--size-sidebar-label-inset`: nothing in the system says 1px, and its row
>   says so plainly rather than dressing an owner's choice as a derivation.

| Token | Value | Weight / line height | Used for | Corroborated by |
|---|---|---|---|---|
| `--size-touch-min` | `2.75rem` (44px) | — | Minimum touch target: status-bar triggers, dropdown rows, Settings option buttons | `design-sheet.md` → *Touchscreen-Regeln* (44px minimum) |
| `--size-icon-xs` | `1.125rem` (18px) | — | Status-bar glyphs (globe, sun/moon) beside a `0.875rem` label | Sized against the label, one step below `--size-icon-sm` (24px) |
| `--size-icon-caret` | `0.75rem` (12px) | — | The disclosure chevron after a status-bar glyph | Subordinate to the glyph it follows, so the pair reads as one control |
| `--size-sidebar-label-inset` | `0.0625rem` (1px) | — | How far the rail label stops short of its item's edge on each side (`sidebar.component.css`), so the ink is not flush against the active pill's border | Nothing corroborates it — **it is an owner decision of 2026-08-29**, chosen against the measurement in the `--size-sidebar` note above: 2px was wanted, 2px and even 1px broke `Einstellungen` at the old width, and 1px at `--size-sidebar: 6.5rem` is the compromise the owner took. Measured clearance ink-to-label-edge afterwards, `Einstellungen`: 4.5px per side at 1280 × 832 (both weights), 2.91px inactive and **1.41px active** at 1024 × 768 |
| `--text-status` | `0.875rem` (14px) | 700 / 1.3 | The language trigger's short label | Reuses `--text-body`'s size at label weight |
| `--text-clock` | `1.125rem` (18px) | 700 / 1.3 | The clock | Reuses `--text-title` / `--text-body-lg`'s size |
| `--text-h1` | `2rem` (32px) | 800 / 1.1 | Screen title — one step below `--text-display`; first used by Settings | `design-sheet.md` → *Type Scale*, H1 32 / ExtraBold 800 / 1.1 (📋 there, built here) |
| `--color-divider` | `color-mix(in srgb, var(--ink-900) 25%, transparent)` | — | The hairline between the appearance control and the clock | A role, not a fourth grey: mixed from the ink primitive so it stays rebrandable and survives the theme flip ([`dark-theme.md`](dark-theme.md) §3) |
| `--color-status-offline` | `#A79DBA` | — | **Decorative status indicators only, never text**: the pairing screen's offline dot, the connection screen's `WifiSlash` glyph, the staleness pill's dot ([`screens-inhouse/`](screens-inhouse/), 2026-08-20) | `design-sheet.md` → *Status* 📋 palette, first value of it realised. Theme-stable (same value in both themes); as a text colour it would fail AA on the light surfaces (~2.2 : 1) — information always rides an adjacent standard text role |
| `--color-status-warning` | `#895806` (light) · `#f9b84e` (dark) | — | **Foreground warning role — text, glyph or border, never a surface fill**: the escalated staleness banner's `WifiSlash` glyph ([`screens-inhouse/connection.md`](screens-inhouse/connection.md), 2026-08-27, `guest-payload-consolidation` REQ-952) | `design-sheet.md` → *Status* 📋 palette, **second** value realised from it. Derived from its `Warning #F8B84E` (hue 37°): the dark theme **is** that value, essentially unchanged, and the light one is the same hue darkened to a working lightness. Measured on glass — light **4.64** over `--mesh-6` and **5.17** over `--mesh-8`, dark **5.63** over `--night-6` and **4.86** over `--night-8` — all above 4.5 : 1 *deliberately*, though a decorative glyph needs none of it: text-capable from the start is the lesson of `--color-status-offline`'s permanent "never a text colour" caveat one row up. **Warning is not error** — `Danger #F26666` sits beside it in the same palette for destructive actions |

> **`--text-clock` is 700, not `design-sheet.md`'s "Caption 13px Medium 500"**
> for times and meta info. Only 400, 700 and 800 are self-hosted (`global.css`),
> so 500 would mean shipping a fourth `woff2` for one string on one screen — a
> runtime cost the on-premise principle does not justify. **Do not "fix" this
> by adding a font file.** Logged as a deliberate deviation, not an oversight.

`--color-divider` and `--color-status-warning` are the two the theme axis
overrides; the divider's dark value and reasoning are in
[`dark-theme.md`](dark-theme.md) §3, the warning role's in its own row above
(the v1 palette value *is* the dark theme; the light one is derived from it).
The other seven are colourless and identical in both themes, and
`--color-status-offline` is deliberately theme-stable (see its row).

> **The warning role is a FOREGROUND role.** `#895806` as a *background* would
> fail any dark text placed on it, so a tinted warning surface — if one is ever
> needed — is a **second** token, not this one reused. It also carries the
> `[data-theme='dark']` override on the **role**: the dark primitive
> `--status-warning-dark-raw` is a new value, added, never an override of the
> light primitive (`frontend/CLAUDE.md` → Appearance & language).

## Breakpoints

**None — deliberately.** Decided 2026-08-17. Still none after the 2026-08-18
revision below: scaling to the new 1024 × 768 floor is a *proportional* change,
not a second layout, so it introduces no breakpoint.

The MVP targets **large touchscreens only**, sized generously enough to render
the layout exactly as designed: permanent sidebar, four cards across, 390px
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

~~`1280px`.~~ **Revised 2026-08-18 — now `1024 × 768`, landscape.** (Original
text: `1280px`; below it the layout breaks rather than degrades, since the
sidebar, the 390px heading block and the 820px card row (four 196px cards with
gaps) consume the full width by design.)

The user set a supported floor of **1024 × 768 in landscape** — classic
iPad-landscape, chosen as a typical tablet size after the user explicitly
delegated the choice. Between that floor and the 1280 × 832 design size the UI
**scales**; it does not reflow:

| | |
|---|---|
| Design size | 1280 × 832 (root font size 16px — the `rem` baseline above) |
| Supported floor | **1024 × 768, landscape** (0.8 × the design width) |
| Between them | The same composition, proportionally smaller. No breakpoints, no rewrapping, no element moving to another row, **no scrollbars** |
| Above the design size | Proportions kept, everything simply larger — as "Units" already anticipated |
| Portrait | **Out of scope.** The design is unambiguously landscape; a portrait viewport is treated exactly like a below-floor one |
| Below the floor | Nothing guaranteed. The `min-width` guard rail stays, moved from `1280px` to `1024px`, and scrolling there is acceptable |

**"No scrollbars" is a project-wide rule, not a Home-screen one** (user, 2026-08-18:
*"das gilt auch für jeden zukünftigen Screen"*). It is proposed as a
constitution-level constraint; until that amendment is ratified, treat it as
binding on any screen planned after this date.

**Resolved 2026-08-18 (was `[NEEDS CLARIFICATION]`).** The user approved the
recommended mechanism: `html { font-size: min(1.25vw, 1.9231vh) }`, uncapped —
see "Units" above for the as-built rule and `docs/constitution.md` v1.2.0 for
the ratified constraint. Implemented and measured on every route at 1024 × 768,
1280 × 832 and 1440 × 900: no overflow on either axis at any of them.

**Still true, unchanged:** treat sizes below the floor as **out of scope rather
than broken** — the app is deployed on known hardware, so this is a guard rail
for development, not a user-facing state. A simple `min-width` on the layout
root with page scrolling is enough; no dedicated fallback screen is worth
building until phone support becomes real.

**Legibility consequence, flagged not solved:** at the floor everything is 0.8×,
so `--text-body` renders at 11.2px and the sidebar labels at 9.6px.
[`design-sheet.md`](design-sheet.md) already calls 14px/12px "at the lower
edge" for an 11–13" touchscreen; 11.2/9.6px is below that edge. The floor is a
*supported* size, not the *target* size (1280 × 832 stays the target), but this
belongs on the same hardware check that entry already asks for.

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
  --ink-600: #5a516e; /* deviates from Figma's #6e6486 — WCAG AA, see above */
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
- **Units in `rem`**, authored against a `16px` root; the root itself is
  viewport-proportional since 2026-08-18 (see "Units").
- **Dark-mode trigger:** a `data-theme` attribute on `<html>`, carrying an
  **always-resolved** value — `light` or `dark`, never absent. An explicit
  guest choice in `Einstellungen → Darstellung` wins over the property's
  default.

  > ⚠️ **Corrected 2026-08-19 — user decision #4, approved 2026-08-18.**
  > This entry previously read: *"It defaults to the system preference
  > (`prefers-color-scheme`) but an explicit user choice … must win over it —
  > so the attribute is authored in three states: absent (follow system),
  > `light`, `dark`."* Two halves of that are now wrong, and the correction is
  > recorded rather than silently rewritten because the old sentence was the
  > basis of an earlier plan.
  >
  > - **The app never writes the absent state.** The attribute is always a
  >   resolved `light` or `dark`, so nothing ever renders in the
  >   attribute-absent state (REQ-105). `:root`'s values *are* the light
  >   theme; `[data-theme='light']` exists for completeness and overrides
  >   nothing.
  > - **The startup value is the backend-delivered default** (`light` for the
  >   example property) — **not** `prefers-color-scheme`. The property's
  >   configuration owns the default; the guest's stored choice, when one
  >   exists, replaces it before the first paint.
  > - **Three states stay *authorable*, and `system` is one of them.** A
  >   property may configure a `system` option, and only then is
  >   `prefers-color-scheme` consulted — as an input to the resolution, not as
  >   the default. The example property does not configure it, so `system` is
  >   not in the shipped option list.
  >
  > Full model: `docs/features/appearance-and-language/02-design.md` §6
  > (defaults ⊕ the guest's choice ⊕ persistence).
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

- ✅ **Resolved 2026-08-18 — ~~No dark palette exists~~.** A dark palette was
  **designed in-house** (not in Figma) as part of
  `docs/features/appearance-and-language/` and is documented in
  **[`dark-theme.md`](dark-theme.md)** — sixteen dark mesh primitives,
  two new ink primitives, three new surface primitives, and the full
  `[data-theme='dark']` role table with measured WCAG contrast for every text
  role.

  Three things follow, and all three are deliberate:

  - **No dark colour value lives in this file, and none ever should.** This
    document records what exists in **Figma**; the dark palette does not, and
    a re-import will not produce it. Keeping it out is what stops hand-authored
    values from becoming indistinguishable from extracted ones. The `⚠️`
    provenance note under `--gradient-icon-active` above is the one exception
    this file already carries — a second, twenty-five rows long, would have
    turned the warning into background noise.
  - **The sub-question is answered: the glass surfaces do stay translucent.**
    `--blur-glass` is `blur(8px)` in both themes, the panel keeps ~1.5 : 1
    contrast against its own backdrop in both, and the dark surfaces are a
    cool-white lightening film instead of light mode's warm-white one, at a
    much lower alpha (the value itself is in `dark-theme.md`, not here).
    Glassmorphism is a property of the design, not of the light palette.
  - **If a dark variant is ever drawn in Figma, it supersedes
    `dark-theme.md`** and this entry gains a real extraction — at which point
    the values belong here like any other.

  Still open in light mode, and **not** a dark-mode problem: `--color-text-secondary`
  measures 3.99 : 1 on a glass panel and 3.48 : 1 on the sidebar, both below
  WCAG AA. Recorded in `dark-theme.md` §4; unchanged here because fixing it
  would alter the signed-off light screen.

## Open questions

- `[NEEDS CLARIFICATION: responsive behaviour — no breakpoints exist.` **Partly
  resolved 2026-08-18:** a supported range (1024 × 768 landscape → 1280 × 832 →
  larger) and a no-scrollbar rule are now decided; still no breakpoints, because
  the range is covered by proportional scaling. The scaling *mechanism* remains
  approval-gated — see "Minimum supported width".`]`
- `[NEEDS CLARIFICATION: explicit line-heights are missing everywhere]`
- `[NEEDS CLARIFICATION: promote the five raw surface/gradient colors to Figma variables?]`

### Resolved

- ✅ **A dark-mode palette was wanted, and now exists (2026-08-18).** Answers
  the former *"no dark-mode palette exists — is one wanted?"*. Yes: designed
  in-house, documented in **[`dark-theme.md`](dark-theme.md)**, realised in
  `frontend/src/styles/tokens.css`. **No dark value lives in this file**, and a
  Figma re-import will not produce any — see "Still blocked on design" above
  for the full resolution, including that the glass surfaces stay translucent.
- ✅ Canvas is 2x; logical viewport is 1280 × 832 (2026-08-17)
- ✅ `Test_Primary` → `Text_Primary`, typo confirmed (2026-08-17)
- ✅ Spacing keeps the design's literal values, normalised onto a 4px scale (2026-08-17)
