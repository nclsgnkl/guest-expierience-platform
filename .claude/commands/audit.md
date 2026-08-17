---
description: Full quality audit of a scope via the reviewer subagent.
argument-hint: <scope to audit, e.g. a directory or component>
---

Delegate to the `reviewer` subagent for a full audit (all `code-review`
categories, not just the diff) of: $ARGUMENTS

Report severity-ranked findings; let the user decide whether to fix now or
track in a TODO.
