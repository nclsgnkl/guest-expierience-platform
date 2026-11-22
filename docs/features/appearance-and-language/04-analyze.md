# Analyze: Appearance & Language
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Result:** **PASS — nothing blocked** (round 2, 2026-08-18)

Cross-artifact consistency is clean for REQ-101 … REQ-127: full coverage, no
orphan tasks, no contradiction between PRD and Design, and nothing that
violates `docs/constitution.md` v1.3.0.

**Round 1 passed with 6 of 37 steps held at four approval gates. All four were
answered by the user on 2026-08-18**, the owning artifacts were amended, and
this gate was re-run from scratch rather than patched. The result: **39 steps,
0 blocked, 27 requirements, 27 covered.**

## What changed since round 1, and why the gate had to be re-run

| # | Answer | Blast radius as predicted in round 1 | Blast radius as it turned out |
|---|---|---|---|
| 1 | Hand-rolled i18n service, German + English | "§7 stands; S3/S4 proceed" | ✅ exactly that. One addition: §7.2's "not built" list is now load-bearing, because the user said the language set is expected to stay at two |
| 2 | **The database owns the preference** (differs from the recommendation) | "Only the implementation behind `PreferenceStorage` and its provider change" | ❌ **understated.** A read-only seam became a read/write **asynchronous** contract: §6 rewritten (6.1–6.7), §1 (dependency direction, bootstrap), §5.2 (rename), §10, §11, §13 amended; REQ-121/REQ-122 restated, REQ-126/REQ-127 added, REQ-106 reconciled; S6 rewritten, S38/S39 added, S9/S11/S15/S23/S26/S28/S30 adjusted |
| 3 | `frontend/CLAUDE.md` amendment approved **verbatim** | "The text is amended before being written" | ✅ approved as drafted. The text was **not touched** in round 2 — see the note below |
| 4 | "Follow system" approved, startup value = backend default | "Only `resolvedTheme()`'s branch and the default option list change" | ✅ plus one thing round 1 missed: `docs/design/tokens.md` still describes *absent = follow system* as the default, which now contradicts REQ-105. S23 corrects it |

> **The approved `frontend/CLAUDE.md` text was left byte-identical.** The
> persistence answer suggests two conventions worth writing down (the gateway
> must stay a DI leaf; nothing but `PreferencesService` touches the guest's
> preference). Adding either to §4's text would have made the approval stale —
> the user approved *that* wording, not a revision of it — and re-opened a gate
> that is closed. Both rules are recorded in `02-design.md` §1/§6 and in
> `docs/system-context.md` instead, neither of which is approval-gated. **No
> new nested-`CLAUDE.md` approval is requested by this feature.**

## Coverage matrix

`Status` means **"a task covers this"** — nothing more. It is not evidence the
requirement is met; that is `06_review`'s job. On 2026-08-18 three shipped
defects violated `REQ-ID`s already ticked in the previous feature's matrix.

| REQ-ID | Design section | TODO step(s) | Status |
|--------|----------------|--------------|--------|
| REQ-101 | §2 Where the dark palette is documented | S1, S22, S23, S24 | ✅ |
| REQ-102 | §3.3 Semantic roles; §4 | S1, S2 | ✅ |
| REQ-103 | §4 The mesh must become a semantic role | S2, S13 | ✅ |
| REQ-104 | §3.4 Measured contrast | S1, S22, S37 | ✅ |
| REQ-105 | §6.1 (`resolvedTheme`, and why it is not the startup source) | S5, S11, S23, S28 | ✅ |
| REQ-106 | §7.1 Shape; §7.3 Why nothing is fetched; §6.5 (the write is outside the render path) | S3, S4, S27 | ✅ |
| REQ-107 | §7.1; §4(b) CLAUDE.md clause | S4, S13, S35 | ✅ |
| REQ-108 | §5.1 Types; §5.2 Resolution and fallback | S7, S8, S9, S10, S29 | ✅ |
| REQ-109 | §5.1; §6.1 (`FALLBACK_DEFAULTS`) | S3, S5, S8 | ✅ |
| REQ-110 | §1 Dependency direction; §4(b) | S11, S13, S30 | ✅ |
| REQ-111 | §7.1 compile-time completeness | S3, S4, S27, S35 | ✅ |
| REQ-112 | §8.1 Placement; §8.2 Collision budget | S13, S18, S25, S36, S37 | ✅ |
| REQ-113 | §8.3 Composition | S14, S15, S17, S25, S33 | ✅ |
| REQ-114 | §8.5 The clock under zoneless CD | S12, S17, S31, S33 | ✅ |
| REQ-115 | §8.4 Dropdowns | S14, S15, S16, S17, S32 | ✅ |
| REQ-116 | §9.2 Routing | S20, S21, S34 | ✅ |
| REQ-117 | §9.1 Composition | S14, S19, S20, S34 | ✅ |
| REQ-118 | §9 Design gap — stated, not hidden | S20, S24 | ✅ |
| REQ-119 | §5.1 `PropertySettingsConfig`; §6.1 | S5, S7, S8, S9, S28, S29 | ✅ |
| REQ-120 | §6.1 default ⊕ the guest's choice | S5, S28 | ✅ |
| REQ-121 | §6.2 What persists it; §6.3 The contract | S6, S26, S38, S39 | ✅ |
| REQ-122 | §1; §5.2; §6.3 (policy above the transport) | S6, S7, S9, S10, S11, S38, S39 | ✅ |
| REQ-123 | §8.2, §8.4, §9.1 height budget; §10 | S36, S37 | ✅ |
| REQ-124 | §10 Test strategy | S27 … S35, S39 | ✅ |
| REQ-125 | §13 system-context updates | S26 | ✅ |
| REQ-126 | §6.4 Bootstrap and the flash; §6.5 The write path | S38, S39 | ✅ |
| REQ-127 | §6.6 Whose preference is it? | S6, S26, S38, S39 | ✅ |

