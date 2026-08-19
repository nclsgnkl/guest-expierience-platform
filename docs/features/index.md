# Feature Registry

Index of all features and their pipeline status.

| Slug | Title | Status | Phase | Owner |
|------|-------|--------|-------|-------|
| _example_ | Example Feature | Not Started | 01_prd | — |
| [home-screen-prototype](home-screen-prototype/01-prd.md) | Home Screen Prototype | In Review | 06_review **PASS ✅** (round 3 final verification, 2026-08-18: 🔴 0 · 🟠 0 · 🟡 0 · 🟢 4 against constitution v1.3.0; all REQ-001…REQ-025 implemented and independently re-measured. **Nothing open** — the one 🟡 `frontend/CLAUDE.md` clause was approved verbatim and written 2026-08-18. Ready to commit — rounds 3–6 are still uncommitted; the user commits) | reviewer |
| [appearance-and-language](appearance-and-language/01-prd.md) | Appearance & Language (dark mode + i18n) | In Review | **06_review PASS ✅ → 07_improve done** (2026-08-19). Review: 🔴 0 · 🟠 0 · 🟡 5 · 🟢 10 against constitution v1.3.0; all 27 REQ-101…REQ-127 confirmed, 0 orphans — see [06-review.md](appearance-and-language/06-review.md). All 39/39 steps implemented. All 5 🟡 addressed: config validation gap, the corrected light status-bar contrast figure, spec glyph registration, option-row reflow, and the light secondary text role darkened (`--ink-600` `#6e6486` → `#5a516e`, user-approved). Three approved `frontend/CLAUDE.md` entries written; `measure.mjs` is Prettier-clean. `ng build` 351.08 kB / 7.49 kB; `ng test` 22 files / 178 tests / 0 failures; 72/72 no-scroll; Home's y 88 / y 744 unchanged. **Ready to commit — the user commits.** One open item below (`section-placeholder` note on the raw mesh) | dev |

**Status values:** Not Started · In Progress · In Review · Done
**Phase values:** 00_constitution · 01_prd · 02_design · 03_todo · 04_analyze · 05_implement · 06_review · 07_improve

## Tracked, not blocking

| Item | Origin | Status |
|------|--------|--------|
| **`.section-placeholder__note` is below WCAG AA in light mode** — measures **3.77 : 1** after the role was darkened (was 2.79). | `appearance-and-language` `06-review.md` 🟡 #3, re-measured 2026-08-19 | **Open — needs a user decision.** The other two consumers of `--color-text-secondary` now pass (action card 5.41, Settings hint 5.12) after the approved darkening of `--ink-600` to `#5a516e`. This one still fails because it is the only consumer with **no surface beneath it** — it composites straight onto the mesh, starting ~0.8 lower than any surface-backed text. It is `--text-body-lg` (18 px, weight 400), so it is *not* WCAG large text and the 4.5 : 1 threshold applies. Reaching 4.5 by darkening alone needs `--ink-600` near `#4e4660` — roughly twice the approved darkening — which would drag the shared role toward primary on **every** surface to compensate for one screen's missing surface. Three options: give the placeholder a glass panel (fixes the cause), put the note on `--color-text-primary` (7.75 : 1 today), or darken the role further. Not fixed autonomously: each changes a shipped screen's appearance. |
