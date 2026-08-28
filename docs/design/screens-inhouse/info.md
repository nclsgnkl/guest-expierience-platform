# Screen: Info (module placeholder) — in-repo design

**Tokens:** [../tokens.md](../tokens.md)
**Source:** none — **designed in-repo, 2026-08-28** (the convention was approved
2026-08-20, `guest-frontend-integration` D1; re-affirmed as owner decision D6,
`sidebar-catalogue-overflow`)
**Last updated:** 2026-08-28

> ⚠️ **Provenance: designed in-repo — no Figma counterpart.** **A Figma
> re-import will not produce this file**; if the screen is ever drawn in Figma,
> that extraction supersedes this file and this file becomes the deviation log.
> This is the **fifth** in-repo surface, after `/pairing`, `/standby`,
> `/connection` and the menu-overflow popover.

> **This is the first module screen ever designed**, and it therefore silently
> becomes the template for the twenty-odd that follow. Everything below is
> marked **generalises** or **Info-specific** — see "What generalises" — so the
> next module screen inherits the decisions deliberately rather than by
> copy-paste.

## Purpose

`/info` is a catalogue section that today renders `SectionPlaceholderComponent`
("Bald verfügbar."). This design gives it **real content: exactly two rows** —
a check-out time and a fictional WiFi code.

It exists for one reason: **`module-content` (Part C) is unplannable without
it.** Part C fixes the per-module-type wire shape (REQ-961/963/964), and you
cannot fix a per-type schema before deciding what the type shows. Its PRD says
so in as many words: *"At least one real, designed module screen must exist
first."* This is that screen.

It is **a placeholder, by the owner's explicit instruction** — *"mach da nur nen
Platzhalter rein. einfach 2 Stichpunkte einmal Check[-out] Zeit und dann nen
fiktiven wlan code"* — and it is **design only**: nothing here is implemented
until Part C puts the values on the wire, because building it now against
hardcoded content means building the same screen twice.

**Scope: two rows. A third row is out of scope, in every direction.** Anything
below that reads like it wants a third row is written as a note *against*
adding one.

## The two rows, and why they are not the same kind of thing

This is the most useful part of the owner's example, and the design must not
smooth it over:

| | Row 1 | Row 2 |
|---|---|---|
| Shows | check-out date and time | the WiFi password |
| **Is** | a **stay** fact | **property content** |
| Where it comes from | `{stay.checkOut\|date}` / `{stay.checkOut\|time}` — already shipped members of `TemplateControl.ALL_PLACEHOLDERS`, resolved server-side per offered language | nowhere today; **this is the thing Part C's content path is for** |
| Exists when the room is `VACANT` | no (there is no stay) | yes — but nobody sees it: `/info` is a shell route and a vacant kiosk is on `/standby` |
| Changes | at every check-in | when the property changes its WiFi password |

The `%dev` seed currently hardcodes the literal *"Bis 11:00 Uhr am Abreisetag"*
in a Home action card instead of using the placeholder that exists two files
away. This screen uses the placeholder. That is not a detail: it is the whole
difference between the two rows, rendered.

## Layout

The screen renders inside `ShellComponent` like every other section route — the
mesh background, the sidebar and the status bar are already there and this
screen adds nothing to them.

**It reuses the Settings screen's frame, unchanged.** ← *generalises*

```
.info                      (:host { display: contents })
├── <h1>                   the section's own catalogue label
└── <dl class="info__panel">   one glass panel
    ├── row 1
    └── row 2
```

| Property | Value | Token |
|---|---|---|
| screen column | flex column, `align-items: flex-start` | — |
| top inset | `padding-top` | `--space-9` |
| title → panel gap | | `--space-6` |
| title | screen title | `--text-h1` / `-weight` / `-line-height`, `--color-text-primary` |
| panel width | `calc(3 * var(--size-panel) + 2 * var(--space-4))` — **derived, not tokenised** | = `38.25rem` |
| panel padding | | `--space-6` |
| panel surface | the one glass recipe | `--color-surface-glass`, `--border-glass`, `--radius-2xl`, `--shadow-panel`, `--blur-glass` |
| row → row gap | | `--space-6` |

