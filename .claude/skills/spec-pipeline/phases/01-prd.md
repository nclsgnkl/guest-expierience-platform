Create a Product Requirements Document (PRD) for the given topic.

Deliverable:
- Create or update: `docs/features/<slug>/01-prd.md`
- Add entry to `docs/features/index.md` if new feature

Structure:
- Problem statement
- Goals / Non-goals
- Assumptions
- User stories
- Acceptance criteria (clear & testable)
- Technical constraints (anchor to `docs/constitution.md`)
- Risks & open questions

Header (mandatory):
```
# PRD: <Feature Title>
**Feature:** [index.md](../index.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Status:** Draft | In Review | Approved
```

Rules:
- Do NOT write code
- Assign every requirement a stable ID (`REQ-001`, `REQ-002`, …)
- Mark ambiguities with `[NEEDS CLARIFICATION: specific question]` — don't guess
- Resolve `[NEEDS CLARIFICATION]` markers via the `grilling` skill (one question
  at a time, with a recommended answer) instead of guessing or asking in bulk
- Otherwise proceed with documented assumptions
- Review and refine an existing PRD — do not remove existing requirements, only add and amend
- Next phase: `02_design`
