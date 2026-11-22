Implement the tasks in `docs/features/<slug>/03-todo.md`.

Prerequisite: the `04_analyze` gate must have passed (`docs/features/<slug>/04-analyze.md` → Result: PASS). If it shows GAPS, stop and resolve them first.

Context (read before implementing):
- PRD: `docs/features/<slug>/01-prd.md`
- Design: `docs/features/<slug>/02-design.md`
- Analyze: `docs/features/<slug>/04-analyze.md`

Process:
- Work strictly top-to-bottom through the TODO
- For each completed task:
  - Apply code changes
  - Add/update tests
  - Update the TODO (checkbox + notes)

Rules:
- **UI work is verified by measurement, not by looking.** If the change touches
  anything backed by `docs/design/**`, render it in a real browser and report
  **numbers** — element geometry against the spec's values, text wrapping and
  truncation, and that nothing scrolls. Use the repo's committed instrument if
  one exists (this project: `frontend/tools/measure.mjs`). "Screenshot compared,
  composition matches" is not a result: that exact claim was once made while
  three Major visual defects were open, including a size token that was 2x too
  large. A headless-browser check is a verification tool, not a test suite —
  do not add end-to-end specs or wire it into `npm test` unless that framework
  decision has actually been approved.
- Small diffs — one task at a time
- No unrelated refactors
- Follow repo conventions (nested `CLAUDE.md` files)
- Build/compile check after each change
- Max 5 file edits per task — if more needed, stop and ask
- If the build fails twice: stop, revert, report

Final output:
- What changed
- How to test (exact commands)
- Remaining TODO items
- Next phase: `06_review`