**Verified mechanically** (parsed both files, not eyeballed):

- 27 requirements defined in `01-prd.md`, **0 duplicate IDs**;
- 27 covered by ≥ 1 step → **0 gaps**;
- 39 steps in `03-todo.md`, every one carrying ≥ 1 `_REQ-1xx_` tag → **0
  orphans**;
- every `REQ-ID` referenced by a step **exists** in the PRD table → 0 dangling
  references;
- 27 requirements ↔ 27 acceptance criteria, one each.

## Checks

- [x] **Coverage** — every `REQ-ID` maps to ≥ 1 step (matrix above).
- [x] **No orphans** — every one of the 39 steps carries a `_REQ-ID_` tag that
      exists in `01-prd.md`'s requirements table.
- [x] **Design fidelity** — the Design answers every requirement and
      contradicts none. Four places where the PRD stops short and the Design
      finishes the thought, checked for agreement: REQ-101 (*where* the palette
      lives → §2); REQ-112 (*must not move existing geometry* → §8.1/§8.2);
      REQ-121 (*the database owns it* → §6.2 picks the stand-in, §6.3 the
      contract); REQ-126 (*no flash, failure-tolerant* → §6.4/§6.5 give the
      ordering and the policy).
- [x] **Internal contradictions hunted, one found and fixed.** REQ-106 said a
      language switch causes "**no network request of any kind**"; REQ-121 now
      requires a persistence write on exactly that event. Both were rewritten:
      nothing is fetched **to render** the switch, the write is same-origin,
      fire-and-forget and outside the render path, and no external host is ever
      contacted. Had this been left, the review round would have had to choose
      which requirement to violate.
- [x] **A second one found and fixed:** `PropertyConfig.settings` (the sidebar's
      Einstellungen menu item, `home-screen-prototype` REQ-018) and the new
      `PropertyContentService.settings` would have coexisted as
      `content.property().settings` and `content.settings()`. The new signal is
      renamed `settingsConfig` (`02-design.md` §5.2, S9).
