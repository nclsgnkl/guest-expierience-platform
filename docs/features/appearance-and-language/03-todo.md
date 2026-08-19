# TODO: Appearance & Language
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md)
**Status:** Implementation complete — **all 39 of 39 steps done, nothing is
blocked.** All four decisions were answered on 2026-08-18. Sections A–J are
closed; the feature is ready for `06_review`. Implementation order deviated from
the reading order for three dependency reasons — see `handoff.md` → "Order
deviation".

> Each task traces to a `REQ-ID` from [`01-prd.md`](01-prd.md) and implements a
> decision already made in [`02-design.md`](02-design.md) — **`dev` does not
> re-decide anything here.** Complete one step, build and test, then move to the
> next.
>
> **Branch: stay on `feature/home-screen-prototype`.** No new branch. No agent
> commits.
>
> **Step IDs are stable and append-only.** S38 and S39 were added in round 2
> and are placed in the section they belong to, so the numbering is not
> sequential in reading order. Existing IDs were **not** renumbered —
> `02-design.md`, `04-analyze.md` and the handoff all reference them.

## Decision record — what was answered, and what it unblocked

Answered by the user on **2026-08-18**; full text and rationale in
[`01-prd.md`](01-prd.md) → "Decisions taken".

| # | Decision | Answer | Effect on these steps |
|---|---|---|---|
| 1 | i18n mechanism | **Hand-rolled signal service**, no dependency; German + English | S3, S4 proceed exactly as drafted |
| 2 | Persistence of the guest's choice | **The database owns it** — persisted on change, delivered on load, defaults always from the backend; `sessionStorage` stand-in until then | S6 **rewritten** (contract + stand-in); **S38 added** (policy + bootstrap); **S39 added** (tests); S9, S11, S15, S26, S28, S30 adjusted |
| 3 | `frontend/CLAUDE.md` amendment | **Approved verbatim** | S2 and S13 proceed; S13 writes the text unchanged |
| 4 | "Follow system" appearance state | **Approved**, and the startup value is the backend default (`light`), never `prefers-color-scheme` | S5 proceeds; **S23 gains** the `tokens.md` correction |

---

## A — Token layer and the dark palette

- [x] **S1** Add the dark **primitives** and the `[data-theme='dark']`
      **semantic-role** block to `tokens.css`, exactly per `02-design.md` §3.2
      and §3.3. New primitives: `--ink-050`, `--ink-300`, `--night-1…16`,
      `--surface-glass-dark-raw`, `--surface-glass-border-dark-raw`,
      `--surface-menu-dark-raw`. New/overridden roles in the dark block only:
      text primary/secondary, the three surfaces, `--gradient-menu-active`,
      `--color-icon-active-start` / `-end` (via `color-mix`), `--shadow-panel`.
      Replace the two placeholder comments currently sitting in the empty
      `[data-theme='light']` / `[data-theme='dark']` blocks — _REQ-101,
      REQ-102, REQ-104_
  - Files: `frontend/src/styles/tokens.css`
  - Validate: `ng build` green; every declaration inside `[data-theme='dark']`
    is a `--color-*` / `--gradient-*` / `--shadow-*` role and **none** is a
    primitive; setting `data-theme="dark"` by hand in devtools visibly
    re-themes text, panels and sidebar (the mesh still does not change — that
    is S2)
- [x] **S2** Promote the mesh to semantic roles (decision #3 approved
      2026-08-18, so the `frontend/CLAUDE.md` clause this contradicts is
      cleared — S13 writes the replacement): add
      `--color-mesh-1…16` and `--color-mesh-base` to the semantic layer
      (defaulting to `--mesh-*` / `--mesh-6`), map them to `--night-*` inside
      `[data-theme='dark']`, and change `shell.component.css` to reference the
      roles instead of the primitives (17 occurrences, including
      `background-color`) — _REQ-103, REQ-102_
  - Files: `frontend/src/styles/tokens.css`,
    `frontend/src/app/layout/shell/shell.component.css`
  - Validate: `grep -rn "var(--mesh-\|var(--ink-\|var(--brand-\|var(--night-" frontend/src`
    returns matches **only** in `tokens.css`; light mode is pixel-identical to
    before; dark mode paints the night mesh; the existing shell spec stays green

