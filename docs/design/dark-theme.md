# Dark Theme

> ## ⚠️ Provenance — read this before trusting a value
>
> **This file is not extracted from Figma. Nothing in it is.**
>
> - **Designed in-house on 2026-08-18**, by derivation from the light palette,
>   as part of `docs/features/appearance-and-language/` (`02-design.md` §3).
>   The brief was the user's own: *"the same properties as the light mode"* —
>   read as *the same structure*, not the same lightness.
> - **No Figma counterpart exists.** There is no dark frame, no dark variable
>   set, no dark component variant in `AirBnB-Screen`.
> - **A re-import will not reproduce it.** If `/design-import` is run again,
>   none of these values come back, and their absence from a fresh extraction
>   is **not** evidence that they were wrong. Same trap as `tokens.md`'s
>   `--gradient-icon-active` row, one file larger.
> - **If a dark variant is ever drawn in Figma, that becomes authoritative**
>   and this file demotes itself to a deviation log — the same relationship
>   `design-sheet.md` has to `tokens.md` today.
>
> **Why this lives in its own file** (decided in `02-design.md` §2):
> [`tokens.md`](tokens.md) records what *exists in Figma* and is treated as the
> authority precisely because of that — a hand-authored 25-value palette
> written into it would be indistinguishable from extracted values within a
> month. [`design-sheet.md`](design-sheet.md) is a German prose document about
> the system as a whole; a 30-row token table with contrast measurements would
> make it the de-facto token file for one theme and the prose file for the
> other. So: one topic, one file, unambiguous provenance, and both existing
> files stay exactly as honest as they are today.

**Realised in:** `frontend/src/styles/tokens.css` — dark primitives on `:root`,
dark roles inside `[data-theme='dark']`.
**Last updated:** 2026-08-19 (checked value-for-value against `tokens.css` as
built in steps S1/S2/S14).

**Related:** [`tokens.md`](tokens.md) (the light/Figma record and the token
architecture) · [`design-sheet.md`](design-sheet.md) (the system as a whole) ·
`docs/features/appearance-and-language/02-design.md` §3 (the full rationale,
including every rejected alternative)

---

## The rule this palette obeys

**The theme axis touches semantic roles only. Not one primitive is redefined
by it** (REQ-102).

That is not tidiness. Per-property branding writes primitives **inline on
`:root`** at runtime, and an inline style beats a `[data-theme='dark']`
stylesheet rule at any specificity. The moment the theme axis overrides a
primitive, a *branded* property silently gets the wrong value — a defect
visible only to paying customers. Dark mode therefore adds **new primitives**
(never overrides) and **remaps roles**.

The same rule is what forced the mesh gradient out of `--mesh-*` primitives
and into `--color-mesh-*` semantic roles (REQ-103).

---

## 1. Method

The light mesh is a 4 × 4 grid running warm pink → peach across the top, mauve
through the middle and violet along the bottom. The dark mesh keeps **the same
hue at every one of the sixteen positions and the same spatial movement**, and
moves saturation and lightness into a night band.

Two deliberate deviations from a naive HSL scale-down:

- **The warm hues are pulled back toward rose.** Peach (`#fe9b82`, hue 13) at
  15 % lightness is *brown*. The top row keeps its warmth as an **ember** —
  deep rose to amber — instead of becoming mud.
- **Saturation stays relatively high (26–55 %) at low lightness.** Desaturating
  proportionally produces the grey-purple sludge that makes most dark modes
  feel cheap, and the design's own brief (*Warm · Ruhig · Premium*) rules it
  out.

---

## 2. Dark mesh — primitives `--night-1 … --night-16`

Row by row from the top left, alongside the light primitive each one mirrors.
Positions, stop percentages and the sixteen-layer `radial-gradient` technique
are unchanged from `tokens.md` → "Background implementation" — only the colour
band moves.

