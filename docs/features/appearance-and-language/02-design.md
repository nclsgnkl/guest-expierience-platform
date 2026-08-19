# Design: Appearance & Language
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **TODO:** [03-todo.md](03-todo.md)
**Status:** Approved — all four decisions answered 2026-08-18, see §12

Requirements live in [`01-prd.md`](01-prd.md) and are referenced by ID, not
repeated. This document is **how**.

> **Round 2 — 2026-08-18.** The user answered all four open decisions
> ([`01-prd.md`](01-prd.md) → "Decisions taken"). Three matched the
> recommendation; **#2 did not**: the guest's preference is **persistent state
> owned by the backend's database**, written when the guest changes it and
> delivered back on load, with the backend always supplying the defaults
> (`light`, `de`). That turned a read-only seam into a read/write, asynchronous
> contract and rewrote §6 — which is now the only part of this design with no
> prior art in the codebase. §1, §5.2, §10, §11 and §13 follow it; §2–§4 and
> §7–§9 are unchanged apart from the removal of the ⏸ markers.

---

## 1. Architecture decision

Three new `core/` services, one new `layout/` component pair, one new
`features/` screen, and an extension of the existing content layer. Nothing
existing is replaced; `SectionPlaceholderComponent` keeps four of its five
routes.

```
frontend/src/app/
├── core/
│   ├── content/                      (existing — extended)
│   │   ├── property.model.ts         + Localized<T>, LanguageOption,
│   │   │                               AppearanceMode/Option, PropertySettingsConfig
│   │   ├── example-property.data.ts  + English copy, + settings block
│   │   └── property-content.service.ts   getProperty() → two signals
│   ├── preferences/                  (new)
│   │   ├── preferences.service.ts    defaults ⊕ guest choice ⊕ persistence policy
│   │   ├── preference-gateway.ts     the contract the backend will implement
│   │   │                               (interface + token + constants)
│   │   └── session-preference-gateway.ts   the stand-in — deleted, not
│   │                                       adapted, when HTTP lands (§6.2)
│   ├── i18n/                         (new)
│   │   ├── translation.model.ts      LanguageCode, TranslationKey
│   │   ├── translation.service.ts    t(key, params?)
│   │   └── catalog/{de.ts,en.ts,index.ts}
│   └── clock/                        (new)
│       └── clock.service.ts          minute-aligned Signal<Date>
├── layout/
│   ├── shell/                        (existing — + position:relative, + <app-status-bar>,
│   │                                   + --color-mesh-* instead of --mesh-*)
│   ├── sidebar/                      (existing — labels now localised, aria-label translated)
│   └── status-bar/                   (new)
│       ├── status-bar.component.*
│       └── status-bar-menu/          (new, generic CDK dropdown)
└── features/
    ├── section-placeholder/          (existing — "Bald verfügbar." now translated)
    └── settings/                     (new)
        ├── settings.component.*
        └── setting-choice/           (new, generic all-options-visible group)
```

Plus one bootstrap change outside that tree: `app.config.ts` gains a
`provideAppInitializer` that applies the property defaults and awaits the
stored preference **before the first paint** (§6.4).

### Dependency direction — and the cycle that had to be designed out

The obvious shape has a hard circular dependency: `PropertyContentService`
needs the active language to resolve localised text, and `PreferencesService`
needs the property config to know the defaults and the available options.
Angular DI would reject it.

It is broken by making `PreferencesService` **depend on nothing but its own
transport** and receive its defaults through a call:

```
PreferenceGateway         (leaf — injects nothing today, HttpClient later)
     ▲
PreferencesService        (injects the gateway; nothing else)
     ▲            ▲
     │            │
TranslationService   PropertyContentService
     ▲            ▲
     └──── components ────┘

provideAppInitializer:  content.settingsConfig() ─► preferences.applyPropertyDefaults(…)
                        await preferences.hydrate()   (bounded, never rejects — §6.4)
App (constructor):      content.property().branding ─► applyBranding(…)   (existing)
                        effect() ─────────────────────► <html data-theme> / <html lang>
```

Three things make this the right shape rather than a workaround:

- `PropertyContentService.settingsConfig` is **language-independent by
  construction** — available language codes, their locales, their *endonym*
  labels ("Deutsch", "English" — a language's name is written in its own
  language, so it is never translated), the available appearance modes, and
  the two defaults. So it is safely readable before any language is known.
- `applyPropertyDefaults()` is exactly the seam the future backend needs. Today
  it is called synchronously from the app initializer with static data; when
  defaults arrive over HTTP instead, it is called when the response lands and
  every effective value recomputes. That is half of REQ-122 in one method.
- **The gateway is a leaf, and must stay one.** It carries the guest's
  preference to and from wherever it lives, and it may **never inject
  `PropertyContentService`** — that would close the cycle the whole shape
  exists to avoid. Anything it needs (a base URL, a timeout) comes from a
  plain injection token or is handed to it, never pulled from the content
  layer.

`App`'s constructor remains the one sanctioned place that touches
`document.documentElement` (`frontend/CLAUDE.md`); the two new attribute
writes join `applyBranding()` there rather than being scattered (REQ-110). Note
what moved and what did not: `applyPropertyDefaults()` moved **out** of that
constructor into the initializer, because the value it feeds must be settled
*before* the first effect run (§6.4). `applyBranding()` did not move — it has a
passing spec, it needs no `await`, and the constructor still runs before the
first paint.

---

## 2. Where the dark palette is documented (REQ-101)

**Decision: a new file, `docs/design/dark-theme.md`.**

The three candidates and why the third wins:

| Candidate | Verdict |
|---|---|
| `docs/design/tokens.md` | **No.** Its own header says it "records what **exists** in Figma", and the constitution treats it as the authority precisely because of that. A hand-authored 25-value dark palette written into it would be indistinguishable from extracted values within a month. The file already carries one hard-won lesson about this (`--gradient-icon-active`: "If this file is ever re-imported from Figma, this row will not come back") — one such landmine is a warning, twenty-five is a broken record |
| `docs/design/design-sheet.md` | **Defensible but crowded.** `CLAUDE.md` does assign it "parts not yet drawn", and it is where a 📋 dark section would conventionally go. But it is a prose design *system* document in German, organised by topic; dropping a 34-row token table with contrast measurements into it would make it the de-facto token file for one theme and the prose file for the other |
| **`docs/design/dark-theme.md` (chosen)** | Unambiguous provenance in its own header, one topic, one table, discoverable by name, and it leaves both existing files exactly as honest as they are today |

Consequences, all of them mandatory (REQ-101):

- `tokens.md`'s `[NEEDS CLARIFICATION: no dark-mode palette exists — is one wanted?]`
  is **replaced by a resolution pointing at the new file** — "✅ Resolved
  2026-08-18: yes; designed in-house, see `dark-theme.md`. No dark values live
  in this file, and a Figma re-import will not produce any."
- `tokens.md` → "Still blocked on design → No dark palette exists" gets the
  same treatment. Note the *second* half of that entry — "including whether the
  glass surfaces stay translucent over a dark background" — is answered here:
  **they do** (§3.3).
- `design-sheet.md` gains a cross-reference in its Farben section, marked 📋
  (intent, not extracted), consistent with its own legend.
- `dark-theme.md` opens with a provenance block: designed in-house on
  2026-08-18 by derivation from the light palette, no Figma counterpart
  exists, a re-import will not reproduce it, and if a dark variant is ever
  drawn in Figma **that** becomes authoritative and this file becomes the
  deviation log.

---

## 3. The dark colour concept

Design brief, from the user: *"the same properties as the light mode"*. Read
literally, that means the dark theme must reproduce light mode's **structure**,
not its lightness: identical semantic roles, identical glassmorphism (a
translucent, blurred, warm-tinted film over a 4 × 4 mesh gradient), identical
geometry, and brand accents that stay brand accents. Everything below follows
from that, and every number is measured, not asserted.

### 3.1 Method

