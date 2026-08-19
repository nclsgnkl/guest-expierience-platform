# PRD: Appearance & Language
**Feature:** [index.md](../index.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Status:** Approved — **all four decisions answered by the user on 2026-08-18**;
see "Decisions taken". Amended the same day (round 2) so the artifacts match the
answers.

> **Continuation of [`home-screen-prototype`](../home-screen-prototype/01-prd.md),
> on the same branch `feature/home-screen-prototype`** (user instruction,
> 2026-08-18). This is still Home-screen development: it adds the two
> foundations — appearance and language — that every later screen will
> otherwise have to retro-fit.

> **REQ-IDs start at REQ-101, not REQ-001 — deliberately.** The shipped code
> carries `REQ-003`, `REQ-014`, `REQ-023` … in its comments, all of them
> `home-screen-prototype`'s. Restarting at `REQ-001` here would make every
> such comment ambiguous the moment a second feature touches the same file,
> and several files *are* touched again (`tokens.css`, `shell.component.*`,
> `app.component.ts`, `property.model.ts`). One flat, project-wide number
> space, 100 per feature, costs nothing and keeps every existing comment
> unambiguous.

## Problem statement

Two capabilities are missing that are cheap now and expensive later:

1. **There is no dark mode.** The `data-theme` mechanism was wired during
   `home-screen-prototype` (REQ-003) and `frontend/src/styles/tokens.css`
   ends with a literally empty `[data-theme='dark'] { }` block, because no
   dark palette was ever designed. `docs/design/tokens.md` records this twice
   — "Still blocked on design → No dark palette exists" and
   `[NEEDS CLARIFICATION: no dark-mode palette exists — is one wanted?]`.
2. **Every user-visible string is hardcoded German**, in three different
   places: component templates (`"Bald verfügbar."`, `aria-label="Hauptnavigation"`),
   property content (`example-property.data.ts` — headline, card copy, nav
   labels), and nothing distinguishes the two. A guest who does not read
   German cannot use the screen at all, and the longer this waits the more
   strings there are to retro-fit.

Both are *foundations*: every screen built after this one inherits them for
free, and every screen built before them has to be revisited. The user asked
for them together and explicitly for that reason.

Alongside them, the Home screen is still missing the top-right chrome the AI
concept ([`../../design/assets/home-concept-ai.png`](../../design/assets/home-concept-ai.png))
has always shown — a language indicator and a clock — which
`docs/design/screens/home.md` currently records only as an uncommitted
hypothesis ("Top bar — language selector, clock … **None of these are
committed** — treat them as prior art, not requirements"). **This PRD commits
it**, extended with an appearance control, on the user's instruction.

Finally, the `Einstellungen` route is still a `SectionPlaceholderComponent`
showing "Bald verfügbar." — the natural and expected home for both settings,
and the first route in the app to get real content beyond Home.

## Goals / Non-goals

**Goals**

- A complete dark appearance with **the same properties as the light one** —
  same semantic roles, same glassmorphism, same mesh-gradient character, same
  or better text contrast. Not an inversion, not a greyscale.
- Runtime language switching with **no page reload and nothing to fetch in
  order to render it**, covering both app chrome and property content. (The
  persistence write of REQ-121 is same-origin, non-blocking and outside the
  render path — see REQ-106.)
- The top-right status bar from the AI concept — language, appearance, clock
  — present on **every** route, not only Home.
- Both settings also reachable, with all options visible, on the
  `Einstellungen` route.
- Both settings modelled the way the backend will really serve them
  (decision #2, answered 2026-08-18): the available options and the
  property-configured default arrive through `PropertyContentService`, and the
  guest's own choice is a separate, explicitly modelled layer that is
  **persisted** and **delivered back on load** through a gateway contract —
  with a stand-in behind that contract until the backend exists.
- Nothing existing regresses: the measured Home geometry, the no-scroll rule
  and the 1024×768 → 1280×832 → larger scaling contract all still hold, now
  across two themes and two languages.

**Non-goals**

- **A Figma design for any of this.** No dark variant and no settings screen
  exist in the design file, and `docs/constitution.md` forbids spending a
  metered design-tool request to look. The dark palette and the settings
  screen are designed **in-house** here (the user explicitly delegated the
  colour concept), and the gap is recorded rather than papered over — see
  REQ-101 and REQ-118.
- **A real backend, an endpoint, or a datastore choice.** Nothing is fetched
  in this feature. What *is* built is the contract the backend will implement —
  a read **and write** gateway for the guest's preference (REQ-121/REQ-122) —
  plus a stand-in behind it. Choosing the database, designing the endpoint and
  securing it on an untrusted LAN stay approval-gated and belong to the backend
  feature.
- **Per-property dark branding derivation.** `docs/design/tokens.md` already
  carries the open question of deriving 16 mesh stops from ~3 brand
  primitives; dark mode adds a second set of 16 and does not answer it. The
  fixed default palette is what ships, exactly as in light mode.
- **Translating property *content* for real properties.** The example
  property gains English copy so the mechanism is demonstrably real; writing
  or sourcing translations for actual customers is an operational task, not
  this feature.
- **Automatic translation, machine translation, or a translation-management
  workflow.** Catalogues are hand-authored TypeScript.
- **RTL languages.** German and English only; no layout mirroring, no
  `dir="rtl"` handling. Adding an RTL language later is a layout project, in
  the same sense phone support is.
- **Guest identity, per-stay scoping, or any auth.** The stored preference
  belongs to **the screen** — one instance, one property, one touchscreen — not
  to an identified person. No guest identifier is invented, stored, transmitted
  or logged (REQ-127), and the open guest-authentication question in
  `docs/system-context.md` stays open and un-pre-empted. "Clear the preference
  when a stay ends" needs the stay model that does not exist.
- **A "reset to property default after N minutes idle" behaviour.** Plausible
  for a shared kiosk, out of scope here; noted in Risks.

## Assumptions

- **The kiosk browser is modern and known.** Self-hosted on a device we
  provision (`docs/system-context.md` → Deployment model), so `Intl`,
  `color-mix()`, CSS custom properties, `matchMedia` and `:has`-free CSS are
  all available. No polyfills, no fallbacks for ancient engines.
- **`Intl.DateTimeFormat` is a platform feature, not a dependency.** The clock
  and any future date formatting use it directly. It ships in the engine, adds
  zero bytes and makes zero requests — exactly what constitution principle 4
  asks for.
- **Only three font weights ship** (400 / 700 / 800 — `global.css`). So
  `docs/design/design-sheet.md`'s 📋 `Caption 13px Medium 500`, which it
  proposes precisely for "Zeiten", **cannot be used** without adding a fourth
  woff2. The clock uses an existing weight instead (REQ-113); nobody should
  "fix" this later by reaching for 500.
- **The dark palette needs no user sign-off.** The user delegated it
  explicitly ("Entwickle bitte selbständig ein Farbkonzept"). Its *values* are
  therefore a design decision made in this feature. Where it is **documented**
  is a different question and is answered explicitly (REQ-101), because
  `docs/design/tokens.md` is the as-built Figma record and must stay honest.
- **`data-theme` and `lang` on `<html>` are written by the app, in one place.**
  `app.component.ts` already owns the only sanctioned write to
  `document.documentElement` (`applyBranding()`, `frontend/CLAUDE.md`). Both
  new attributes join it there rather than being scattered.
- **The status bar belongs to `layout/`, not `features/home/`.** It is
  route-agnostic chrome, exactly like `SidebarComponent` and the mesh
  background, both of which were moved out of `features/home/` into `layout/`
  during `home-screen-prototype` for this same reason. Putting it in
  `features/home/` would mean the clock and the language switch vanish on
  five of six routes.
- **The status bar overlays; it does not push.** Home's vertical composition
  was measured and signed off (heading ink at y = 88, card row bottom at
  y = 744, REQ-014/REQ-022). A status bar in the flow moves those. See
  REQ-112.
- **English uses `en-GB`, i.e. a 24-hour clock.** The properties are
  European, the concept render shows `16:42`, and a 12-hour clock would also
  make the status bar's width jump by ~3 characters when the language
  changes. Recorded as a decision, not a question — it lives in config
  (REQ-119), so a property can override it without a code change.
- **The example property stays a single example property.** English copy is
  added to it; no second property is introduced.
- **The backend always supplies a default** — appearance `light`, language
  `de` (user decision, 2026-08-18). The startup value is therefore a
  *configured default*, never `prefers-color-scheme`: resolution order is
  stored preference → property/backend default. `system` is reached only if a
  property configures it.
- **The app and its preference store are served by the same local box.** The
  bundle already comes from the property's own server, so "the store is
  unreachable at load" is close to impossible — if it were, the app would not
  have loaded at all. The realistic failure is that server restarting *while* a
  screen is open, which is a **write** failure, not a read failure. That is what
  makes a short, bounded wait for the stored preference at startup acceptable
  (REQ-126).

## User stories

- *As a guest who does not read German*, I switch the interface to English
  from the top-right corner and every label, card, menu entry and message
  changes immediately — without the screen reloading, and without the
  property's WiFi details or house rules becoming unreachable while it does.
- *As a guest using the screen at night*, I switch to the dark appearance and
  the room stops being lit by a bright pink screen — while the app still looks
  like the same product, not like a different app.
- *As a guest*, I glance at the top-right corner and see what time it is,
  the same way the concept always showed it.
- *As a guest who missed the top-right controls*, I find both settings under
  `Einstellungen`, with every option visible at once rather than hidden behind
  a menu.
- *As a property owner*, I set my house's default language and default
  appearance in my configuration — the screen comes up that way for every
  guest, and I do not need a code change or a deploy to change it.
- *As the next developer*, I add a screen and never write a German string in a
  template, because there is one obvious mechanism and a test that fails if I
  skip it.

## Requirements

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| REQ-101 | A dark colour concept is designed in-house and documented in a **new file, `docs/design/dark-theme.md`** — *not* in `docs/design/tokens.md`, which stays the as-built record of what exists in Figma. `tokens.md`'s `[NEEDS CLARIFICATION: no dark-mode palette exists — is one wanted?]` and its "Still blocked on design" note are resolved **by pointing at that file**, with the provenance stated: designed in-house 2026-08-18, no Figma counterpart, and a re-import will not reproduce it (same discipline `--gradient-icon-active` already established). `design-sheet.md` gains a cross-reference | Must |
| REQ-102 | The `[data-theme='dark']` block overrides **semantic roles only**. No primitive is ever redefined by the theme axis. Dark *raw values* are added as **new primitives** in `:root` (`--ink-050`, `--ink-300`, `--night-1…16`, the dark surface rawvalues), so raw values still live once in the primitive layer and the two axes stay independent: a property's runtime branding (`applyBranding()`, inline on `:root`) keeps working identically in both themes | Must |
| REQ-103 | The mesh background gains semantic roles `--color-mesh-1…16` and `--color-mesh-base`, defaulting to the existing `--mesh-*` primitives; `shell.component.css` references the semantic roles instead of the primitives, and the dark theme maps them to `--night-*`. This removes the last place in `src/` that reads a primitive. It contradicts an existing `frontend/CLAUDE.md` clause; the exact replacement text drafted in `02-design.md` §4 was **approved verbatim by the user on 2026-08-18** (decision #3) and is written by step S13 | Must |
| REQ-104 | Dark mode preserves light mode's **properties**, provably: glass surfaces stay translucent with the same menu:glass alpha ratio (0.4 : 0.6 = 2 : 3), the same `blur(8px)`, the same radii, spacing, typography and layout; brand accents (`--gradient-icon`, the icon tile, the logo) stay the brand colours; and **no text role in dark falls below WCAG AA (4.5:1) on any surface it appears on** — which light mode cannot claim, its secondary text scoring 3.48:1 on the sidebar and 3.99:1 on glass. Dark's weakest text role is 5.42:1. Exactly one dark role scores lower than its light counterpart (primary text on a glass panel, 8.91 vs 10.89), and both are far above every threshold. All measured figures are recorded in `02-design.md` and `docs/design/dark-theme.md` | Must |
| REQ-105 | Appearance is modelled as `'light' \| 'dark' \| 'system'`; the app always writes a **resolved** `light` or `dark` to `<html data-theme>`, so nothing renders in the "attribute absent" state. Which options a guest is offered comes from config and defaults to Hell/Dunkel only. **Approved 2026-08-18** (decision #4), with one clarification that changes where the initial value comes from: the startup appearance is the **backend-delivered default** (`light`), **not** `prefers-color-scheme`, which is consulted only when a property actually configures `system`. `docs/design/tokens.md` → Implementation still says *absent = follow system* and is corrected to match by step S23 | Must |
| REQ-106 | The UI language can be changed at runtime with **no page reload and nothing fetched in order to render it** — catalogues are TypeScript modules inside the bundle, so the new language is on screen before anything leaves the device. The **only** request a switch may ever cause is the same-origin, fire-and-forget persistence write of REQ-121: the switch does not await it, and if it never completes the switch is still complete and correct. **No external host is ever contacted**, and switching works with the property's internet connection down (constitution principle 4) | Must |
| REQ-107 | **No user-visible string is hardcoded in a component, template or route definition** — including `aria-label`s, `alt` text and empty/error states. Every one resolves through the translation mechanism. The two that exist today (`"Bald verfügbar."`, `aria-label="Hauptnavigation"`) are migrated | Must |
| REQ-108 | **Property content is localisable.** Guest-facing config fields (`heroSubtitle`, `headline`, `paragraph`, every `menuItems`/`settings` `label`, every card `header`/`description`) become per-language maps. `PropertyContentService` resolves them for the active language and falls back **to the property's own default language** per field — never to the app's, and never to an empty string. A config missing its default-language entry is rejected loudly (constitution: input validated at every system boundary) | Must |
| REQ-109 | German and English ship. Adding a third language is a **config + catalogue** change with **no component change** — demonstrated by the fact that nothing in `src/app/**` names a language other than through config/catalogue lookup | Must |
| REQ-110 | `<html lang>` tracks the active language, and `<html data-theme>` the resolved appearance. Both are written in exactly one place, next to the existing `applyBranding()` hook | Must |
| REQ-111 | Catalogue completeness is enforced **at compile time** — one catalogue is the key source of truth and every other is typed `Record<TranslationKey, string>`, so a missing key fails `ng build`, not a runtime guess — plus a unit test asserting identical key sets and no empty values | Must |
| REQ-112 | An app-wide status bar lives in `frontend/src/app/layout/status-bar/` and renders on **every** route. It is positioned as an **overlay inside the shell's padding box (top-right)** and consumes no flow space, so no route's existing geometry moves: Home's heading ink stays at y = 88 and its card row bottom at y = 744 at the design size (REQ-014/REQ-022 of `home-screen-prototype` remain satisfied, unchanged) | Must |
| REQ-113 | The status bar shows, left to right, matching [`home-concept-ai.png`](../../design/assets/home-concept-ai.png)'s top-right corner: a **language control** (globe glyph + the language's short code, e.g. `DE`, + caret), an **appearance control** (a glyph reflecting the current mode — sun for light, moon for dark — + caret), a thin vertical divider, and the **current time**. Both controls open a dropdown listing the configured options | Must |
| REQ-114 | The clock shows hours and minutes, formatted for the active language's locale via `Intl.DateTimeFormat`, and updates **on the minute boundary** (not every 60s from load). It ticks correctly under zoneless change detection — the time lives in a signal, no `NgZone`, no manual `detectChanges()` — and its timer is torn down with its owner | Must |
| REQ-115 | Both dropdowns are built on the already-approved `@angular/cdk` menu primitives: keyboard operable (Enter/Space/Arrow/Escape), touch operable, focus-managed, closing on selection and on Escape; every option row is at least 44 × 44 CSS px at the design size (`design-sheet.md` → Touchscreen-Regeln); the open overlay never introduces a scrollbar on any axis (REQ-123) | Must |
| REQ-116 | `/einstellungen` renders a real `SettingsComponent` from `features/settings/` instead of `SectionPlaceholderComponent`. The other four undesigned sections keep the placeholder | Must |
| REQ-117 | The Settings screen offers both settings with **all options visible simultaneously** (no dropdown), each option a full touch target. Both controls read and write the **same state** as the status bar: changing one is reflected in the other with no navigation and no refresh | Must |
| REQ-118 | The Settings screen has **no Figma design**. It is composed from existing tokens and `design-sheet.md` conventions only, and the gap is stated explicitly in `02-design.md` and recorded in `docs/design/design-sheet.md`, so it is never mistaken for an extracted spec | Must |
| REQ-119 | The **available options and the property-configured defaults** for both settings arrive through `PropertyContentService` — available languages (with their code, short code, locale and display name), default language, available appearance options, default appearance — the same slice the backend will serve, and the source of the always-present default (`de` + `light` for the example property, per decision #2). No component imports the data file (constitution principle 2), and no component hardcodes an option list | Must |
| REQ-120 | The distinction between the **property-configured default** and the **guest's runtime override** is modelled explicitly: the override is a separate, initially-unset layer, the effective value is `override ?? default`, and a `reset()` returns the screen to the property's defaults. Reading code must not have to guess which of the two a value is | Must |
| REQ-121 | The guest's choice of language **and** appearance is **persistent state owned by the backend's database** (user decision, 2026-08-18) — not ephemeral UI state. Changing either one **persists** it; the stored value is **delivered on load**; and the backend always supplies the defaults (`light`, `de`) that apply when nothing is stored. The app therefore has a read path **and a write path**, both behind one gateway contract. Until the backend exists, a lightweight stand-in implements that same contract (`02-design.md` §6.2) and is **deleted, not extended**, when the HTTP implementation lands | Must |
| REQ-122 | Both contracts stay swappable. Replacing static TypeScript with the future Quarkus backend must be a change **inside `PropertyContentService`** (property config) plus **one new gateway implementation and the single provider line that names it** (guest preference). No component changes, and no line of `PreferencesService`'s policy changes — optimistic update, validation, retry and fallback live above the transport, so swapping the transport cannot alter behaviour. Components consume signals; nothing outside those two seams knows where a value came from or that a network exists | Must |
| REQ-123 | Nothing scrolls, on **any route × either theme × either language**, across 1024 × 768 → 1280 × 832 → larger — `scrollWidth ≤ clientWidth` and `scrollHeight ≤ clientHeight`, met by construction, never by `overflow: hidden` (constitution → Constraints; `home-screen-prototype` REQ-024/REQ-025 stay in force). English and German string lengths differ; no label may wrap or truncate that did not before | Must |
| REQ-124 | Vitest unit tests accompany every behaviour: preference defaults and overrides, translation resolution and fallback, catalogue completeness, localised content resolution and the missing-default-language rejection, the theme/lang attribute writes, the clock (fake timers, minute alignment, teardown), the status bar's rendering and both dropdowns, the Settings screen, and the `/einstellungen` route change | Must |
| REQ-125 | `docs/system-context.md` is updated in the same pass: the new components, the appearance/language cross-cutting concern, the newly non-empty *Persistence* and *Messaging / async* entries, and the "top-right region is reserved app-wide" layout constraint that every future screen now inherits | Must |
| REQ-126 | Preference delivery is **asynchronous and failure-tolerant, and the guest sees neither**: (a) the stored preference is awaited **once, at bootstrap, behind a bounded timeout**, so the first paint is already correct — no light→dark flash, no German→English flip; (b) a preference that arrives after the guest has already chosen **never overwrites that choice**; (c) a stored value that is not in the property's configured option list is **discarded, not applied**; (d) a failed or timed-out read falls back to the property defaults and the app renders normally; (e) a failed write is retried a bounded number of times, never blocks input, never shows the guest an error, and leaves the screen on the guest's choice | Must |
| REQ-127 | The stored preference is scoped to **the property's own screen**, not to a person: no guest identifier is invented, stored, sent or logged, and nothing here pre-empts the open guest-authentication decision in `docs/system-context.md`. Neither the preference values nor any identifier ever reaches a log line (constitution principle 3) — a failed write may report **that** it failed, and nothing more | Must |

## Acceptance criteria

- **REQ-101** — Given `docs/design/dark-theme.md`, when it is opened, then it
  contains the complete dark role table with its measured contrast figures and
  a provenance header stating it was designed in-house and has no Figma
  counterpart; and `docs/design/tokens.md` contains **no** dark colour values,
  only a resolved pointer replacing the old `[NEEDS CLARIFICATION]`.
- **REQ-102** — Given `frontend/src/styles/tokens.css`, when the
  `[data-theme='dark']` block is inspected, then every declaration in it is a
  `--color-*` / `--gradient-*` / `--shadow-*` semantic role and **none** is a
  primitive (`--brand-*`, `--ink-*`, `--mesh-*`, `--night-*`,
  `--surface-*-raw`). Given a runtime branding override is applied, when the
  theme is switched, then the branded icon tile keeps the property's colours
  in both themes.
- **REQ-103** — Given `frontend/src/**`, when grepped for `var(--mesh-`,
  `var(--ink-`, `var(--brand-` and `var(--night-`, then the only matches are
  in `tokens.css` itself. (Today `shell.component.css` matches 17 times.)
- **REQ-104** — Given the dark theme, when the composite contrast of each text
  role over its own surface is computed, then every value is ≥ 4.5:1, the
  weakest is 5.42:1, and every figure matches the table recorded in
  `dark-theme.md` within rounding. Given the dark theme, when the glass panels
  are inspected, then `backdrop-filter` is still `blur(8px)`, the surfaces are
  still translucent (alpha < 1), and the sidebar:panel alpha ratio is still
  2 : 3.
- **REQ-105** — Given any of the three appearance values, when the app
  renders, then `document.documentElement.dataset.theme` is exactly `light`
  or `dark` — never absent, never `system`. Given a fresh load with the shipped
  configuration and nothing stored, then the appearance is `light` because that
  is the configured default, and `prefers-color-scheme` is **not** consulted —
  provable by flipping the mocked media query and observing no change. Given a
  property that *does* configure `system` and a changing `prefers-color-scheme`,
  when the media query flips, then the resolved attribute follows without a
  reload.
- **REQ-106** — Given the app is running with the property's internet
  connection down, when the language is switched, then every string changes and
  the route does not reload; **zero requests leave the property** — the only
  request permitted is the same-origin preference write, and the rendered
  result does not depend on it. Given a production build, when its output is
  searched, then no `fetch`/`XMLHttpRequest`/`import()` of a translation
  resource exists, and no external URL of any kind does either.
- **REQ-107** — Given `frontend/src/app/**/*.html` and `*.ts`, when scanned
  for user-visible literals, then none remains outside catalogue files and
  property config. Given the language is English, when every route is
  visited, then no German text is on screen.
- **REQ-108** — Given the active language is English and the example property
  supplies English copy, when Home renders, then headline, subtitle,
  paragraph, all six nav labels and all four cards are English. Given a
  language for which one field has no translation, when it renders, then that
  field falls back to the property's default language while the rest stay
  translated. Given a config whose default-language entry is missing on any
  localised field, when the service loads it, then it throws.
- **REQ-109** — Given `frontend/src/app/**`, when searched for the strings
  `'de'`/`'en'`, then no component matches; only config data and catalogue
  registration do.
- **REQ-110** — Given English + dark, when `<html>` is inspected, then
  `lang="en"` and `data-theme="dark"`. Given the setting changes, then both
  update within the same change-detection pass.
- **REQ-111** — Given a key removed from a non-source catalogue, when
  `ng build` runs, then it fails. Given `npm test`, then a test asserts equal
  key sets across catalogues and no empty values.
- **REQ-112** — Given every route at 1280 × 832, when the status bar's
  bounding box is measured, then its right edge is at x = 1248 and its top at
  y = 24 (the shell's padding box), and Home's heading ink is still at y ≈ 88
  with the card row bottom at y = 744 — **identical to before this feature**.
  Given the status bar and the Home card row, when their boxes are compared at
  both 1024 × 768 and 1280 × 832, then they do not intersect.
- **REQ-113** — Given the light theme and German, when the status bar renders,
  then it shows a globe glyph, `DE`, a caret, a sun glyph, a caret, a divider
  and a `HH:MM` time, in that order. Given the dark theme, then the sun glyph
  is a moon.
- **REQ-114** — Given fake timers at 16:41:30, when 30 s elapse, then the
  displayed time becomes `16:42` (one update, at the boundary — not at
  16:42:30). Given the owning injector is destroyed, then no timer remains.
  Given `provideZonelessChangeDetection()`, then the DOM updates without any
  manual change-detection call.
- **REQ-115** — Given a closed dropdown, when its trigger is activated by
  Enter, Space or touch, then it opens, focus moves into it, Escape closes it
  and returns focus to the trigger, and selecting an option closes it and
  applies the value. Given an open dropdown at 1024 × 768, then
  `scrollWidth ≤ clientWidth` and `scrollHeight ≤ clientHeight` still hold.
- **REQ-116** — Given `/einstellungen`, when it renders, then
  `SettingsComponent` is in the DOM and `SectionPlaceholderComponent` is not;
  the shell, sidebar and background are unchanged and the sidebar's
  Einstellungen item is active.
- **REQ-117** — Given the Settings screen, when it renders, then every
  configured language option and every configured appearance option is
  visible without opening anything, the current one is marked, and each option
  is ≥ 44 × 44 CSS px at the design size. Given a change made in the status
  bar, when Settings is opened, then it already shows the new value, and vice
  versa.
- **REQ-118** — Given `02-design.md`, then it states in its own section that
  no Figma design exists for this screen and what it was composed from; and
  `design-sheet.md` records the screen as 📋 (intent, not extracted).
- **REQ-119** — Given `PropertyContentService`, then it exposes the available
  languages and appearance options and both defaults. Given
  `frontend/src/app/**` excluding `core/content/`, when grepped for the data
  file, then there are no imports of it outside the service and its own spec.
- **REQ-120** — Given a fresh load, then the effective values equal the
  property's defaults and the override layer is unset. Given the guest
  changes one, then only that override is set and the other stays at its
  default. Given `reset()`, then both return to the defaults.
- **REQ-121** — Given the guest changes the language, when the change is
  applied, then the gateway's `save()` is called once with the full current
  record (`{ language, appearance }`), and the screen shows the new language
  immediately, before the save resolves. Given a stored record and a fresh
  load, then the screen comes up on the stored values rather than the defaults.
  Given no stored record, then it comes up on the property defaults (`de`,
  `light`). Given `reset()`, then the gateway's `clear()` is called and both
  values return to the defaults. Given the whole feature, then exactly one file
  and one provider line name the transport.
- **REQ-122** — Given components, then none injects anything but
  `PropertyContentService` for content and `PreferencesService` for the two
  settings; the services' public surfaces return signals whose production could
  become asynchronous without changing a single call site's shape. Given a test
  that swaps the provided gateway for one that fails, delays or returns a
  stored record, then every behaviour of REQ-121/REQ-126 is exercised **without
  editing `PreferencesService`**.
- **REQ-123** — Given all six routes × 2 themes × 2 languages at 1024 × 768,
  1280 × 832 and 1440 × 900, when measured in a real browser, then
  `scrollWidth ≤ clientWidth` and `scrollHeight ≤ clientHeight` in all 72
  combinations, with `overflow: visible` on `html`/`body`. Given English, then
  no sidebar label, card title or status-bar label wraps or truncates.
- **REQ-124** — Given `npm test`, then the suite is green and covers every
  behaviour listed.
- **REQ-125** — Given `docs/system-context.md`, then the new components are in
  the component table, appearance/language is a cross-cutting concern, the
  reserved top-right region is recorded as a layout constraint on future
  screens, and *Persistence* / *Messaging / async* no longer read "none".
- **REQ-126** — Given a gateway whose `load()` resolves with a stored record
  differing from the defaults, when the app boots, then the **first** value
  written to `<html data-theme>`/`<html lang>` is already the stored one — the
  defaults are never written first. Given a gateway whose `load()` never
  resolves, when the timeout elapses, then the app boots on the property
  defaults and renders normally. Given a `load()` that resolves *after* the
  guest has changed a setting, then the stored value is ignored for that
  setting and the guest's choice stands. Given a stored value not present in
  the configured option list, then it is discarded and the default applies.
  Given a `save()` that rejects, then the UI keeps the guest's choice, no error
  surface appears anywhere in the DOM, the call is retried a bounded number of
  times, and the retries stop.
- **REQ-127** — Given the gateway and `PreferencesService`, when read, then no
  identifier of any kind (guest, stay, session, device) is constructed, stored
  or transmitted, and the stored record's shape is exactly
  `{ language?, appearance? }`. Given every failure path, when it is exercised
  in a test with `console` spied, then no call carries a preference value, a
  key, or an identifier.

## Technical constraints

Anchored to `docs/constitution.md` v1.3.0 and `frontend/CLAUDE.md`:

- **No dependency-manifest edit.** `package.json` is not touched. Everything
  needed is already installed: `@angular/cdk` (dropdowns), `@ng-icons` (globe,
  caret, sun, moon), and the platform's own `Intl`. Any i18n option that would
  add a package is approval-gated — see decision #1.
- **No external runtime requests.** Translation catalogues are bundled
  TypeScript. No HTTP-loaded locale JSON, not even same-origin: it would add a
  failure mode (a request that can fail mid-switch) to buy nothing, on a
  device whose entire value proposition is working with the internet down.
  The preference write (REQ-121) is the one same-origin request this feature
  can cause, and it is deliberately **outside the render path** — it cannot
  fail "mid-switch", because the switch never waits for it.
- **Tokens only.** No raw hex or px in a component. Dark values live in the
  token layer; the *only* new raw values are new primitives in `tokens.css`.
- **`rem`, no breakpoints**, hairlines and blur radii excepted.
- **Nothing scrolls**, now across the theme and language axes too (REQ-123).
- **`:host` display discipline** (`frontend/CLAUDE.md` → Layout): the status
  bar's host must be `display: contents`, or the absolutely-positioned bar
  would resolve against the wrong box and an empty host would still take a
  flex gap — the exact defect class that cost four bugs in the last feature.
- **Icon glyphs cannot be styled from a component stylesheet** — anything
  reaching inside an `@ng-icons` `<svg>` goes in `global.css` behind a
  purpose-named class.
- **Guest-sensitive data is never logged.** A language or appearance choice is
  not sensitive, but the mechanism that stores it must not become a general
  "log what the guest did" hook, and no missing-translation warning may ever
  interpolate property content into a log line.
- **Zoneless.** All new state is signals; the clock's interval writes a signal
  and that is the entire change-detection story.
- **Tests accompany code**; every REQ-ID is traced by a TODO task.
- **No new dependency for persistence either.** The stand-in uses a platform
  API; the future implementation uses Angular's own `HttpClient`
  (`@angular/common`, already installed). A mock-server package
  (`json-server`, MSW and friends) would be a `package.json` edit and is ruled
  out twice over — constitution principle 4 and "No dependency-manifest edits".
- **Bootstrap may wait briefly, boundedly, and must never hang.** Exactly one
  `await` gates the first paint (the stored preference), it is capped, and on
  timeout the app boots on the property defaults (REQ-126). No spinner, no
  splash screen: on a local box this resolves in single-digit milliseconds.
- **Branch:** stay on `feature/home-screen-prototype`. No new branch, no
  commit by an agent.

## Decisions taken

All four were raised with a recommendation on 2026-08-18 and **answered by the
user the same day**. The options and their costs are kept below deliberately:
a later reader should be able to see *what was decided and why it beat the
alternatives*, not only the outcome. Nothing here is open.

### ✅ 1 — The i18n mechanism · **decided: A, hand-rolled** (2026-08-18)

| Option | What it costs |
|---|---|
| **A. Hand-rolled signal-based translation service (chosen)** | ~80 lines in `core/i18n/`. No dependency. Catalogues are typed TS objects, so completeness is a compile error. Switching is a signal write; templates re-render because template expressions are reactive consumers under zoneless Angular. Costs: we own it — pluralisation, gender and ICU message formatting are not free, and we have none of those needs today |
| **B. `@angular/localize`** | Angular's official answer, and **the wrong shape for this product**: it is build-time. One bundle per locale, chosen by URL or by deploying separate artifacts; runtime switching means a reload and serving N builds from the kiosk. Also a `package.json` addition. Its strengths (extraction tooling, XLIFF workflows, ICU) buy nothing for two hand-authored catalogues |
| **C. `ngx-translate` / `transloco`** | Mature, runtime-switchable, signal-friendly (Transloco especially). But a **new dependency** on a device that "cannot be patched centrally" (constitution principle 4), whose default loaders fetch JSON over HTTP — the exact pattern principle 4 rules out, so we would configure it to do less than it is designed for |

**The user's answer:** *"Ja eine Eigenentwicklung schadet hier nicht. Es wird
sich auf Englisch und Deutsch hinauslaufen die Sprachen."* — option A, and the
language set is expected to stay **German + English**.

Consequence for the design: build the mechanism, not a platform. REQ-109 ("a
third language is config + catalogue, no component change") stays, because it
is a *property* of doing the simple thing correctly and costs nothing to keep —
but nothing speculative is built for it: no lazy loading, no locale
negotiation, no plural machinery, no extraction tooling (`02-design.md` §7.2).

### ✅ 2 — Persistence of the guest's choice · **decided: the database owns it** (2026-08-18)

This answer **differs from the recommendation** and is the one with real design
impact. The options as they were put:

| Option | Consequence |
|---|---|
| In-memory only *(was recommended)* | The screen returns to the property's default on reload/reboot. A guest's choice cannot leak to the next guest. Decides nothing infrastructural |
| `sessionStorage` | Survives a reload, dies with the tab. On a kiosk the tab is never closed — so it is effectively permanent |
| `localStorage` | Survives everything, including the guest leaving. Someone must then decide when it is cleared |
| **Server-side (chosen)** | The honest long-term answer if the preference is real state. Needs the backend, which does not exist yet |

**The user's answer:** *"Die Persistenz sollte in der Datenbank erfolgen. Ändert
der Gast seine Präferenz soll das in der Datenbank gespeichert werden und übers
Backend geliefert werden. Gilt für Sprache als auch Theme. Somit wird auch immer
ein Standardwert (Light und Deutsch) vom Backend geliefert. Bis es das Backend
gibt, nutze leichtgewichtige schnell ausbaubare oder austauschbare Tools um es
zu testen."*

Read out into requirements — this is what REQ-121, REQ-122, REQ-126 and
REQ-127 now say:

1. The preference is **persistent state owned by the backend/database**, for
   language *and* appearance. It is not ephemeral UI state.
2. The contract therefore needs a **write path**, not only the read seam the
   first draft designed: changing a preference *means* persisting it.
3. The stored value is **delivered by the backend on load**, and the backend
   **always supplies a default** — appearance `light`, language `de`. So the
   resolution order is stored preference → configured default, and `system` is
   never the shipped default (decision #4).
4. **Until the backend exists**, a lightweight, easily swappable stand-in sits
   behind the same contract. Choosing it was delegated to the planner as an
   implementation detail; the choice, its argument and the alternative are
   recorded in `02-design.md` §6.2.

What this answer does **not** decide, and what stays approval-gated: the
database itself, the endpoint shape, and how an unauthenticated write endpoint
is secured on an untrusted LAN. Those belong to the backend feature.

### ✅ 3 — The `frontend/CLAUDE.md` amendment (REQ-103) · **approved verbatim** (2026-08-18)

REQ-103 makes the mesh gradient read semantic roles, which **contradicts an
existing clause** in `frontend/CLAUDE.md` → Structure ("One documented
exception: the mesh-gradient background … composes the `--mesh-1…16`
primitives directly"). The exact replacement wording — plus a new
"Appearance & language" section — is drafted in `02-design.md` → "Proposed
`frontend/CLAUDE.md` amendment".

**The user's answer:** *"Ja passt"* — the drafted text is approved **as
drafted**. Step S13 writes it verbatim; only line wrapping may be adjusted. The
approval covers that wording and not a later revision of it, so the round-2
amendments deliberately added **nothing** to it (see `02-design.md` §4).

### ✅ 4 — The "follow system" appearance state (REQ-105) · **approved, with a clarification** (2026-08-18)

`docs/design/tokens.md` → Implementation specifies three `data-theme` states,
with **absent = follow the system preference**. On a fixed-purpose kiosk, "the
system" is a wall-mounted tablet's OS setting — a provisioning detail, not a
signal from the guest.

**The user's answer:** *"Ja kommt standardmäßig aus dem Backend siehe Punkt 2"*
— the recommendation is accepted (`system` stays representable in the model,
`<html>` always receives a resolved `light`/`dark`, and `system` is not offered
in the guest's dropdown by default), **and** the startup value is not "follow
the system" but the **backend-delivered default (`light`)**. `prefers-color-scheme`
is therefore *not* the initial source of truth anywhere in this feature; it is
consulted only if a property explicitly configures the `system` option. Step
S23 corrects `docs/design/tokens.md` accordingly.

## Risks & open questions

- **The status bar is an overlay, and overlays collide silently.** It occupies
  roughly 210 × 44 CSS px at the top-right of the content area. Nothing is
  there today on any route, and at the 1024 × 768 floor the gap to Home's card
  row is only ~6 px. This becomes a standing constraint on every future screen
  (REQ-125) and is exactly the kind of rule that gets forgotten — hence a
  measured assertion rather than a note.
- **Two controls for one setting** (compact dropdown in the bar, all-options
  in Settings) is deliberate, and is also two places to keep consistent. Both
  bind to the same signal, which is what makes it safe; a future third
  surface should reuse the same components rather than add a third pattern.
- **The appearance trigger shows no text label**, only a sun/moon glyph and a
  caret, so the bar's width does not depend on the language. This bends
  `design-sheet.md`'s "Icons immer mit Text kombinieren, wenn die Funktion
  nicht eindeutig ist". Judged acceptable — sun/moon is near-universal, the
  dropdown itself is fully labelled, and Settings carries the explicit
  labelled control. If you disagree, the fix is a label plus a width budget.
- **Property content translation is now an operational burden.** Every
  property that adds a language must author copy for it, or guests silently
  see the default language for those fields. The fallback is per field, so a
  half-translated property degrades gracefully rather than breaking — but
  nothing yet *reports* that a property is half-translated. A future admin
  concern.
- **A shared kiosk keeps the previous guest's choice** until the page reloads,
  whatever decision #2 lands on. An idle-reset ("return to property defaults
  after N minutes untouched") is the real answer and is out of scope. Worth
  scheduling before real properties run this.
- **Dark mode's mesh has 16 more colours to derive per property**, and
  `tokens.md`'s existing question — how to derive 16 stops from ~3 brand
  primitives — now has two answers to produce, not one. Unchanged for this
  feature (fixed palette, both themes), but the debt doubled.
- **Legibility at the 1024 × 768 floor is still an open hardware check**
  (`design-sheet.md`, `tokens.md`): body text renders at 11.2 px and labels at
  9.6 px there. Dark mode makes light text on dark surfaces, which is
  perceptually thinner. If the pending hardware check fails, it fails harder
  in dark mode. Flagged, not solved.
- **A preference endpoint on an untrusted LAN is a *write* endpoint.** Once
  the backend exists, "store this screen's preference" is an unauthenticated
  mutation that any device on the LAN can call. It changes what a guest sees
  rather than exposing data, so the impact is nuisance rather than disclosure —
  but it is the first write surface this product has, and designing and
  securing it belongs to the backend feature (`docs/constitution.md` →
  "Security is a review category"). Recorded here because this feature is what
  makes it inevitable.
- **Database persistence makes the shared-kiosk carryover permanent, by
  design.** Storing the choice server-side means it outlives the reload, the
  reboot and the guest. "Return to the property defaults at check-out, or after
  N minutes idle" therefore stops being a nice-to-have and becomes a *required*
  future capability — and it needs the stay model that does not exist. The
  stand-in was chosen so this is visible during development rather than on the
  day the backend ships (`02-design.md` §6.2).
- **The stand-in is temporary and must be deleted, not extended.** Anything
  built on top of it beyond the gateway contract would become a second source
  of truth for a value the database owns.
- **One screen, one tab is assumed.** Two tabs on the same screen would each
  hydrate their own copy and the last write would win. A kiosk runs one tab;
  written down so nobody rediscovers it as a bug.
- **`color-mix()` is used once** (the dark active-glyph stops, so they stay
  derived from the branding primitives instead of being frozen). It is a
  platform feature, baseline in every current engine, and adds no dependency —
  but it is the first use in this codebase.
