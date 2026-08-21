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

> ⚠️ **Read the two middle bullets together, not separately (note added
> 2026-08-18, after they were misimplemented).**
>
> `align: end` describes where a column's **bottom edge** lands. It does **not**
> mean the column shrinks to its content: the `Screen` column is full height,
> and `space-between` + `128px` padding is what puts the heading at the **top**
> and the cards at the **bottom** with empty space between them. Bottom-aligning
> the whole column instead collapses that empty space and glues the heading onto
> the cards in mid-screen — which is exactly what the first implementation did.
>
> **Measured from [`../assets/home-no-artwork.png`](../assets/home-no-artwork.png)**
> (edge scan, CSS px on the 1280 × 832 logical viewport — this is the
> no-artwork variant's real geometry, which was never extracted from Figma, so
> these are the only concrete numbers that exist for it):
>
> | Element | x | y | w | h |
> |---|---|---|---|---|
> | Sidebar | 32 | **24** | 100 | **784** (= viewport height − 2 × 24 outer padding) |
> | Active nav pill | 40 | 195 | **84** | **84** |
> | Heading block (ink top) | 172 | ≈ 88–98 | **390** | — |
> | Card row | **172** | — | 820 (4 × 196, gaps 12) | 196 |
> | Card row bottom edge | — | **744** | — | — |
>
> Three things follow that the prose above does not spell out: the sidebar runs
> the **full** height (24 → 808), the content column starts at x = 172 —
> immediately after the sidebar plus one `--space-8` gap, with **nothing** in
> between — and the heading/card pair is inset `--space-9` (64px) from the top
> and bottom of the content area (24 + 64 = 88; 808 − 64 = 744).

## Components

> ⚠️ **Glyph sizes in this section were corrected 2026-08-18 — read this before
> trusting any icon dimension below.** Every "`24px` glyph" this section used to
> carry came from reading Figma's `aspect-[24/24]` as a 24px measurement. It is
> an aspect **ratio** (24:24 = 1:1) and carries no size at all; the same misread
> is what made every icon in the built app render at roughly a third of its
> intended size. The real sizes differ by context and come from the surrounding
> Figma boxes — `--size-icon-lg` (2.25rem / 36px CSS, 72 raw) for a glyph inside
> a filled tile, `--size-icon-md` (1.625rem / 26px CSS, ~52 raw) for sidebar
> navigation, `--size-icon-sm` (1.5rem / 24px CSS, 48 raw) for inline icons.
> Note that `--size-icon-sm` **is** 24px, so the card arrow and heading sparkle
> below were numerically right for the wrong reason — they are stated as tokens
> now so the coincidence cannot be mistaken for corroboration. `tokens.md` →
> Sizing is the authority. **Any other value in this file sourced from an
> `aspect-[a/b]` describes proportion, not measurement** — including the `24px`
> in the card-4 clarification note below, which is unverified.

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

Both states use `--color-text-primary` for the label. The icon glyph box is
**1:1** — Figma's `aspect-[24/24]` is that ratio and nothing more. Its size is
`--size-icon-md` (1.625rem / **26px** CSS, ~52 raw), the sidebar-navigation
value, derived from the surrounding Figma box; see the warning under
"Components".

`[NEEDS CLARIFICATION: no hover, focus, pressed or disabled state exists in the component. Required before implementation — this is a navigation control.]`

`[NEEDS CLARIFICATION: the variant named "Discover" is labelled "Umgebung", and "Info" is labelled "Info" while the PNG says "Infos". Variant names and visible labels should be reconciled.]`

### Glass Panel (`11:62`, 4 instances)

Square `392px` (min-width and min-height both `392px`), flex column, gap `8px`,
centered, padding `32px` horizontal / `48px` vertical.

- Fill `--color-surface-glass`, border `--border-glass`, radius `--radius-2xl`
- `--blur-glass` (`backdrop-filter: blur(16px)`), shadow `--shadow-panel`

Structure, top to bottom:

1. **Icon block** — `flex: 1`, padding `10px` / `16px`, containing an
   `Intense_Icon`: `112px` tile, radius `--radius-xl`, `--gradient-icon`,
   opacity 90%, padding `20px`, glyph at `--size-icon-lg` (2.25rem / **36px**
   CSS, 72 raw) — a glyph inside a filled tile, not the `aspect-[24/24]` ratio
   this used to be read as.
