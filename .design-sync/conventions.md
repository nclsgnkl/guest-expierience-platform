# Guest Experience Platform — design system

An in-property guest information screen. One self-hosted instance per property,
running offline on a wall-mounted kiosk. **Design target: 1280×832, landscape.**
1024×768 is the supported floor; below that, and in portrait, nothing is
guaranteed.

## No component library ships here

This design system is authored in **Angular**, so there is no React component
bundle to render — this project carries the *look*, not the parts. Build your
own markup and style it with the vocabulary below; every token, class and rule
here is the real shipping stylesheet, copied verbatim from the app.

## Setup

Two things the app's root does, which your designs must do too:

- **Theme.** A `data-theme` attribute on the root element — `"light"` or
  `"dark"`. Absent means "follow the system". It remaps semantic roles only.
- **Background.** Nearly every surface sits on the shared 16-layer mesh
  gradient. Put `class="mesh-background"` on the full-screen element; it paints
  itself and flips with the theme. Do not hand-roll a background colour.

Sizes are in `rem` against a **viewport-proportional root**: `html` is
`font-size: min(1.25vw, 1.9231vh)`, which resolves to exactly 16px at 1280×832
and scales the entire UI to fit any aspect ratio. There are no breakpoints and
no second layout. Author at the design size and the scaling is automatic —
never add `clamp()` or media queries to compensate.

## The idiom: semantic CSS custom properties

No utility classes, no CSS framework, no component library. Plain CSS with
BEM-ish class names of your own, and **every value a `var(--*)`**.

Tokens come in two layers and you may only ever read the second one. Primitives
(`--ink-*`, `--mesh-*`, `--night-*`, `--brand-*`, `--surface-*-raw`) are the
per-property branding axis and are overwritten at runtime — reading one
directly breaks both branding and dark mode. Semantic roles are the contract:

| Family | Roles |
|---|---|
| Text | `--color-text-primary`, `--color-text-secondary`, `--color-text-on-dark` |
| Surfaces | `--color-surface-glass`, `--color-surface-glass-border`, `--color-surface-menu`, `--color-divider` |
| Background | `--color-mesh-base`, `--color-mesh-1` … `--color-mesh-16` |
| Status | `--color-status-offline`, `--color-status-warning` |
| Gradients | `--gradient-icon`, `--gradient-icon-active`, `--gradient-menu-active`, `--color-icon-active-start`, `--color-icon-active-end` |
| Type | `--font-family-base`, and `--text-display`, `--text-h1`, `--text-title`, `--text-body-lg`, `--text-body`, `--text-label`, `--text-label-active`, `--text-status`, `--text-clock` — each with a matching `-weight`, and a `-line-height` except `--text-label-active`, which shares `--text-label-line-height` |
| Spacing | `--space-1` … `--space-9` (0.25rem → 4rem) |
| Radii | `--radius-md`, `--radius-lg`, `--radius-xl`, `--radius-2xl` |
| Surface trim | `--border-glass`, `--shadow-panel`, `--blur-glass` |
| Sizing | `--size-touch-min`, `--size-icon-tile`, `--size-icon-lg/-md/-sm/-xs/-caret`, `--size-sidebar`, `--size-panel`, `--size-heading` |

Two rules that are not negotiable, because the colours were measured for them:

- `--color-status-offline` is **decorative only** — a dot or a glyph, never
  text. It measures ~2.2:1 on the light surfaces.
- `--color-status-warning` is a **foreground** role — text, glyph or border,
  never a surface fill. Warning is not error; a destructive action is a
  different colour that this system has not built yet.

Only 400, 700 and 800 of Plus Jakarta Sans are self-hosted. There is no 500,
and adding a weight means shipping another font file — design within the three.

### One gotcha: the active-nav gradient glyph

`global.css` ships a `.icon-gradient-active` rule that fills an SVG glyph from
a paint server — but the paint server itself lives in the app's shell template,
which does not ship here. Use the class only alongside this `<defs>` block, or
the glyph falls back to flat `currentColor`:

```html
<svg width="0" height="0" aria-hidden="true">
  <defs>
    <linearGradient id="icon-gradient-active" gradientTransform="rotate(49 0.5 0.5)">
      <stop offset="0%" stop-color="var(--color-icon-active-start)" />
      <stop offset="100%" stop-color="var(--color-icon-active-end)" />
    </linearGradient>
  </defs>
</svg>
```

## Where the truth lives

Read `styles.css` and its two imports — `tokens/tokens.css` (both layers, with
the reasoning for every deviation) and `tokens/global.css` (reset, font faces,
the root scaling rule, `.mesh-background`). `guidelines/` holds the as-built
spec: `tokens.md`, `design-sheet.md`, `dark-theme.md`, and per-screen
composition notes under `screens/` and `screens-inhouse/`.

## The idiom, in one component

This is the real action card off the home screen — the glass-surface recipe
every panel in the product uses:

```css
.action-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  width: var(--size-panel);
  min-height: var(--size-panel);
  padding: var(--space-6) var(--space-5);
  background: var(--color-surface-glass);
  border: var(--border-glass);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-panel);
  backdrop-filter: var(--blur-glass);
  text-align: left;
}

.action-card__icon-tile {
  width: var(--size-icon-tile);
  height: var(--size-icon-tile);
  border-radius: var(--radius-xl);
  background: var(--gradient-icon);
  color: var(--color-text-on-dark);
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-card__title {
  font-size: var(--text-title);
  font-weight: var(--text-title-weight);
  line-height: var(--text-title-line-height);
  color: var(--color-text-primary);
}
```
