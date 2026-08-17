---
name: grilling
description: >-
  Model-invoked interview primitive that pressure-tests a plan, design, or
  ticket one question at a time — each with a recommended answer — until a
  shared understanding is reached. Used by `planner` (PRD/Design decisions)
  and `architect` (deep-review design decisions) instead of guessing or
  batching clarification questions. Also load it when the user asks to be
  grilled, interviewed, or challenged on a plan — e.g. "grill me",
  "pressure-test this", "poke holes in this plan".
---

# Grilling

## Origin

Adapted from the [`grilling`](https://www.aihero.dev/skills-grilling) skill in
[mattpocock/skills](https://github.com/mattpocock/skills/tree/main/skills/productivity/grilling)
(Matt Pocock, aihero.dev).

## Core Idea

Model the plan as a **decision tree**: every plan branches into decisions, and
decisions depend on each other. Walk the tree one node at a time, resolving
dependencies in order — an early answer can reshape which questions come
next. That is why questions arrive singly and in dependency order: a firehose
of parallel questions loses the structure that makes the interview converge
on a shared understanding.

## Workflow

1. Walk down each branch of the decision tree, resolving dependencies
   between decisions one at a time.
2. Ask **one question at a time** and wait for the answer before
   continuing — never a bulk list.
3. For every question, offer a **recommended answer**, so the user reacts
   to a proposal instead of facing a blank prompt.
4. If a *fact* can be settled by exploring the environment (filesystem,
   tools, codebase), look it up instead of asking.
5. The *decisions* remain the user's — put each one to them and wait for
   their answer.
6. Do not act on the plan until the user confirms a shared understanding
   has been reached.

## Rules

1. **One question at a time** — parallel questions are bewildering and
   break the dependency order the tree relies on.
2. **Recommend, don't just ask** — always propose an answer.
3. **Codebase-first** — resolve facts from the environment before asking
   the user.
4. **No premature action** — wait for explicit confirmation before
   implementing anything.

## Inputs & Outputs

**Input** — any plan, design or proposal that needs refinement: a PRD/Design
draft, a task breakdown, a migration plan.

**Output** — a refined plan covering all aspects and dependencies, reflecting
a shared understanding between agent and user. When the input was a PRD or
Design draft (`docs/features/<slug>/01-prd.md` / `02-design.md`), **write the
resolved decisions back into that document** — it stays the single source of
truth driving implementation. Do not leave the outcome in chat only.

## When to Use

Reach for this (or let a subagent reach for it automatically) whenever a plan
or design still has soft spots that should be surfaced before code is
written — and whenever the user asks to be grilled or challenged on a plan.
