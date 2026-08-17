Improve the agent setup based on learnings from recent work.

Run after completing significant tasks (features, refactorings, multi-step workflows).

## Process

1. **Review** — What was built? Which subagents/skills were involved?
2. **Identify gaps** — Missing instructions? Incomplete skills? Wrong routing? Unclear subagent boundaries?
3. **Propose changes** — For each gap: type (subagent/skill/CLAUDE.md), file, change, rationale. Present to the user.
4. **Confirm** — User approves proposals ("all", "1,3,5", or "none").
5. **Implement** — Make confirmed changes. Update the tables in `CLAUDE.md` if subagents/skills changed.

## Rules

- No hypothetical over-engineering — only fix what was actually needed
- Check for existing patterns before adding new ones
- Verify changes don't break existing flows
- Note what changed and why somewhere durable (a CHANGELOG, or a note at the top of the changed file)
