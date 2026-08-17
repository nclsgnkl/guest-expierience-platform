---
name: code-review
description: >-
  Structured code review checklist. Use when reviewing code, auditing a
  change, or checking convention compliance. Loaded by the `reviewer`
  subagent; also invocable directly as `/review`.
---

# Code Review Checklist

## Review Process

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
