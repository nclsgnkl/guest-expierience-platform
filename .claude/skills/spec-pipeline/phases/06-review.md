Review the implemented changes for `docs/features/<slug>/`.

Context:
- PRD: `docs/features/<slug>/01-prd.md`
- Design: `docs/features/<slug>/02-design.md`
- TODO: `docs/features/<slug>/03-todo.md`

Check for:
- Logical errors
- Edge cases
- Missing tests
- Performance issues
- Security or data leakage risks
- Unnecessary complexity
- Deviation from design decisions in 02-design.md
- Convention violations (load `code-review` skill for full checklist)
- **REQ coverage** — every `REQ-ID` from the PRD is actually implemented
- **Spec drift** — where the implementation deviates from PRD/Design, update `01-prd.md` / `02-design.md` so the spec stays the source of truth (do not silently accept code-only changes)

Deliverable:
- Create or update: `docs/features/<slug>/06-review.md` (result + findings)

Output:
- Findings (bulleted, with severity 🔴/🟠/🟡/🟢)
- Concrete improvement suggestions
- Update feature status in `docs/features/index.md`
- For architecture/security concerns: escalate to `architect`
- Next phase after PASS: `07_improve` (optional, after significant work)