| | col 1 | col 2 | col 3 | col 4 |
|---|---|---|---|---|
| **row 1** | `#4f172a` ← `#f5668c` | `#4d1925` ← `#f4779c` | `#4b211b` ← `#fe9b82` | `#4e2f1d` ← `#fdcfad` |
| **row 2** | `#451c30` ← `#efa2b5` | `#3e1d28` ← `#e8b2bc` | `#431e21` ← `#f3b5aa` | `#472b1f` ← `#fdd6be` |
| **row 3** | `#361c34` ← `#d5b3d0` | `#321b28` ← `#c693a7` | `#2a1920` ← `#a27684` | `#3a1d22` ← `#f4b9bf` |
| **row 4** | `#2d1c3f` ← `#b69ed1` | `#291c40` ← `#af99d2` | `#2f1d3e` ← `#bea3d6` | `#3d1f3d` ← `#e4bfdd` |

The base colour under the sixteen layers is **`--night-6`**, mirroring light
mode's choice of `--mesh-6` for the same reason: it sits in the grid's interior
and is closest to the palette's average.

### Other new primitives

Added to `:root`, never overridden by a theme:

| Primitive | Value | Role it serves |
|---|---|---|
| `--ink-050` | `#f7f2fb` | Dark `--color-text-primary` |
| `--ink-300` | `#c6bcd8` | Dark `--color-text-secondary` |
| `--surface-glass-dark-raw` | `rgb(246 240 255 / 0.14)` | Dark `--color-surface-glass` |
| `--surface-glass-border-dark-raw` | `rgb(255 255 255 / 0.14)` | Dark `--color-surface-glass-border` |
| `--surface-menu-dark-raw` | `rgb(246 240 255 / 0.09)` | Dark `--color-surface-menu` |

`--ink-050` is **near-white with a violet cast, not pure white** — the same
warmth relationship the light ink (`--ink-900 #2c1f4a`) has to black, inverted.

---

## 3. Semantic roles in `[data-theme='dark']`

Everything below is a role. The light column is unchanged and is repeated only
so the pair can be read in one place.

| Role | Light (unchanged) | Dark | Why |
|---|---|---|---|
| `--color-text-primary` | `var(--ink-900)` `#2c1f4a` | `var(--ink-050)` `#f7f2fb` | Near-white with a violet cast, not pure white |
| `--color-text-secondary` | `var(--ink-600)` `#5a516e` | `var(--ink-300)` `#c6bcd8` | Quieter than primary. Light's primitive was `#6e6486` until 2026-08-19, when it was darkened to clear AA (§4) — dark already sat above it and is unchanged |
| `--color-text-on-dark` | `var(--ink-on-dark)` `#ffffff` | *unchanged* | It means "on a saturated brand fill", which is saturated in both themes |
| `--color-surface-glass` | `rgb(255 246 240 / 0.6)` | `rgb(246 240 255 / 0.14)` | A lightening film instead of a warming one. Cool-white tint so it does not turn the night mesh muddy |
| `--color-surface-menu` | `rgb(255 246 240 / 0.4)` | `rgb(246 240 255 / 0.09)` | **The 2 : 3 menu : glass alpha ratio is preserved exactly** (0.4/0.6 = 0.09/0.14 ≈ 0.64) — the sidebar stays the quieter surface by the same margin |
| `--color-surface-glass-border` | `rgb(255 255 255 / 0.4)` | `rgb(255 255 255 / 0.14)` | Still a white hairline. Lower alpha because white on dark carries much further |
| `--color-divider` | `color-mix(in srgb, var(--ink-900) 25%, transparent)` | `color-mix(in srgb, var(--ink-050) 22%, transparent)` | A hairline on a dark surface is a light one. 22 %, not 25 %, for the same reason the glass border drops |
| `--color-mesh-1 … -16` | `var(--mesh-1 … -16)` | `var(--night-1 … -16)` | §2 |
| `--color-mesh-base` | `var(--mesh-6)` | `var(--night-6)` | §2 |
| `--gradient-menu-active` | `-74.707deg`, `rgb(255 243 250 / 0.9)` 7.717% → `/ 0.5` 92.116% | same axis and stops at `/ 0.18` → `/ 0.1` | Same colour, same axis, same 9 : 5 stop ratio, scaled to 20 % alpha |
| `--color-icon-active-start` | `var(--brand-primary)` | `color-mix(in oklab, var(--brand-primary) 65%, white)` | §5 |
| `--color-icon-active-end` | `var(--brand-secondary)` | `color-mix(in oklab, var(--brand-secondary) 65%, white)` | §5 |
| `--gradient-icon` | brand → brand-deep | *unchanged* | Design principle 4, *"Pink und Lila sind Akzente"*. The filled icon tile and the logo keep the brand's own colours and glow against the night mesh — that is the point of an accent |
| `--shadow-panel` | `0 10px 40px 0 rgb(44 31 74 / 0.1)` | `0 10px 40px 0 rgb(6 3 14 / 0.5)` | A shadow on a dark surface must be far darker and stronger to exist at all |
| `--blur-glass`, radii, spacing, type, sizing | — | *unchanged* | "The same properties" taken literally: **only colour changes** |