The light mesh is a 4 × 4 grid running warm pink → peach across the top,
mauve through the middle and violet along the bottom. The dark mesh keeps
**the same hue at every one of the sixteen positions and the same spatial
movement**, and moves saturation and lightness into a night band. Two
adjustments were made against a naive HSL scale-down, both for a reason:

- **Warm hues were pulled back toward rose.** Peach (`#FE9B82`, hue 13) at 15 %
  lightness is *brown*. The top row keeps its warmth as an **ember** — deep
  rose to amber — rather than becoming mud.
- **Saturation is kept relatively high (26–55 %) at low lightness.** Desaturating
  proportionally produces the grey-purple sludge that makes most dark modes
  feel cheap; the design's own brief ("Warm · Ruhig · Premium") rules it out.

### 3.2 Dark mesh — new primitives `--night-1 … --night-16`

Row by row from the top left, alongside the light value each one mirrors:

| | col 1 | col 2 | col 3 | col 4 |
|---|---|---|---|---|
| **row 1** | `#4f172a` ← `#F5668C` | `#4d1925` ← `#F4779C` | `#4b211b` ← `#FE9B82` | `#4e2f1d` ← `#FDCFAD` |
| **row 2** | `#451c30` ← `#EFA2B5` | `#3e1d28` ← `#E8B2BC` | `#431e21` ← `#F3B5AA` | `#472b1f` ← `#FDD6BE` |
| **row 3** | `#361c34` ← `#D5B3D0` | `#321b28` ← `#C693A7` | `#2a1920` ← `#A27684` | `#3a1d22` ← `#F4B9BF` |
| **row 4** | `#2d1c3f` ← `#B69ED1` | `#291c40` ← `#AF99D2` | `#2f1d3e` ← `#BEA3D6` | `#3d1f3d` ← `#E4BFDD` |

The base colour under the sixteen layers is `--night-6`, mirroring light mode's
choice of `--mesh-6` for the same reason: it sits in the grid's interior and is
closest to the palette's average.

### 3.3 Semantic roles in `[data-theme='dark']`

Everything below is a **semantic role**. Not one primitive is redefined by the
theme axis (REQ-102) — that is what keeps per-property branding, which writes
primitives inline on `:root`, working identically in both themes.

| Role | Light (unchanged) | Dark | Why |
|---|---|---|---|
| `--color-text-primary` | `var(--ink-900)` `#2c1f4a` | `var(--ink-050)` `#f7f2fb` | Near-white with a violet cast, not pure white — the same warmth relationship the light ink has to black |
| `--color-text-secondary` | `var(--ink-600)` `#5a516e` | `var(--ink-300)` `#c6bcd8` | Quieter than primary. Light's primitive was `#6e6486` until 2026-08-19, when it was darkened to clear AA (§3.4) — dark was already above it and is unchanged |
| `--color-text-on-dark` | `#ffffff` | *unchanged* | It means "on a saturated brand fill", which is saturated in both themes |
| `--color-surface-glass` | `rgb(255 246 240 / 0.6)` | `rgb(246 240 255 / 0.14)` | A lightening film instead of a warming one. Cool-white tint so it does not turn the night mesh muddy |
| `--color-surface-menu` | `rgb(255 246 240 / 0.4)` | `rgb(246 240 255 / 0.09)` | **The 2 : 3 menu:glass alpha ratio is preserved exactly** (0.4/0.6 = 0.09/0.14 ≈ 0.64) — the sidebar stays the quieter surface by the same margin |
| `--color-surface-glass-border` | `rgb(255 255 255 / 0.4)` | `rgb(255 255 255 / 0.14)` | Still a white hairline. Lower alpha because white on dark carries much further |
| `--color-mesh-1…16` | `var(--mesh-1…16)` | `var(--night-1…16)` | §4 |
| `--gradient-menu-active` | `rgba(255,243,250,.9) → .5` | `rgba(255,243,250,.18) → .10` | Same colour, same `-74.707deg` axis, same 9 : 5 stop ratio, scaled to 20 % |
| `--color-icon-active-start` | `var(--brand-primary)` | `color-mix(in oklab, var(--brand-primary) 65%, white)` | See §3.5 |
| `--color-icon-active-end` | `var(--brand-secondary)` | `color-mix(in oklab, var(--brand-secondary) 65%, white)` | See §3.5 |
| `--gradient-icon` | brand → brand-deep | *unchanged* | Design principle 4: "Pink und Lila sind Akzente". The filled icon tile and logo stay the brand's own colours and glow against the night mesh — that is the point of an accent |
| `--shadow-panel` | `0 10px 40px rgb(44 31 74 / .1)` | `0 10px 40px rgb(6 3 14 / .5)` | A shadow on a dark surface must be much darker and stronger to exist at all |
| `--color-divider` *(new)* | `color-mix(in srgb, var(--ink-900) 25%, transparent)` | `color-mix(in srgb, var(--ink-050) 22%, transparent)` | New role, needed by the status bar (§6) |
| `--blur-glass`, radii, spacing, type | — | *unchanged* | "Same properties" taken literally: only colour changes |

New **primitives** added to `:root` (never overridden by a theme):
`--ink-050: #f7f2fb`, `--ink-300: #c6bcd8`, `--night-1…16`,
`--surface-glass-dark-raw`, `--surface-glass-border-dark-raw`,
`--surface-menu-dark-raw`. Raw values therefore still live exactly once, in
the primitive layer, which is what constitution principle 5 actually asks for.

### 3.4 Measured contrast (REQ-104)

Composite values: each surface alpha-composited over the mesh's average
colour (`#dea3b4` light, `#3d1e2b` dark), then WCAG 2.x contrast against the
text role that sits on it.

| Pair | Light | Dark | |
|---|---|---|---|
| Primary text on a glass panel | **10.89** | **8.91** | the one role that scores lower — both far above AAA |
| Secondary text on a glass panel | **5.38** | **5.42** | both pass AA — light was 3.99 until the note below |
| Secondary text on the Settings panel *(sampled)* | **5.12** | **5.31** | the panel uses the card's recipe over a different piece of mesh |
| Primary text on the sidebar | **9.52** | **10.40** | |
| Secondary text on the sidebar | **4.70** | **6.32** | no consumer today; the role is recorded because it is reachable |
| Primary text directly on the mesh | **7.15** | **13.40** | |
| Secondary text directly on the mesh *(section placeholder, sampled)* | **3.77** ⚠️ | **8.32** | light still fails AA — open defect, see the second note below |
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

> **The light secondary rows changed on 2026-08-19: `--ink-600` was darkened to
> reach AA.** They read **3.99** (glass) and **3.48** (sidebar) until
> `06-review.md` finding #3, with the Settings hint measuring **3.80** — all
> below AA's 4.5 : 1, and the large-text exemption does not apply (the hints are
> `--text-body`, 0.875rem / 14 px at weight 400). The role's primitive
> `--ink-600` was changed **`#6e6486` → `#5a516e`**, by scaling the old value in
> **linear-light space** (x0.65) so the hue is preserved — the same purple-grey,
> deeper — and the ink ramp still reads as one family. **The dark theme was not
> touched**: `--color-text-secondary` maps to `--ink-300` there and already
> passed at 5.42, and re-measurement confirms every dark figure in this table is
> unchanged to the hundredth. This is a **deliberate deviation from Figma**,
> recorded as such in `docs/design/tokens.md`.

