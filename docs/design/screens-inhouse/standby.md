# Screen: Standby (vacant room) — in-repo design

**Tokens:** [../tokens.md](../tokens.md)
**Source:** none — **designed in-repo, 2026-08-20** (convention approved by the user 2026-08-20, `guest-frontend-integration` D1)
**Last updated:** 2026-08-20

> ⚠️ **Provenance: designed in-repo — no Figma counterpart.** `docs/backend/
> specification.md` §11 item 1 recorded that no design exists for this
> screen; the user decided (2026-08-20) it is designed in-repo instead of in
> Figma. **A Figma re-import will not produce this file**; if the screen is
> ever drawn in Figma, that extraction supersedes this file and this file
> becomes the deviation log.

## Purpose

What the kiosk shows while its room is **vacant** (`occupancy: VACANT` from
`GET /api/guest/content` — one resource since `guest-payload-consolidation`;
the vacant answer simply carries no stay-derived half). The backend is
maximally data-sparing when vacant (spec decision 9): no guest name, no
stay-derived value reaches the device —
so the screen is property-level only: the property presents itself calmly to
an empty room (or to housekeeping/the next arriving guest). The texts are
the **server-resolved standby templates** (`standby.headline`,
`standby.message` — e.g. headline `{property.name}` → "Meine Unterkunft").

## Layout

**Property-branded** (the session is available — branding primitives are
applied as on every screen).

- Full viewport, mesh background. **No sidebar, no cards, no navigation** —
  a vacant room exposes no navigable surface (data-sparing extended to UI:
  there is nothing behind the navigation that may be shown anyway, and
  `screens/*` answer `409 room-vacant`).
- **The status bar stays**, in its reserved top-right position, fully
  functional: clock, language switcher, appearance switcher. Language and
  theme switching work exactly as everywhere (the standby texts are
  localized maps — switching fetches nothing). A preference write while
  vacant receives `409 room-vacant` and fails silently by design — the
  choice still applies on screen for whoever is standing there.
- Centered block (both axes), single column, gap `--space-4`, max-width
  `min(36rem, 80vw)`, text-centered:
  1. **Property logo**, when the property has one — rendered from the
     authorized asset fetch at `--size-icon-tile` height (3.5rem),
     width auto. No logo → the block simply starts with the headline (no
     placeholder tile — the sidebar's logo-tile treatment is app chrome,
     which this screen deliberately does not carry).
  2. **Standby headline** — `--text-display` (3rem / ExtraBold 800 / 1.1),
     `--color-text-primary`. Server-resolved, rendered verbatim.
  3. **Standby message** — `--text-body-lg` (1.125rem / 400 / 1.5),
     `--color-text-secondary`, max-width ~28rem for measure.

No glass panel: the standby state is atmosphere, not interface — text
directly on the mesh, matching the Home heading block's treatment (which
also sits panel-free on the background). Contrast: `--color-text-primary`
and `--color-text-secondary` on the raw mesh are the same pairing the Home
heading block already ships; the dark theme's night mesh keeps its measured
ratios (`dark-theme.md`). Verified by measurement at review, not assumed.

## Components

| Component | Variants / states | Notes |
|-----------|-------------------|-------|
| Status bar | as built | Unchanged component, same reserved geometry |
| Standby block | `with-logo` / `no-logo` | Logo is an object-URL image with `alt=""` (decorative — the headline names the property) |

## Content

Entirely **property data** (server-resolved standby templates, per offered
language) — no app-chrome strings of its own, so nothing new enters the
translation catalogues for the block itself. The neutral seed ships
headline `{property.name}`, message "Herzlich willkommen".

## Responsive behaviour

None — proportional scaling only. The centered block is far inside the
no-scroll envelope at both reference viewports.

## Interactions

- Status-bar dropdowns work as everywhere (CDK overlay).
- Any touch outside the status bar does nothing.
- The flip to `OCCUPIED` (and back) is driven by the session poll
  (≤ 30 s): the kiosk leaves standby without any human action, restoring
  the normal app. Transition, if any: page-transition timing per
  `design-sheet.md` (350 ms easeOutExpo), reduced-motion respected.

## Both themes

Same roles as the Home heading block; theme axis flips them identically.

## Open questions

- None for v1. (A future "attract mode" — slow crossfade of property
  artwork — would be a new feature, not this screen.)
