# Screen: Connection lost (no cache) — in-repo design

**Tokens:** [../tokens.md](../tokens.md)
**Source:** none — **designed in-repo, 2026-08-20** (convention approved by the user 2026-08-20, `guest-frontend-integration` D1)
**Last updated:** 2026-08-28 (the banner's horizontal padding made asymmetric, at unchanged width — owner decision)

> ⚠️ **Provenance: designed in-repo — no Figma counterpart.** **A Figma
> re-import will not produce this file**; if the screen is ever drawn in
> Figma, that extraction supersedes this file and this file becomes the
> deviation log.

## Purpose

The **last resort**: a paired kiosk cannot reach the backend **and** holds no
last-known-good cache (first boot after pairing, or storage cleared). With
any cache present the kiosk keeps rendering content with the staleness pill
(below) instead — this screen exists only for the nothing-to-render case. It
must be honest, guest-presentable and self-healing: the kiosk retries with
backoff and leaves this screen unaided the moment the backend answers.

## Layout

Brandless-tolerant: branding may or may not have been applied earlier in the
session; the screen uses only theme/token roles, so it is correct either
way.

- Full viewport, mesh background, no sidebar, no status bar (the clock's
  value would suggest liveness the device cannot vouch for while
  disconnected).
- One glass panel, centered both axes: `--color-surface-glass`,
  `--radius-2xl`, `--border-glass`, `--blur-glass`, `--shadow-panel`;
  padding `--space-6`/`--space-7`; width `min(26rem, 80vw)`; single centered
  column, gap `--space-4`:
  1. **Glyph** — Phosphor `WifiSlash` (regular weight, per the icon rules),
     `--size-icon-lg` (2.25rem), colour `--color-status-offline` —
     **decorative**: the text below carries the information, so the muted
     status colour needs no text-contrast conformance (it is the design
     sheet's Offline `#A79DBA`, first use of the status palette).
  2. **Title** — `--text-h1`, `--color-text-primary`: one calm sentence,
     not an error code.
  3. **Body** — `--text-body`, `--color-text-secondary`, ≤ 2 lines: says
     the screen reconnects by itself; asks nothing of the guest.

No button. Retry is automatic (backoff 5 → 30 s); a manual "retry" button
would imply the guest is responsible for infrastructure.

## Components

| Component | Variants / states | Notes |
|-----------|-------------------|-------|
| Connection panel | one | Static; no interactive element |

## Content

Via `TranslationService` (both catalogues; keys indicative):

| Key | de | en |
|---|---|---|
| `connection.title` | Keine Verbindung | No connection |
| `connection.body` | Die Anzeige verbindet sich automatisch neu, sobald der Server erreichbar ist. | This display reconnects automatically as soon as the server is reachable. |

Never on this screen: HTTP status codes, hostnames, stack traces, retry
counters.

## The staleness pill (chrome, not a screen — specified here)

Shown while the kiosk renders **cached** content because the backend is
unreachable (`STALE` state). The constitution's wording — "a discreet
staleness hint" — is the requirement:

- Position: directly **below the status-bar cluster**, right-aligned to it,
  offset `--space-1` — inside the reserved corner's clearance, verified by
  measurement at both reference viewports (the recorded clearance to Home's
  card row is 48px/38px horizontal, 20px/16px vertical — the pill is
  ≤ 1.75rem tall and must stay inside that envelope; if it cannot, it moves
  into the status bar's own row rather than pushing content).
- Shape: pill — `--color-surface-glass` fill, `--border-glass`,
  `border-radius: 999px` (the design sheet's chip radius), padding
  `--space-1` vertical / `--space-3` horizontal, `--blur-glass`.
- Content: a `0.5rem` dot in `--color-status-offline` (decorative) +
  `--text-status` (0.875rem / 700) in `--color-text-secondary`:
  - de: `Stand HH:MM` · en: `As of HH:MM` — from the cache's `storedAt`,
    formatted with the active language's locale (the clock's own convention).
- Not interactive, `role="status"` with `aria-live="polite"` set once (it
  appears/disappears; it must not chatter on every minute tick — the
  timestamp is static per stale episode, showing when the data was fetched,
  not a countdown).
- Appears in `STALE` only; never during pairing, never on this connection
  screen (nothing is rendered from cache there — there is no cache).
- **Until the escalation replaces it** (added 2026-08-27): after 60 minutes
  without successful backend contact the pill is *replaced* by the banner
  below. The two are never on screen together — one derived level decides,
  not two independent conditions.

## The escalated staleness banner (chrome, not a screen — specified here)

Added 2026-08-27 for `guest-payload-consolidation` **REQ-952** (owner decision
**D13**), implementing `docs/constitution.md` v1.9.0: *"After a prolonged loss
of contact that hint escalates to a prominent banner naming the situation and
directing the guest to their host; content keeps rendering."*

**The reason is operational, not security.** On a box with no remote access a
guest complaint is the only alerting channel that exists, and the failure being
fixed is a screen that looks perfectly normal while serving day-old content. A
hard cutoff was explicitly rejected: a guest whose backend is unreachable is
exactly the guest who still needs the door code and the WiFi password.

### Trigger

- **60 minutes since the last successful backend contact** — not since the
  content last changed. A backend answering `304` forever is `ONLINE` and
  raises nothing.
- `STALE` only. `DISCONNECTED` already renders the full-screen connection panel
  above, which names the situation as prominently as anything can.
- A cold boot from a cache whose `storedAt` is already older than an hour shows
  the banner **immediately**. That is correct, not an edge case.
- It **replaces** the pill. Content keeps rendering behind it, on every route.

### Layout — a bar, not a panel (revised 2026-08-27)

The hint **escalates in place**: same anchor as the pill — directly below the
status-bar cluster, right-aligned to it — but it grows **sideways**, not into a
block. The first build clamped it at `min(22rem, 40vw)` and the body ran to
three lines; the owner struck the clamp instead of the clause, so the width
now follows the copy. Measured, the object is ~540 × 60 at 1024×768 and
~679 × 74 at 1280×832 — roughly **9 : 1**, a bar. It is deliberately *not*
"the pill grown into a panel" any more: a wide flat bar states one sentence
and covers almost nothing, where a panel would sit on the screen like a
dialogue the guest cannot dismiss.

- Overlay, not flow: `position: absolute` inside the host's padding box, host
  `display: contents`. It takes no layout space, so it cannot grow the document
  on any route (`docs/constitution.md` → "Nothing scrolls") and it moves
  nothing when it appears.
- **Width follows the copy**: `width: max-content` — exactly as wide as the
  body needs on **one line** — with `max-width` stopping it at the **content
  area's left edge** (the sidebar stays fully visible and navigable, and the
  bar never reaches over it). No `rem` clamp: a fixed width plus a line-count
  clause is the contradiction the first build shipped.
- Surface: `--color-surface-glass`, `--border-glass`, `--radius-2xl`,
  `--blur-glass`, `--shadow-panel`; padding **`--space-4` vertical, and
  horizontally `--space-3` left / `--space-5` right**, glyph-to-text gap
  `--space-4`.
  **The horizontal asymmetry is deliberate, and it is free** (owner decision
  2026-08-28, revising the 2026-08-27 cut to `--space-4` on all four sides —
  *"chrome a bar does not need is width the copy does"*, which remains true
  and is why the total is not allowed to grow). Both text edges on the right
  end inside a `--radius-2xl` arc, and the tighter of the two is **not** the
  timestamp: because the bar is `max-content`, **the body is what sizes it**,
  so the body's own bottom-right corner sits at exactly the right padding,
  in the bottom arc. Measured perpendicular to the arc it was **4.4 px
  (1024) / 5.1 px (1280)** — that is the pinch, and it is the thing that
  reads as cramped. The glyph on the left is a small wedge crossing the
  *straight* part of the left edge, so it loses only 0.3–0.5 px to the
  rounding and padding costs least there. Moving **one scale step** from the
  left to the right therefore lifts the bar's worst clearance to **6.4 px /
  7.7 px** while **`--space-3` + `--space-5` = 2 × `--space-4` exactly** —
  the only such pair on the scale. The bar's width, its left edge, and with
  it the clearance to Home's headline column are unchanged **to the pixel**;
  see "Where the bar lands, measured".
- **Glyph** — Phosphor `WifiSlash` (regular weight), `--size-icon-md` (one step
  down from the panel's `lg`: a bar carries a smaller glyph), in
  **`--color-status-warning`**. Still **decorative** — the text carries the
  information — but the colour now carries the severity. Deliberately the
  **connection screen's own glyph**, not a warning sign: the glyph names *what*
  is wrong (the connection), the colour says *that* it matters, and swapping in
  an alarm symbol would replace the first statement with the second. The
  severity order stays honest: the full-screen panel above is a takeover and
  outranks anything a bar can do.
- **Two rows, not three.** Top row: the **title** (`--text-title`,
  `--color-text-primary`, one line) at the left and the **timestamp** —
  the pill's own `Stand HH:MM` / `As of HH:MM`, `--text-status` in
  `--color-text-secondary` — right-aligned at the bar's own edge. Second row:
  the **body** (`--text-body`, `--color-text-secondary`), **one line**. The
  timestamp rides the title's row rather than a line of its own because it
  costs no width there (the body is always the widest line) and it keeps the
  bar flat.
- Not interactive. No dismiss, no retry button: recovery is automatic, and a
  button would imply the guest is responsible for infrastructure.
- `role="status"` with `aria-live="polite"`, set once — the pill's discipline
  verbatim. The banner appears once per stale episode and its text is static,
  so the live region never chatters on the minute tick. **The entrance
  animation below must never be what changes that text**: the region is created
  and destroyed by an `@if`, so an animation that also re-wrote the copy would
  make the live region announce mid-motion.

### Motion (added 2026-08-27)

Both behind `prefers-reduced-motion: reduce`, as `../design-sheet.md` → *Motion*
requires; both are disabled there, and nothing about the bar depends on either.

- **Entrance** — 350 ms `cubic-bezier(0.16, 1, 0.3, 1)` (the sheet's *Page
  Transition* duration and easing), opacity plus a `--space-3` rise. The
  pill → bar swap is an event, not a silent substitution; calm, no bounce, no
  scale.
- **A slow pulse on the glyph only** — 4 s, opacity 1 ↔ 0.55. **Never on the
  text**: the bar can stand for hours in an occupied guest room and its copy
  must stay re-readable at any moment. For the same reason there is **no
  marquee** — it was proposed and rejected: it contradicts *"Animationen ruhig
  und hochwertig"*, needs a WCAG 2.2.2 pause mechanism a kiosk cannot offer,
  and disappears under reduced-motion exactly where a fallback is needed.

### Where the bar lands, measured

Measured on the built app by `frontend/tools/measure-states.mjs` — **28 banner
cells**: both hosts × both reference viewports × both themes × both languages
(16), plus the `/info` route at both viewports × both themes × both languages
(8, added by `module-content`), plus a **37-character unbroken display name**
at both viewports × both languages (4). `storedAt` aged past the threshold,
`/api` blocked at the browser. The whole run is **52/52 cells `NO-SCROLL OK`,
0 findings**. *(The counts read "20 banner cells" and "44/44" until
2026-08-28: `module-content` added eight `/info` cells to the instrument
without these sentences being touched.)*

**Every figure in the table below survived the 2026-08-28 padding revision
unchanged**, and that is the whole point of it: `--space-3` + `--space-5` is
the same total as `--space-4` + `--space-4`, so the chrome sum, the bar's
width, its left edge and its clearance to the headline column are identical
before and after. Re-measured to confirm rather than assumed.

| | 1024 × 768 | 1280 × 832 |
|---|---|---|
| bar, longest German cell (shell) | `x459 y58 w540 h60` | `x569 y72 w679 h74` |
| bar, longest English cell (standby) | `x446 y58 w553 h60` | `x554 y72 w694 h74` |
| chrome (glyph + gap + padding + border) | 51.6 px | 64.0 px |
| `.heading-block` — the headline's own column, `--size-heading` wide | x138…**450** | x172…**562** |
| `.home-content__screen` — Home's whole left flex item, sized by the card row at its bottom | x138…**794** | x172…**992** |
| `.home-content__widgets` — the empty widget column | **x819**…998 (179 px) | **x1024**…1248 (224 px) |
| Home's card row | y541…698 | y548…744 |
| body / title / timestamp lines | 1 / 1 / 1 in every cell | 1 / 1 / 1 in every cell |
| **the bar's worst text-to-arc clearance** — body's bottom-right corner, perpendicular | **4.4 → 6.4 px** | **5.1 → 7.7 px** |
| timestamp's top-right corner → the same arc | 6.4 → 12.4 px | 7.8 → 15.5 px |
| glyph → the left edge (straight there; 0.3–0.5 px lost to the arc) | 9.4 → 6.4 px | 11.5 → 7.7 px |

The last three rows are the only thing 2026-08-28 changed, and they are read
as *before → after*. Note the third: after the revision the bar's tightest
point is no longer on the right but is the **glyph on the left**, at
essentially the value the body's corner had before it. That is not a
regression — a glyph beside a straight edge and a line of text pinched into a
curve do not read alike — but it is the reason more right-hand padding buys
nothing further without also widening the left.

> **Correction, 2026-08-27.** Earlier revisions of this table gave the widget
> column as x475…998 / x594…1248 and named x450 / x562 as
> `.home-content__screen`. Both were wrong: x475 is x449.6 + `--space-7`, i.e.
> the gap was measured from the *heading block* as though that were the flex
> item, which is true only for a Home with no cards. The widget column is a
> third of the width the old figure claimed.

Two consequences worth writing down rather than re-deriving:

- **The bar lies over the empty band between Home's heading and its card row.**
  Home's left item runs to x794 / x992 and lays its two children out with
  `space-between` — heading at the top, cards at the bottom — so the band
  beside the heading is deliberately empty. Of the bar's 540 px at 1024:
  **335 px** sit over that band (x459…794), 25 px over the gutter and 179 px
  over the widget column; at 1280, **423 px** of 679 over the band, 32 px
  gutter, 224 px widget column. *(The 1024 figure read 360 px until the third
  review round re-measured it: 360 is the distance from the bar's left edge to
  the widget column's left edge — 819.2 − 458.8 — which already contains the
  25 px gutter, so the three parts summed to 564 of a 540 px bar.)* The old claim — "the bar overlaps the empty
  widget column and, for the longest strings, the gutter beside it" — had the
  proportions backwards. What matters is unchanged and is what the anchor rests
  on: the region is **empty by design in both directions**, and the bar reaches
  neither the heading block nor the card row (clearance to the card row:
  424 px / 402 px vertically).
- **Clearance to Home's hero headline is structural, for any name.** The
  headline is manager-authored with `{stay.displayName}` interpolated, and the
  first build cleared it by 4–5 px — a margin that depended on one guest's
  name. `.heading-block` is a fixed `--size-heading` wide and
  `.heading-block__headline` carries **`overflow-wrap: anywhere`**, so not even
  an unbroken token can pass x450 / x562, while the bar starts at x459 / x569:
  **≥ 9 px / ≥ 7 px for any name**. Measured with a 37-character unbroken
  display name the headline takes five lines and reaches x450 / x560 — 9 px of
  clearance at both viewports. Without that one CSS line the same name renders
  **one** line to x935…953 / x1173…1195 (the lower figure of each pair is
  the German cell, the higher the English one) and passes under the bar; the
  instrument's
  `OVERFLOW` check is the ratchet that says so (removing the line reproduces
  8 findings in exactly those 4 cells).

The anchor is free on both hosts: on Home the top-right is the empty band
above the card row and beside the heading — the widget column
(`../screens/home.md`, "open by design") and the gutter and the heading's own
empty right-hand side — and standby has no sidebar and a centred block
(measured clearance to it: 230 px / 224 px vertically). Verified by measurement
at 1024×768 and 1280×832, both themes, both languages — the bar collides with
neither the status-bar cluster, Home's heading block, Home's headline lines,
Home's card row nor the standby block, and nothing scrolls in any cell.

### Content

Via `TranslationService` (both catalogues; app chrome, like every other string
here). **Two body variants**, because the occupied wording directs the guest to
their host and a vacant room has no guest — there the audience is whoever else
sees the screen (cleaning staff, the manager), and the escalation serves the
same operational purpose: a dead backend must not stay invisible just because
the room happens to be empty. The variant follows **occupancy**, not the host
component.

| Key | de | en |
|---|---|---|
| `stale.banner.title` | Diese Anzeige ist nicht aktuell | This display is out of date |
| `stale.banner.body` (occupied) | Seit über einer Stunde besteht keine Verbindung zum Server. Bitte den Gastgeber informieren. | There has been no connection to the server for over an hour. Please let your host know. |
| `stale.banner.bodyStandby` (vacant) | Seit über einer Stunde besteht keine Verbindung zum Server. Bitte die Verwaltung informieren. | There has been no connection to the server for over an hour. Please inform the property manager. |
| `stale.asOf` (reused verbatim) | Stand {time} | As of {time} |

German stays imperative-neutral (no *ihr/euch*), like the rest of the
connection copy. Never here, as on the connection screen: HTTP status codes,
hostnames, stack traces, retry counters — or the word "error".

### Both themes

Token roles only; every piece of information rides a standard text role. The
bar's one status colour is **`--color-status-warning`**, and unlike
`--color-status-offline` it *is* mapped by the theme axis: `#895806` light,
`#f9b84e` dark — the design sheet's own v1 `Warning #F8B84E` is the dark value
and the light one is that hue darkened to a working lightness. Both were
measured **above 4.5 : 1 on glass** (light 4.64 over `--mesh-6`, 5.17 over
`--mesh-8`; dark 5.63 over `--night-6`, 4.86 over `--night-8`), which a
decorative glyph does not need — it is deliberate, so the role can later move
onto a title or a border with no re-measurement. See
[`../tokens.md`](../tokens.md) → *In-house tokens*.

## Responsive behaviour

None — proportional scaling; the connection panel, the pill and the escalated
banner are all far inside the no-scroll envelope. Sizes are `rem` and the root
is viewport-proportional, so a fit at 1280 x 832 is a fit at 1024 x 768 —
measured, not assumed.

## Interactions

None. Recovery is automatic; on the first successful response the app
returns to its normal route (or standby, per occupancy).

## Both themes

Token roles only; `--color-status-offline` is theme-stable (one value in
both themes — a muted mid-tone chosen by the design sheet precisely to read
as "neither alarming nor invisible" on light and dark; re-measured at review
on both meshes as a non-text element, 3:1 not required for decorative use).

## Open questions

- None for v1. *(2026-08-27: whether the full-screen `DISCONNECTED` panel above
  should also point at the host after an hour is deliberately left open — D13
  scoped the escalation to `STALE`, where the invisibility problem is, and that
  panel already names the situation.)*
