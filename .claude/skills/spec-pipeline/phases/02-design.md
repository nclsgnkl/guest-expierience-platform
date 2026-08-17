Based on `docs/features/<slug>/01-prd.md`, create a Design Document.

Deliverable:
- Create or update: `docs/features/<slug>/02-design.md`

Header (mandatory):
```
# Design: <Feature Title>
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **TODO:** [03-todo.md](03-todo.md)
**Status:** Draft | In Review | Approved
```

Structure:
- Architecture decision (components/modules affected, new classes/interfaces)
- Data model (schema changes, migrations)
- API contract (new endpoints/functions, request/response shapes)
- Sequence diagram (optional, for complex flows)
- Risks & trade-offs
- Open decisions

Rules:
- Reference the PRD for requirements — do not duplicate
- Focus on HOW, not WHAT (that's the PRD's job)
- Keep it concise — one page max for simple features
- Mark unknowns with `[NEEDS CLARIFICATION: specific question]`
- Resolve unknowns via the `grilling` skill (one question at a time, with a
  recommended answer) instead of guessing
- Do NOT write code
- For complex/risky designs: escalate to `architect`
- Next phase: `03_todo`
