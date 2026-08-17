# Screen: Home / Welcome Screen

**Figma:** file `JGH2ZQOXCoL0KNLM4uXtCN` (`AirBnB-Screen`), frame `1:2891`
"Welcome Screen", 2560 × 1664
**Reference images** (all 5120 × 3328, exported from Figma unless noted):

| File | What it is |
|------|------------|
| [`../assets/home.png`](../assets/home.png) | **Current Figma state, default variant** — with the 3D rendering. The authoritative visual reference |
| [`../assets/home-no-artwork.png`](../assets/home-no-artwork.png) | Current Figma state, **variant without rendering** — the fallback for properties with no artwork |
| [`../assets/home-concept-ai.png`](../assets/home-concept-ai.png) | The original **AI-generated concept**, kept for history. Shows widgets, a 7-item menu and a tip bar that were never built. **Not a spec** — do not implement from it |
**Extracted:** 2026-08-17 · 3 MCP calls
**Tokens:** see [`../tokens.md`](../tokens.md)

---

## Source of truth: Figma, with one deliberate gap

**Resolved 2026-08-17.** The PNG was AI-generated as a concept and then rebuilt
by hand in Figma; the rebuild was never finished. So the two are related but not
interchangeable, and the rule is:

> **Figma (`1:2891`) is authoritative for everything it contains.**
> The PNG is a concept render — useful for intent, never for values.

The one intentional exception is the right-hand **widget column**: it is empty in
Figma because the widget set is genuinely undecided, *not* because it was
forgotten. Widgets are still wanted there.

| # | PNG shows | Figma contains | Status |
|---|-----------|----------------|--------|
| 1 | Weather / Host / Recommendations cards | `18:296` "Widgets" — empty, 443px (221.5 CSS px) | **Open by design** — content TBD, see below |
| 2 | 7 menu items (Infos, Haus, Tipps, Nachrichten …) | 6 items (Home, Info, Umgebung, Smart Home, Services, Einstellungen) | **Figma wins** — implement the 6 |
| 3 | 4 distinct card texts | Only card 1 overridden; 2–4 show component defaults | **Figma incomplete** — needs 3 text overrides |
| 4 | "von Anna", plus a "Tipp des Tages" bar | "von Lisa", no tip bar | **Figma wins** — no tip bar |

Two follow-ups fall out of this:

`[NEEDS CLARIFICATION: cards 2–4 need their text overrides set in Figma. The glyphs already imply the intent (LightbulbFilament / MapPin / FileText). Suggested, from the PNG: "Haus & Funktionen" · "Alles im Haus auf einen Blick"; "Entdecken" · "Tipps, Restaurants & Aktivitäten"; "Wichtige Infos" · "Hausregeln, WLAN, Parken & mehr".]`

`[NEEDS CLARIFICATION: which widgets go in the right column? Until this is decided the column is dead space at 221.5 CSS px — roughly 17% of the viewport width. Worth deciding before layout work starts, because the answer may change the column's width or remove it on narrow screens entirely.]`

---

## Layout

Single fixed canvas, no responsive behaviour defined.

> **All px values in this section are raw Figma values on the 2x canvas.**
> Divide by 2 for CSS px — see the "Scale" section in
> [`../tokens.md`](../tokens.md), which lists the normalised values to implement
> against. The 2560 × 1664 frame is a MacBook Air 13" panel, i.e. a
> **1280 × 832** logical viewport.

```
┌─ Welcome Screen 2560×1664 · bg --color-screen-bg · radius --radius-2xl ─┐
│  hero artwork (bitmap, absolute: 1550×999 @ x793 y155)                  │
│  ┌─ Layout · flex row · gap 68 · padding 65 / 48 ──────────────────┐    │
│  │ ┌─ Welcome · flex row · gap 80 · items-end ──────────┐          │    │
│  │ │ ┌ Sidebar ┐  ┌─ Screen · flex col · py 128 ─────┐  │  Widgets │    │
│  │ │ │  199px  │  │  Heading  (780px wide)           │  │  443px   │    │
│  │ │ │         │  │       ⋮  space-between           │  │  EMPTY   │    │
│  │ │ │         │  │  Cards row · 4 × 392 · gap 24    │  │          │    │
│  │ │ └─────────┘  └──────────────────────────────────┘  │          │    │
│  │ └────────────────────────────────────────────────────┘          │    │
│  └─────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────┘
```

