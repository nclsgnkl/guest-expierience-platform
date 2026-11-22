---
name: code-review
description: >-
  Structured code review checklist. Use when reviewing code, auditing a
  change, or checking convention compliance. Loaded by the `reviewer`
  subagent; also invocable directly as `/review`.
---

# Code Review Checklist

> **2026-08-18** — added the mandatory visual check (step 0) and category 9.
> `home-screen-prototype` passed review with two shipped visual bugs (a
> mesh-gradient background with no explicit radial-gradient radius, and
> action-cards missing `flex-shrink: 0`) because every prior category compares
> code/text against `docs/design/**`'s prose — none of them compare the
> rendered pixels against the reference image. In both cases the code matched
> the spec's text; only the rendered result didn't match the spec's intent.
> See `docs/features/home-screen-prototype/handoff.md` for the full incident.

## Review Process

0. **Visual check, UI changes only.** If the change touches components backed
   by `docs/design/**`, launch the app and capture a screenshot **before**
   scoring any category below — a text-only review cannot catch a rendering
   defect that matches the spec's prose but not its intent (see the note
   above). Prefer the `run` skill if the session has it; otherwise start the
   dev server and run:
   ```
   npx playwright screenshot http://localhost:4200 /tmp/review.png
   ```
   Then read the screenshot and compare it against the matching
   `docs/design/assets/*.png` reference. For a change with no rendered surface
   (backend-only, config-only, etc.), skip this step explicitly with a
   one-line reason — don't skip it silently.
1. Read all changed files
2. Check each category below (skip categories that genuinely don't apply, but say so explicitly rather than silently)
3. Report findings with severity and file links
4. Provide actionable recommendations

## Categories

### 1. Correctness
- [ ] Logic matches the stated intent / requirement
- [ ] Edge cases handled (empty input, null/undefined, boundary values)
- [ ] No off-by-one, race condition, or state-mutation bugs

### 2. Error Handling
- [ ] Failures are caught and surfaced, not silently swallowed
- [ ] Errors are actionable (enough context to debug, not just "something failed")
- [ ] External calls (network, disk, DB) have failure paths considered

### 3. Security
- [ ] No hardcoded secrets/credentials
- [ ] No sensitive data in logs
- [ ] User input validated/sanitized at system boundaries
- [ ] No obviously injectable string-built queries or shell commands
- [ ] Authorization checked on new endpoints/handlers, not just authentication

### 4. Naming & Readability
- [ ] Names describe intent, not implementation detail
- [ ] No dead code, commented-out blocks, or leftover debug statements
- [ ] Function/module size reasonable for what it does

### 5. Conventions
- [ ] Matches patterns already established in the codebase (check nested `CLAUDE.md` for the area)
- [ ] Consistent with existing naming/formatting style
- [ ] No unauthorized dependency-manifest changes

### 6. Testing
- [ ] Tests present for non-trivial new behavior
- [ ] Tests actually exercise the changed logic, not just happy-path scaffolding
- [ ] No flaky patterns introduced (time-dependent, order-dependent tests)

### 7. Performance
- [ ] No obvious N+1 patterns or unnecessary repeated work in hot paths
- [ ] Reasonable behavior on large inputs/collections where relevant

### 8. Dependencies & Scope
- [ ] No unnecessary new external dependencies for a small task
- [ ] Change stays within the requested scope — no unrelated drive-by edits

### 9. Visual Fidelity (UI changes only)
- [ ] Screenshot captured and read (step 0) — or explicitly marked not
      applicable, with a one-line reason
- [ ] Layout, sizing, and colour match the `docs/design/assets/*.png` reference
- [ ] Any mismatch is traced to a root cause and reported as a finding —
      never silently accepted as "close enough"

## Output Format

```
🔍 Review Report

| # | Category | Status | Finding |
|---|----------|--------|---------|
| 1 | Correctness | ✅ | — |
| 2 | Security | ⚠️ | [file](path): user input reaches query unsanitized |
| ... | ... | ... | ... |

Recommendations:
1. {Highest priority fix}
2. {Second priority}
```

Severity: 🔴 Critical (blocking) | 🟠 Major | 🟡 Minor | 🟢 Info
