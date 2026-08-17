---
description: Start the Full Pipeline for a new feature via the planner subagent.
argument-hint: <feature name and short description>
---

Delegate to the `planner` subagent to plan this feature: $ARGUMENTS

Start with Step 1 (context gathering), then create `docs/features/<slug>/01-prd.md`
following the `spec-pipeline` skill's `phases/01-prd.md`. Stop after the PRD and
wait for feedback before moving to design.