## B — Translation mechanism

- [x] **S3** Create the i18n model and catalogues (decision #1: hand-rolled,
      approved 2026-08-18):
      `LanguageCode`, `TranslationKey` derived from the German catalogue via
      `keyof typeof de`, `de.ts` as the key source of truth, `en.ts` typed
      `Record<TranslationKey, string>`, and `index.ts` registering both with
      their codes — _REQ-106, REQ-109, REQ-111_
  - Files: `frontend/src/app/core/i18n/translation.model.ts`,
    `frontend/src/app/core/i18n/catalog/{de,en,index}.ts`
  - Validate: deleting one key from `en.ts` **fails `ng build`**; the catalogue
    files contain no `fetch`, no `import()` and no HTTP of any kind
- [x] **S4** Implement `TranslationService` (decision #1): injects
      `PreferencesService`, exposes `language`, and `t` **as an arrow-function
      class property** (`02-design.md` §7.1 — a plain method loses `this` when
      components hold a reference to it). Unknown key → default catalogue →
      the key itself, and never logged with any content interpolated —
      _REQ-106, REQ-107, REQ-111_
  - Files: `frontend/src/app/core/i18n/translation.service.ts`
  - Validate: unit test (S27); `const t = service.t; t('nav.aria')` works
    detached

## C — Preferences

- [x] **S5** Implement `PreferencesService`'s **state layer** per
      `02-design.md` §6.1 (the persistence layer is S38, deliberately a
      separate step): `defaults` signal seeded by `applyPropertyDefaults()`;
      `languageOverride` / `appearanceOverride` as `T | null`; `language()` /
      `appearance()` = `override ?? default`; `resolvedTheme()` resolving
      `'system'` through `matchMedia` **and** following its `change` event;
      `setLanguage` / `setAppearance` validating against the configured option
      lists; `reset()`. `FALLBACK_DEFAULTS` takes its language from the **first
      registered catalogue**, never a literal `'de'`.
      Decision #4 (approved 2026-08-18) fixes what `matchMedia` is *for*: it
      resolves `'system'` **when a property configures it**, and is **not** the
      startup source of truth — the shipped config defaults to `light` and
      never reaches that branch — _REQ-105, REQ-119, REQ-120, REQ-109_
  - Files: `frontend/src/app/core/preferences/preferences.service.ts`
  - Validate: unit tests (S28); no `import` of `example-property.data.ts`
    anywhere in the file; with the shipped defaults, flipping the mocked
    `prefers-color-scheme` changes nothing
- [x] **S6** Define the persistence **contract and its stand-in** per
      `02-design.md` §6.2/§6.3 (decision #2, 2026-08-18 — the database owns the
      guest's preference): `PreferenceGateway` with `load()` / `save()` /
      `clear()`, **all `Promise`-returning**, the `PREFERENCE_GATEWAY`
      injection token, and the three constants
      (`PREFERENCE_LOAD_TIMEOUT_MS = 1000`, `PREFERENCE_WRITE_ATTEMPTS = 3`,
      `PREFERENCE_WRITE_BACKOFF_MS = [250, 1000]`). Then
      `SessionPreferenceGateway`: one namespaced `sessionStorage` key
      (`gxp.preferences`) holding exactly `{ language?, appearance? }`,
      resolving through `Promise.resolve()` and **never synchronously**, and
      reading a missing *or malformed* record as `null`. Provide it as
      `PREFERENCE_GATEWAY` in `app.config.ts`.
      **This is a stand-in and is deleted, not adapted, when the HTTP gateway
      lands** — say so in the file's header comment — _REQ-121, REQ-122,
      REQ-127_
  - Files: `frontend/src/app/core/preferences/preference-gateway.ts`,
    `frontend/src/app/core/preferences/session-preference-gateway.ts`,
    `frontend/src/app/app.config.ts`
  - Validate: unit test (S39); the gateway files import **nothing** from
    `core/content/` (`02-design.md` §1 — the gateway must stay a leaf, or the
    DI cycle comes back); the stored record contains no identifier of any kind
    (REQ-127)
- [x] **S38** Wire persistence into `PreferencesService` **and bootstrap**, per
      `02-design.md` §6.4/§6.5. In the service: `hydrate()` racing `load()`
      against `PREFERENCE_LOAD_TIMEOUT_MS`, catching everything and **never
      rejecting**; applying a stored value **per setting and only while that
      setting's override is still `null`**, so a late answer can never
      overwrite a choice the guest already made; **discarding** any stored
      value that is not in the configured option list; the optimistic update +
      **coalescing** writer (in-flight ⇒ mark dirty, re-send a fresh whole-record
      snapshot afterwards) with bounded retry and backoff; `reset()` → `clear()`;
      every timer torn down through `DestroyRef`. In `app.config.ts`: a
      `provideAppInitializer` that calls
      `preferences.applyPropertyDefaults(content.settingsConfig())` **first**
      and then returns `preferences.hydrate()`.
      **Nothing is logged on any path and no error UI exists** (`02-design.md`
      §6.5) — _REQ-121, REQ-122, REQ-126, REQ-127_
  - Files: `frontend/src/app/core/preferences/preferences.service.ts`,
    `frontend/src/app/app.config.ts`
  - Validate: unit tests (S39); with a fake gateway answering
    `{ appearance: 'dark' }`, `<html data-theme>` is `dark` on the **first**
    value ever written — `light` is never written and then corrected; with a
    gateway whose `load()` never resolves, the app still boots after the
    timeout

## D — Content layer

- [x] **S7** Extend `property.model.ts` with `Localized<T>`, `LanguageOption`,
      `AppearanceMode`, `AppearanceOption`, `PropertySettingsConfig`, and turn
      the guest-facing fields (`heroSubtitle`, `headline`, `paragraph`,
      `MenuItem.label`, `ActionCard.header`/`.description`) into
      `Localized<string>`. Leave `guestName`, `icon`, `route`, `id`,
      `targetSectionId`, `logoAsset`, `hasArtwork`, `branding` alone —
      _REQ-108, REQ-119, REQ-122_
  - Files: `frontend/src/app/core/content/property.model.ts`
  - Validate: `ng build` fails until S8 supplies the new shape — that is the
    point; the compiler enumerates every site to touch
- [x] **S8** Rewrite `example-property.data.ts` in the new shape: German copy
      unchanged, English copy added for every localised field, plus the
      `settings` block — `languages` (`de`/`DE`/`Deutsch`/`de-DE`,
      `en`/`EN`/`English`/`en-GB` — endonyms, and `en-GB` for a 24-hour clock
      per `01-prd.md` → Assumptions), `defaultLanguage: 'de'`,
      `appearances` (light → `phosphorSun`, dark → `phosphorMoon`; **no
      `system` entry** — decision #4), `defaultAppearance: 'light'`. Those two
      defaults are exactly what the backend will supply later (decision #2), so
      do not "improve" them to follow the system or the browser language. English headline keeps the `{guestName}`
      placeholder: `Welcome to {guestName}'s Airbnb!` — _REQ-108, REQ-109,
      REQ-119_
  - Files: `frontend/src/app/core/content/example-property.data.ts`
  - Validate: `ng build` green again; no English string appears in any
    component or template
- [x] **S9** Rework `PropertyContentService`: replace `getProperty()` with
      `settingsConfig: Signal<PropertySettingsConfig>` (language-independent —
      **not** `settings`, which would collide with `PropertyConfig.settings`,
      the sidebar's Einstellungen menu item; `02-design.md` §5.2) and
      `property: Signal<PropertyConfig>` (a `computed()` resolving each
      localised field for `preferences.language()`, falling back per field to
      the property's own `defaultLanguage`). Add the **eager constructor-time
      validation** of `02-design.md` §5.2 — the three checks — throwing rather
      than resolving. `getSectionRoute()` keeps its signature — _REQ-108,
      REQ-119, REQ-122_
  - Files: `frontend/src/app/core/content/property-content.service.ts`
  - Validate: unit tests (S29); a config missing a default-language entry
    throws at construction, not at render
- [x] **S10** Migrate the five `getProperty()` call sites to the signal:
      `app.component.ts`, `shell.component.ts`, `home-content.component.ts`,
      `heading-block.component.ts`, `section-placeholder.component.ts` (plus
      the `()` in their templates). Do it as **one step** so no half-migrated
      state is ever committed — _REQ-108, REQ-122_
  - Files: the five components + their templates
  - Validate: `ng build` green; all existing specs green; a language change in
    devtools re-renders sidebar labels, heading and cards with no navigation

## E — Bootstrap and the clock

- [x] **S11** Extend `App`'s constructor: keep `applyBranding(...)` (now
      reading `content.property().branding`) and add two `effect()`s writing
      `document.documentElement.dataset.theme = resolvedTheme()` and
      `.lang = language()`. This stays the only code in `src/` touching
      `document.documentElement`.
      **`applyPropertyDefaults()` does *not* go here** — it moved to the app
      initializer in S38, because the defaults must be settled *and* the stored
      preference hydrated before the first effect run, or the screen paints the
      default and corrects itself a frame later (`02-design.md` §6.4) —
      _REQ-105, REQ-110, REQ-122_
  - Files: `frontend/src/app/app.component.ts`
  - Validate: unit test (S30); `<html>` carries `data-theme="light"` and
    `lang="de"` on a fresh load with nothing stored, and both change on a
    setting change
- [x] **S12** Implement `ClockService`: `providedIn: 'root'`, a private
      `signal(new Date())`, a first `setTimeout` to the **next minute
      boundary** then `setInterval(60_000)`, both cleared via `DestroyRef`.
      No `NgZone`, no `ChangeDetectorRef` — _REQ-114_
  - Files: `frontend/src/app/core/clock/clock.service.ts`
  - Validate: unit test with fake timers (S31)

## F — Conventions

- [x] **S13** Write the two `frontend/CLAUDE.md` edits from `02-design.md` §4
      — the replaced mesh/primitive clause and the new "Appearance & language"
      section — **verbatim as approved**, adjusting only line wrapping.
      **Cleared: the user approved this exact text on 2026-08-18** (decision
      #3, *"Ja passt"*). The approval covers the text *as drafted*: if writing
      it makes you want to change a word, stop and propose the new wording
      instead. Deliberately placed before the UI steps — the convention is
      written before the code that must obey it — _REQ-103, REQ-107, REQ-110,
      REQ-112_
  - Files: `frontend/CLAUDE.md`
  - Validate: the file says what was approved and nothing more — in particular
    it gains **no** clause about the preference gateway (that lives in
    `02-design.md` §6 and `docs/system-context.md`, so the approved text does
    not have to be re-approved); no other section is touched

## G — Status bar and Settings

- [x] **S14** Add the new semantic tokens: `--size-touch-min: 2.75rem`,
      `--size-icon-xs: 1.125rem`, `--size-icon-caret: 0.75rem`,
      `--text-status` / `--text-clock` / `--text-h1` (each with its weight and
      line-height, per `02-design.md` §8.3), and `--color-divider` in both
      themes — _REQ-113, REQ-115, REQ-117_
  - Files: `frontend/src/styles/tokens.css`
  - Validate: `ng build` green; no component yet references them
- [x] **S15** Register the new Phosphor glyphs (`phosphorGlobe`,
      `phosphorCaretDown`, `phosphorSun`, `phosphorMoon`, `phosphorCheck` —
      **not** `phosphorCircleHalf`: decision #4 keeps `system` out of the
      shipped option list, and an unused registration is dead weight in the
      bundle) in `app.config.ts`'s
      `provideIcons`, and import `@angular/cdk/overlay-prebuilt.css` once in
      `src/styles.css` — _REQ-113, REQ-115_
  - Files: `frontend/src/app/app.config.ts`, `frontend/src/styles.css`
    (`app.config.ts` is also touched by S6 and S38 — providers and icons are
    independent, but do not revert one while editing the other)
  - Validate: `ng build` green; the built output contains **no** external URL —
    the CDK stylesheet ships inside the installed package. If the bundler
    cannot resolve the bare specifier from CSS, add it to `angular.json`'s
    `styles[]` instead (an `angular.json` edit, **not** a dependency-manifest
    edit) and note which route was taken
- [x] **S16** Build `StatusBarMenuComponent` — one generic CDK dropdown
      (`CdkMenuTrigger`/`CdkMenu`/`CdkMenuItem`) taking options, the selected
      value and trigger content; glass panel, `--radius-lg`, rows at
      `--size-touch-min`, selected row marked with `--gradient-menu-active`
      **and** `phosphorCheck` — _REQ-115_
  - Files: `frontend/src/app/layout/status-bar/status-bar-menu/*`
  - Validate: unit test (S32): opens on Enter/Space/click, Escape closes and
    returns focus, selecting emits and closes
- [x] **S17** Build `StatusBarComponent`: language trigger (globe + `shortLabel`
      + caret), appearance trigger (**sun/moon by resolved theme**, no text
      label, + caret), hairline divider, and the clock as `<time [attr.datetime]>`
      formatted by a memoised `Intl.DateTimeFormat` per locale. `:host {
      display: contents }`; no `role="toolbar"` (`02-design.md` §8.1) —
      _REQ-113, REQ-114, REQ-115_
  - Files: `frontend/src/app/layout/status-bar/status-bar.component.*`
  - Validate: unit test (S33)
- [x] **S18** Wire it into the shell: `position: relative` on `.shell`,
      `<app-status-bar>` as a sibling of `<app-sidebar>` and `<main>`,
      absolutely positioned at `top: var(--space-6)` / `right: var(--space-7)`
      — _REQ-112_
  - Files: `frontend/src/app/layout/shell/shell.component.{html,css}`
  - Validate: it appears on **all six** routes; measured in a browser (S37)
    Home's heading ink is still at y ≈ 88 and the card row bottom still at
    y = 744 at 1280 × 832 — unchanged from before this feature
- [x] **S19** Build `SettingChoiceComponent`: label, hint, and a row of
      all-visible option buttons; selected = `--gradient-menu-active` +
      `--text-title` weight + `phosphorCheck`; each ≥ `--size-touch-min` —
      _REQ-117_
  - Files: `frontend/src/app/features/settings/setting-choice/*`
  - Validate: unit test (S34)
- [x] **S20** Build `SettingsComponent`: `--text-h1` title, two glass panels
      (`Sprache`, `Darstellung`), panel width
      `calc(3 * var(--size-panel) + 2 * var(--space-4))`, content inset
      `--space-9` from the top so it clears the reserved corner — _REQ-116,
      REQ-117, REQ-118_
  - Files: `frontend/src/app/features/settings/settings.component.*`
  - Validate: unit test (S34); measured no-scroll at both viewports (S37)
- [x] **S21** Point the `einstellungen` route at `SettingsComponent` and drop
      its `data: { sectionId }`. The other four placeholder routes are
      untouched and `SectionPlaceholderComponent` is **not** deleted —
      _REQ-116_
  - Files: `frontend/src/app/app.routes.ts`
  - Validate: route test (S34); the sidebar's Einstellungen item is still
    active on that route

## H — Design documentation

- [x] **S22** Create `docs/design/dark-theme.md` from `02-design.md` §3 — the
      provenance header (designed in-house 2026-08-18, no Figma counterpart, a
      re-import will not reproduce it, and a future Figma dark variant would
      supersede it), the `--night-1…16` grid, the full role table, and the
      measured contrast table — _REQ-101, REQ-104_
  - Files: `docs/design/dark-theme.md`
  - Validate: every value matches `tokens.css` as built in S1/S2 — the doc and
    the code are checked against each other, not written twice from memory
- [x] **S23** Update `docs/design/tokens.md`: replace
      `[NEEDS CLARIFICATION: no dark-mode palette exists — is one wanted?]` and
      the "Still blocked on design → No dark palette exists" entry with
      resolutions pointing at `dark-theme.md` (including the answer to its own
      sub-question: **the glass surfaces do stay translucent**), and record the
      seven new tokens from S14 with an in-house provenance note in the style
      of `--gradient-icon-active`. **No dark colour value is added to this
      file.**
      Also correct → Implementation → "Setup decisions" → *Dark-mode trigger*,
      which still says the attribute "defaults to the system preference
      (`prefers-color-scheme`)" and is authored "absent (follow system)". Per
      **decision #4, approved 2026-08-18**: the three states stay authorable,
      but the app **never writes the absent state** — it always resolves to
      `light` or `dark` — and the startup value is the **backend-delivered
      default** (`light`), with `prefers-color-scheme` consulted only by a
      property that explicitly configures the `system` option. Keep it a
      correction with its date and reason, not a silent rewrite — _REQ-101,
      REQ-105_
  - Files: `docs/design/tokens.md`
  - Validate: `grep -n "night-\|#4f172a" docs/design/tokens.md` finds nothing;
    the two markers are gone; no sentence still claims the theme defaults to
    the system preference
- [x] **S24** Update `docs/design/design-sheet.md`: a 📋 cross-reference to
      `dark-theme.md` in *Farben*, and a 📋 entry recording the Settings screen
      as designed in-house with no Figma counterpart — _REQ-101, REQ-118_
  - Files: `docs/design/design-sheet.md`
  - Validate: both entries carry the 📋 marker per the file's own legend
- [x] **S25** Update `docs/design/screens/home.md`: the "Top bar — language
      selector, clock" hypothesis under *The widget column* is no longer
      uncommitted — record that it was **committed on 2026-08-18** by the
      user, extended with an appearance control, built app-wide in `layout/`
      rather than as part of the widget column, and specified in this
      feature's `02-design.md` §8. Leave the rest of that section's "not
      committed" framing intact for the widgets themselves — _REQ-112,
      REQ-113_
  - Files: `docs/design/screens/home.md`
  - Validate: the section no longer claims the top bar is uncommitted, and
    still claims it for weather/host/recommendations/tip bar
- [x] **S26** Update `docs/system-context.md` per `02-design.md` §13: the new
      components in the component table (including `PreferenceGateway` and its
      stand-in), the two updated existing rows, the *Theming* and new
      *Appearance & language* cross-cutting entries, the reserved top-right
      region under *Viewport & scaling*, and both seams under *Stack*
      (`applyPropertyDefaults()` for config, `PreferenceGateway` for the
      guest's preference). **Replace the round-2 "planned" notes with real
      entries**: *Persistence* no longer reads "none in the first step" (the
      guest's preference is state the future database owns, stood in for by
      `sessionStorage`; the datastore itself stays unchosen and gated), and
      *Messaging / async* no longer reads "none" (one bootstrap-gated read,
      one fire-and-forget write). Keep the new *Open security question* about
      the preference write endpoint. Move this feature's entry out of "in
      flight" — _REQ-125, REQ-121, REQ-127_
  - Files: `docs/system-context.md`
  - Validate: every component named exists in `frontend/src/`; no row
    describes something not built; nothing in the file names a datastore

## I — Tests

- [x] **S27** `TranslationService`: known key per language; unknown key →
      default catalogue → key; parameter interpolation; the detached-`t`
      binding case — _REQ-124, REQ-106, REQ-111_
- [x] **S28** `PreferencesService` — **state layer only** (persistence is
      S39): effective = default before any choice;
      override wins; the other stays put; `reset()`; rejection of an
      unconfigured value; `applyPropertyDefaults()` recomputing; and
      `resolvedTheme()` following a mocked `matchMedia` including its `change`
      event — _REQ-124, REQ-105, REQ-119, REQ-120_
- [x] **S29** `PropertyContentService`: per-language resolution; per-field
      fallback to the property's `defaultLanguage`; **throws** on a missing
      default-language entry, on a `defaultLanguage` outside `languages`, and
      on a configured language with no catalogue; `getSectionRoute()`
      unchanged — _REQ-124, REQ-108, REQ-119_
- [x] **S30** `App`: `data-theme` and `lang` written and updated; existing
      `applyBranding()` assertion kept. `applyPropertyDefaults()` is **no
      longer asserted here** — it moved to the app initializer (S38) and is
      asserted in S39 — _REQ-124, REQ-110_
- [x] **S31** `ClockService` with fake timers: the first tick lands on the
      minute boundary (not 60 s after load), subsequent ticks are 60 s apart,
      and teardown clears both timers — _REQ-124, REQ-114_
- [x] **S32** `StatusBarMenuComponent`: open/select/close, Escape returning
      focus to the trigger, the selected row carrying both the background and
      the check glyph — _REQ-124, REQ-115_
- [x] **S33** `StatusBarComponent`: short code rendered, glyph follows the
      **resolved** theme, divider present, time formatted per locale
      (`de-DE` → `16:42`, `en-GB` → `16:42`), accessible names translated —
      _REQ-124, REQ-113, REQ-114_
- [x] **S34** `SettingsComponent` + `SettingChoiceComponent` + routing: every
      configured option rendered and the current one marked; selecting updates
      the shared state; `/einstellungen` renders `SettingsComponent` and the
      other four routes still render `SectionPlaceholderComponent` —
      _REQ-124, REQ-116, REQ-117_
- [x] **S39** `FakePreferenceGateway` + the persistence and async tests
      (`02-design.md` §10). The fake answers `load()` with a scripted value, on
      a scripted delay or never, records every `save()`/`clear()` call, and can
      be told to reject N times. Assert, **without editing
      `PreferencesService`** (which is the proof of REQ-122):
      a stored record is applied on boot and is the **first** value the `<html>`
      effects ever write; an absent record leaves the defaults; a value outside
      the configured options is discarded; a rejecting or never-resolving
      `load()` still lets `hydrate()` resolve and the app boot; an answer
      arriving after the guest has chosen does not overwrite that choice, per
      setting; one change ⇒ one whole-record `save()`; three fast changes
      coalesce to the latest state; a rejecting `save()` is retried
      `PREFERENCE_WRITE_ATTEMPTS` times and then stops, with the guest's choice
      still on screen and no error node in the DOM; `reset()` calls `clear()`;
      the initializer applies the defaults **before** hydrating; and **no
      `console.*` call is made on any path** (spy on all of them) —
      _REQ-124, REQ-121, REQ-122, REQ-126, REQ-127_
  - Files: `frontend/src/app/core/preferences/preference-gateway.spec.ts`,
    `frontend/src/app/core/preferences/preferences.persistence.spec.ts`
  - Validate: also covers the stand-in itself — round-trips through
    `sessionStorage`, resolves asynchronously (a synchronous return fails the
    test), and reads a malformed record as `null`
- [x] **S35** Catalogue completeness test (identical key sets, no empty
      values) and a scan test asserting no German literal remains in
      `layout/`, `features/` or `app.routes.ts` — _REQ-124, REQ-107, REQ-111_

## J — Verification (review-time measurement, not a test suite)

- [x] **S36** Extend `frontend/tools/measure.mjs`: the status bar's box on
      every route; non-collision with Home's card row and heading block at
      1024 × 768 and 1280 × 832; the settings screen's boxes; an open
      dropdown's overlay; and no-scroll across **6 routes × 2 themes ×
      2 languages × 3 viewports = 72 combinations** — _REQ-112, REQ-123_
  - Files: `frontend/tools/measure.mjs`
  - Validate: it reports numbers and asserts nothing (`docs/constitution.md`
    v1.3.0 — Playwright is a verification tool, no committed E2E specs,
    nothing wired into `npm test`)
- [x] **S37** Run it and record the numbers: all 72 no-scroll combinations
      pass with `overflow: visible` on `html`/`body`; Home's pre-existing
      geometry is **unchanged**; no label wraps or truncates in English; and
      the sampled dark composites match `02-design.md` §3.4 within rounding —
      _REQ-104, REQ-112, REQ-123_
  - Files: none (measurement only)
  - Validate: the report goes into the session summary, not into a new file
