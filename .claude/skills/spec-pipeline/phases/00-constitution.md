Create or update the project constitution — the governing principles and
non-negotiable constraints that every feature phase must respect.

Deliverable:
- Create or update: `docs/constitution.md`

Header (mandatory):
```
# Constitution: <Project Name>
**Status:** Draft | Ratified
**Version:** <semver> | **Last amended:** <YYYY-MM-DD>
```

Structure:
- **Principles** — numbered, testable rules (derived from how the codebase actually works, or how the user wants it to work)
- **Constraints** — hard technical boundaries (language/framework baseline, no dependency-manifest edits without approval, branch discipline)
- **Decisions Requiring Your Approval** — standing list of decision categories (new dependency, datastore/infra choice, new architectural pattern or cross-cutting convention, any nested `CLAUDE.md` entry) that a subagent must propose-and-wait-for-confirmation on rather than decide itself, at any point in the project — not just during this phase. Include the "how this works technically" note (a subagent can't pause mid-task, so it ends its turn on an open question and the delegating session relays it), and state that this section is in force even while the constitution is still `Draft`.
- **Quality bar** — what "done" means (build + tests green, conventions, no sensitive data logged)
- **Governance** — how the constitution is amended

Rules:
- **Greenfield check first:** if there is no existing codebase to derive conventions from and the stack in root `CLAUDE.md` is still a placeholder, resolve the stack (language, framework, hosting, datastore) with the user via the `grilling` skill — one question at a time, each with a recommended default — before writing any Principles or Constraints. Do not pick a stack unprompted. Once resolved, update root `CLAUDE.md`'s `Stack` line to match.
- Derive principles from the existing codebase (if any) and from explicit user input — ask rather than invent conventions from nothing
- Each principle must be checkable — an agent should be able to flag a violation
- Keep it stable — this is the least frequently changed artifact
- Do NOT write code
- The constitution governs every later phase (`01_prd` … `07_improve`); the Analyze gate (`04_analyze`) checks compliance
- Next phase: `01_prd` for the first feature
