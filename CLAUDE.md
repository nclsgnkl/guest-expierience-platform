# Project Agent Setup

This file is always loaded into context, in every session — including a
plain `claude` session that is not running as any custom agent. Keep it
short: it only orients Claude, it deliberately does **not** contain the
routing logic for this project's spec-driven pipeline.

## Project

- **Name:** Guest Experience Platform — an in-property guest info screen
  (welcome, house info, smart home, surroundings, services), one self-hosted
  instance per property.
- **Stack:** Angular 22 + TypeScript (strict), standalone components, **zoneless**
  change detection, Angular Router, signals for local UI state. Plain CSS with
  custom properties — no Tailwind, no SCSS, no component library. Unit tests on
  **Vitest + jsdom**; no end-to-end framework chosen yet. Content comes from
  static TS/JSON via `PropertyContentService` — no backend, no auth.
  Frontend lives in `frontend/`.
- **Runs on premises, offline.** Every property runs its own instance on a local
  server on an untrusted LAN. No external runtime requests at all — fonts, icons
  and assets ship inside the build. See `docs/constitution.md` principle 4.
- **Repo conventions:** stack-specific rules live in nested `CLAUDE.md` files —
  see `frontend/CLAUDE.md`. Claude Code loads those automatically when working in
  that directory.

## How work is organized

This project's agent workflow (planning → implementation → review, with an
optional architecture escalation) is **only** started explicitly:

```
claude --agent lead
```

That runs the `lead` subagent (`.claude/agents/lead.md`) as the main session.
It owns the routing table, the subagent roster, and every orchestration rule
— see that file for the full logic. A plain `claude` session intentionally
does not carry any of that.

**If you are a generic/default session** and the user's request looks like it
needs the pipeline (new feature, plan, PRD, a bug fix that should go through
review, a refactor, an architecture/security concern): do not delegate to
`planner`, `dev`, `reviewer`, or `architect` yourself. Tell the user to
restart with `claude --agent lead` — do not silently take on orchestrator
behavior. Answering direct questions, one-off explanations, or small
standalone asks that don't need the pipeline is fine as normal.

The slash commands (`/plan`, `/build`, `/review`, `/audit`, `/constitution`,
`/improve` — see `.claude/commands/`) are explicit, user-invoked shortcuts to
individual subagents. They work in any session, including a generic one,
because the user is asking for them directly rather than Claude deciding to
delegate. They don't replace `lead` for anything spanning multiple steps.

**If a subagent you invoked returns `⏸ Awaiting decision`** — it hit something
listed under `docs/constitution.md` → "Decisions Requiring Your Approval" (a
new dependency, a datastore/infra choice, a new architectural pattern, or a
proposed nested `CLAUDE.md` entry). Relay its recommendation to the user and
wait for their answer, then re-invoke the subagent with the decision included.
Never answer on the user's behalf, even if the recommendation looks obviously
right — that gate exists precisely so these choices stay with the user.

## Reference

- `docs/constitution.md` — project principles and constraints, governs every phase
- `docs/system-context.md` — existing components and cross-cutting concerns
- `docs/features/index.md` — feature registry (status of everything in flight)
- `docs/design/tokens.md`, `docs/design/screens/` — the design spec for UI work
  (the **as-built** record: exactly what exists in Figma and was extracted)
- `docs/design/design-sheet.md` — the design system as a whole, including parts
  not yet drawn (motion, status colours, voice, unbuilt components). Where it
  conflicts with `tokens.md`, **`tokens.md` wins**

**Design-tool access is metered.** Never query a Figma (or equivalent) MCP
server directly — read `docs/design/**` instead. Only the `design-import`
skill may spend a request, and only when the user explicitly runs
`/design-import`.