> **One consumer still fails, and darkening the role cannot fix it.** The
> section placeholder's note (`.section-placeholder__note`, the "coming soon"
> line on `/info`, `/umgebung`, `/smart-home`, `/services`) is the only
> `--color-text-secondary` consumer with **no surface under it** — it composites
> straight onto the mesh. It measured **2.79 : 1** before the change and
> **3.77 : 1** after, sampled at 1280 x 832 (mesh `#e3abae`; the reading is
> stable at 2.79–2.82 → 3.77 across all four routes x three viewports). It is
> `--text-body-lg`, 1.125rem / 18 px at weight 400, so it is *not* WCAG large
> text (that needs 24 px, or 18.66 px bold) and AA's 4.5 : 1 applies. Reaching
> 4.5 on the raw mesh needs `--ink-600` at roughly **`#4e4660`** (linear x0.49),
> about twice the darkening approved here, which would pull the shared secondary
> role toward the primary on *every* surface (7.2 : 1 on glass against
> primary's 10.9) to compensate for one screen's missing surface. **Not done —
> tracked as its own defect**; the cause is the absent surface, not the role.

**The claim that matters: dark mode has no text role below 4.5 : 1. Since
2026-08-19, light mode has none either on any surfaced role** — its weakest
surfaced pair is 4.70 : 1 against dark's 5.42 : 1. This is the concrete meaning
of "the same properties, not a naive inversion" — where the light palette was a
known weak spot, the dark one was not allowed to copy the weakness, and the
light one has since been brought up to it. The one remaining light failure is
the unsurfaced placeholder note above.

### 3.5 The one genuinely hard case: the active nav glyph

`home-screen-prototype` REQ-023 fills the active sidebar glyph with
`--brand-primary → --brand-secondary`, and made a deliberate decision that
those stops **must** stay `var(--brand-*)`: a near-duplicate literal would
leave the glyph as the one element a rebranded property could not recolour.

On a dark pill those same colours measure **2.23 : 1** and **1.60 : 1** — the
violet half of the glyph all but disappears.

Rejected fixes: hard-coding lighter stops (breaks branding — the exact defect
REQ-023 was written to prevent); brightening the whole pill (changes the
sidebar's character); accepting it (the active state would visually rest on
two signals in dark and three in light).

**Chosen:** the dark theme lightens the *same* primitives at the role layer —
`color-mix(in oklab, var(--brand-primary) 65%, white)`. The stop is still
**derived from the property's brand colour**, so a rebranded property still
recolours its own glyph; it is simply lifted for the darker backdrop, which
is what a designer would do by hand.

| | light | dark |
|---|---|---|
| start stop on the active pill | 2.73 | **3.42** |
| end stop on the active pill | 3.80 | **2.84** |

The weakest stop goes from 2.73 (light) to 2.84 (dark) — dark is not worse.
`oklab` interpolation, not sRGB, because sRGB mixing toward white desaturates
unevenly across hues and would push the pink and the violet apart by different
amounts.

`color-mix()` is a platform CSS feature, baseline in every current engine, and
adds no dependency — but this is its **first use in the codebase**, alongside
`--color-divider`. Recorded here so it is a decision, not a surprise.

---

## 4. The mesh must become a semantic role (REQ-103) — ✅ decision #3, approved

`shell.component.css` composes `--mesh-1 … --mesh-16` directly. That is a
**primitive** read, sanctioned today by a named exception in
`frontend/CLAUDE.md`. Dark mode cannot live with it, and the reason is not
tidiness:

- The theme axis is only allowed to touch semantic roles (REQ-102), so it
  cannot override `--mesh-*`.
- If it did anyway, it would break the moment per-property mesh tinting is
  built. Tinting writes primitives **inline on `:root`** (that is how
  `applyBranding()` works), and an inline style beats a `[data-theme='dark']`
  rule at any specificity. A branded property would get its light mesh in
  dark mode — a silent, hard-to-diagnose defect appearing only for customers
  who bought branding.

**Design:** add semantic roles `--color-mesh-1 … --color-mesh-16` and
`--color-mesh-base`, defaulting to the matching primitive; `[data-theme='dark']`
maps them to `--night-*`; `shell.component.css` references only the roles. This
removes the last primitive read in `src/` — after it, the rule is simply
"nothing in `src/` reads a primitive", with no exception at all.

### ✅ Approved `frontend/CLAUDE.md` amendment — exact text, written by step S13

**Approved verbatim by the user on 2026-08-18** (*"Ja passt"*). The file is
still untouched — S13 writes exactly the two edits below, adjusting only line
wrapping.

> The wording below **is** the approved wording. Round 2 deliberately added
> nothing to it: the approval covers this text, not a later revision of it, and
> re-opening it would re-open the gate. The conventions the persistence answer
> suggests (the gateway is a leaf; nothing but `PreferencesService` reads or
> writes the guest's preference) are recorded in §6 and in
> `docs/system-context.md` instead, neither of which is approval-gated.

Two edits.

**(a) Replace the `--mesh` exception** in *Structure*, bullet 2. Current text:

> … Components reference semantic tokens only — never a raw hex or px. One
> documented exception: the mesh-gradient background in `shell.component.css`
> composes the `--mesh-1…16` primitives directly, because those are gradient
> **layers**, not a colour role — see `docs/design/tokens.md` →
> "Background implementation". Nothing else in `src/` may *read* a primitive.
> **Writing** one is a different thing …

Proposed replacement for the emphasised portion:

> … Components reference semantic tokens only — never a raw hex or px.
> **Nothing in `src/` may *read* a primitive — there is no exception.** The
> mesh-gradient background in `shell.component.css` used to be one; since
> `appearance-and-language` (REQ-103) it composes the semantic
> `--color-mesh-1…16` roles instead, because the theme axis may only override
> roles and an inline per-property primitive override would otherwise win over
> `[data-theme='dark']` and silently show a light mesh in dark mode.
> **Writing** a primitive is a different thing …

*(the remainder of the bullet — the `applyBranding()` sentence — is unchanged.)*

**(b) Add a new section** after *Icons*:

> ## Appearance & language
>
> - **No user-visible string is written in a component or template** — not a
>   label, not an `aria-label`, not `alt` text, not an empty state. App chrome
>   resolves through `TranslationService` (`core/i18n/`); property-authored
>   text comes from `PropertyContentService` as a per-language map. If you are
>   typing German into a `.html` file, you are in the wrong file.
> - **`<html>`'s `data-theme` and `lang` are written in exactly one place**,
>   `app.component.ts`, beside `applyBranding()`. That is the only code in
>   `src/` allowed to touch `document.documentElement`.
> - **The theme axis overrides semantic roles only.** `[data-theme='dark']`
>   never redefines a primitive: primitives are the per-property branding axis
>   and are written inline on `:root` at runtime, which beats any stylesheet
>   rule. Dark raw values are *new* primitives (`--ink-050`, `--ink-300`,
>   `--night-1…16`, `--surface-*-dark-raw`), added, never overridden.
> - **The top-right corner of the content area is reserved** for
>   `StatusBarComponent` (~13rem × 2.75rem, inset by the shell's own padding).
>   It is an overlay and takes no flow space, so a new screen will not be
>   pushed out of its way — check it does not collide.

---

## 5. Data model & content contract

### 5.1 Types (`core/content/property.model.ts`)

```
type LanguageCode = string;                  // BCP-47 primary subtag, e.g. 'de'
type Localized<T = string> = Record<LanguageCode, T>;
type AppearanceMode = 'light' | 'dark' | 'system';

interface LanguageOption {
  code: LanguageCode;      // 'de'          — also written to <html lang>
  shortLabel: string;      // 'DE'          — the status bar's compact label
  label: string;           // 'Deutsch'     — endonym; never translated
  locale: string;          // 'de-DE'       — what Intl gets, incl. the clock's 12h/24h
}
interface AppearanceOption { mode: AppearanceMode; icon: string; }  // labels come from the catalogue
interface PropertySettingsConfig {
  languages: LanguageOption[];        // available, in display order
  defaultLanguage: LanguageCode;      // property-configured default
  appearances: AppearanceOption[];    // available, in display order
  defaultAppearance: AppearanceMode;  // property-configured default
}
```

`PropertyConfig`'s guest-facing fields become `Localized<string>`:
`heroSubtitle`, `headline`, `paragraph`, every `MenuItem.label`, every
`ActionCard.header` / `.description`. **Not** localised, deliberately:
`guestName` (a person's name), `icon`, `route`, `id`, `targetSectionId`,
`logoAsset`, `hasArtwork`, `branding` — none of them is prose.

`headline` keeps its `{guestName}` placeholder in every language, so English
reads `Welcome to {guestName}'s Airbnb!` and the existing interpolation in
`HeadingBlockComponent` is unchanged apart from reading a resolved string.

### 5.2 Resolution and the fallback rule (REQ-108)

`PropertyContentService` exposes two signals and keeps one method:

```
readonly settingsConfig: Signal<PropertySettingsConfig>  // language-independent
readonly property:       Signal<PropertyConfig>          // resolved for the active language
getSectionRoute(id: string): string                      // unchanged; routes are not localised
```

> **Why `settingsConfig` and not `settings`** (renamed in round 2):
> `PropertyConfig` *already* has a `settings` field — the sidebar's single
> Einstellungen menu item (REQ-018 of `home-screen-prototype`). Two adjacent
> expressions, `content.settings()` and `content.property().settings`, meaning
> two completely different things, is a trap someone will fall into. The type
> keeps its name (`PropertySettingsConfig`); only the signal is renamed.

`property()` is a `computed()` over the raw config and
`preferences.language()`. Per **field**, resolution is:

1. the active language's entry, if present and non-empty;
2. otherwise the **property's own `defaultLanguage`** entry — not the app's
   idea of a default, and not the first key in the object;
3. otherwise **throw**.

Step 3 is not defensive pedantry: a config whose default-language entry is
missing is a content-authoring bug, and on a touch kiosk the alternative is a
guest staring at a blank headline.

**Validation runs eagerly, once, in the service's constructor** — not lazily
inside the `computed()`. A throw from inside a computed surfaces during
rendering, at whatever moment the guest happens to switch language; a throw at
construction fails the app at boot and fails every test immediately. Four
things are checked over the raw config, across *all* languages:

1. every localised field has an entry for `settings.defaultLanguage`;
2. `settings.defaultLanguage` is one of `settings.languages[].code`;
3. every `settings.languages[].code` has a registered translation catalogue —
   otherwise the guest could select a language that translates the property's
   content but leaves every app-chrome string in the fallback;
4. `settingsConfig.defaultAppearance` is one of
   `settingsConfig.appearances[].mode` — the mirror of check 2, **added in
   `07_improve` after `06-review.md` finding #1**, which found the gap in this
   section as much as in the code. `AppearanceMode` includes `'system'`, so a
   default outside the offered options type-checks: the config is accepted, and
   the Settings screen renders with *no* option marked selected until the guest
   taps one.

`docs/constitution.md` → "Input validated at every system boundary"; this
config *is* the boundary that becomes an HTTP response later, which is exactly
when unvalidated shapes start arriving.

### 5.3 Migration of the five call sites

`getProperty()` disappears. `shell`, `home-content`, `heading-block`,
`section-placeholder` and `app.component` change from a field holding a plain
object to a field holding the signal, and their templates gain `()`. This is
what makes a language switch propagate without a single subscription:
`ActionCardComponent` re-renders because `home-content`'s `property()` changed
and its `card` input changed with it.

---

## 6. Preferences: defaults ⊕ the guest's choice ⊕ persistence (REQ-119 … REQ-122, REQ-126, REQ-127)

> **Rewritten in round 2.** Decision #2 came back as *"die Persistenz sollte in
> der Datenbank erfolgen"* — the opposite end of the option list from the
> recommendation. The first draft designed a read-only seam and left the answer
> open; what is actually needed is a **read/write, asynchronous contract with a
> stand-in behind it**. This section is the part of the feature with no prior
> art in this codebase, so every sub-question the answer raises is answered
> here rather than left to implementation.

### 6.1 The three layers, and what each one means

```
class PreferencesService {                      // injects PreferenceGateway, nothing else
  private defaults  = signal<PreferenceDefaults>(FALLBACK_DEFAULTS);   // ← property config
  private languageOverride   = signal<LanguageCode | null>(null);      // ← the guest
  private appearanceOverride = signal<AppearanceMode | null>(null);    // ← the guest

  readonly language   = computed(() => this.languageOverride()   ?? this.defaults().language);
  readonly appearance = computed(() => this.appearanceOverride() ?? this.defaults().appearance);
  readonly resolvedTheme = computed<'light'|'dark'>(…);   // 'system' → matchMedia

  applyPropertyDefaults(config: PropertySettingsConfig): void
  hydrate(): Promise<void>                      // §6.4 — bounded, never rejects
  setLanguage(code: LanguageCode): void         // validated against config.languages
  setAppearance(mode: AppearanceMode): void     // validated against config.appearances
  reset(): void                                 // clears both, locally and in the store
}
```

The distinction REQ-120 asks to be explicit is explicit in the **types**: the
override signals are `T | null`, and `null` means "no guest choice is in
effect". Nothing can read an override as a default or vice versa, and `reset()`
is two lines.

One clarification the persistence answer forces: after hydration the override
layer may hold a choice the guest made **on a previous visit to this screen**,
restored from the store. It is still the guest's choice, not a default — the
override layer is *never* populated from `defaults`. That is what keeps
`override ?? default` honest and `reset()` meaningful.

`FALLBACK_DEFAULTS` exists only for the window before `applyPropertyDefaults()`
runs (today: zero frames — the app initializer). Its language is **not** a
hardcoded `'de'`; it is the first registered catalogue's code, so REQ-109's "no
component names a language" holds all the way down.

`resolvedTheme()` maps `'system'` through
`matchMedia('(prefers-color-scheme: dark)')` and subscribes to its `change`
event so a system flip is followed live. `matchMedia` is a platform API and
issues no request. **It is not the startup source of truth** (decision #4): the
shipped configuration offers `light` and `dark` only and defaults to `light`, so
`prefers-color-scheme` is read only by a property that deliberately configures
the `system` option.

### 6.2 What persists it — the decision, the stand-in, and the argument

**The database owns it.** Both values, written on change, delivered on load,
with the backend always supplying the defaults. That is the user's answer and
it is not re-litigated here.

Two things follow that the first draft did not have to design:

1. a **write** path — "the guest changed a preference" now means "persist it";
2. an **asynchronous** read — the stored value arrives after the module graph
   is constructed, not during it.

**Until the backend exists, the stand-in is a `sessionStorage`-backed gateway**
(`SessionPreferenceGateway`, ~25 lines), provided as the default
implementation of the contract in §6.3.

Why that and not the alternatives:

| Candidate stand-in | Verdict |
|---|---|
| **`sessionStorage` (chosen)** | Reproduces every observable property of the target: a change is stored, a reload delivers it back, an absent record yields the defaults. It exercises the **whole** hydrate path — load, validate, apply-before-paint — on every F5, which is exactly what "nutze … Tools um es zu testen" asks for. It dies with the browser session, so a kiosk reboot returns the screen to the property defaults |
| In-memory only | Simplest, and it was the round-1 recommendation *when nothing was meant to persist*. Against the decided architecture it is now the weakest option: the load path would be permanently dead code — `load()` always answering "nothing stored" — so the riskiest behaviour in this design (a stored value arriving, being validated and applied before the first paint) would be exercised by unit tests and never once by a human before the backend lands |
| `localStorage` | Same as `sessionStorage` but outlives the browser session too. It buys nothing the stand-in needs and leaves a stale record on the kiosk after the real gateway ships |
| A mock server (`json-server`, MSW, …) | The most faithful, and **not available**: a new dependency and a `package.json` edit, both approval-gated, on a device that cannot be patched centrally (constitution principle 4). Ruled out without asking |

**The counter-argument, and why it loses.** A stand-in that persists locally
carries one guest's choice to the next, because a kiosk tab is never closed.
That was the strongest argument for forgetting in round 1 — but the decided
architecture *already* carries the choice across guests, and more durably: a
database record outlives the reload, the reboot and the guest. A forgetting
stand-in would not protect anyone; it would only hide the carryover during
exactly the period when noticing it is cheapest. "Return to the property
defaults at check-out, or after N minutes idle" is now a required future
capability (`01-prd.md` → Risks), and it needs the stay model that does not
exist yet.

**If you disagree, it is one class and one provider line** —
`InMemoryPreferenceGateway` implementing the same three methods, named here so
the swap needs no design work.

Two rules the stand-in must honour, both of which stop it from being a lie:

- **It resolves through `Promise.resolve()`, never synchronously.** A gateway
  that answered inline would let ordering bugs hide until the day HTTP arrives.
- **It stores under one namespaced key** (`gxp.preferences`) holding the same
  `{ language?, appearance? }` record the backend will hold — no extra fields,
  no schema of its own. It is **deleted, not adapted**, when the HTTP gateway
  lands (REQ-121).

### 6.3 The contract (REQ-122)

```ts
// core/preferences/preference-gateway.ts
export interface StoredPreferences {
  language?: LanguageCode;
  appearance?: AppearanceMode;
}

export interface PreferenceGateway {
  /** The screen's stored preference; null when nothing is stored. */
  load(): Promise<StoredPreferences | null>;
  /** Persists the whole record (idempotent — a PUT, not a patch). */
  save(value: StoredPreferences): Promise<void>;
  /** Removes the record; the screen falls back to the property defaults. */
  clear(): Promise<void>;
}

export const PREFERENCE_GATEWAY = new InjectionToken<PreferenceGateway>('PreferenceGateway');
export const PREFERENCE_LOAD_TIMEOUT_MS = 1_000;
export const PREFERENCE_WRITE_ATTEMPTS  = 3;
export const PREFERENCE_WRITE_BACKOFF_MS = [250, 1_000] as const;
```

Three deliberate properties:

- **Whole-record writes, not patches.** `save()` always carries the current
  value of both settings, so a lost or reordered write can never leave the
  store half-updated, and the future endpoint is a plain idempotent `PUT`.
- **Promises, not observables.** There is no stream here — one load, one write
  per change. `HttpClient` returns an observable, and the HTTP gateway will
  `firstValueFrom()` it; that conversion belongs inside the transport, not in
  every consumer's signature.
- **Policy lives above the transport.** Validation, the late-arrival rule, the
  optimistic update, the retry and the fallback are all in
  `PreferencesService` (§6.4/§6.5). The gateway is dumb on purpose: swapping it
  cannot change behaviour, which is what makes REQ-122 testable rather than
  aspirational.

### 6.4 Bootstrap, and how the flash is avoided (REQ-126 a–d)

`PropertyContentService` is synchronous today and stays synchronous — the
property config is static TypeScript. Only the *guest's stored preference* is
asynchronous. So there is exactly one `await` in the whole boot sequence:

```ts
// app.config.ts
provideAppInitializer(() => {
  const content = inject(PropertyContentService);
  const preferences = inject(PreferencesService);
  preferences.applyPropertyDefaults(content.settingsConfig());
  return preferences.hydrate();          // resolves; never rejects
})
```

Angular holds bootstrap until the initializer's promise settles, so **`App`'s
constructor, its two `effect()`s and the first paint all happen after
hydration**. The first value ever written to `<html data-theme>` / `<html lang>`
is already the final one. There is no light→dark flash and no German→English
flip, structurally — not by being fast, but by ordering.

What could go wrong, and the rule for each:

| Case | Rule |
|---|---|
| The store answers slowly, or not at all | `hydrate()` races `load()` against `PREFERENCE_LOAD_TIMEOUT_MS` (1 s) and resolves either way. On timeout the app boots on the property defaults and renders normally. Worst case is a 1 s blank frame on a screen that is otherwise about to paint — and note the app is served **by the same local box**, so if that box were unreachable the bundle would not have loaded at all (`01-prd.md` → Assumptions) |
| `load()` rejects | Caught inside `hydrate()`; treated as "nothing stored". `hydrate()` **must never reject** — a rejected app initializer fails the whole bootstrap, which would turn a preference hiccup into a blank kiosk |
| A late answer arrives after the timeout | Applied under the same rule as any other answer — **only into an override that is still `null`** (below). The window is a second at boot; if the guest has already tapped, the tap wins |
| The stored value is not in the property's configured option list | **Discarded, not applied.** A property that removed English must not have it resurrected by an old record; `AppearanceMode`/`LanguageCode` are strings, so a stored value is untrusted input at a system boundary (constitution → Constraints) |
| The guest changes a setting while `load()` is still in flight | The late answer is applied **per setting**, and only if that setting's override is still `null`. So `if (this.languageOverride() === null) this.languageOverride.set(stored.language)` — the guest's choice is never overwritten. No extra "dirty" flag exists, because "the override is non-null" already means exactly "a choice is in effect" |

`applyPropertyDefaults()` must run **before** `hydrate()` applies anything —
otherwise there is no option list to validate the stored value against. The
initializer above is the single place that ordering is expressed.

### 6.5 The write path (REQ-126 e)

A guest tap does three things, in this order:

1. **update the signal** — the screen changes immediately; nothing awaits
   anything;
2. **schedule a persist** of the current whole record;
3. return. There is no loading state, no disabled control, no confirmation.

Persisting is a *sync-the-current-state* operation, not a queue of events:

```
persist():
  if a write is in flight → mark dirty, return
  snapshot = { language: languageOverride(), appearance: appearanceOverride() }   // nulls dropped
  attempt save(snapshot), up to PREFERENCE_WRITE_ATTEMPTS with the backoff
  on settle: if dirty → clear dirty and persist() again with a fresh snapshot
```

Coalescing rather than queueing matters: a guest tapping through three
languages produces one or two writes carrying the *latest* state, never three
races whose arrival order decides what the database ends up holding.

`reset()` clears both overrides and calls `clear()` through the same
mechanism.

**When every attempt fails.** The retries stop, the screen keeps the guest's
choice, and **nothing is shown and nothing is logged**:

- *Nothing is shown* — a guest cannot fix the property's server, and an error
  toast about a database on a display-preference tap is the definition of a UI
  that feels broken. The visible consequence is that the choice does not
  survive the next reload, which is invisible to a guest who never reloads.
- *Nothing is logged* — this project has no logging convention yet
  (`docs/system-context.md` → Logging: `<not yet decided>`), and a failed
  preference write is not the place to invent one. Constitution principle 3
  also forbids this mechanism from becoming a "log what the guest did" hook.
  The failure is observable where it belongs — in tests, via the fake gateway's
  attempt count. When a diagnostics surface exists, this is the hook point;
  it is deliberately not built now.
- The next preference change starts a fresh attempt cycle, so a server that
  comes back is picked up by the next tap without any retry timer running in
  between.

All timers (`backoff`, the load timeout) are cleared through `DestroyRef`, so
no test leaks one into the next.

### 6.6 Whose preference is it? (REQ-127)

There is no guest identity in this product, and
`docs/system-context.md` → "Open security questions" still carries
`[NEEDS DECISION: does the app authenticate the guest at all?]`. This feature
must not pre-empt it, and does not need to:

**The subject is the screen.** One instance per property, one touchscreen, one
stored record. No identifier of any kind is invented, stored, sent or logged —
not a guest id, not a stay id, not a session id, not a device id. The record's
entire shape is `{ language?, appearance? }`.

Consequences, stated so nobody "improves" them later:

- The future endpoint is `GET`/`PUT`/`DELETE` on a single resource with **no
  identifier in the path** and no cookie or token to carry one. The instance
  *is* the scope, exactly as it is for the property's content.
- When guest authentication *is* decided, scoping the record per stay is a
  change **inside the gateway and the backend**. `PreferencesService`, every
  component and every test stay as they are — which is the same argument that
  made `PropertyContentService` a service in the first place (principle 2).
- This is the placeholder concept, flagged as one: it is the *absence* of an
  identity scheme, chosen because it is the only option that cannot conflict
  with whatever the open decision lands on.

### 6.7 What the backend feature inherits

Not built here; written down so it is not rediscovered:

- Serve the stored preference **fast, or in the same response as the property
  config** — the 1 s budget in §6.4 is a safety net, not a design target.
- Always answer with the property's defaults when no record exists
  (`light`, `de` for the example property).
- Accept an idempotent whole-record write; `DELETE` means "back to defaults".
- **Validate the payload server-side.** The client validates before applying,
  but the endpoint is on an untrusted LAN and the constitution requires input
  validation at every boundary.
- Decide what clears the record — check-out, idle timeout, or an explicit
  reset in Settings (which already exists as `reset()`).
- Secure it: this is the product's **first write endpoint**, unauthenticated,
  on a network shared with guest devices and possibly neighbours
  (`01-prd.md` → Risks). Its exposure model is an `architect`-level question
  for that feature, not this one.

## 7. i18n mechanism — ✅ decision #1, approved (option A)

The three options and their costs are in [`01-prd.md`](01-prd.md) → "Decisions
taken" #1 and are not repeated. **The user chose option A on 2026-08-18** — a
hand-rolled signal-based service, no dependency — and stated that the language
set is expected to stay German + English. This section designs it.

That last part is a licence to keep it small, and §7.2 spends it: REQ-109's
"a third language is config + catalogue only" is preserved because it is a
property of doing the simple thing correctly, **not** by building machinery for
a language nobody has asked for.

### 7.1 Shape

```
// core/i18n/catalog/de.ts  — the source of truth for the key set
export const de = {
  'nav.aria':                 'Hauptnavigation',
  'section.comingSoon':       'Bald verfügbar.',
  'statusBar.language.aria':  'Sprache wählen',
  'statusBar.appearance.aria':'Darstellung wählen',
  'statusBar.time.aria':      'Aktuelle Uhrzeit',
  'appearance.light':         'Hell',
  'appearance.dark':          'Dunkel',
  'appearance.system':        'Automatisch',
  'settings.title':           'Einstellungen',
  'settings.language.title':  'Sprache',
  'settings.language.hint':   '…',
  'settings.appearance.title':'Darstellung',
  'settings.appearance.hint': '…',
  'settings.selected.aria':   'Ausgewählt',
} as const;

export type TranslationKey = keyof typeof de;      // compile-time completeness
// core/i18n/catalog/en.ts
export const en: Record<TranslationKey, string> = { … };
```

`en` is typed `Record<TranslationKey, string>`, so **a missing key is a build
error** (REQ-111), not a runtime warning nobody sees on a kiosk. The unit test
adds what types cannot: no empty strings, and no key present in `en` but absent
from `de`.

```
class TranslationService {                       // injects PreferencesService
  readonly language = this.preferences.language;
  t(key: TranslationKey, params?: Record<string, string|number>): string
}
```

`t()` reads `language()`. Because Angular's template expressions are reactive
consumers under zoneless change detection, `{{ t('section.comingSoon') }}`
re-renders when the language signal changes — with no pipe, no
`markForCheck()`, and no subscription.

Components expose it as `protected readonly t = inject(TranslationService).t;`.
For that to work, **`t` must be declared on the service as an arrow-function
class property, not as a method** — a method reference detached from its
receiver loses `this` and throws on the first call. This is a small detail that
fails loudly and immediately, which is why it is pinned here rather than left
to be discovered. A `TranslationPipe` was considered and rejected: it buys
nothing over a bound function under signal-based change detection, and a pure
pipe would additionally have to be made impure to see the language change.

Unknown key → falls back to the default catalogue, then returns the key
itself. It **never logs the failing value** — a translation key is not guest
data, but the mechanism must not become the place where property content
leaks into a log line (constitution principle 3).

> **Do not touch `LOCALE_ID`.** Angular's `DatePipe`/`DecimalPipe` read it, it
> is provided once at bootstrap and is not meaningfully runtime-switchable.
> Nothing in this feature uses those pipes — the clock formats through `Intl`
> directly — so `LOCALE_ID` stays at its default and is not part of the
> language mechanism. Changing it looks like the "proper" fix and is a dead end.

### 7.2 What is explicitly not built

No pluralisation, no gender, no ICU, no date/number formatting helpers, no
lazy loading, no extraction tooling. `Intl.PluralRules` and
`Intl.NumberFormat` are in the platform when they are first genuinely needed.

### 7.3 Why nothing is fetched

Two languages × ~15 chrome strings is well under 2 kB in the main bundle.
Loading them over HTTP — even same-origin — would add a request that can fail,
to an app whose defining constraint is working with the building's internet
down (constitution principle 4), and would buy a saving smaller than one icon.

---

## 8. The status bar (REQ-112 … REQ-115)

### 8.1 Placement — overlay, not a row

Read off [`../../design/assets/home-concept-ai.png`](../../design/assets/home-concept-ai.png)
(scale 1280 / 1671 = 0.766), the bar sits in the top-right corner of the
content area, above and clear of everything else.

Putting it in the flow (a header row inside `.shell__content`) would push
Home's heading down by ~40 px and shrink the empty space the composition
depends on — silently regressing `home-screen-prototype`'s REQ-014/REQ-022,
which were measured and signed off. So:

```
.shell            { position: relative }                    ← added
<app-status-bar>  sibling of <app-sidebar> and <main>,      ← new
                  :host { display: contents }               ← frontend/CLAUDE.md
.status-bar       { position: absolute;
                    top:   var(--space-6);   /* = the shell's own vertical padding */
                    right: var(--space-7);   /* = the shell's own horizontal padding */
                    height: var(--size-touch-min) }
```

`display: contents` on the host is not optional: without it the host box —
not `.status-bar` — would be the absolutely positioned element, and an empty
in-flow host would additionally consume one `--space-8` flex gap in `.shell`.
That is the exact defect class that produced four bugs in the previous
feature.

It is **not** placed inside `<main>`: app chrome does not belong in the main
landmark. It is a sibling of it.

> **It deliberately carries no `role="toolbar"`.** That role is the obvious
> reach, and it is a promise: ARIA's toolbar pattern requires roving `tabindex`
> and arrow-key traversal between its controls. We are not building that for
> two buttons and a clock, so claiming the role would describe an interaction
> model the component does not have — worse than no role. Two `<button>`s with
> real accessible names and a `<time>` are already correct and already
> keyboard-reachable in tab order.

### 8.2 Geometry and the collision budget

| | design 1280 × 832 | floor 1024 × 768 |
|---|---|---|
| Bar box | x 1040 → 1248, y 24 → 68 | x 832 → 998, y 19 → 54 |
| Home card row (existing) | ends x 992, top y 548 | ends x 794 |
| Home heading ink (existing) | starts y 88 | starts y 70 |
| **Horizontal clearance** | **48 px** | **≈ 38 px** |
| **Vertical clearance to the heading** | **20 px** | **16 px** |

Nothing collides at either end of the supported range, but the margin is not
generous, which is why REQ-112 requires it **measured**, not eyeballed — and
why `frontend/CLAUDE.md` gets the "top-right corner is reserved" clause (§4b).

### 8.3 Composition

```
[🌐 DE ⌄]   [☀ ⌄]   │   16:42
 language   appear.  div  clock
```

- **Language trigger** — `phosphorGlobe` at `--size-icon-xs`, the language's
  `shortLabel` at `--text-status`, `phosphorCaretDown` at `--size-icon-caret`.
  Matches the concept exactly.
- **Appearance trigger** — `phosphorSun` when the resolved theme is light,
  `phosphorMoon` when dark, plus a caret. **No text label**, so the bar's
  width does not move when the language changes; the trade-off, and the
  `design-sheet.md` rule it bends, are recorded in `01-prd.md` → Risks. Its
  accessible name comes from the catalogue.

  > Note the trigger glyph follows the **resolved** theme, not the selected
  > mode: with `system` selected and the system dark, the trigger shows a moon,
  > because it is reporting what the guest is looking at. The *menu* lists each
  > configured option with its own configured icon — including `system`
  > (`phosphorCircleHalf`) *if* a property ever configures it, which the
  > shipped config deliberately does not (decision #4), so that one glyph is
  > not registered in S15. Getting this backwards would make the trigger show
  > a half-circle for a screen that is plainly dark.
- **Divider** — 1 px (`--color-divider`), full control height, `--space-4`
  either side. The one place a raw `px` is correct: it is a hairline
  (`docs/constitution.md` → Constraints).
- **Clock** — `<time [attr.datetime]>` at `--text-clock`.

New tokens, all semantic, all recorded in `tokens.md` with an in-house
provenance note (the same discipline `--gradient-icon-active` established):
`--size-touch-min: 2.75rem`, `--size-icon-xs: 1.125rem`,
`--size-icon-caret: 0.75rem`, `--text-status` (0.875rem / 700 / 1.3),
`--text-clock` (1.125rem / 700 / 1.3), `--text-h1` (2rem / 800 / 1.1, from
`design-sheet.md`'s 📋 H1), `--color-divider`.

> **Why the clock is not `Caption 13px Medium 500`**, which `design-sheet.md`
> proposes for exactly this ("Zeiten, Distanzen, Meta-Infos"): only 400, 700
> and 800 are self-hosted (`global.css`). Using 500 means shipping a fourth
> woff2 for one string. `--text-clock` is 700 instead. Written down so the
> next person does not "fix" the deviation by adding a font file.

### 8.4 Dropdowns (REQ-115)

`@angular/cdk/menu` — `CdkMenuTrigger` / `CdkMenu` / `CdkMenuItem`,
already-approved, unstyled. It supplies the keyboard model, focus return,
Escape handling and ARIA roles; every pixel is ours. A native `<select>` was
rejected: its popup is OS-rendered, cannot take the glass treatment, and looks
like an admin tool — precisely what `design-sheet.md`'s design direction rules
out.

One generic `StatusBarMenuComponent` serves both controls: inputs are the
options, the selected value and the trigger content; output is the choice.
Panel: `--color-surface-glass` + `--blur-glass` + `--border-glass` +
`--radius-lg`, `--space-2` padding, options at `--size-touch-min` minimum,
selected option marked with `--gradient-menu-active` **and** a
`phosphorCheck` — never colour alone.

`@angular/cdk/overlay-prebuilt.css` must be imported once
(`src/styles.css`); it ships inside the installed package, so this is a
bundled asset and no external request (REQ-106).

Overlay geometry: panel anchored below-end of its trigger, 2 options each,
≈ 104 px tall, bottom at y ≈ 180 — nowhere near the viewport edge, so the
overlay cannot introduce a scrollbar. Asserted at review time anyway
(REQ-123).

### 8.5 The clock under zoneless change detection (REQ-114)

```
class ClockService {                     // providedIn: 'root'
  private readonly _now = signal(new Date());
  readonly now = this._now.asReadonly();
  // setTimeout(msUntilNextMinute) → then setInterval(60_000); cleared via DestroyRef
}
```

Three things this gets right that a naive implementation gets wrong:

- **Zoneless is a non-issue if state is a signal.** Writing `_now` marks the
  consuming template dirty and schedules change detection. No `NgZone`, no
  `ChangeDetectorRef`, no `markForCheck()`. Reaching for any of those here
  would be the bug, not the fix.
- **Minute alignment.** A bare `setInterval(60_000)` from load ticks at
  whatever second the app happened to start, so the display is wrong by up to
  59 s. The first timer fires at the next `:00`.
- **Teardown.** `DestroyRef.onDestroy` clears both timers, so tests do not
  leak them between cases.

Formatting lives in the component: a `computed()` combining `clock.now()` with
the active language's `locale` through `Intl.DateTimeFormat(locale, { hour:
'2-digit', minute: '2-digit' })`. The `DateTimeFormat` instance is memoised per
locale — constructing one per tick is the usual quiet performance mistake.

`en-GB`, not `en-US`, so English shows `16:42` rather than `04:42 PM` (see
`01-prd.md` → Assumptions). It is a config value, so a property can differ.

---

## 9. The Settings screen (REQ-116 … REQ-118)

### ⚠️ Design gap — stated, not hidden

**No Figma design exists for this screen, and none was requested.** Every
value below is composed from `docs/design/tokens.md`'s existing tokens and
`docs/design/design-sheet.md`'s conventions; nothing here was extracted from
the design file and nothing here may be cited as if it were. `design-sheet.md`
records the screen as 📋 (intent, not built/extracted), consistent with its own
legend. If Settings is ever drawn, that becomes authoritative and this section
becomes a deviation log.

### 9.1 Composition

```
Einstellungen                                    --text-h1, top-anchored
┌─ glass panel ──────────────────────────────┐
│ Sprache                --text-title         │
│ kurzer Hinweis         --text-body/secondary│
│ [✓ Deutsch] [ English ]                     │  all options visible
└─────────────────────────────────────────────┘
┌─ glass panel ──────────────────────────────┐
│ Darstellung   /   [☀ ✓ Hell] [🌙 Dunkel]     │
└─────────────────────────────────────────────┘
```

- Content column inset `--space-9` from the top, matching Home, so it starts
  below the status bar's reserved corner. Title, then two panels with
  `--space-4` between them.
- Panel width is **derived, not a new token**:
  `calc(3 * var(--size-panel) + 2 * var(--space-4))` — exactly three action
  cards wide, so it lines up with Home's grid instead of introducing an
  arbitrary measurement.
- Panels use the existing glass recipe (`--color-surface-glass`,
  `--border-glass`, `--blur-glass`, `--radius-2xl`, `--shadow-panel`), so the
  screen inherits dark mode with no extra work.
- One generic `SettingChoiceComponent` renders both groups: a label, a hint,
  and a row of option buttons. Selected = `--gradient-menu-active` background
  + `--text-title` weight + `phosphorCheck`; unselected = transparent with a
  1 px `--color-divider` border. Three signals, never colour alone.
- Each option button is at least `--size-touch-min` (44 px) tall
  (`design-sheet.md` → Touchscreen-Regeln).

> **At the 1024 × 768 floor, 44 px renders as 35 px** — below the guideline.
> This is not a defect introduced here: the whole UI is `rem`-based on an
> uncapped viewport-proportional root (`docs/constitution.md` v1.2.0), so every
> target shrinks by the same 0.8 factor there, exactly as body text drops to
> 11.2 px. The design size is the target size; the floor is a supported size.
> Inflating this one token to compensate would break its relationship with
> everything around it. Recorded here so it joins the pending hardware check
> rather than being rediscovered as a bug.

**Height budget** (design size): title 35 + `--space-6` 24 + panel 152 +
`--space-4` 12 + panel 152 ≈ **375 px** in the 656 px available between the
`--space-9` insets. Comfortable at both ends of the viewport range, so
REQ-123 is met by construction.

### 9.2 Routing

`app.routes.ts`'s `einstellungen` entry swaps `SectionPlaceholderComponent`
for `SettingsComponent` and drops its `data: { sectionId }`. The other four
placeholder routes are untouched — and `SectionPlaceholderComponent` itself
still exists and is still needed, so nothing is deleted.

---

## 10. Test strategy (REQ-124)

Vitest + `TestBed`, zoneless, `PropertyContentService` **and the preference
gateway** supplied as test doubles via DI — never by importing the data file
(`frontend/CLAUDE.md`). jsdom computes no layout, so **nothing about geometry,
wrapping or contrast is asserted in a unit test**; those are review-time
measurements.

A single `FakePreferenceGateway` carries the whole persistence surface: it
answers `load()` with a scripted value, on a scripted delay or never; it
records every `save()`/`clear()` call; and it can be told to reject a given
number of times. Every behaviour in §6.4/§6.5 is reachable through it **without
touching `PreferencesService`** — which is simultaneously the proof of
REQ-122.

| Area | What is asserted |
|---|---|
| `PreferencesService` — state | effective = default before any choice; override wins; only the changed one moves; `reset()`; unknown value rejected; `applyPropertyDefaults()` recomputes |
| `PreferencesService` — hydrate | a stored record is applied; an absent one leaves the defaults; a value outside the configured options is discarded; a `load()` that rejects or never resolves leaves the defaults and **`hydrate()` still resolves**; an answer arriving after the guest has chosen does **not** overwrite that choice, per setting |
| `PreferencesService` — persist | a change calls `save()` once with the whole record; three fast changes coalesce and the last state is what is finally written; a rejecting `save()` is retried `PREFERENCE_WRITE_ATTEMPTS` times and then stops; after every failure the effective values still hold the guest's choice; `reset()` calls `clear()`; no `console.*` call is made on any path |
| The stand-in gateway | writes and reads back through `sessionStorage` under one namespaced key; resolves asynchronously (a synchronous return would be a bug); an absent or malformed record reads as `null` |
| Bootstrap initializer | applies the property defaults **before** hydrating; the app boots even when `load()` rejects |
| `resolvedTheme()` | `'system'` follows `matchMedia` (mocked), and a live `change` event flips it |
| `TranslationService` | known key per language; unknown key → default catalogue → key; parameter interpolation |
| Catalogues | identical key sets, no empty values (the type system covers "missing") |
| `PropertyContentService` | resolves per active language; per-field fallback to the property's `defaultLanguage`; **throws** when a default-language entry is missing; `getSectionRoute()` unchanged |
| `App` | writes `data-theme` and `lang`; both update on change; `applyBranding()` still runs (existing test kept). With a stored preference hydrated first, the **first** attribute value written is already the stored one — the defaults are never written and then corrected (REQ-126 a) |
| `ClockService` | fake timers: first tick lands on the minute boundary, not 60 s after load; subsequent ticks at 60 s; teardown clears timers |
| `StatusBarComponent` | renders short code, mode glyph (sun ↔ moon), divider, formatted time; `Intl` formatting per locale; both menus open/select/close; `role="toolbar"` and translated labels |
| `SettingsComponent` | every configured option rendered, current one marked, selection updates the shared state, status bar reflects it |
| Routing | `/einstellungen` renders `SettingsComponent`, not the placeholder; the other four still render the placeholder |
| Migrated components | shell / home-content / heading-block / section-placeholder re-render on a language change |

**Review-time measurement** (`frontend/tools/measure.mjs`, extended — a
verification tool, not an E2E suite; `docs/constitution.md` v1.3.0): the
status bar's box on every route; non-collision with Home's card row and
heading at 1024 × 768 and 1280 × 832; no-scroll across **6 routes × 2 themes ×
2 languages × 3 viewports**; the settings screen's boxes; an open dropdown's
overlay; and a sampled contrast check of the dark composites against §3.4.

---

## 11. Risks & trade-offs

| Risk | Mitigation |
|---|---|
| The overlay status bar collides with a future screen's top-right content, silently | Measured assertion now (REQ-112), and a standing `frontend/CLAUDE.md` clause + `system-context.md` entry so it is inherited, not rediscovered |
| Two controls per setting drift apart | Both bind to the same `PreferencesService` signals; no local copies anywhere. A test asserts cross-surface consistency |
| The dark palette is unverified on real hardware | The pending 1024 × 768 legibility check (`design-sheet.md`) now has a second axis. Light-on-dark text renders perceptually thinner; if the hardware check fails, it fails harder here. Recorded, not solved |
| A half-translated property degrades invisibly | Per-field fallback keeps the screen usable; the missing-default-language case throws. Nothing yet *reports* partial translation — an admin-area concern, noted in the PRD |
| `color-mix()` is new to this codebase | Used twice, both times to keep a value **derived** rather than frozen. Baseline in every current engine; the kiosk browser is provisioned by us |
| `getProperty()` → signal touches five components at once | Mechanical, type-checked, covered by the existing specs. Done as one TODO step so no half-migrated state is ever committed |
| The language switch is instant, so a mistap changes the whole screen | Both controls are reversible in one tap and always visible. No confirmation dialogue — a kiosk should not ask twice about a display preference. A mistap now also writes to the store; the coalescing writer (§6.5) means the correction overwrites it rather than racing it |
| Gating the first paint on an `await` could hang the kiosk | The wait is bounded (1 s), `hydrate()` never rejects, and the store is the same box that just served the bundle. Worst case is one blank second, and only when the store is broken |
| The stand-in behaves *almost* like the backend, so a difference goes unnoticed | It is deliberately reduced to the same three methods and the same record shape, and it is async like the real one. The remaining differences are named in §6.2 (session lifetime) and §6.7 (server-side validation) |
| `sessionStorage` on the kiosk keeps one guest's choice for the next | True — and true of the decided architecture too, more durably (§6.2). The mitigation is a check-out/idle reset, which needs the stay model and is recorded as a required future capability in `01-prd.md` → Risks, not silently deferred |
| The preference endpoint becomes the product's first unauthenticated write surface | Out of scope here and handed to the backend feature with a written brief (§6.7). Flagged in `01-prd.md` → Risks so it is not discovered during that feature's review |

---

## 12. Decisions — all resolved 2026-08-18

Full statements, the options and the user's own words are in
[`01-prd.md`](01-prd.md) → "Decisions taken". Summary of what was answered and
what it changed here:

| # | Decision | Answer | Effect on this design |
|---|---|---|---|
| 1 | i18n mechanism | **A — hand-rolled signal service**, no dependency; languages stay German + English | §7 stands as drafted. §7.2's "what is explicitly not built" gains weight: no machinery for a hypothetical third language |
| 2 | Persistence of the guest's choice | **The database owns it** — written on change, delivered on load, defaults always supplied by the backend; stand-in until then | **§6 rewritten.** Read/write contract (§6.3), bootstrap gate and no-flash ordering (§6.4), write policy and failure behaviour (§6.5), the identity placeholder (§6.6), the backend brief (§6.7). Follow-on edits in §1, §5.2, §10, §11, §13 |
| 3 | `frontend/CLAUDE.md` amendment (§4) | **Approved verbatim** (*"Ja passt"*) | §4's text is unchanged and now authorised; S13 writes it. Round 2 added nothing to it on purpose |
| 4 | "Follow system" appearance state | **Approved**, with the clarification that the startup value is the backend-delivered default (`light`), not `prefers-color-scheme` | §6.1 states it; S23 corrects `docs/design/tokens.md` → Implementation, which still describes *absent = follow system* |

**Nothing in this feature is open.** The decisions this feature *creates* for
someone else — the datastore, the endpoint, its security, and what clears a
stored preference at check-out — are recorded in §6.7 and stay approval-gated
where they belong.

---

## 13. `docs/system-context.md` updates (REQ-125)

To be written as part of this design phase's follow-through, in the same
tables rather than in a new section:

- **Components** — `PreferencesService`, `TranslationService`, `ClockService`,
  `StatusBarComponent` (+ its menu), `SettingsComponent` (+ `SettingChoice`);
  `PropertyContentService`'s row updated to mention the two signals and
  language resolution; `SectionPlaceholderComponent`'s row updated to four
  sections.
- **Cross-cutting concerns** — *Theming* gains the dark palette, its
  documentation location and the semantic-mesh rule; a new
  *Appearance & language* entry covers the runtime switch, the
  default-vs-choice model and the "nothing is fetched to render it"
  constraint; the *Viewport & scaling* entry gains the reserved top-right
  region.
- **Persistence** — currently reads "none in the first step". It stops being
  true in intent the moment this feature ships: the guest's language and
  appearance are **state the future database owns**, reached through
  `PreferenceGateway` and stood in for by `sessionStorage` until then. The
  datastore itself stays unchosen and approval-gated — the entry records the
  *fact*, not a choice.
- **Messaging / async** — currently reads "none". The app now has exactly one
  asynchronous boundary, gated at bootstrap with a bounded timeout, plus a
  fire-and-forget write with bounded retry.
- **Open security questions** — add the preference endpoint: the product's
  first unauthenticated *write* surface on an untrusted LAN, to be designed and
  secured by the backend feature (§6.7). Also record that this feature does
  **not** pre-empt the guest-authentication question: the stored record carries
  no identifier (§6.6).
- **Stack** — note that appearance and language are the first two settings
  shaped for the future Quarkus backend, that `applyPropertyDefaults()` is the
  read seam and `PreferenceGateway` the read/write one.
