Cross-artifact consistency gate. Verify that PRD, Design, and TODO agree
before implementation starts.

Inputs (read all four):
- PRD: `docs/features/<slug>/01-prd.md`
- Design: `docs/features/<slug>/02-design.md`
- TODO: `docs/features/<slug>/03-todo.md`
- Governance: `docs/constitution.md`

Deliverable:
- Create or update: `docs/features/<slug>/04-analyze.md`

Header (mandatory):
```
# Analyze: <Feature Title>
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Result:** PASS | GAPS
```

Checks (all must hold for PASS):
- **Coverage** — every `REQ-ID` in the PRD maps to ≥1 TODO task
- **No orphans** — every TODO task references an existing `REQ-ID`
- **Design fidelity** — the Design satisfies every requirement; no contradictions between PRD and Design
- **Constitution compliance** — nothing in PRD/Design/TODO violates `docs/constitution.md`
- **No unresolved markers** — no `[NEEDS CLARIFICATION]` left in any artifact
- **Reverse defect trace (correction rounds only)** — when this gate runs on a
  round that exists *because* defects were found, trace every known defect back
  to the `REQ-ID` it violates. A requirement covered by a task is **not**
  evidence the requirement is met: on 2026-08-18 three shipped defects violated
  `REQ-ID`s already marked ✅ in this very matrix. The coverage table proves a
  task exists, nothing more — say so rather than letting ✅ read as "done".

Output:
- A coverage table: `REQ-ID | Design section | TODO task(s) | Status` — where
  `Status` means "a task covers this", not "this works"
- On a correction round: a defect table `Defect | REQ-ID violated | Root cause`
- List of gaps (if any), each with the artifact and the fix needed
- **Result: PASS** → proceed to `05_implement`
- **Result: GAPS** → stop; return to the owning phase (PRD/Design/TODO) to resolve, then re-run this gate

Rules:
- Do NOT write code
- Do NOT fix gaps here — only detect and report; fixes happen in the owning phase
- Next phase after PASS: `05_implement`