- Outer `Layout` is a flex row, `align: center`, gap `68px`, padding `65px` horizontal / `48px` vertical.
- `Welcome` group is `align: end` — sidebar and content column are bottom-aligned.
- `Screen` column distributes `Heading` and the card row with `space-between` and `128px` vertical padding.
- `Widgets` column is `flex: 1` — it absorbs all remaining horizontal space.

## Components

### Sidebar (`Menu Bar`, instance `18:204`)

Width `199px`, full height, fill `--color-surface-menu`, radius `--radius-2xl`,
padding `16px` horizontal / `48px` vertical, `space-between` in three groups:

1. **Logo** — `112px` tile, radius `--radius-lg`, `--gradient-icon`, opacity 90%, glyph `AngularLogo` ⚠️
2. **MenuItems** — five `MenuIcons` instances
3. **Settings** — one `MenuIcons`-shaped item, "Einstellungen", `GearSix` glyph

> ✅ **Resolved:** `AngularLogo` is a **placeholder**, picked arbitrarily. The
> project's own logo does not exist yet. Implement the logo as a **swappable
> slot** — a single asset reference used once — so replacing it later is a
> one-line change and never a hunt through components.
> `[NEEDS CLARIFICATION: final logo asset pending. Also needs a decision on format (SVG strongly preferred at 56px) and whether it inherits --gradient-icon like the placeholder does, or carries its own colors.]`

#### `MenuIcons` component

Square `146px` (`128px` aspect box), flex column, gap `8px`, centered,
padding `16px` horizontal / `28px` vertical.

| Variant (`menu`) | Label | Glyph | Default `state` |
|---|---|---|---|
| `Home` | Home | `House` | `true` (active) |
| `Info` | Info | `Info` | `false` |
| `Discover` | Umgebung | `MapPin` | `false` |
| `Smarthome` | Smart Home | `LightbulbFilament` | `false` |
| `Services` | Services | `ShoppingBag` | `false` |

**States** — the component exposes a boolean `state`, so exactly two exist:

- **Active** (`state = true`): background `--gradient-menu-active`, radius `--radius-md`, label `--text-label-active` (ExtraBold).
- **Inactive** (`state = false`): no background, label `--text-label` (Bold).

Both states use `--color-text-primary` for the label. Icon glyph box is `24px`, 1:1.

`[NEEDS CLARIFICATION: no hover, focus, pressed or disabled state exists in the component. Required before implementation — this is a navigation control.]`

`[NEEDS CLARIFICATION: the variant named "Discover" is labelled "Umgebung", and "Info" is labelled "Info" while the PNG says "Infos". Variant names and visible labels should be reconciled.]`

### Glass Panel (`11:62`, 4 instances)

Square `392px` (min-width and min-height both `392px`), flex column, gap `8px`,
centered, padding `32px` horizontal / `48px` vertical.

- Fill `--color-surface-glass`, border `--border-glass`, radius `--radius-2xl`
- `--blur-glass` (`backdrop-filter: blur(16px)`), shadow `--shadow-panel`

Structure, top to bottom:

1. **Icon block** — `flex: 1`, padding `10px` / `16px`, containing an `Intense_Icon`: `112px` tile, radius `--radius-xl`, `--gradient-icon`, opacity 90%, padding `20px`, `24px` glyph.
2. **Text block** — `flex: 1`, column, gap `8px`:
   - **Header** — `--text-title`, `--color-text-primary`, full width
   - **Description row** — description text `--text-body` / `--color-text-secondary` (`flex: 1`), plus a `48px`-wide **Action** column bottom-aligned holding a `24px` `ArrowRight` glyph

**Props:** `header` (default `"Haus & Funktionen"`), `description` (default
`"Tipps, Restaurants & Aktivitäten"`), `eigenschaft` (single value `"Default"`).

