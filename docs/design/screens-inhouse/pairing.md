# Screen: Pairing (in-repo design)

**Tokens:** [../tokens.md](../tokens.md)
**Source:** none — **designed in-repo, 2026-08-20** (convention approved by the user 2026-08-20, `guest-frontend-integration` D1)
**Last updated:** 2026-08-20

> ⚠️ **Provenance: designed in-repo — no Figma counterpart.** This screen was
> never drawn in Figma; every value below is composed from the existing
> system (`tokens.md`, which wins conflicts, and `design-sheet.md`). **A
> Figma re-import will not produce this file**, and its absence from a fresh
> extraction is not evidence that it is wrong. If this screen is ever drawn
> in Figma, that extraction supersedes this file and this file becomes the
> deviation log.

## Purpose

What an **unpaired** kiosk shows: the device requests a pairing code from the
backend and displays it for the **property manager**, who confirms it to a
room. The audience is the manager/installer during provisioning — never a
guest — but the screen stands in a guest-visible room, so it must look
finished, calm and on-brand, and it must leak nothing (the poll secret and
the device token never appear; the display code alone is worthless by the
backend's two-credential design).

The flow is **unattended on the kiosk side**: no buttons, no input. The kiosk
requests, displays, polls, renews on expiry and recovers from network loss on
its own (see `docs/features/guest-frontend-integration/02-design.md` §3).

## Layout

**Deliberately brandless.** Branding arrives with the session, which needs
the token this screen exists to obtain — so the pairing screen renders on the
token-layer defaults (the standard mesh, the standard ink ramp) and never on
a property's colours.

- Full viewport, the semantic mesh background (`--color-mesh-1…16` +
  `--color-mesh-base`, as composed by the shell), **no sidebar, no status
  bar** — this is a pre-app state, not a route inside the app chrome.
- One glass panel, horizontally and vertically centered:
  `--color-surface-glass`, `--radius-2xl`, `--border-glass`, `--blur-glass`,
  `--shadow-panel`; padding `--space-6` vertical / `--space-7` horizontal;
  width `min(30rem, 80vw)`. A single centered column — no-scroll holds by
  construction at 1024 × 768 and 1280 × 832 (total content height ≈ 22rem,
  well inside the 52rem design height).
- Column content, top to bottom, gap `--space-4`:
  1. **Title** — `--text-h1` (2rem / ExtraBold 800 / 1.1),
     `--color-text-primary`.
  2. **Instruction** — `--text-body` (0.875rem / 400 / 1.5),
     `--color-text-secondary`, max ~2 lines.
  3. **The code** — the hero of the screen. 8 characters rendered as two
     groups of four (`XXXX XXXX`, a spacing-only grouping — the code itself
     has no separator and the manager types/reads it without one).
     `--text-display` (3rem / ExtraBold 800 / 1.1), `--color-text-primary`,
     `letter-spacing: 0.15em`, `font-variant-numeric: tabular-nums` (the
     alphabet is unambiguous by backend design — no 0/O/1/I). Top/bottom
     margin `--space-5`. **The code block never moves or resizes between
     states** (no-jump rule): state changes swap only the status line below.
  4. **Status line** — `--text-body`, `--color-text-secondary`, exactly one
     line, preceded by a `0.5rem` round **status dot** (decorative — the text
     carries the information): waiting → `--color-brand-primary` with a slow
     opacity pulse (1.8 s ease-in-out loop, opacity 0.4 ↔ 1.0; a calm
     heartbeat, not a spinner); offline/error states → `--color-status-offline`,
     no pulse. The pulse is suppressed under `prefers-reduced-motion`.

## Components

| Component | Variants / states | Notes |
|-----------|-------------------|-------|
| Pairing panel | one | The glass panel above; no interactive element inside |
| Code display | `visible` / `renewing` | While a replacement code is being fetched (expiry), the old code dims to 40 % opacity rather than disappearing — the block must not collapse (no-jump rule) |
| Status dot + line | `waiting` / `offline` / `renewing` | One line, swapped in place; dot colour per state (see Layout 4). **`cooldown` was removed 2026-08-24** — see the as-built note at the end of this file |

No buttons, no inputs, no focusable elements — there is nothing a person may
do *on the kiosk*. (Touch does nothing; that is correct, not an omission.)

## Content

All strings via `TranslationService` (both catalogues; keys indicative):

| Key | de | en |
|---|---|---|
| `pairing.title` | Gerät koppeln | Pair this device |
| `pairing.instruction` | Diesen Code im Verwaltungsbereich bestätigen, um das Gerät einem Zimmer zuzuweisen. | Confirm this code in the management area to assign this device to a room. |
| `pairing.waiting` | Warten auf Bestätigung … | Waiting for confirmation … |
| `pairing.renewing` | Neuer Code wird erstellt … | Requesting a new code … |
| `pairing.offline` | Keine Verbindung zum Server. Verbindet automatisch neu. | No connection to the server. Reconnecting automatically. |

Voice per `design-sheet.md`: short, warm, non-technical; the audience is the
manager, so the copy is imperative-neutral (no *ihr/euch* here — that is
guest address).

**Never on this screen:** the poll secret, the device token, HTTP status
codes, exception text.

## Responsive behaviour

None — the single centered column scales with the viewport-proportional root
like every other screen (no breakpoints). Portrait/below-floor: out of scope
project-wide.

## Interactions

- No touch interaction. State transitions are network-driven
  (02-design.md §3: create → waiting → claimed / expired-renew /
  offline-retry). A failed create of any kind now takes the offline path
  (**as-built 2026-08-24**, below).
- On claim, the kiosk stores the token and boots the guest app **without a
  reload**; the pairing panel simply gives way to the app's normal bootstrap
  (an instant swap is acceptable; if a transition is used, it follows the
  design sheet's page-transition timing, 350 ms easeOutExpo, reduced-motion
  respected).

## Both themes

Uses only theme-stable roles (`--color-text-*`, glass surfaces, mesh roles) —
works in light and dark without a single screen-specific override. Verified
at review by the standard measure run (route × theme × language ×
viewport).

## Open questions

- None. (The code group-of-four presentation is this spec's own call; if the
  manager-side UI later renders codes differently, align both then.)

---

## As-built note — 2026-08-24: the `cooldown` state was removed

This file originally specified a fourth status-line state, `cooldown`, shown
when pairing-request creation was answered `429` with a `Retry-After` header.

**That state no longer exists and cannot occur.** The backend's pairing rate
limiter was removed on 2026-08-24 (owner decision — the limiter was global, so
an attacker looping create requests could keep a real kiosk from ever obtaining
a pairing code; the wider throttling question is deferred until the production
hardware is known, see `REQ-409` and `REQ-326` as amended). The `429` was then
removed from `api/openapi.yaml`, and with it the kiosk's `'cooldown'` status,
its copy in both catalogues, and its component branch.

**What happens instead:** any failed create — transport error, or any HTTP
status — takes the `offline` path with its 5 s → 30 s backoff. A stray `429`
from a proxy in front of the app therefore still behaves sanely; it simply has
no dedicated state.

Nothing else on this screen changed: the layout, the code display and its
`renewing` behaviour, and the `waiting` / `offline` copy are untouched.
