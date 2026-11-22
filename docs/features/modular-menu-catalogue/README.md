# `modular-menu-catalogue` — retired 2026-08-27

**This feature no longer exists.** Owner decision **D1** of 2026-08-27 split it
into three sequenced features. Nothing was lost: every requirement moved across
under its existing ID, and no ID changed meaning.

| Part | Feature | REQ block | Status |
|---|---|---|---|
| **A** | [`sidebar-catalogue-overflow`](../sidebar-catalogue-overflow/01-prd.md) | REQ-901…935 | Planned through `04_analyze` |
| **B** | [`guest-payload-consolidation`](../guest-payload-consolidation/01-prd.md) | REQ-941…951 | PRD only — decision-complete, not designed |
| **C** | [`module-content`](../module-content/01-prd.md) | REQ-961…979 | PRD only — **not ready to plan**, three open gates |

Why the split: Part A is the owner's actual ask (*"die Sidebar, die strukturell
verbessert werden soll"*) and needs no contract change beyond a `maxItems` that
should always have been there. Part B is small, mechanical and closes a live
defect. Part C is schema design for content nobody has specified yet, and is
blocked on three approval gates and on at least one designed module screen.

**No PRD lives here any more** — the parent `01-prd.md` and its handoff were
deleted rather than left as a fourth, drifting copy. Git history holds both,
along with the owner-written 2026-08-26 draft that preceded them. The
old→new requirement-ID map was deliberately **not** carried forward: that draft
was the owner's personal notes, not an official artifact, and no provenance
trail is wanted.
