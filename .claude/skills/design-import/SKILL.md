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

### Step 4.5 — Derivation check (mandatory, before writing any size token)

**This step exists because skipping it has cost two correction rounds.** Both
times the extracted spec was wrong, `dev` implemented it faithfully, and the
consistency gate plus two code reviews all passed — because everything was
internally consistent *with a wrong source value*. No amount of cross-checking
documents against each other can catch that. Only checking a value against its
origin can.

For **every numeric token**, record the derivation in the file itself:

```
raw source value  →  conversion applied  →  result
199 (Figma, 2x canvas)  →  ÷2  →  100px / 6.25rem
```

Rules:

- **`aspect-[a/b]` is a ratio and carries no size.** `aspect-[24/24]` means
  1:1 — nothing more. Reading it as "24px" once made every icon in the app
  render at about a third of its intended size. The same applies to any other
  utility class in generated framework code (`w-[…]`, `h-[…]`, `p-[…]`):
  that output is *code*, not measurements. Treat it as a hint and confirm the
  number against the frame geometry.
- **If the canvas is 2x, every size halves — with no exceptions.** State the
  scale factor once at the top of the file and apply it uniformly. A single
  un-halved value (`780` written where `390` belonged) sat undetected through a
  full planning-and-review cycle.
- **Where a document deliberately quotes raw values** (e.g. a Components section
  mirroring the design file), say so explicitly at the section head, and give
  the CSS value alongside. A number that is raw in one section and converted in
  the next is the exact ambiguity that produced the `780` bug.
- **Cross-check derived sizes against the exported PNG.** Measure the element,
  compare against the derived token, and reconcile any disagreement before
  writing. The PNG is cheap to measure and costs no metered request.

### Step 4.6 — Mark provenance

`docs/design/**` is not purely a Figma transcript. Values also arrive from a
reference PNG, or from the user reading the design file by hand. Label each
non-extracted value where it is defined:

| Marker | Meaning |
|---|---|
| *(none)* | extracted from the design tool |
| `measured from reference PNG` | derived by measuring the exported render |
| `user-supplied` | read off the design by the user and reported |

Without this, a later reader assumes every number carries the design tool's
authority. Some carry a measurement's tolerance instead — which matters when
deciding whether a mismatch is a bug or noise.

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