**The glass stays glass.** This answers `tokens.md`'s open sub-question
directly: the surfaces remain **translucent and blurred** in dark mode.
Glassmorphism is a property of the design, not of the light palette —
`--blur-glass` is `blur(8px)` in both themes, and the panel-versus-backdrop
contrast is held at ~1.5 : 1 in both (§4).

`color-mix()` is a platform CSS feature, baseline in every current engine, and
adds no dependency — but this palette is its **first use in the codebase**
(`--color-divider` and the two active-glyph stops). Recorded so it is a
decision, not a surprise.

---

## 4. Measured contrast (REQ-104)

Method: each surface is alpha-composited over the mesh's average colour
(`#dea3b4` light, `#3d1e2b` dark), then WCAG 2.x contrast is computed against
the text role that sits on it. Measured, not asserted.

| Pair | Light | Dark | |
|---|---|---|---|
| Primary text on a glass panel | **10.89** | **8.91** | the one role that scores lower in dark — both far above AAA |
| Secondary text on a glass panel | **5.38** | **5.42** | both pass AA — light read 3.99 until the second note below |
| Primary text on the sidebar | **9.52** | **10.40** | |
| Secondary text on the sidebar | **4.70** | **6.32** | both pass AA — light read 3.48 until the second note below |
| Primary text directly on the mesh | **7.15** | **13.40** | |
| Secondary text directly on the mesh *(section placeholder, sampled)* | **3.77** ⚠️ | **8.32** | light still fails AA — open defect, third note below |
| Status-bar text on the mesh, across the bar's own box | **7.66 – 8.71** | **11.80 – 12.39** | a range, not a point — see the note below the table |
| Glass panel vs. its own backdrop | 1.52 | 1.50 | the glassmorphism "presence" is preserved |
| Sidebar vs. its own backdrop | 1.33 | 1.29 | |
| Active nav pill vs. sidebar | 1.41 | 1.69 | slightly stronger in dark, as dark UI needs |

> **The status-bar row is a range, and was corrected in `07_improve`.** It read
> **10.49** (light) / **10.90** (dark) until `06-review.md` finding #2. Those
> figures were taken at mesh **grid point 4**, which is *lighter* than the mesh
> actually is where the bar sits; the bar has no surface of its own, so its text
> composites straight onto whatever the mesh is beneath it, and the mesh is not
> one colour across a ~204 px box. The values above are what
> `frontend/tools/measure.mjs` prints: 52 pixels sampled across the bar's own
> box at 1280 x 832, the 10 % most extreme samples at each end trimmed (those
> are the glyph pixels), the status-bar text role measured against each. Re-run
> it rather than re-deriving the number by hand. **No accessibility
> consequence** — 7.66 : 1 still clears AAA; the old figure was simply
> optimistic by about two points.

