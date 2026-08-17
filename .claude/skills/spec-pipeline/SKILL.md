---
name: spec-pipeline
description: >-
  The 8-phase spec-driven feature pipeline (constitution → PRD → design →
  TODO → analyze gate → implement → review → improve). Load the matching
  phase file before creating or updating any feature document under
  docs/features/<slug>/. Triggers on: "PRD", "design doc", "feature TODO",
  "analyze gate", "spec pipeline", "constitution", "01_prd", "02_design",
  "03_todo", "04_analyze", "06_review", "07_improve".
---

# Spec-Driven Feature Pipeline

Canonical instructions for every phase of the feature pipeline. Each phase
lives in its own file under `phases/` — **load only the phase you are
executing**, not all of them.

```
00_constitution → 01_prd → 02_design → 03_todo → 04_analyze → 05_implement → 06_review → 07_improve
```

| Phase | File | Input | Produces |
|-------|------|-------|----------|
| `00_constitution` | `phases/00-constitution.md` | project conventions so far | `docs/constitution.md` |
| `01_prd` | `phases/01-prd.md` | user request + codebase research | `01-prd.md` |
| `02_design` | `phases/02-design.md` | PRD + codebase research | `02-design.md` |
| `03_todo` | `phases/03-todo.md` | PRD + Design | `03-todo.md` |
| `04_analyze` | `phases/04-analyze.md` | PRD + Design + TODO + constitution | `04-analyze.md` (gate) |
| `05_implement` | `phases/05-implement.md` | TODO + passed gate | code, no document |
| `06_review` | `phases/06-review.md` | PRD + Design + TODO + code | `06-review.md` |
| `07_improve` | `phases/07-improve.md` | completed work | agent-setup changes |

Feature documents live in `docs/features/<slug>/`.

## Rules that apply to every phase

- `docs/constitution.md` governs all phases — load it first and flag violations.
- Stop after each document and wait for user confirmation before the next phase.
- Phases `00`–`04` and `06`–`07` never write code.
- `04_analyze` is a gate: `GAPS` blocks implementation. Do not fix gaps inside the
  gate — return to the owning phase, then re-run it.
- Mark ambiguities with `[NEEDS CLARIFICATION: specific question]` and resolve them
  via the `grilling` skill — one question at a time, never in bulk, never by guessing.
