# Screen: Connection lost (no cache) — in-repo design

**Tokens:** [../tokens.md](../tokens.md)
**Source:** none — **designed in-repo, 2026-08-20** (convention approved by the user 2026-08-20, `guest-frontend-integration` D1)
**Last updated:** 2026-08-20

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

## Responsive behaviour

None — proportional scaling; both the panel and the pill are far inside the
no-scroll envelope.

## Interactions

None. Recovery is automatic; on the first successful response the app
returns to its normal route (or standby, per occupancy).

## Both themes

Token roles only; `--color-status-offline` is theme-stable (one value in
both themes — a muted mid-tone chosen by the design sheet precisely to read
as "neither alarming nor invisible" on light and dark; re-measured at review
on both meshes as a non-text element, 3:1 not required for decorative use).

## Open questions

- None for v1.