2. **Text block** — `flex: 1`, column, gap `8px`:
   - **Header** — `--text-title`, `--color-text-primary`, full width
   - **Description row** — description text `--text-body` /
     `--color-text-secondary` (`flex: 1`), plus a `48px`-wide **Action** column
     bottom-aligned holding an `ArrowRight` glyph at `--size-icon-sm`
     (1.5rem / **24px** CSS, 48 raw). 24px is correct here, but as the
     inline-icon token, not because `aspect-[24/24]` said so.

**Props:** `header` (Figma default `"Haus & Funktionen"`), `description` (default
`"Tipps, Restaurants & Aktivitäten"`), `eigenschaft` (single value `"Default"`).

| # | Node | Glyph | Header | Description |
|---|------|-------|--------|-------------|
| 1 | `15:62` | `Key` | Check-Out | Bis 11:00 Uhr am Abreisetag |
| 2 | `15:74` | `LightbulbFilament` | Smart Home | Alles im Haus auf einen Blick |
| 3 | `15:87` | `MapPin` | Umgebung | Tipps, Restaurants & Aktivitäten |
| 4 | `15:100` | `FileText` | Wichtige Infos | Hausregeln, WLAN, Parken & mehr |

✅ **Done 2026-08-17:** cards 2–4 were carrying the component default; their
text overrides are now set in Figma. The table above reflects the design file.

**Amended 2026-08-18 (user decision):** card 3's header is **"Umgebung"**, not
"Entdecken" — it now reads the same as the section it navigates to. Figma still
says "Entdecken" (the suggestion in "PNG vs Figma" above is the older wording);
the table and the shipped config are the authority for this string until the
design file catches up.

**Amended 2026-08-18 (user decision, second pass):** card 2's header is
**"Smart Home"**, not "Haus & Funktionen". The original copy needs 167px of
advance width in the card's real 162px title box (196 − 2 × 1px glass border −
2 × 16px padding) and was rendered ellipsized; the user chose to rename the
copy rather than widen the box, so the card's padding is unchanged. Like card
3, it now reads the same as the section it navigates to — that repetition of
the sidebar's "Smart Home" label is deliberate, not a duplication to
de-duplicate. Figma still says "Haus & Funktionen"; the table and the shipped
config are the authority until the design file catches up.

`[NEEDS CLARIFICATION: the panel has one variant ("Default") and no hover/pressed state, but it carries an arrow affordance and is clearly interactive.]`

`[NEEDS CLARIFICATION: card 4's icon tile contains a second, empty 24px "Info" node alongside FileText — leftover?]`

### Heading block (`19:102`)

Fixed `780px` wide (raw Figma — **390px CSS**, see the note below), column,
gap `12px`:

1. **Subtitle row** — `Sparkle` glyph at `--size-icon-sm` (1.5rem / **24px**
   CSS, 48 raw) + "Schön, dass ihr da seid" in `--text-body-lg`, gap `10px`.
   Same as the card arrow: 24px is right as the inline-icon token, not as a
   reading of `aspect-[24/24]`.
2. **Headline** — "Willkommen im Airbnb von Lisa!" in `--text-display` (the
   Figma layer and the reference render both read "AirBnb"; **"Airbnb"** is the
   correct trademark spelling and is what ships — corrected here 2026-08-18 so
   REQ-008's "matches this document exactly" is true of the shipped copy, see
   `design-sheet.md` → Schreibweise)
3. **Spacer** — empty `30px`-high frame (`19:115`) — in the PNG this position holds a hand-drawn underline + heart
4. **Paragraph** — "Entspannt euch, genießt die Zeit und fühlt euch wie zuhause." in `--text-body-lg`

All four use `--color-text-primary`.