> **The light `secondary` rows changed on 2026-08-19: `--ink-600` was darkened
> to reach AA.** They read **3.99** (glass) and **3.48** (sidebar) here, with
> the Settings hint measuring **3.80**, until `06-review.md` finding #3 — all
> below AA's 4.5 : 1, and the large-text exemption does not apply (`--text-body`
> is 0.875rem / 14 px at weight 400). `--ink-600` was changed **`#6e6486` →
> `#5a516e`** by scaling the old value in **linear-light space** (x0.65), which
> preserves the hue: the same purple-grey, deeper, so the ink ramp still reads
> as one family. **The dark theme was not touched** — `--color-text-secondary`
> maps to `--ink-300` there and already passed at 5.42 — and re-measurement
> confirms every dark figure in this table is unchanged to the hundredth. This
> is a **deliberate deviation from Figma**, recorded as such in `tokens.md`.

> **The one remaining ⚠️ row cannot be fixed by the role.** The section
> placeholder's note is the only `--color-text-secondary` consumer with **no
> surface under it**: it composites straight onto the mesh. It measured
> **2.79 : 1** before the darkening and **3.77 : 1** after (sampled at
> 1280 x 832, stable across four routes x three viewports), and at
> `--text-body-lg` / 18 px / weight 400 it is not WCAG large text, so 4.5 : 1
> applies. Clearing it on the raw mesh would need `--ink-600` near `#4e4660` —
> about twice this darkening — pulling the shared role toward the primary on
> every surface to compensate for one screen's missing surface. Tracked as its
> own defect; the cause is the absent surface, not the role.

**The claim that matters: dark mode has no text role below 4.5 : 1, and since
2026-08-19 neither does light on any *surfaced* role.** Dark's weakest text
role is 5.42 : 1, light's weakest surfaced one 4.70 : 1. This is the concrete
meaning of "the same properties, not a naive inversion" — where the light
palette had a known weak spot, the dark one was not allowed to copy it, and the
light one has since been brought up to it.

---

## 5. The hard case: the active nav glyph

`home-screen-prototype` REQ-023 fills the active sidebar glyph with
`--brand-primary → --brand-secondary`, and decided deliberately that those
stops **must** stay `var(--brand-*)`: a near-duplicate literal would leave the
glyph as the one element a rebranded property could not recolour.

On a dark pill those same colours measure **2.23 : 1** and **1.60 : 1** — the
violet half of the glyph all but disappears.

Rejected: hard-coding lighter stops (breaks branding — the exact defect REQ-023
was written to prevent); brightening the whole pill (changes the sidebar's
character); accepting it (the active state would rest on two signals in dark
and three in light).

**Chosen:** the dark theme lightens the *same* primitives at the role layer.

```css
--color-icon-active-start: color-mix(in oklab, var(--brand-primary) 65%, white);
--color-icon-active-end:   color-mix(in oklab, var(--brand-secondary) 65%, white);
```

The stop is still **derived from the property's brand colour**, so a rebranded
property still recolours its own glyph; it is simply lifted for the darker
backdrop, which is what a designer would do by hand.

| | light | dark |
|---|---|---|
| start stop on the active pill | 2.73 | **3.42** |
| end stop on the active pill | 3.80 | **2.84** |

The weakest stop goes from 2.73 (light) to 2.84 (dark) — dark is not worse.

`oklab`, not sRGB: mixing toward white in sRGB desaturates unevenly across
hues and would push the pink and the violet apart by different amounts.

---

## 6. What is *not* in this file

- **No light values are defined here.** The light column above is a repetition
  for reading; [`tokens.md`](tokens.md) remains the source of truth for it.
- **No new geometry, type or spacing.** The theme axis changes colour only.
- **No `system` / `prefers-color-scheme` behaviour.** Which theme is active,
  how it is chosen and how it is persisted are runtime concerns, specified in
  `docs/features/appearance-and-language/02-design.md` §6 and summarised in
  `tokens.md` → Implementation → "Setup decisions" → *Dark-mode trigger*.
