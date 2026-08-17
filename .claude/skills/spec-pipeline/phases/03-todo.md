Based on `docs/features/<slug>/01-prd.md` AND `docs/features/<slug>/02-design.md`,
create an executable TODO list.

Deliverable:
- Create or update: `docs/features/<slug>/03-todo.md`

Header (mandatory):
```
# TODO: <Feature Title>
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md)
**Status:** Not Started | In Progress | Done
```

Rules:
- Tasks must be small and sequential
- Group tasks by phase (setup, implementation, tests, cleanup)
- Each task includes:
  - [ ] What is done
  - Files likely touched
  - How to validate (build, test, manual check)
- Every task must trace back to a PRD requirement via its `REQ-ID`
- Reference design decisions from 02-design.md — do not re-decide
- Mark unknowns with `[NEEDS CLARIFICATION: specific question]`
- Do NOT write code
- Next phase: `04_analyze` (consistency gate before implementation)