The panel width is *exactly three action cards plus their two gaps*, which is
what `settings.component.css` already does and for the same stated reason: the
screen lines up with Home's grid instead of introducing an arbitrary
measurement. Horizontal position comes from `.shell__content`; this screen sets
no horizontal padding of its own.

**One panel holding both rows, not one panel per row.** The two rows are one
topic — the practical facts of this stay — and two single-row panels would read
as two unrelated settings. *(Settings uses one panel per setting because each
one is an independent control.)*

**No divider between the rows.** `--space-6` separates them, and
`--color-divider` — whose documented role is the status bar's hairline — is not
spent here. ← *generalises, with a caveat:* a module with many rows probably
does need one, and `--color-divider` is the token for it when that day comes.
It is not needed for two.

**Zero new tokens, zero new icons.** Everything above already exists.

## The row

The reusable unit. ← *generalises*

```
label      --text-body / 400 / 1.5    --color-text-secondary
value      --text-title / 800 / 1.3   --color-text-primary
```

- **Stacked, not label-left/value-right.** A definition row reads like a table
  and needs the two columns to negotiate width; stacked handles a long value
  (a password) without that negotiation, works at any panel width, and is the
  block `setting-choice` (label + hint) and the action card (header +
  description) already use twice.
- Gap between label and value: `--space-1`.
- **The value is the heavier of the two.** The value is what the guest is
  scanning for — a time, a password — and the label is wayfinding. `--text-title`
  is the role the design system already gives to "the thing you read first on a
  glass surface".
- `overflow-wrap: anywhere` on the value. Same reason as
  `.heading-block__headline`: an unbroken 60-character token must wrap inside
  the panel rather than pass through its right edge. A value is
  property-authored and its length is not ours to assume.
- No `text-transform`, no `letter-spacing`, no `font-variant` — a password must
  render character-for-character exactly as it was authored.
- **No per-row icon.** Design principle 7: *"Icons unterstützen Text, ersetzen
  ihn nur bei sehr eindeutigen Aktionen."* A glyph beside "Check-out" repeats
  the label in a second alphabet and buys a column of decoration. The section's
  icon is already on screen, in the sidebar; the screen does not restate it.
- **The row is not interactive.** No touch target, no focus ring, no copy
  button — a kiosk's clipboard cannot reach the guest's phone. `/info` is
  therefore the first screen in the app whose content area contains **no
  focusable element at all**; the sidebar and status bar remain the only
  interactive things on it. `--size-touch-min` does not apply here.

Markup: a `<dl>` with one `<div>` per pair wrapping `<dt>` (label) and `<dd>`
(value). That is literally the term/definition semantic, so the screen needs
**no `aria-label`, no `role`, and therefore no accessible-name string** — which
is why it introduces no app-chrome key (see "Content").

## Decision 1 — a module is **not** a generic row list

> **✅ CONFIRMED by the owner, 2026-08-28** — recorded as **D14** in
> [`docs/features/module-content/01-prd.md`](../../features/module-content/01-prd.md).
> *"ja, jedes Modul hat ein eigenen Typ. Jedes Modul ist sein eigener
> Spezialtyp. Das ist das modulare design was ich stets verfolge. Wenn dann
> jedes mal die api angepasst werden muss, ist das doof, aber werden wir so
> machen."* **He named the cost back before choosing it.** A third option — a
> generic `GuestRowsModule` as one member of the union, bespoke members added
> later — was put to him and **declined**; do not re-propose it as a discovery.
> The recommendation below stands as written, including its price.

**The question:** is a module a list of label/value rows, or does each module
type get a bespoke layout?

**Recommendation: each module type gets its own layout. `info` is simply the
type whose layout happens to be a list of label/value rows.** The row above is
a shared *component*, not the module model.