> ⚠️ **Overridden 2026-08-18 by the user — the Sparkle does not.** The
> round-3 review compared the rendered pixels against
> [`../assets/home-no-artwork.png`](../assets/home-no-artwork.png) and found the
> reference's sparkle is a **filled** glyph carrying the same pink→violet
> gradient as the active nav icon, not an outline glyph in flat
> `--color-text-primary`. The build now matches it: Phosphor weight `fill`
> (`phosphorSparkleFill`) plus the shared `.icon-gradient-active` paint server
> — the identical mechanism and ruling as the active nav glyph below, so the
> gradient still comes from `--color-icon-active-start`/`-end` and still
> follows a property's runtime branding override. Measured after the fix: ink
> runs `(201,91,199)` at the top to `(173,92,220)` at the bottom, against the
> reference's `(220,96,185)` → `(179,95,216)`, with zero flat-navy pixels. The
> other three elements of this block are unchanged.
>
> **The Figma component still shows the outline glyph in flat ink**, so a
> future re-extraction will not reproduce this — same gap as the nav icon, and
> recorded here for the same reason.

> Note the weight exception this creates: `regular` is used for every glyph in
> this screen **except** this one. See "Assets to export" below, where the
> `regular`-throughout decision and its two overrides are recorded together.

> The `30px` spacer is an empty frame, not an asset. The decorative underline
> visible in the PNG does not exist in the Figma file.

> ⚠️ **`780px` is a raw 2× value — the CSS width is `390px` (corrected
> 2026-08-18).** Like every other number in this Components section it has to be
> halved (sidebar 199 → 100, logo tile 112 → 56, glass panel 392 → 196, widget
> column 443 → 220, `--text-display` 96 → 48); this one was carried into
> `--size-heading` unhalved, which is why the build's headline stayed on one
> line where the reference render wraps to two. The measured table under
> "Layout" above also carried `780` and is corrected to `390`. Verified in a
> browser at 390px: headline breaks 359 + 375px, paragraph 347 + 150px, against
> the reference render's 356 + 369 and 348 + 150.4.

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

> ⚠️ **As-built note added 2026-08-21 (guest-frontend-integration fix round,
> review finding 🔴-1).** The `1550 × 999 @ x793 / y155` above are raw values
> on the 2x canvas like everything else in this file, so the **CSS box is
> `775 × 499.5` at `x396.5 / y77.5`** of the 1280 × 832 frame — implemented in
> `rem` (the root is viewport-proportional), absolutely positioned behind the
> content and out of the flex flow.
>
> Corroborated by measurement rather than by reading: diffing
> [`../assets/home.png`](../assets/home.png) against
> [`../assets/home-no-artwork.png`](../assets/home-no-artwork.png) puts the
> artwork's differing pixels at `{x: 402.4, y: 82.4, w: 764.8, h: 491.2}` CSS
> px — the same box, inset by the rendering's own transparent margin. (Both
> reference PNGs are 1600 × 1040, not the 5120 × 3328 the table at the top of
> this file claims.)
>
> The first implementation made the artwork an inline `max-width: 18rem` flex
> item instead. It pushed the content column from x 172 to x 492, clipped the
> fourth action card and scrolled Home horizontally at both reference
> viewports — the one thing this project's constraints forbid outright. An
> artwork whose aspect ratio differs from the specified box is centred inside
> it (`object-fit: contain`), never cropped or distorted.

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
   heading block still occupies only its original 390px (780 raw) and the card
   row still stops well short of the right edge.
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
language selector and a clock. **The widgets are still not committed** — treat
them as prior art, not requirements. **The top bar is the one exception: it was
committed on 2026-08-18 and is built** (see below).

Whatever lands here needs designing in Figma first; nothing below has
token-accurate values, because a concept render cannot supply them.

- **Weather** — "Wetter heute", current temp, condition, H/L, four-slot forecast — *not committed*
- **Host** — avatar, name, tagline, phone + WhatsApp actions (the only place the PNG uses `--color-brand-secondary`) — *not committed*
- **Recommendations** — list rows with thumbnail, pin icon, title, travel time — *not committed*
- **Tip bar** — "Tipp des Tages" (spans the content column, not the widget column) — *not committed*
- ~~**Top bar** — language selector, clock~~ → ✅ **committed and built, see below**

