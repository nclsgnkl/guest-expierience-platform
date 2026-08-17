---
description: Implement the next unchecked TODO step via the dev subagent.
argument-hint: "[optional: feature slug]"
---

Delegate to the `dev` subagent: confirm the `04_analyze` gate for the relevant
feature is PASS, then implement the next unchecked step from
`docs/features/<slug>/03-todo.md`. If no slug is given, infer it from the most
recently updated entry in `docs/features/index.md` or ask.

Feature slug (if given): $ARGUMENTS