Why:

- **The alternative is one step from the thing D9 already rejected.** A single
  generic `rows: [{label, value}]` member for every module type collapses A3's
  *"fixed, known-at-build-time shape per type"* into one shape for everything.
  It is not literally the free-form key/value bag (it is bounded, so it *can*
  be boundary-validated) — but it validates the *container* and never the
  content, and REQ-963's per-member bounds would have nothing type-specific
  left to bind to.
- **The rest of the catalogue is visibly not rows.** Its sections name their
  own shapes already: `umgebung` and `gastronomie` are places with distances,
  `smart-home` is control tiles, `hausordnung` is prose, `aktivitaeten` is
  cards. Forcing those into label/value rows does not simplify them — it pushes
  the structure into the value string, where nothing can validate it and the
  author has to invent a convention per property.
- **Rows still generalise, as a component.** Any module type may render a row
  list. `info` is the first; `wlan` and `anreise` are the obvious next. What
  does *not* generalise is the wire shape.

**Be honest about the commitment.** Choosing bespoke-per-type means:

1. **Every module type needs a designed screen before its schema can be
   written.** The blocker that stopped Part C recurs once per type — roughly
   twenty times. That is the real price, and it is not paid once.
2. Each type costs a `oneOf` member, a registry entry (D10's two places), a
   validator/mapper branch and a component.
3. The mitigation is real but partial: the **screen frame** and the **row** are
   settled here, so screen #2 is a fraction of screen #1. What is never free is
   deciding what a type *shows*.

~~**This is a recommendation, not an owner decision, and it shapes twenty
screens.** It should be confirmed before REQ-961 is written.~~ **Confirmed
2026-08-28 — see the banner at the top of this section (D14).** REQ-961 may now
be written, and it is written against exactly this: one union member per module
type, `info` first.

## Decision 2 — provenance is invisible

**The question:** do a server-resolved stay value and an authored property
value render identically?

**Yes. Identically — same row, same tokens, same position, no marker of any
kind.** ← *generalises*

Why the distinction is invisible to the guest:

- It is a fact about *where the value came from*, which is an implementation
  detail of no interest to someone reading a wall. A guest needs "Check-out:
  11:00" and "WLAN-Passwort: …" to be equally readable and equally trustworthy.
- Marking one would be the interface explaining its own architecture — design
  principle 2: *"Die Oberfläche wirkt wie eine digitale Rezeption, nicht wie ein
  Admin-Tool."*
- By the time either value reaches the kiosk, **they are the same thing**: a
  resolved string in a `LocalizedText`. The server resolves templates before the
  wire (REQ-964, and `GuestActionCard.title`'s existing *"RESOLVED per offered
  language — never a raw template"*). The frontend could not tell them apart if
  it wanted to, and nothing should make it able to.

What differs, invisibly, and what the design does about it:

- **Lifecycle.** The stay value changes at check-in; the property value does
  not. Nothing on screen depends on this — `/info` renders only under
  `OCCUPIED` (a vacant kiosk is on `/standby`, and `screens/*` answer
  `409 room-vacant`), so "no stay" never reaches this screen.
- **An unresolvable placeholder degrades to literal text — and on this screen
  that outcome is *unreachable*, not merely unlikely.** *(Corrected 2026-08-28.
  This paragraph read "an occupied stay with no check-out timestamp would put
  the raw braces on the guest screen". **That state cannot exist:**
  `stay.check_out` is `timestamptz NOT NULL` (`V2__domain_config.sql`), and
  `GuestControl.resolveLocalized` passes a null `checkOut` into the template
  context **only when `stay == null`** — a vacant room, which is on `/standby`.)*
  `TemplateControl.resolve` leaves `{stay.checkOut|time}` as-is rather than
  rendering `null`, and the kiosk cannot detect it — by then it is just a
  string. So the do-nothing fallback is free, correct and already in place for
  the day somebody adds a placeholder whose context value may legitimately be
  absent. **Answered by D18** (owner, 2026-08-28): refused at the *write* — that
  half belongs to the successor feature `module-content-authoring`, since Part C
  has no write path — and **nothing clever at the read**. The mapper carries no
  placeholder-shaped logic at all; dropping a value because it contains `{…}`
  was put to the owner as option (a) and **rejected**.
- **A property value can simply be unauthored.** Rule: **an absent value means
  an absent row.** Not an em dash, not an empty `<dd>` — a labelled blank tells
  the guest the screen is broken. ← *generalises*. For this screen the
  "zero rows survive" case is unreachable (row 1 is always present under
  `OCCUPIED`), so **no empty state is designed here** and none should be
  invented. A module type that can legitimately deliver nothing needs one; that
  is that type's design work.

## Decision 3 — what the wire must carry per row

**Described as a content need, in prose. The schema is REQ-961's and is
deliberately not designed here.** ← *generalises*

Per row, the wire must carry:

- **A label.** Short, guest-facing, **localized and server-resolved per offered
  language**, following the existing `LocalizedText` convention exactly as
  `GuestActionCard.title` does. It is **content, not chrome** — see below.
- **A value.** Same treatment, and it must pass through the same template
  resolution the home-screen fields already use, because row 1 *is* a template.
  Two things worth knowing before the schema is written:
  - Some values are **language-invariant by nature** — a password, a door code,
    a house number. The wire does not need to express that, but the author will
    be typing the same string into every language slot, and that is the
    authoring experience this shape produces. It is a cost, not a defect; the
    alternative (a "not translatable" flag) is a second kind of field for one
    convenience.
  - A value is authored, so its length is not ours to assume. Whatever bound
    REQ-963 sets, the renderer wraps (`overflow-wrap: anywhere`) rather than
    trusting it.
- **Nothing else is required.**
  - **No icon.** The design does not use one. Adding one later is additive and
    cheap; adding it now is a field nothing reads.
  - **No ordering field.** **The delivered array order is the order.** This is
    REQ-962's rule applied one level down, and it avoids repeating the mistake
    the PRD already names: `guest-content.mapper.ts` re-sorts `menu.items` by
    `sortIndex` even though the contract says the server ordered them. Rows get
    no `sortIndex` to re-sort by.
  - **No row id.** `ActionCardShape.id` is *"client-derived, stable per payload
    — `@for track` only"*; the same precedent covers a row.
  - **No `sensitive` flag.** D12 withdrew per-field sensitivity marking
    explicitly and said it should not be re-proposed. The WiFi row is precisely
    where somebody will want to. Do not.
  - **No title on the module.** The screen's `<h1>` is the section's own
    catalogue label, already delivered in `GuestMenuItem.label`. **A module
    never restates the name of the section it fills** — otherwise every module
    type grows a `title` field that duplicates the catalogue and can disagree
    with it. ← *generalises, and this one matters*

**One thing the wire must carry that no requirement asked for — ✅ now
decided.** A way to get from **a route to its module**. REQ-962 delivered *"the
resolved, enabled, ordered module list"*; REQ-970's registry maps *type →
renderer*; nothing bound a module to the catalogue section whose route the guest
just opened, and `/info` cannot render without that link. **Answered 2026-08-28
as D15** (the lead, on the owner's explicit delegation): **the menu item carries
its module, as an optional field** — `menu.items[] = { key, label, icon, route,
sortIndex, module? }`. Reasoning in
[`docs/features/module-content/01-prd.md`](../../features/module-content/01-prd.md)
→ D15, written out because the owner asked to be able to reverse it. For this
screen the consequence is only that the section host reaches the module through
`itemForRoute(items, url)` and reads `.module` — which is what "What replaces the
`/info` placeholder" below already describes.

## Content

**Nothing on this screen is app chrome.** No new `TranslationService` key, in
either catalogue. The `<h1>` is the property's configured catalogue label
(`GuestMenuItem.label`, resolved) — exactly what `SectionPlaceholderComponent`
already renders in the same slot — and both rows are module content. The `<dl>`
semantics mean the screen needs no accessible-name string either.

Both German and English are still specified, because both rows are **content
delivered per offered language**. This is the copy the design asks the `%dev`
seed to carry (writing it is Part C's REQ-976, not this document's):

| Row | | de | en |
|---|---|---|---|
| 1 | label | `Check-out` | `Check-out` |
| 1 | value | `{stay.checkOut\|date}, {stay.checkOut\|time} Uhr` | `{stay.checkOut\|date}, {stay.checkOut\|time}` |
| 2 | label | `WLAN-Passwort` | `Wi-Fi password` |
| 2 | value | `Meeresrauschen-77` | `Meeresrauschen-77` |

Notes on the copy, each deliberate:

- **Row 1 carries the date as well as the time.** "Check-out: 11:00" leaves
  *which day* unanswered, and the guest screen is the one surface that knows.
  It also exercises **both** shipped placeholders, which is the point of a
  screen that exists to demonstrate the content path. Rendered with the seeded
  `Europe/Berlin` and the seeded locales (`de-DE`, `en-GB`): **"28.08.2026,
  11:00 Uhr"** and **"28 Aug 2026, 11:00"**.
- **" Uhr" is authored German, not a formatter setting.** `FormatStyle.SHORT`
  at `de-DE` yields `11:00`; the word is part of the template, which is exactly
  where per-language phrasing belongs.
- **`Check-out` is unchanged between the languages**, and that is correct — it
  is the word German hospitality uses, and `design-sheet.md` → *Gute Labels*
  lists it verbatim.
- **`WLAN-Passwort`, not `Dein WLAN`.** The sheet's own example label is
  *"Dein WLAN"*, and it is rejected here for two reasons: it names the
  *network* where the row's value is the *secret*, and it takes a position on
  the du/ihr address question that a plain noun does not have to. A label/value
  row labels the value.
- **The fictional code avoids every transcribable ambiguity.** A guest reads
  this off a wall and types it into a phone. `Meeresrauschen-77` contains no
  character from `{I, l, 1, O, 0}` — the pairs Plus Jakarta Sans renders as
  near-identical strokes — and it is unmistakably fictional. **The rule is the
  requirement; the string is one instance of it.**
- **The rendering side of that has a real limit worth recording.** A *real*
  property authors its own password and cannot be held to that rule, and the
  bundle ships **only Plus Jakarta Sans, weights 400/700/800** — there is no
  monospace or "code" face, and adding one is a new self-hosted font asset and a
  design-system change, not a CSS line. So: the value renders as legibly as that
  face allows, and `I` vs `l` stays ambiguous. Recorded, not solved.

**Two things the WiFi row must not grow into.** Both will be proposed:

- **An SSID row.** A real `wlan` module needs the network name beside the
  password. This screen has two rows, one of which is check-out. The SSID
  belongs to the `wlan` module, which is a separate design.
- **A WPA QR code.** It is the right feature for a WiFi screen and it is out of
  scope twice over: it needs a QR generator (a **new dependency**, approval-
  gated) and it is a third element on a two-row placeholder.

The password lives on **Info** rather than on the `/wlan` fixture section
because the owner put it there. That is a placeholder arrangement and **does not
pre-empt a future `wlan` module** — the eight fixture catalogue rows are
disposable by REQ-903's own statement.

## The WiFi row and the cache clause (constitution v1.9.0)

**This screen is the first design to carry a value the amended clause is about.
It sits on the permitted side of the line, and the permission is the point.**

- The WiFi password is **guest-facing content the manager deliberately chose to
  display**. By v1.9.0 that is exactly the class the last-known-good cache
  **may** hold, WiFi passwords named explicitly. It **may be cached and rendered
  offline**, and REQ-979 says the same.
- It is **not** a credential that extends reach beyond the device: it
  authenticates to the *property's* WiFi — a network principle 4 already
  declares untrusted and which every guest is handed by design. It is no device
  token, no session token, no key material, and it addresses neither the backend
  nor another room.
- **The availability is the whole reason the clause was amended.** A guest whose
  backend is unreachable is precisely the guest who still needs the WiFi
  password. So this screen must render **identically from cache** — no
  degradation, no hidden row, no "unavailable" substitute.
- **Consequence for the layout, and it is not hypothetical:** the escalated
  staleness banner and the WiFi password are on screen **at the same time**, by
  design. See "No-scroll and the banner" — the values are left-aligned, so the
  banner's anchor lands on the panel's empty right-hand side and never on a
  value.
- **Principle 3 is untouched.** No row label and no row value ever reaches a
  log, an analytics event or a captured stack trace. Specifically: when REQ-972
  drops a malformed module, **the drop must not quote the offending content** —
  not the value, not the label. A cache is not a log, and neither is a
  console warning.
- **The disclosure obligation D12 records applies from the moment this ships.**
  Operator documentation must state that door, key-box and alarm codes are
  rotated when a kiosk goes missing. D12 notes it "is owned by nobody yet"; this
  screen is what makes it due.

## What replaces the `/info` placeholder

`/info` already resolves — `{ path: ':sectionKey', component:
SectionPlaceholderComponent }` catches the whole catalogue, and the route table
deliberately stopped enumerating sections.

**Do not add a `{ path: 'info' }` route.** The `einstellungen` precedent exists
because Settings is app chrome, not a catalogue row. Hardcoding `info` would
re-enumerate the catalogue the route table just stopped enumerating, and it
would break the moment the catalogue renames that row's `route`.

Instead, the component behind `:sectionKey` becomes a **section host**: it
resolves the section from the URL exactly as it does today
(`itemForRoute(items, url)`, shared with `SidebarComponent`), finds the
delivered module for that section, and renders the renderer registered for that
module's type. ← *generalises — this is the render-dispatch half of D10's
registry, and it is the shape the design needs; **how** the registry is written
is Part C's REQ-970/REQ-980 and is not decided here.*

**`SectionPlaceholderComponent` survives, demoted.** REQ-974 asks whether a
delivered-but-unauthored module is a real state. It is, twice over:

- **Thirteen catalogue rows, twelve of them section routes, one designed
  screen.** The other eleven still need the placeholder, and will for a long
  time.
- **REQ-973's forward-compatibility path lands here.** A module type this build
  does not know degrades to "not shown" — on a dedicated route, "not shown" is
  an empty screen, and the placeholder's `section.comingSoon` is precisely the
  right thing to render in it. Its second arm (`section.unavailable`, for a
  route the delivered menu does not claim) is untouched.

So: no new chrome key, no route change, no component deleted. The placeholder
stops being *the* component for a section route and becomes the **empty state
inside the section host**.

## No-scroll and the banner

Everything scales with the viewport-proportional root, so both reference
viewports are the same layout at two sizes. Root = `16px` at 1280×832,
`12.8px` at 1024×768.

| | 1024 × 768 | 1280 × 832 |
|---|---|---|
| content area, left edge | x137.6 | x172 |
| `<h1>` top | y70.4 | y88 |
| panel top | y117.8 | y147.2 |
| panel size (border box) | 491.6 × 137.0 | 614 × 170.8 |
| panel bottom | y254.8 | y318.0 |
| **vertical headroom below the panel** | **≈ 494 px** | **≈ 490 px** |
| value line capacity | ≈ 55 characters | ≈ 55 characters |

*(The `+2 px` on the panel's border box is `--border-glass`'s 1 px hairline on
each side, which `docs/constitution.md` → Styling exempts from the rem rule —
the same 2 px `menu-overflow.md` records. **These are computed, not measured:**
verify with `node tools/measure.mjs` when the screen is built, on every
route × theme × language × viewport.)*

The screen uses under a third of its vertical budget at both sizes, so no-scroll
is met with ~490 px to spare — a value would have to wrap to ten lines before
the panel became the binding constraint.

**The status-bar corner** (~13 rem × 2.75 rem, right-inset) is clear: the panel
ends at x627 / x784, the bar begins at x832 / x1040.

**The escalated staleness banner is a near-miss, and it is worth writing down
rather than re-deriving.** Measured in `connection.md`, the bar occupies
`x459 y58 w540 h60` at 1024 and `x569 y72 w679 h74` at 1280 — bottom edges
**y118** and **y146**. The panel's top edge is **y117.8** and **y147.2**: it
clears by **1.2 px** at 1280 and **overlaps by 0.2 px** at 1024. This is the
Settings screen's situation exactly, because it is the Settings screen's frame.

Three things make that acceptable rather than a defect, and one of them is a
design rule:

1. The banner is an **overlay by construction** — v1.9.0 and `connection.md`
   both say content keeps rendering behind it on every route. A partial overlap
   is its designed behaviour, not a collision.
2. The 0.2 px is against the banner's **padding**, not its copy: the body is
   inset by `--space-4`, so the text clears the panel by ~9 px.
3. **The rows are left-aligned, and that is why it does not matter.** Row
   content starts at x157.8 / x196 and the longest expected value ends around
   x278 / x348 — nowhere near the banner's x446–459 / x554–569. The band the bar
   lands on is the panel's empty right-hand side. ← *generalises: a module
   screen keeps its values in the left half of the panel, because the banner
   and the values are on screen together by design.*

**Verify, do not assume.** These are arithmetic. The 1024 figure is a fifth of a
pixel on the wrong side of zero and must be measured on the built screen.

## Both themes

Nothing theme-specific is authored. Every surface, border and text colour is a
semantic role, so `[data-theme='dark']` repaints the screen with the overrides
it already applies: `--color-surface-glass`, `--color-surface-glass-border`,
`--color-text-primary` → `--ink-050`, `--color-text-secondary` → `--ink-300`
(the role that already passed contrast in dark, per `tokens.md`'s deviation
note). Verified at review with `node tools/measure.mjs` in both themes.

## Responsive behaviour

None, deliberately. No breakpoints, no second layout: the root font size is
viewport-proportional, so the title, the panel and both rows scale together from
1024×768 upward. The panel's width is a derived multiple of `--size-panel`, so
it scales with everything else.

## Interactions

**None.** The screen is read-only: no button, no link, no focusable element in
the content area. The sidebar and the status bar behave exactly as on every
other route.

- A language switch **re-resolves both rows from data already held and fetches
  nothing** — the existing behaviour, and it works because the server already
  resolved every template per offered language.
- **No motion.** No entrance animation, no transition. There is no motion token
  layer yet (`design-sheet.md` → *Motion* is still largely unspent), and a
  static two-row screen has nothing to animate.
- Content refreshes with the poll, silently. A `304` changes nothing on screen.

## Open questions

Three when this screen was written, all of them **Part C's**, none of them a
question about the screen. **All three are now closed** — question 1 by D15,
question 2 by D18, question 3 reclassified by D19, all on 2026-08-28. They are
tracked in
[`docs/features/module-content/01-prd.md`](../../features/module-content/01-prd.md)
→ "Open items".

1. ~~**Nothing binds a module to a section route.**~~ **✅ ANSWERED 2026-08-28 —
   D15.** REQ-962 delivered an ordered module list and REQ-970's registry maps
   *type → renderer*, but nothing answered *"which module fills `/info`?"* — a
   **requirement-level gap, not a design choice**, and it blocked REQ-961 as
   much as a missing screen did. **Decided: the menu item carries its module, as
   an optional field.** Three reasons, in the order that decided it — it closes
   a leak by construction (a disabled section has no `menu.items` entry, so its
   content is absent from the payload, where a `modules[]` array joined on
   catalogue key would have permitted it); it reuses `itemForRoute(items, url)`,
   which already ships and is already the documented convention; and the join
   alternative creates two failure modes this shape does not have. **The module
   carrying its own route was rejected** — two sources for one fact.
   **Consequence for this screen:** the field is optional, absent means "nothing
   authored yet", and that is exactly when `SectionPlaceholderComponent` renders
   — so REQ-974 is answered in the form proposed under "What replaces the
   `/info` placeholder", and that section is now settled rather than proposed.
2. ~~**An unresolvable stay placeholder reaches the guest as literal
   braces.**~~ **✅ ANSWERED 2026-08-28 — D18 (owner).** Refused at the
   **write** (the server will not store content carrying a placeholder it
   cannot resolve — reusing `TemplateControl.findInvalidPlaceholders`, which
   already ships for property text and card templates), and **nothing clever at
   the read**: the raw placeholder text simply renders, which is already
   `TemplateControl.resolve`'s documented behaviour, so the fallback costs no
   code. Option (a) — the mapper drops a value containing `{…}` — was
   **rejected**: it hides an authoring error and false-positives on
   legitimately braced text. Under D16 the write half belongs to the successor
   feature `module-content-authoring`. **And the outcome itself is unreachable
   on `/info` today** — see the correction in Decision 2.
3. ~~**A module type that can deliver zero rows needs an empty state.**~~
   **✅ RECLASSIFIED 2026-08-28 — D19 (owner): it is not a question.** Empty
   states are **per module type**; there is no shared generic one, because what
   "empty" means and what belongs in its place depend on what the module shows.
   It is **a line on the checklist for designing any module type** — see "What
   generalises" — not something owed to a future screen. `info` cannot be
   empty (row 1 is a stay fact always present under `OCCUPIED`), so none is
   designed here and none should be invented. **Keep the two cases apart:** a
   menu item carrying **no module** means "nothing authored yet" and renders
   `SectionPlaceholderComponent`; a module delivering **zero entries** means
   "authored and empty right now" and renders that type's own empty state.

**Not open:** whether a row carries an icon (no), whether rows carry an ordering
field (no — array order), whether a value is marked sensitive (no — D12 closed
it), whether the two rows look different (no — Decision 2), whether a module
type gets a bespoke shape (yes — **D14**, owner-confirmed 2026-08-28), and how a
module reaches its route (**D15**, question 1 above).

## What generalises, and what is Info-specific

| | Generalises to every module screen | Info-specific |
|---|---|---|
| Screen frame | `<h1>` = the section's catalogue label, `--space-9` top inset, `--space-6` to the content, panel width `3 × --size-panel + 2 × --space-4` | — |
| Panel | the one glass recipe, `--space-6` padding | one panel for both rows |
| Row | stacked label/value, `--text-body`/secondary over `--text-title`/primary, `--space-1`, `overflow-wrap: anywhere`, no icon, not interactive | which two rows exist, and their order |
| Module shape | **bespoke per type** (Decision 1) — the row is a shared component, never the model | `info` happens to be a row list |
| Provenance | stay-resolved and property-authored render identically (Decision 2) | row 1 is the stay one |
| Absent value | absent value ⇒ absent row | no empty state (unreachable here) |
| **Empty state** | **every module type states its empty state — or a stated reason the type cannot be empty** (D19, added 2026-08-28) | `info` **cannot** be empty: row 1 is a stay fact always present under `OCCUPIED` |
| Module title | a module never restates the section's name | — |
| Ordering | delivered array order, no per-row sort key | — |
| Alignment | values left-aligned; the top-right band belongs to the staleness banner | — |
| Route | catalogue route → section host → registered renderer; `SectionPlaceholderComponent` is the empty state | — |
| Copy | codes avoid `{I, l, 1, O, 0}`; no monospace face exists | `Meeresrauschen-77`, the two labels |