> ✅ **Top bar — committed 2026-08-18, no longer a hypothesis.**
>
> The user committed this one, and extended it: alongside the language selector
> and the clock it carries an **appearance (light/dark) control**. It shipped
> as part of `docs/features/appearance-and-language/`, specified in that
> feature's `02-design.md` §8.
>
> Two things about it contradict the framing above and must not be re-derived
> from this section:
>
> - **It is not part of the widget column.** It is app-wide chrome, built in
>   `layout/` as a sibling of the sidebar and `<main>`, and it appears on
>   **every** route, not only Home. Nothing about it is Home-specific.
> - **It does not sit in the flow.** It is absolutely positioned in the
>   content area's top-right corner, deliberately, because a header row would
>   push Home's heading down ~40px and silently regress the measured,
>   signed-off geometry recorded in this document (heading ink at y = 88, card
>   row bottom at y = 744 at 1280 × 832). **That corner is now reserved** —
>   anything added to the widget column has to clear it. At the 1280 × 832
>   design size the clearance to the card row is 48px horizontally and 20px
>   vertically to the heading; at the 1024 × 768 floor it is ~38px and 16px.
>
> It has **no Figma design either** — the composition was read off the AI
> concept render, so its tokens are in-house (`tokens.md` → "In-house
> tokens"). Committing it did not make it extracted.

Worth weighing when choosing: weather and recommendations both imply **external
data sources**, which is an approval-gated infrastructure decision under
`docs/constitution.md`. Host contact is self-contained by comparison.

## Content

Real German copy throughout — no lorem ipsum. Informal address (*ihr/euch*)
consistently. Note the spelling **"AirBnb"** in the headline (Figma) vs
**"Airbnb"** (PNG); the latter is the correct trademark spelling and the one
this document and the shipped config now use (2026-08-18). The Figma file is
the outlier and still needs correcting.

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
- **Weight (decided 2026-08-17): `regular` throughout, in every state —
  with one exception added 2026-08-18, see below.**
  Using `fill` for the active sidebar item was proposed and **rejected on
  aesthetic grounds** — it read as too heavy against the soft design.

  The active state therefore rests on two signals: the
  `--gradient-menu-active` background and the heavier label
  (`--text-label-active`, ExtraBold vs Bold). That is sufficient and not
  colour-only, so it holds up for accessibility; the icon simply does not
  participate in the distinction.

  > ⚠️ **Overridden 2026-08-18 by the user — the icon *does* participate now.**
  >
  > The paragraph above stands as written for the **icon weight**: `regular` is
  > still used in every state, and switching the active icon to Phosphor's
  > `fill` weight is still rejected for the reason given. What changed is the
  > separate question of the active glyph's **colour**.
  >
  > After the screen was built and run, the user compared it against
  > [`../assets/home-no-artwork.png`](../assets/home-no-artwork.png) and found
  > the active glyph there is not flat `--color-text-primary` at all: it carries
  > a pink→violet gradient. Measured off that render (linear regression across
  > the glyph's stroke pixels): a **139°** axis from `#f45aa8` to `#8b5cf6` —
  > i.e. `--color-brand-primary` → `--color-brand-secondary`. The user confirmed
  > those values and the override.
  >
  > **So the active state now rests on three signals** — the
  > `--gradient-menu-active` background, the heavier label, and the
  > gradient-filled glyph. None of them is colour-only, so the accessibility
  > argument in the original decision is unaffected; and the aesthetic objection
  > that killed `fill` does not apply, because a gradient recolours the same
  > `regular` stroke rather than thickening it.
  >
  > The token is `--gradient-icon-active` — see
  > [`../tokens.md`](../tokens.md) → Gradients, which carries the full
  > provenance (measured from a render, not extracted from Figma) and the SVG
  > realisation. **The Figma component still does not contain this fill**, so a
  > future re-extraction will not reproduce it; that gap is recorded in
  > `tokens.md` rather than silently accepted.
  >
  > `[NEEDS CLARIFICATION: apply the same gradient in the Figma MenuIcons component so design file and build stop disagreeing. Not urgent, not blocking — but until it happens, tokens.md is the only record.]`

  > ⚠️ **Second override, 2026-08-18 — the heading Sparkle is `fill`.** The
  > `regular`-throughout rule now has exactly one weight exception: the Sparkle
  > in the Heading block, which the reference render shows solid and
  > gradient-filled (see the note in "Heading block" above). The aesthetic
  > objection that killed `fill` for the **sidebar** stands and is unaffected —
  > the sidebar's active glyph is still `regular`, recoloured, not thickened.
  > The registered icon name is therefore `phosphorSparkleFill`, imported from
  > `@ng-icons/phosphor-icons/fill`; every other glyph still comes from
  > `.../regular`.