- [x] **Constitution compliance (v1.3.0)** — checked clause by clause below.
- [x] **No unresolved `[NEEDS CLARIFICATION]`** — none of the four artifacts
      *raises* one. `grep` returns hits that quote the **single existing marker
      in `docs/design/tokens.md`** (*"no dark-mode palette exists — is one
      wanted?"*) that REQ-101 and step S23 exist to resolve, plus one quoting
      `docs/system-context.md`'s open guest-authentication decision, which §6.6
      deliberately does **not** answer. Both are pre-existing markers in other
      files, quoted so this feature's relationship to them is explicit.
- [x] **No open approval gates.** All four are answered and recorded with their
      date and the user's own words in `01-prd.md` → "Decisions taken". The
      decisions this feature *creates for someone else* — the datastore, the
      preference endpoint and its security, and what clears a stored preference
      at check-out — are written down in `02-design.md` §6.7 and stay gated
      where they belong, i.e. in the backend feature, not here.
- [x] **Existing requirements not regressed** — REQ-014/REQ-022 (Home geometry)
      protected by REQ-112's *unchanged* acceptance figures and S18/S37;
      REQ-024/REQ-025 (no-scroll, scaling) extended by REQ-123's 72-combination
      matrix; REQ-023 (branded active glyph) protected by §3.5; REQ-003
      (`applyBranding()` writes primitives at startup) protected by S11, which
      keeps it in `App`'s constructor and moves only `applyPropertyDefaults()`.
- [ ] **Reverse defect trace** — not applicable: no implementation exists yet,
      so there are no defects to trace back.

## Constitution compliance, clause by clause

| Clause | Assessment |
|---|---|
| P1 — property content is data, not code | Available languages, both defaults, the appearance option list and all copy live in `PropertyContentService`. Adding a language is config + catalogue (REQ-109). The guest's *own* choice is explicitly not config — it is state, and §6.1 keeps the two layers separate in the types |
| P2 — content through a service, never imported | Unchanged and extended. The guest's preference gets the same treatment: components read signals, and exactly one file knows where the value is stored (REQ-122). §1 pins the one rule that keeps it working — the gateway may never inject `PropertyContentService`, or the DI cycle returns |
| P3 — guest-sensitive data never logged | Strengthened. §6.5 chooses to log **nothing** on the write-failure path rather than invent a logging convention this project has not decided (`system-context.md` → Logging: not yet decided), and S39 asserts it by spying on `console`. §7.1 still forbids the missing-key path from interpolating content into a log line. §6.6 keeps any guest identifier from existing in the first place |
| P4 — self-hosted, offline, few dependencies | **`package.json` is not touched.** Catalogues are bundled TS; `Intl`, `matchMedia`, `sessionStorage` and `color-mix()` are platform features; CDK and `@ng-icons` are already approved. A mock-server package for the stand-in was considered and rejected without asking, because it would be a manifest edit (§6.2). The one request this feature can cause is same-origin, to the same box that served the bundle, and the UI never waits on it |
| P5 — semantic tokens only, raw values live once | Strengthened. Dark raw values become **new primitives** (§3.3), and REQ-103 removes the last primitive read in `src/` |
| P6 — tests accompany code | S27–S35 and S39, one per behaviour (REQ-124). The persistence policy is fully testable through one fake gateway, without editing the service |
| P7 — traceability | 27/27 covered, 0 orphans, verified mechanically |
| Angular strict / standalone / signals | All new state is signals. The one asynchronous boundary is a `Promise` resolved at bootstrap and a fire-and-forget write; neither introduces a subscription, an `NgZone` touch or a manual `detectChanges()` |
| Plain CSS, `rem`, no SCSS/Tailwind | Seven new tokens. Two deliberate `px`, both hairlines, both inside the constitution's own exception |
| **Nothing scrolls** | REQ-123 extends the rule to theme × language (72 combinations). Height budgets stated for all three new surfaces. Met by construction, verified by measurement, never by `overflow: hidden` |
| Approved dependencies only | No new dependency. The future HTTP gateway uses `HttpClient` from `@angular/common`, already installed |
| Vitest, no E2E suite | S36/S37 extend `tools/measure.mjs`, which reports and asserts nothing. Nothing wired into `npm test` |
| No dependency-manifest edits | None. S15 names `angular.json` as the fallback route for the CDK stylesheet and states explicitly that this is not a manifest edit |
| Branch discipline | Stays on `feature/home-screen-prototype` per the user's instruction; no agent commits |
| Secrets / input validation | §5.2 validates the property config eagerly at construction. §6.4 treats a **stored preference as untrusted input**: a value outside the configured option list is discarded, not applied. §6.7 requires the future endpoint to validate server-side as well — the client check is not the boundary once a LAN device can call the endpoint |
| No external runtime requests | REQ-106's acceptance requires that **zero requests leave the property** on a language switch, and a build-output search for any external URL. The preference write is same-origin by construction |
| Security is a review category | Assessed, and this round it is not a null answer. The feature itself adds no endpoint and stores no guest-identifying data (§6.6). It does make one inevitable: the preference endpoint will be the product's **first unauthenticated write surface on an untrusted LAN**. Impact is nuisance (a neighbour could change what the screen shows) rather than disclosure, and designing/securing it is handed to the backend feature with a written brief (§6.7) and recorded in `01-prd.md` → Risks and `docs/system-context.md` → Open security questions |
| Datastore / infrastructure stays approval-gated | **Respected.** The user decided *that* the preference is persisted server-side; **no datastore was chosen** by this feature, no schema, no endpoint. The stand-in is `sessionStorage` — chosen under the user's explicit delegation of "bis es das Backend gibt, nutze leichtgewichtige … Tools", recorded with its argument and its one-class alternative in §6.2 |
| Nested `CLAUDE.md` changes | One, approved verbatim on 2026-08-18, written by S13 and **not modified in round 2** (see the note above). No new entry is proposed |
| Design-tool requests are metered | **Zero MCP calls, in either round.** The dark palette and the settings screen are designed in-house from `docs/design/**`, and both gaps are recorded rather than queried (REQ-101, REQ-118) |

## Decisions — closed

| # | Decision | Answer (2026-08-18) | Recorded in |
|---|---|---|---|
| 1 | i18n mechanism | Hand-rolled signal service, no dependency; German + English | `01-prd.md` → Decisions taken #1; `02-design.md` §7 |
| 2 | Persistence of the guest's choice | **The database owns it**; write on change, deliver on load, defaults always from the backend; `sessionStorage` stand-in until then | `01-prd.md` → #2 + REQ-121/122/126/127; `02-design.md` §6 |
| 3 | `frontend/CLAUDE.md` amendment | Approved verbatim | `01-prd.md` → #3; `02-design.md` §4; step S13 |
| 4 | "Follow system" appearance state | Approved; startup value is the backend default (`light`), not `prefers-color-scheme` | `01-prd.md` → #4 + REQ-105; `02-design.md` §6.1; step S23 |

Nothing under `docs/constitution.md` → "Decisions Requiring Your Approval" is
open for this feature. **No constitution amendment is proposed** — see below.

### Constitution: no amendment needed (assessed, not skipped)

Three clauses were checked against the persistence answer:

- *Constraints → "No backend, auth or admin area in the first step — content
  comes from static JSON/TS files."* Still true. This feature builds a contract
  and a client-side stand-in; it does not build a backend. The clause will need
  revisiting **when the Quarkus feature lands**, not now.
- *Decisions Requiring Your Approval → "A datastore … or other
  infrastructure".* Untouched: no datastore was chosen. The gate stays armed
  for the backend feature.
- *Principle 3 / Security.* Already covered by the existing wording; §6.5 and
  §6.6 comply rather than needing new licence.

`docs/system-context.md` **does** need entries — *Persistence* and
*Messaging / async* both currently read "none" — and got "planned, not built"
notes in this round, with S26 replacing them with real ones. That file is a
map, not governance, so it needs no approval.

## Notes carried into implementation

Not gaps — decisions already made that are easy to lose between here and code:

1. **`t` must be an arrow-function class property**, not a method
   (`02-design.md` §7.1). A detached method reference throws on first call.
2. **Validate the property config in the constructor, not in the `computed()`**
   (§5.2). A throw inside a computed surfaces mid-render.
3. **`hydrate()` must never reject** (§6.4). A rejected app initializer fails
   the whole bootstrap — a preference hiccup would become a blank kiosk.
4. **`applyPropertyDefaults()` runs before `hydrate()` applies anything**
   (§6.4), or there is no option list to validate a stored value against.
5. **A late-arriving stored value is applied per setting, and only while that
   setting's override is still `null`** (§6.4). No extra "dirty" flag: non-null
   already means "a choice is in effect".
6. **The gateway is a DI leaf** and must never inject `PropertyContentService`
   (§1) — that closes the cycle the whole shape exists to avoid.
7. **The stand-in resolves through `Promise.resolve()`, never synchronously**
   (§6.2), or ordering bugs hide until HTTP arrives.
8. **Writes coalesce, they do not queue** (§6.5) — always send the current
   whole record, never a patch per event.
9. **Nothing is logged and no error UI exists on the write-failure path**
   (§6.5). S39 asserts it.
10. **The appearance trigger's glyph follows the *resolved* theme**, not the
    selected mode (§8.3).
11. **`:host { display: contents }` on the status bar** (§8.1) — exactly the
    defect class that cost four bugs last time.
12. **Do not touch `LOCALE_ID`** (§7.1). It looks like the proper fix and is a
    dead end.
13. **The clock's first tick is a `setTimeout` to the next minute boundary**
    (§8.5), not a `setInterval` from load.
14. **`--size-touch-min` renders at 35 px at the 1024 × 768 floor** (§9.1).
    Accepted consequence of the approved uncapped scaling, not a defect.

## Result

**PASS. Implementation may begin on all 39 steps; none is blocked.**

Suggested order, since the dependencies are real: S1 → S2 (tokens and the
mesh), S3 → S4 (i18n), S5 → S6 → S38 (preferences: state, then contract, then
persistence), S7 → S10 (content layer, one migration), S11 → S12, S13, then the
UI block S14 → S21, the docs block S22 → S26, and the tests S27 → S35 + S39
alongside the code they cover rather than at the end (constitution principle
6). S36/S37 close it out with the measured verification.
