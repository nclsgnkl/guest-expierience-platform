# Screen: Connection lost (no cache) — in-repo design

**Tokens:** [../tokens.md](../tokens.md)
**Source:** none — **designed in-repo, 2026-08-20** (convention approved by the user 2026-08-20, `guest-frontend-integration` D1)
**Last updated:** 2026-08-27 (the escalated staleness banner — `guest-payload-consolidation` REQ-952)

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

### Layout

The hint **escalates in place**: same anchor as the pill — directly below the
status-bar cluster, right-aligned to it — grown from a pill into a panel.

- Overlay, not flow: `position: absolute` inside the host's padding box, host
  `display: contents`. It takes no layout space, so it cannot grow the document
  on any route (`docs/constitution.md` → "Nothing scrolls") and it moves
  nothing when it appears.
- Panel: `min(22rem, 40vw)` wide, `--color-surface-glass`, `--border-glass`,
  `--radius-2xl`, `--blur-glass`, `--shadow-panel`; padding `--space-4`
  vertical / `--space-5` horizontal; a row with the glyph, then a column with
  gap `--space-2`.
- **Glyph** — Phosphor `WifiSlash` (regular weight), `--size-icon-lg`,
  `--color-status-offline` — **decorative**, like the pill's dot: the text
  carries the information, so the muted status colour needs no text-contrast
  conformance. Deliberately the **connection screen's own glyph**, not a
  warning sign: it is already registered (`registered-icons.ts`), and it keeps
  the severity order honest — the full-screen panel is the more severe state,
  and the two should not be told apart by an alarm symbol on the milder one.
- **Title** — `--text-title` weight, `--color-text-primary`, one line.
- **Body** — `--text-body`, `--color-text-secondary`, at most two lines.
- **Timestamp** — the pill's own `Stand HH:MM` / `As of HH:MM`, `--text-status`
  in `--color-text-secondary`. The information the pill carried is not lost
  when it is replaced.
- Not interactive. No dismiss, no retry button: recovery is automatic, and a
  button would imply the guest is responsible for infrastructure.
- `role="status"` with `aria-live="polite"`, set once — the pill's discipline
  verbatim. The banner appears once per stale episode and its text is static,
  so the live region never chatters on the minute tick.

The anchor is free on both hosts it renders in: on Home the top-right is the
deliberately empty widget column (`../screens/home.md`), and standby has no
sidebar and a centred block. Verified by measurement at 1024×768 and 1280×832,
both themes, both languages — the banner must collide with neither the
status-bar cluster nor Home's card row.

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

Token roles only. `--color-status-offline` is theme-stable and used
decoratively; every piece of information rides a standard text role.

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
