---
name: design-import
description: >-
  One-time extraction of a design (Figma or equivalent) into durable,
  in-repo specs — design tokens and per-screen component specs — so that
  implementation never has to query the design tool again. Use when a new
  screen or design system needs to be brought into the project, or when a
  design has changed and the stored spec is stale. Explicitly user-invoked
  via `/design-import`; never triggered automatically, because each run
  spends a metered design-tool request.
---

# Design Import

## Why this exists

Design-tool MCP access is **metered** (a free Figma plan allows only a
handful of requests per month). A raw MCP response is also a poor
implementation input: verbose node trees, absolute coordinates, no semantic
naming.

So: extract **once**, distill into durable markdown under `docs/design/`, and
have every later phase read those files instead. Implementation agents must
never call the design tool themselves.

## Hard Rules

1. **Only this skill may query the design tool.** `dev`, `planner`,
   `reviewer` and `architect` read `docs/design/**` and never issue a
   design-tool request, no matter how convenient it seems.
2. **One pass per run.** Plan the complete list of what you need *before* the
   first call. You cannot go back for a forgotten detail without spending
   another request from a very small monthly budget.
3. **Never re-extract silently.** If a spec for this screen already exists,
   stop and ask whether to refresh it — state how many requests that costs.
4. **Log every request** in `docs/design/extraction-log.md` so the remaining
   monthly budget stays visible.
5. **Prefer the zero-cost path** where it suffices — see below.

## The zero-cost path (try this first)

A manually exported PNG costs **no** MCP request and can be read directly by
any agent. For a single screen this is often enough:

1. Ask the user to export the frame from Figma as PNG (2x) into
   `docs/design/assets/<screen-slug>.png`.
2. Read that image and write the screen spec from it.
3. Ask the user for the handful of values an image cannot convey exactly —
   hex colors, font family/sizes, spacing scale — via the `grilling` skill,
   one at a time with a recommendation read off the image.

Use the MCP path when you need exact token values across a whole design
system, many screens, or component variants that aren't all visible in one
frame. Say which path you are proposing, and what it costs, before starting.

## Workflow

### Step 1 — Scope and budget check
- Read `docs/design/extraction-log.md` (create it if missing) and report how
  many requests have been used this period.
- Confirm with the user: which screen/frame, which path (PNG or MCP), and how
  many requests it will cost. **Wait for confirmation** — this spends a
  metered resource, so it is an approval-gated action under
  `docs/constitution.md` → Decisions Requiring Your Approval.

### Step 2 — Plan the extraction
Before any call, write down the complete checklist you intend to fill:

- **Tokens** — colors (hex + intended semantic role), typography (family,
  size, weight, line-height), spacing scale, border radii, shadows, breakpoints
- **Layout** — structure and hierarchy, alignment, responsive intent
- **Components** — inventory, with variants and states (hover/active/disabled/error)
- **Content** — real text copy, not lorem ipsum
- **Assets** — icons/images that need exporting, and in what format

### Step 3 — Extract in one pass
Pull everything on the checklist. Do not stop early to "check something" —
that costs another request.

### Step 4 — Distill and write
Write these files (create `docs/design/` if needed):

| File | Contents |
|------|----------|
| `docs/design/tokens.md` | The design primitives, shared by every screen. Written once, extended as new values appear. |
| `docs/design/screens/<slug>.md` | Per-screen layout and component spec. |
| `docs/design/assets/` | Exported images/icons. |
| `docs/design/extraction-log.md` | Append one row: date, screen, path used, requests spent. |

Distill — do not paste raw MCP output. Every color gets a semantic name
(`--color-surface`, not `#F7F7F8`); every repeated value becomes a token.
Flag anything genuinely ambiguous as `[NEEDS CLARIFICATION: …]` rather than
inventing it.

### Step 5 — Report
State what was written, how many requests were spent, how many are left, and
list any `[NEEDS CLARIFICATION]` markers for the user to resolve.

## Handing off to implementation

The tokens file is a **specification, not an implementation**. How those
tokens are realised — CSS custom properties, a Tailwind config, a theme
object, a component library — is an architectural decision under
`docs/constitution.md` → Decisions Requiring Your Approval. Propose an
approach with a recommendation and let the user decide; do not pick one while
extracting.

Once a screen spec exists, `planner` references it from the feature's
`02-design.md` and `dev` implements against it.