| # | Node | Glyph | Header | Description |
|---|------|-------|--------|-------------|
| 1 | `15:62` | `Key` | Check-Out | Bis 11:00 Uhr am Abreisetag |
| 2 | `15:74` | `LightbulbFilament` | Haus & Funktionen | Alles im Haus auf einen Blick |
| 3 | `15:87` | `MapPin` | Entdecken | Tipps, Restaurants & Aktivitäten |
| 4 | `15:100` | `FileText` | Wichtige Infos | Hausregeln, WLAN, Parken & mehr |

✅ **Done 2026-08-17:** cards 2–4 were carrying the component default; their
text overrides are now set in Figma. The table above reflects the design file.

`[NEEDS CLARIFICATION: the panel has one variant ("Default") and no hover/pressed state, but it carries an arrow affordance and is clearly interactive.]`

`[NEEDS CLARIFICATION: card 4's icon tile contains a second, empty 24px "Info" node alongside FileText — leftover?]`

### Heading block (`19:102`)

Fixed `780px` wide, column, gap `12px`:

1. **Subtitle row** — `24px` `Sparkle` glyph + "Schön, dass ihr da seid" in `--text-body-lg`, gap `10px`
2. **Headline** — "Willkommen im AirBnb von Lisa!" in `--text-display`
3. **Spacer** — empty `30px`-high frame (`19:115`) — in the PNG this position holds a hand-drawn underline + heart
4. **Paragraph** — "Entspannt euch, genießt die Zeit und fühlt euch wie zuhause." in `--text-body-lg`

All four use `--color-text-primary`.

> The `30px` spacer is an empty frame, not an asset. The decorative underline
> visible in the PNG does not exist in the Figma file.

`[NEEDS CLARIFICATION: guest name "Lisa"/"Anna" is hardcoded — confirm it is dynamic content.]`

### Hero artwork (`2:5`)

Bitmap `Maxresdefault 1280x720 1`, absolutely positioned `1550 × 999` at
`x793 / y155`, sitting above the background and below the layout.

> ✅ **Resolved 2026-08-17.** Placeholder for **per-property artwork**: a
> commissioned **3D rendering** of the individual property, produced by the
> project owner as a paid add-on — not a guest- or host-supplied photograph.
>
> This resolves the contrast question. Because the artwork is authored rather
> than collected, its brightness and composition are controlled at production
> time, so **no scrim or overlay is needed** and the current placeholder's
> characteristics can be treated as the reference. The layout keeps the image
> behind the heading and panels as designed.
>
> The trade-off accepted here: text legibility depends on a **production
> convention**, not on a mechanism in the code. That is sound while one person
> renders every asset. It becomes a risk the moment rendering is delegated or
> outsourced, so the convention needs writing down rather than living in one
> person's head.
>
> **Render brief: dropped (2026-08-17).** A written specification for the
> artwork was proposed and deliberately declined — the project owner produces
> every rendering personally and can see immediately whether one works against
> the headline. The legibility requirement therefore lives in practice, not in a
> document. Revisit only if rendering is ever delegated or outsourced.

**Fallback is still required — for a business reason, not a visual one.** The
rendering is ordered separately, so there will always be properties that do not
have one yet: newly onboarded ones, and those that never buy the add-on. The
screen must therefore work with the image absent, not merely with it present.

✅ **Resolved 2026-08-17: show no artwork at all.** The pastel gradient carries
the screen on its own; there is no substitute image.

Rejected: a **generic apartment rendering**. On a screen headlined "Willkommen im
Airbnb von …", a picture in that position reads as *this* property, so showing a
different flat is more confusing than showing none. It would also erase the
visible value of the paid rendering — the fallback should be *fine*, not *good*;
that gap is the product. Also rejected: a neutral illustration, which would
introduce a third visual language to design and maintain.

Two requirements follow:

1. ✅ **Done 2026-08-17: the no-artwork variant exists in Figma.** Reviewed from
   screenshots (no MCP cost), which also confirmed the renamed variable, the
   corrected card texts and the six-item sidebar.

   **Observation:** in its current form the variant is close to *the layout with
   the image removed* rather than a recomposition. With neither artwork nor
   widgets, the right ~40% of the canvas carries nothing. It reads as calm
   rather than broken, so this is a judgement call, not a defect — but the
   heading block still occupies only its original 780px and the card row still
   stops well short of the right edge.
   **Deferred to the planning phase (2026-08-17).** The right region is not
   meant to stay empty — widgets are wanted there, and their selection and
   layout will be worked out during `/plan` rather than settled here. Until
   then the current composition stands.

   `[NEEDS CLARIFICATION: this variant has not been extracted — its node id and layout values are unknown. Fold into the next design-import run rather than spending a call on it alone.]`
2. **Tint the gradient with the property's brand colors.** The branding
   primitives are already injected per property (see `../tokens.md` →
   "Implementation"), so deriving the background gradient from
   `--brand-primary` gives every property without a rendering something of its
   own at no production cost.
   `[NEEDS CLARIFICATION: confirm the gradient should derive from brand primitives rather than staying fixed at #f5c7e2.]`

A **loading state** is still needed for properties that *do* have a rendering —
it is a large image. Falling back to the gradient while it loads makes the empty
variant do double duty.

`[NEEDS CLARIFICATION: fixed dimensions or responsive fit? The image is currently absolutely positioned at 1550 × 999 with a fixed offset. Since you author the renders, standardising one output aspect ratio is simpler than making the layout tolerate arbitrary ones — but that standard needs stating.]`

---

## The widget column (`18:296`) — undesigned, still wanted

Empty in Figma by decision, not oversight. The PNG's concept version suggested
weather, host contact and nearby recommendations; the top bar suggested a
language selector and a clock. **None of these are committed** — treat them as
prior art, not requirements.

Whatever lands here needs designing in Figma first; nothing below has
token-accurate values, because a concept render cannot supply them.

- **Weather** — "Wetter heute", current temp, condition, H/L, four-slot forecast
- **Host** — avatar, name, tagline, phone + WhatsApp actions (the only place the PNG uses `--color-brand-secondary`)
- **Recommendations** — list rows with thumbnail, pin icon, title, travel time
- **Top bar** — language selector, clock
- **Tip bar** — "Tipp des Tages" (spans the content column, not the widget column)

Worth weighing when choosing: weather and recommendations both imply **external
data sources**, which is an approval-gated infrastructure decision under
`docs/constitution.md`. Host contact is self-contained by comparison.

## Content

Real German copy throughout — no lorem ipsum. Informal address (*ihr/euch*)
consistently. Note the spelling **"AirBnb"** in the headline (Figma) vs
**"Airbnb"** (PNG); the latter is the correct trademark spelling.

## Assets to export

Not downloaded during this run — the MCP asset URLs expire after ~7 days, and
choosing an icon strategy is an implementation decision, not an extraction one.

- 12 SVG glyphs: `House`, `Info`, `MapPin`, `LightbulbFilament`, `ShoppingBag`, `GearSix`, `AngularLogo`, `Sparkle`, `ArrowRight`, `Key`, `FileText`
- 1 PNG: hero artwork `Maxresdefault 1280x720 1`

✅ **Resolved: Phosphor Icons is the project's icon convention.** The glyph names
in Figma (`House`, `MapPin`, `LightbulbFilament`, `ShoppingBag`, `GearSix`,
`FileText`, `Sparkle`, `ArrowRight`, `Key`, `Info`) are all Phosphor names, so
the icons come from Phosphor rather than being exported one by one.

Consequences:

- Only **two** real assets need exporting: the final logo (pending) and the hero
  photo (dynamic, per-property) — neither of which is a Phosphor glyph.
- **Package (decided 2026-08-17):** `@ng-icons/core` + `@ng-icons/phosphor-icons`.
- **Weight (decided 2026-08-17): `regular` throughout, in every state.**
  Using `fill` for the active sidebar item was proposed and **rejected on
  aesthetic grounds** — it read as too heavy against the soft design.

  The active state therefore rests on two signals: the
  `--gradient-menu-active` background and the heavier label
  (`--text-label-active`, ExtraBold vs Bold). That is sufficient and not
  colour-only, so it holds up for accessibility; the icon simply does not
  participate in the distinction.
