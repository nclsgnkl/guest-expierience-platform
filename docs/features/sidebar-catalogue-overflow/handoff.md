# Handoff — sidebar-catalogue-overflow (Part A)

**Last session:** 2026-08-27, `reviewer` (**`06_review` round 2 — Phases 4, 5 and 6, T-10 … T-21**)
**Branch:** `refactor/bce-subpackages-and-boundary-security` (owner instruction — do not switch, **nothing committed, nothing staged**)
**Status:** **all 21 tasks implemented; `06_review` round 2 = NEEDS CHANGES ❌ — 🔴 0 · 🟠 1 · 🟡 5 · 🟢 8.** The **code is ready to commit**; three *documents* must be corrected in the same commit. Full record: [`06-review.md`](06-review.md) → "round 2".

## `06_review` round 2 — result (2026-08-27, `reviewer`)

**NEEDS CHANGES ❌ — no defect was found in shipped behaviour.** Every behavioural
requirement in this cut (REQ-912 … REQ-935, 24/24) was **driven in a real
browser**, not read — including the two no unit test can reach: REQ-930's
poll-drop (driven against `PUT /api/manager/menu-config`, promotion dropped
within 30 s) and REQ-929's survival across a reload. The mandatory visual check
(`code-review` step 0) **was performed** and the popover was screenshotted in
both themes and both languages at both constitutional viewports.

**Reviewer's own numbers.** Frontend **298/298 in 33 files** and `ng build` clean
(`main` 457.69 kB / `styles` 9.10 kB) — identical to dev's. `node
tools/measure.mjs` **168 cells · 0 overflowing · 0 truncated · 0 findings**, panel
**376×160 / 470×200**, tiles 67/84 px vs `--size-touch-min` 35.2/44 px, 9/9 glyphs
distinct, document never scrolls, Home's baselines `heading.y 88` /
`cardRow.bottom 744` unmoved — all identical to dev's. Backend `./mvnw verify`
**425 · 0 failures · 4 errors · 3 skipped**: **zero assertions failed**, and all
four errors are the established Testcontainers container-startup timeouts on
`@TestProfile` classes. `git diff HEAD -- backend/ packages/ api/` and every
dependency manifest: **empty**. Prettier clean over every changed file.

| # | Sev | What | Where |
|---|---|---|---|
| 1 | 🟠 | **The prescribed isolation flag aborts the whole suite before a single test runs** — `-Djunit.jupiter.testclass.order.default=…$Random` → *"corrupted application errors … the Quarkus JUnit configuration has been overridden"*, `Tests run: 0`, `BUILD FAILURE`. It is round 1's own text. dev's replacement works but **defeats Quarkus' profile grouping** (measured). | `03-todo.md` T-03, T-20 |
| 2 | 🟡 | V10's header names three sanctioned fixture sites; `measure.mjs` is the fourth. **REQ-903 itself is not breached** — only the sentence is wrong. | `V10__catalogue_expansion.sql` |
| 3 | 🟡 | The panel-width table is 2 px out — the spec quotes the padding box, the browser reports the border box. **The CSS is right.** | `02-design.md` §7.4 · `menu-overflow.md` |
| 4 | 🟡 | `SecondFactorLoginTest`'s randomized-order failure — **tracked, not absorbed**, and it is *not* an isolation defect. | test suite, not this feature |
| 5 | 🟡 | From the visual check: the action-card icons read through the glass panel; dark-mode label contrast bottoms out at **4.53** (AA floor is 4.5). Violates nothing; no code change needed to ship. | `sidebar.component.css` |
| 6–13 | 🟢 | Prose mentioning a fixture in `registered-icons.ts`; a benign Prettier drive-by in `en.ts`; `chosen` ignored rather than cleared; a duplicated route normalisation; a 5th raw motion literal; the glyph detector covers popover tiles only; `measure.mjs` crashes instead of `note()`-ing on one timeout; `index.md` states 436 · 0 · 0 · 0 as a baseline. | — |

**Rulings on dev's four open items** (full reasoning and exact replacement text in
`06-review.md` → round 2):

1. **`SecondFactorLoginTest`** — dev's reasoning **verified and extended**. Zero
   backend files changed; 6/6 in isolation; `203.0.113.10x` appears in that one
   file only across the whole test tree, so no cross-test coupling exists; the
   200-instead-of-429 symptom is only reachable if the class ran against a
   non-`TrustedProxyProfile` application. **Not reproducible** in 5-class subsets
   with the profiled class first or last (both green). **Track it against the
   ordering mechanism, not against the test or this feature.** Does not block.
2. **The isolation flag** — dev is right; the plan is wrong. **Correct
   `03-todo.md` T-03 and T-20**; exact text is in the review. The rule: randomize
   the classes that share a database, never across a profile boundary.
3. **V10's header** — **do not edit the migration.** REQ-903 is not breached, and
   the checksum cost buys a comment on an already-committed migration. **Add one
   clause to `docs/system-context.md`** instead; exact text is in the review.
   dev's header wording is good and should be applied only if the owner prefers
   to carry the checksum change — in which case, in this commit.
4. **The 2 px** — **the spec is wrong, the CSS is right.** `--border-glass` is
   `1px solid`; 29.25rem is the padding box; 374.4 + 2 = 376 and 468 + 2 = 470,
   both measured. A hairline can never appear in a rem figure (the constitution
   exempts it), so the table must say which box it quotes. Exact replacement
   table is in the review.

**All six declared deviations ACCEPTED**, two of them with a plan correction
attached (T-13's fixture-label parenthetical, which repeats the trap round 1
closed in T-03) and one reclassified as not a deviation at all (T-21's folding of
the `system-context.md` rows was required by design §11).

### Next action

1. Apply **🟠 1** (`03-todo.md` T-03 + T-20), **🟡 2** (one clause in
   `docs/system-context.md`), **🟡 3** (two tables) and deviation 1's T-13
   correction — all four have exact wording written out in `06-review.md`. No
   judgement required, no code touched.
2. **The user commits** — one commit, staged **explicitly** (the tree also carries
   `docs/features/index.md`, which Part B/C planning touches). **Do not stage
   `packages/api-client/.openapi-generator/openapi.yaml-null.sha256`** — it is
   recreated by **every** `quarkus:dev` run (the reviewer's run recreated it and
   deleted it again, byte-identical to the real hash file). A `.gitignore` entry
   is the owner's call.
3. 🟡 4 goes on the tracked-items list; 🟡 5 is the owner's; the 🟢s are
   deferrable.

**Nothing needs `architect`**: no architecture, auth, concurrency, external
integration or exposure-model change. No new dependency, no new pattern, no
nested `CLAUDE.md` entry, no constitution amendment.

---

## Previous session (05_implement, Phases 4–6) — `dev`, 2026-08-27

**Status at the time:** **all 21 tasks are implemented.** T-01…T-09 are committed (`33f745a`); T-10…T-21 are in the working tree. **Ready for `06_review`.**

## What landed this session

| Task | Change | Verification actually run |
|---|---|---|
| T-10 | Three keys in both catalogues — `nav.more` / `nav.more.aria` / `section.unavailable`, wording verbatim from design §8 and the screen spec's Content table. | `ng build` clean. **Probed the guard:** deleting `'nav.more.aria'` from `en.ts` fails the build with `TS2741: Property '"nav.more.aria"' is missing … but required in type 'TranslationCatalog'`. Restored. |
| T-11 | `app.routes.ts` — the four hard-coded section children and their `data: { sectionId }` replaced by `{ path: ':sectionKey' }` **after** `einstellungen`, plus `{ path: '**' }` as the shell's **last child**. The three state routes, their guards and `shellGuard` untouched. | Whole frontend suite green with the new table (256/256 at that point). |
| T-12 | `SectionPlaceholderComponent` resolves from the URL: `toSignal(route.url)` (not `snapshot`) → `'/' + segments.join('/')` → `itemForRoute()` against `menuItems + settings`, matched **by `route`**. Unclaimed route renders `t('section.unavailable')` with no note and no redirect. | Spec rewritten: 8 cases (known route, second route, settings, Home `''`↔`/`, unclaimed, deep path, param change under a reused instance, claimed→unclaimed). 284/284. |
| T-13 | `layout/sidebar/menu-split.ts` — `SIDEBAR_SLOTS = 5`, `routeMatches`, `itemForRoute`, `splitMenu`, `sortByLabel`, `collatorFor`. Design §6.1's five-line expression implemented as written, not branched. | `menu-split.spec.ts`, 23 cases. **Mutation-probed twice:** dropping `numeric: true` fails 1 test; replacing `collator.compare` with code-point compare fails 2. Both reverted. |
| T-14 | `SidebarComponent`: `locale` input, `chosen = signal<string \| null>(null)`, one `split` computed, `isActive()` rewritten onto `routeMatches()`, `choose()`. `ShellComponent`: `locale = this.content.locale` + `[locale]="locale()"`. | `ng build` clean; 286/286. |
| T-15 | The popover inside `SidebarComponent` (no new component): the trigger `.sidebar-item` with `phosphorDotsThree` + `t('nav.more')` behind `@if (split().hasOverflow)`, the `<ng-template>` `cdkMenu` panel with `[attr.aria-label]="t('nav.more.aria')"`, one `cdkMenuItem` tile per overflow entry with `[cdkMenuitemTypeaheadLabel]` and `(cdkMenuItemTriggered)`. Design §7.2's three `ConnectedPosition`s in order, no pixel offsets. No `aria-label` on the trigger, no active variant on a tile. | Verified **live in Chromium**, not by eye — see "Live interaction check" below. |
| T-16 | `sidebar.component.css` — panel: glass recipe, `padding: var(--space-4)`, `margin-left: var(--space-4)`, `max-height: calc(100dvh - 2 * var(--space-6))`; grid: `repeat(5, calc(var(--size-sidebar) - 2 * var(--space-3)))`, `gap: var(--space-2)`, `overflow-y: auto`; tile: `aspect-ratio: 1`, 2-line clamped label, the sidebar item's focus ring. Semantic tokens only, no entry animation. | T-19's browser run. |
| T-17 | `sidebar.component.spec.ts` +12 cases (1 in the original describe, 11 in a new `SidebarComponent — overflow` describe). | 298/298. |
| T-18 | `measure.mjs`: `ROUTES` 6 → 14 with the "must track the `%dev` catalogue" comment; a new **PASS 3B** that opens the popover and reports the trigger box, the panel box/role/aria-label/`max-height`, the computed `grid-template-columns`, whether the grid scrolls internally, every tile box against a live `--size-touch-min`, label lines + clamp, the glyph each tile really rendered, and the **document's** scroll dimensions while open. Still reports only. | Prettier-clean, `node --check` clean, ran. |
| T-19 | Measurement. | **168/168 cells, 0 overflowing, 0 truncated, 0 findings.** Full numbers below. |
| T-20 | Whole-suite verification. | Backend run 1 **436 · 0 failures · 0 errors · 0 skipped, BUILD SUCCESS**; frontend **298/298 in 33 files**; `ng build` clean; run 2 in a randomized class order — **one unrelated auth failure, see the open item**. |
| T-21 | `docs/system-context.md`: the four "planned / implementation-pending" rows **folded into the real-code tables** (`SidebarComponent`, the new `menu-split.ts` row, `SectionPlaceholderComponent`, `PropertyContentService.locale`), the pending block replaced by an "Added by … (2026-08-27)" record keeping the binding facts, the whole-set-replace limitation and the `role="menu"` keyboard rationale. `docs/features/index.md` row flipped `Not Started` → `In Review`, phase → dev → reviewer with the measured numbers. REQ-903 grep run. | Grep results below. |

## Measurement — `node tools/measure.mjs`, 2026-08-27

Backend `quarkus:dev` on a **fresh** Dev Services Postgres (V10 applied,
`GET /api/guest/session` → **13 menu items**, routes `/wlan … /aktivitaeten`
exactly as `ROUTES` lists them); `ng serve` on 4300.

**PASS 1–4: 168 route × theme × language × viewport combinations, 0 overflowing,
0 truncated strings, 0 findings.** Home's signed-off baselines are unmoved in all
four 1280×832 cells: **heading.y 88 vs 88, cardRow.bottom 744 vs 744**.

**PASS 3B — the popover open**, identical in all four cells per viewport:

| | 1024×768 (root 12.8 px) | 1280×832 (root 16 px) |
|---|---|---|
| trigger | `{x:32, y:474, w:67, h:67}` | `{x:40, y:528, w:84, h:84}` |
| panel | `{x:109, y:427, w:376, h:160}` | `{x:136, y:470, w:470, h:200}` |
| `grid-template-columns` | 5 × 67.1875 px | 5 × 84 px |
| gap to trigger (`panel.x - trigger.right`) | 10 px = `--space-4` | 12 px = `--space-4` |
| vertical centre offset vs. trigger | −0.5 px | 0 px |
| clear of the sidebar (`panel.x - sidebar.right`) | 3 px | 4 px |
| tiles | 9 × 67×67, smallest 67 px ≥ **35.2 px** `--size-touch-min` | 9 × 84×84, smallest 84 px ≥ **44 px** |
| labels | 9, longest 2 lines, **none clamped**, none truncated | same |
| glyphs | **9/9 rendered, none fell back to `phosphorCircle`** | same |
| `role` / `aria-label` | `menu` / "Weitere Bereiche" · "More sections" | same |
| overlay | `onBody=true`, `inShell=false`, `withinViewport=true` | same |
| document scroll | 1024/1024 · 768/768 **NO-SCROLL OK** | 1280/1280 · 832/832 **NO-SCROLL OK** |

**Panel width vs. the spec.** Design §7.4 and `menu-overflow.md` predict **374 px**
at 1024×768; the render is **376**. The 1.6 px is exactly the glass border —
`--border-glass` is `1px solid`, and the spec's arithmetic
(`2 × 0.75 + 5 × 5.25 + 4 × 0.375 = 29.25rem = 374.4 px`) is the **content box**,
not the border box. Confirmed at the other viewport: 29.25 × 16 = 468 + 2 = **470**,
measured 470. **Not a CSS departure** — a two-pixel omission in the spec's table.
Recorded rather than silently "fixed"; if the owner wants the table to state
border-box widths they read 376 px / 470 px.

**Internal scroll (REQ-917) — probed, because the `%dev` set never reaches it.**
Nine tiles fill 2 rows and `gridScrolls` is `false` everywhere. Forcing
`max-height: 120px` on the panel in the live page: grid `scrollHeight 174 >
clientHeight 94`, `overflow-y: auto`, and the **document is still 1280/1280 ·
832/832** in both themes. The panel scrolls itself; the screen never does.

**Screenshots** (panel open, 1280×832, both themes) reviewed:
`/tmp/claude-1000/popover-light.png`, `/tmp/claude-1000/popover-dark.png`.
Dark mode is inherited with no theme-specific rule, as the spec claims.

## Live interaction check (T-15, driven in Chromium — not "by hand")

```
slots closed:            [Home, Info, Umgebung, Smart Home, Mehr]
aria-expanded            false → true on open, back to false on close
focus on open            first tile (Aktivitäten)
ArrowDown                → next tile in reading order (Anreise & Abreise)
ArrowRight / ArrowLeft   → unchanged  (inert, deliberately — role="menu")
End / Home               → last tile (WLAN) / first tile
typeahead p / w / s      → Parken / WLAN / Services   (needs the CDK's ~200 ms debounce)
Escape                   → panel gone, focus back on the trigger ("Mehr")
outside click            → panel gone
choose "Parken"          → url /parken, slots [Home, Info, Umgebung, Parken, Mehr], active = Parken
then click slot 2        → slots UNCHANGED, active = Info   (REQ-928: promotion holds slot 4)
localStorage after       → ['gxp.cache.home', 'gxp.cache.session'] — nothing added
deep link /gastronomie   → slots [Home, Info, Umgebung, Essen & Trinken, Mehr], active = Essen & Trinken (REQ-932)
/gibt-es-nicht           → "Dieser Bereich ist nicht verfügbar", no note, url kept, 1280/1280 832/832
```

## REQ-903 disposability grep (T-21)

- **All 8 keys:** hits only in `V10__catalogue_expansion.sql`, `DevDataSeeder`,
  `MenuFixtures` and `measure.mjs`'s `ROUTES` — the four sanctioned sites. Clean.
- **All 16 labels:** the only hits outside `V10` are **prose, not dependencies** —
  `DevDataSeeder`'s pre-existing action-card copy ("Hausregeln, WLAN, Parken &
  mehr", a sanctioned file anyway) and a **comment** in
  `registered-icons.ts` explaining why `phosphorWifiSlash` is not reused. That
  file is the second half of REQ-903's own removal path, and the comment is not
  a dependency. No `.ts`/`.html`/`.css` or test file depends on a fixture key or
  label.
- **Nothing this session introduced one.** `menu-split.spec.ts`,
  `sidebar.component.spec.ts`, `section-placeholder.component.spec.ts` and
  `app.routes.spec.ts` use invented keys and labels throughout.
- **The five catalogue-count assertions still exist** where the migration header
  claims: `CatalogueExpansionTest:81` and `:160`,
  `MenuCatalogueAndConfigTest:59`, `FirstRunSeedTest:59`, `DevDataSeederTest:85`.

## Open items for the reviewer

1. **`SecondFactorLoginTest.aWrongCodeIsTheGenericFailureAndCountsAgainstTheAddress`
   fails under a randomized class order — unrelated to this feature, not fixed.**
   Run 2 (`-Djunit.quarkus.orderer.secondary-orderer=…$Random`,
   `-Djunit.jupiter.execution.order.random.seed=20260827`): **436 run · 1 failure ·
   1 error · 1 skipped**. Expected 429, got 200 at line 169 — the address's
   throttle bucket held fewer than 5 failures, i.e. the class did not get the
   application its `@TestProfile` describes. Evidence it is not ours: **this cut
   changes zero backend files** (`git status --porcelain backend/` is empty); run 1
   in the default order is **436 · 0 · 0 · 0 BUILD SUCCESS**; and the class is
   **6/6 green in isolation, BUILD SUCCESS**. Reported, not chased — auth and
   throttling are outside this feature's surface. The 1 error is the established
   Testcontainers container-startup timeout (`UntrustedPeerAttributionTest`).
2. **T-20's isolation mechanism needs the same correction 🟠 3 gave T-03.**
   `-Djunit.jupiter.testclass.order.default=…$Random` — which the fix round
   prescribed after `-Dsurefire.runOrder` proved inert — **aborts a full
   `./mvnw verify`** in 2:30 with *"Critical failure. Quarkus tests would fail
   with corrupted application errors … the Quarkus JUnit configuration has been
   overridden."* It worked in the fix round only because that was a 9-class
   subset. Quarkus names the supported knob itself:
   **`-Djunit.quarkus.orderer.secondary-orderer=org.junit.jupiter.api.ClassOrderer$Random`**,
   which does randomize (run 2's order starts
   `PairingCaTest → BoundarySecuritySemanticsTest → AccountAuditTest →
   FirstRunSeedTest → SecondFactorTest → …` against run 1's alphabetical
   `AssetDeleteGuardTest → AssetResourceTest → AuditCoverageTest → …`).
   `03-todo.md` T-03/T-20 still prescribe the flag that aborts — **not edited**,
   because correcting a plan document is the reviewer's or the owner's call.
3. **Untracked build artifact, not mine to delete:**
   `packages/api-client/.openapi-generator/openapi.yaml-null.sha256`, created by
   my `quarkus:dev` run (group `docker`, 12:51). Same content as the real
   `openapi.yaml-generate-ts-client.sha256`. **It must not be staged.** Deleting
   it needs a word from the owner (guardrail: no deletions without confirmation).
4. **`V10`'s header names only three sanctioned sites**, but `measure.mjs`'s
   `ROUTES` list is now the fourth. I wrote the correction and then **reverted
   it**: editing an applied Flyway migration changes its checksum and breaks
   validation for anyone holding a dev database with V10. Proposed wording, for
   the owner to apply if they judge the checksum change acceptable:
   > `-- is that no fixture KEY or LABEL appears outside this file, DevDataSeeder,`
   > `-- MenuFixtures and frontend/tools/measure.mjs's ROUTES list (the review`
   > `-- instrument walks every route the %dev catalogue can name — added with the`
   > `-- popover, and the fourth and last sanctioned site). Four test classes`
   `measure.mjs`'s own comment already records the obligation, so nothing is
   undocumented meanwhile.

## Deviations from the plan

1. **T-13 landed before T-12**, as the sequencing note allowed. Reason: `T-12`
   needs `routeMatches()`, and introducing it in `section-placeholder` and then
   moving it would have been churn in a file `menu-split.spec.ts` already covers.
2. **T-13's spec does not use the label `Ärztliche Versorgung`.** The TODO's
   parenthetical names it verbatim from the PRD's REQ-924/925 acceptance
   criterion — but it is a **fixture label**, and putting it in a spec is exactly
   the REQ-903 violation T-21 greps for, the same trap the fix round corrected in
   T-03. The spec proves the same property with `Öffnungszeiten` / `Services` /
   `Umgebung` (only the first is invented; the other two are original sections):
   German collation gives `Öffnungszeiten, Services, Umgebung`, code point would
   give `Services, Umgebung, Öffnungszeiten`.
3. **T-12 also touched `app.routes.spec.ts`** (4 files, under the cap). Its
   section assertions read `el.textContent` on the whole shell, and the sidebar
   renders the same labels — so they would have passed even with the placeholder
   resolving nothing. They are now scoped to `app-section-placeholder`, plus two
   new cases for `:sectionKey` and the `**` child.
4. **T-14 also touched `sidebar.component.spec.ts`** (5 files, at the cap): the
   new required `locale` input made all 7 existing cases throw NG0950.
5. **T-18's fallback-glyph detector had to be tightened after its first run.**
   Comparing a 25-character prefix of the tile's first `<path d>` against
   `phosphorCircle` produced **8 false findings**: `phosphorClock` is a clock
   face, so its first path *is* the identical 104-radius circle. It now compares
   the **whole** `d` **and** requires exactly one path. **Mutation-probed**:
   deleting `phosphorFirstAid` from `REGISTERED_ICONS` makes the tile fall back
   and the finding fire; restored, `registered-icons.ts` is byte-identical to
   `33f745a`.
6. **One `LOGO DID NOT DECODE` finding in the first full run was a timing flake**
   — `complete:false, naturalWidth:0` in 1 of 168 cells (`/muell` dark/de at
   1024×768). The asset serves 200 / `image/svg+xml` / 562 B; re-measuring that
   exact cell is clean, and the final full run reports **0 findings**.
7. **T-21 folded the system-context rows rather than only confirming them**,
   per design §11's *"T-21 folds these into the real-code tables once `dev` has
   landed them"*.

## Nothing from the "Not in this feature" list came up

No `GuestModule` union or module registry was needed; `guest-data.service.ts`,
the two guest fetches and the ETag are untouched; `introduced_release` stays
inert; no `Router.resetConfig()`; no `parent_key`. **No dependency was added**
(`package.json`/`package-lock.json`/`pom.xml` byte-identical — the popover is the
already-present `@angular/cdk/menu`), **no nested `CLAUDE.md` entry is proposed**
and **no constitution amendment is proposed**: every convention this cut used
already exists — `menu-split.ts` follows the `resolveProperty` pure-function
precedent named in `frontend/CLAUDE.md` → Tests, the popover follows
`StatusBarMenuComponent`, and design §4.1 already re-checked that a parameterised
child route is not a new pattern.

## What must happen next

1. **`06_review`.**
2. **The user commits** — one commit, staged **explicitly**: the tree also
   carries the concurrent planner's Part B/C work. Do **not** stage
   `packages/api-client/.openapi-generator/openapi.yaml-null.sha256`.
3. Settle open items 1–4 above.

---

## Previous session (05_implement fix round, 2026-08-27) — kept for reference

## Fix round — 2026-08-27 (`dev`), all five items closed

| # | What changed | Verification actually run |
|---|---|---|
| 🟠 1 | **The kiosk-side menu bound is restored at 50** — `MAX_MENU_ITEMS = 50` in `guest-content.mapper.ts`, written as the exact sibling of `MAX_CARDS = 4`: named constant, length check, `GuestContentMappingError`, message `menu.items exceeds the contract's 0–50`, which `api/openapi.yaml` now really declares. **Lead decision** under the autonomy grant (`docs/handoff.md` → *"Decisions the lead took"*); the owner may reverse it. The constant's doc comment now explains the corrected history (the old `= 5` invented a bound and lied about its source) instead of describing the constant as gone. `02-design.md` §2/§5/§9 and `03-todo.md` T-08 corrected to match. | `guest-content.mapper.spec.ts` gained *"maps exactly maxItems menu items and rejects one more"* — 50 map, 51 throws against the `0–50` message. **Frontend: 256/256, 32 files** (was 255). `ng build` clean, `main` 453.07 kB. `grep -rn "0–5" frontend/src` → one hit, the `0–50` regex itself. |
| 🟠 2 | **`DevDataSeederTest.reseedingIsAFullNoOp` can now fail for its stated reason.** It turns one property-scope row **off** before re-seeding — chosen key-free via `min(id)`, so REQ-903 holds — asserts the arrangement took, then asserts the count is still `enabledConfigs - 1` afterwards, and restores the exact row in a `finally` so the other case in the class still sees 13 whichever order the two run in. The old version snapshotted `enabled = true` *after* the first seed had set all eight true, against an idempotent `UPDATE`. | Ran the class: **2 run · 0 failures · BUILD SUCCESS**. Then **mutation-probed** it — moved `enableCatalogueFixtures()` above `seedIfMissing()`'s `devDeviceExists()` early return and re-ran: **1 failure**, `re-seeding must not re-enable what a developer turned off … expected: <7> but was: <8>`. Probe reverted; `DevDataSeeder.java` is byte-identical to before it. |
| 🟠 3 | **The isolation proof is replaced with a mechanism that works.** `-Dsurefire.runOrder=reversealphabetical` is inert here (Quarkus' profile-aware orderer overrides Surefire's; the reviewer measured a byte-identical 76-class order). `03-todo.md` T-03 and T-20 now prescribe `-Djunit.jupiter.testclass.order.default='org.junit.jupiter.api.ClassOrderer$Random'` with a **recorded seed**, and say in as many words that `-Dsurefire.runOrder` must not be used. | Two runs of the 9-class menu subset, orders extracted from the `Running de.gxp.*` lines. **Seed `7`** — `AuditCoverageTest (W) → ActionCardTest (W) → CatalogueExpansionTest → MenuCatalogueAndConfigTest (W) → MenuDisableGuardTest (W) → MigrationTest → GuestSessionTest (victim, pos. 7) → GuestHomeScreenTest → FirstRunSeedTest`: **48 run · 0 failures**. **Seed `20260827`** — a genuinely different order (`ActionCardTest (W) → GuestSessionTest (pos. 2) → FirstRunSeedTest → MenuCatalogueAndConfigTest → MigrationTest → AuditCoverageTest → GuestHomeScreenTest → CatalogueExpansionTest → MenuDisableGuardTest`): **48 run · 0 failures**. Two different orders from two seeds is itself the proof the orderer is not inert. |
| 🟡 4 | `V10__catalogue_expansion.sql`'s *"AND NOTHING ELSE"* is reworded to the property that is actually greppable — no fixture **key or label** outside `V10`, `DevDataSeeder`, `MenuFixtures` — plus the five catalogue-count assertions across four test classes that an add/remove also touches. T-21's grep step carries the same correction. | Re-grepped the count sites: `CatalogueExpansionTest:81` and `:160`, `MenuCatalogueAndConfigTest:59`, `FirstRunSeedTest:59`, `DevDataSeederTest:82` — five, exactly as the header now claims. |
| 🟡 5 | `property-content.service.ts`'s class doc now reads *"`settingsConfig()`/`property()`/`locale()` throw when nothing is loaded"* — `locale` calls `requireConfig(this.raw())` at line 81. | Comment only; covered by the 256/256 run. |
| 🟡 6 | No action, as ruled. `%dev` is still not runnable end-to-end until Phase 4 — see the last item under *What must happen next*. | — |

**Also corrected, per the reviewer's ruling on deviation 2:** `03-todo.md` T-03's
`FirstRunSeedTest` bullet and `02-design.md` §9's `FirstRunSeedTest` row now say
the five-key list is **narrowed to `key[0..4]`, never widened to thirteen** —
enumerating the fixture keys is what REQ-903 forbids and what T-21 greps for.
`hasSize(13)` + `key[0..4]` as implemented stands. T-20 additionally absorbs
finding 7's wording fix (the `.sha256` input hash is expected to change).

**Not touched, deliberately:** `pom.xml`, `package.json`, any nested `CLAUDE.md`,
`docs/constitution.md`, `docs/handoff.md`, and the Part B/C planning documents
(`docs/features/guest-payload-consolidation/**`, `docs/features/module-content/**`,
`docs/features/index.md`).

## Review result — 06_review, 2026-08-27 (`reviewer`)

**No defect was found in shipped behaviour.** All three 🟠 are *evidence*
problems, and all three must be settled before the single commit:

| # | Sev | What |
|---|---|---|
| 1 | 🟠 | **REQ-901's acceptance criterion is unmet — `maxItems: 50` is enforced nowhere.** Response DTOs are not runtime-validated (the generated `@Size(max=50)` is inert), and T-08 removed the only kiosk-side check. Design §2 says *"the enforcement that matters is the kiosk's, at the boundary (§5)"* and §5 then deletes it; §5's reason (*"trusted for count the same way `actionCards` is"*) is factually wrong — `MAX_CARDS = 4` still rejects. **Owner decision:** restore the guard at 50 (~5 lines, makes the requirement true) **or** strike the criterion and fix design §2/§5. Leaving both as they are is the one option that is not acceptable. |
| 2 | 🟠 | **`DevDataSeederTest.reseedingIsAFullNoOp`'s new assertion cannot fail for the reason its message states.** It snapshots `enabled = true` *after* the first seed already set all eight true; the `UPDATE` is idempotent, so the count is unchanged either way. The "inside the `devDeviceExists()` early return" half of REQ-906 is therefore **unverified** — and this test was offered in place of T-05's manual `quarkus:dev` run. The behaviour is correct (verified by reading `seedIfMissing()`); the test is not. Fix: turn one row back **off** before re-seeding, assert it stayed off (key-free via `min(id)`). |
| 3 | 🟠 | **T-03's prescribed isolation proof is inert.** `-Dsurefire.runOrder=reversealphabetical` produced a **byte-identical 76-class order** (measured — `diff` of both runs' class sequences is empty); Quarkus' profile-aware orderer overrides Surefire's. *"Reverse class order: 0 failures"* was therefore not evidence. **The fix itself is correct** — proved with `-Djunit.jupiter.testclass.order.default='org.junit.jupiter.api.ClassOrderer$Random'`. Correct T-03's and T-20's validation lines, or every future feature inherits a check that always passes. |

🟡: the migration header's *"and nothing else"* removal claim omits four
catalogue-count assertions; `PropertyContentService`'s class doc does not list
`locale()` among the throwing signals; and **this cut is not runnable in `%dev`**
— 13 menu items against a router that still knows four section paths (Phase 4
fixes it; `%test`/`%prod` unaffected because D5 keeps the fixtures disabled).

🟢: T-01's *"zero diff"* wording is at fault, not the outcome; the tree also
carries Part B/C work, so stage explicitly at T-21 rather than `git commit -a`.

### Rulings on the five declared deviations

1. **Four tests changed, not two — accepted.** Both extras are right, and dev's
   *"no guest-facing assertion changed"* is **TRUE, verified line by line**: all
   four changed assertions hit `/api/manager/**` or `flyway_schema_history`.
2. **`FirstRunSeedTest` — dev is RIGHT, the plan is wrong.** `hasSize(13)` +
   `key[0..4]` is correct. Listing thirteen keys would have put eight fixture
   keys in a test file — exactly what REQ-903 forbids and T-21 greps for, in the
   same feature. **`03-todo.md` T-03 and design §9 need correcting**; exact
   proposed wording is in `06-review.md` (not written — `dev` or the owner
   applies it).
3. Two sub-steps — accepted, no comment.
4. Test instead of `quarkus:dev` — **accepted in part.** The positive half is
   better pinned than the plan asked. The early-return half is not — see 🟠 2.
5. The `.sha256` file — **the TODO's wording is at fault.** No generated source
   moved; verified independently.

### Measured independently (not taken from the report below)

- **Backend `./mvnw verify`: 436 run · 0 failures · 2 errors · 2 skipped.**
- **Backend, reverse flag: 436 · 0 failures · 2 errors** — and a **byte-identical
  class order**, which is finding 3.
- **The 2 errors are environmental — confirmed, with evidence.** Testcontainers
  container-startup timeouts thrown at `QuarkusTestExtension`
  `interceptTestClassConstructor` (`0.006 s` elapsed), only ever on
  `@TestProfile` classes, and they **move**: four observations have now hit four
  different classes (`TrustedProxyLoginTest`, `UntrustedPeerAttributionTest`,
  `DevDataSeederTest`, `OpenApiDisabledTest`). Both classes that errored pass
  **2/2 in isolation, BUILD SUCCESS**. The box: aarch64 Pi, 4 cores, 3796 MB RAM
  with 1.6 GB already swapped, six unrelated containers, Quarkus boot **155.8 s**,
  an observed *successful* container start **23.8 s**. Not a blocker.
- **Test isolation proved another way.** `ClassOrderer$Random` seed `7` put all
  four whole-set writers ahead of `GuestSessionTest`
  (`AuditCoverageTest → ActionCardTest → CatalogueExpansionTest →
  MenuCatalogueAndConfigTest → MenuDisableGuardTest → MigrationTest →
  GuestSessionTest → …`): **48 run · 0 failures · BUILD SUCCESS**, with
  `hasSize(5)`/`hasSize(4)` intact. Seed `424242` gave a second adverse order,
  also green.
- **D5 verified by resolution:** `CatalogueExpansionTest` 5/5 in all five backend
  runs; `%dev` delivers 13, `%test` delivers 5.
- **REQ-903 grep clean:** all 8 keys and all 16 labels across the whole tree —
  code hits only in the three sanctioned files.
- **REQ-904 verified at the data level:** all 8 icons resolve in the installed
  `@ng-icons/phosphor-icons` 35.0.1, all SVGs distinct, none equal to
  `phosphorCircle`.
- **Frontend: `ng test` 255/255, 32 files. `ng build` clean.**
- **Tree is whole after the `git stash`/`pop`:** `git stash list` empty; the
  dangling stash commit has two parents (no untracked file was ever stashed);
  all 19 tracked files it carried are byte-identical to the tree today except
  `docs/features/index.md`, which the concurrent `planner` is editing.

### Not owed, and why — the visual check

`code-review` step 0 requires a rendered screenshot for any change touching a
`docs/design/**`-backed component. **This cut contains no UI.** No sidebar,
popover, route or template changed; the eight catalogue rows arrive
property-disabled in `%test`/`%prod`; `registered-icons.ts` adds map entries
nothing yet renders. The screenshot obligation attaches to T-15/T-16 and is
discharged by T-19 — recorded so this is not read as a precedent for passing a UI
change without looking at it.

## What `dev` reported last session (05_implement) — kept for reference

## What landed

| Task | Change |
|---|---|
| T-01 | `api/openapi.yaml` — `GuestMenu.items.maxItems: 50`. Generated `GuestMenu` carries `@Size(max=50)`; `packages/api-client/` shows only its input-hash file, and regeneration is idempotent. |
| T-02 | `V10__catalogue_expansion.sql` — 8 definitions (`default_sort_index` 5…12, `introduced_release '1.1.0'`), 16 `de`/`en` translations, 8 property-scope `enabled = false` rows. Header states disposability, the one-migration-plus-one-icon-file removal path, and why statement 3 exists. |
| T-03 | `MenuFixtures.restoreCatalogueOptOut()` (test scope, `DeviceFixtures` precedent) + `@AfterEach` in the five writer classes; the two planned assertion amendments **plus two unplanned ones** (see Deviations). |
| T-04 | `CatalogueExpansionTest` — 5 cases, `@BeforeEach` arranges the D5 state. **Names no fixture key**: the key under test is derived from the catalogue. |
| T-05 | `DevDataSeeder.enableCatalogueFixtures()` inside the `devDeviceExists()` early return + `CATALOGUE_FIXTURE_KEYS`. `DevDataSeederTest` now proves REQ-906 against a real dev-profile database. |
| T-06 | Verification only. No Control added; five architecture guards green **and unedited**; `AuditCoverageTest` green with its assertions and `NOT_A_MANAGER_MUTATION` list untouched. |
| T-07 | Eight glyphs registered (`phosphorWifiHigh`, `Clock`, `Recycle`, `FirstAid`, `Car`, `ForkKnife`, `PersonSimpleHike`, `DotsThree`), all verified present in the installed `@ng-icons/phosphor-icons/regular`. |
| T-08 | `MAX_MENU_ITEMS`, its check and its false attribution deleted; `property.model.ts` comments corrected. `grep -r "0–5" frontend/src` is clean. |
| T-09 | `PropertyContentService.locale` — one `computed`, three spec cases. The two inline lookups deliberately not refactored (recorded follow-up). |

## Test results actually observed

- **Backend, final full `./mvnw verify`: 436 run, 0 failures, 1 error.** The
  error is a Testcontainers *container startup timeout*
  (`UntrustedPeerAttributionTest`, a `@TestProfile` class that needs its own
  Postgres); it passes **2/2 in isolation**. This box flakes this way on
  `@TestProfile` classes under load — the **pre-change baseline run flaked
  identically** (`TrustedProxyLoginTest`, also green in isolation).
- ~~**Reverse class order** (`-Dsurefire.runOrder=reversealphabetical`):
  **0 failures.**~~ **Withdrawn** — that flag is inert here (review 🟠 3): Quarkus'
  profile-aware orderer overrides Surefire's and the class order came out
  byte-identical, so the run proved nothing. The property *does* hold; it is
  proved with `ClassOrderer$Random` and a recorded seed — see the fix round at
  the top of this file.
- 420 → 436 is fully accounted for: `CatalogueExpansionTest` +5, plus 11 tests
  the *baseline* run under-counted because two classes reported a boot error
  instead of their methods. **No existing test was lost.**
- **Frontend: `ng test` 255/255, 32 files** (baseline 251; +1 T-08, +3 T-09).
  `ng build` clean.

## Deviations from the plan — read these before reviewing

1. **The plan said "exactly two existing tests change". It is four.** The two
   extra are real invalidations the plan missed, both manager-scope, **no
   guest-facing assertion changed anywhere**:
   - `MigrationTest.flywayHeadIsV9UpdateChannelTrustedKeys` pins the Flyway
     head as a **literal** so a migration cannot be added silently. Renamed to
     `flywayHeadIsV10CatalogueExpansion`, head `"9"` → `"10"`. Updating it is
     the conscious step the pin exists to force. Nothing else in the class
     changed; what V10 inserts is asserted in `CatalogueExpansionTest`, not
     here (shared DB, no `@AfterEach`).
   - `MenuDisableGuardTest` twice asserted `GET /api/manager/menu-config`
     returns `hasSize(0)` to mean *"the rejected PUT changed nothing"*. The
     property scope now legitimately holds D5's eight opt-out rows. Changed to
     `findAll { it.menuItemKey == '<key under test>' }` → `hasSize(0)`, which
     states the same atomicity, names the key actually under test, and depends
     on no fixture key.
2. **`FirstRunSeedTest` step 3 does not list the eight fixture keys.** The
   plan's wording ("its exact five-key list → the 13-row catalogue") reads as
   *list all thirteen*, which would put eight fixture keys in a test file and
   break REQ-903's disposability — the thing T-21 greps for. It asserts
   `hasSize(13)` plus `key[0..4]` = the five real sections instead.
3. **T-03 was executed as two sub-steps** (fixture + the two catalogue-count
   amendments; then the four remaining `@AfterEach`), to stay inside the
   5-file-per-step cap. Same seven files, same result.
4. **T-05 validated by `DevDataSeederTest`, not by a manual `quarkus:dev`.**
   That class already boots a real dev-profile app against its own database,
   so REQ-906 is now a *test* rather than a manual observation, and its
   `reseedingIsAFullNoOp` case gained an assertion that the enablement sits
   inside the `devDeviceExists()` early return.
5. **T-01's "zero diff under `packages/api-client/`" is one file off.**
   `.openapi-generator/openapi.yaml-generate-ts-client.sha256` necessarily
   changes when the contract changes — it is the input hash. No generated
   *code* changed, and a second run is byte-identical. Expect that one line in
   the commit.

## Verified, so Phase 4+ need not re-derive it

- **REQ-903 disposability grep is clean today.** No fixture key appears in any
  `.ts`/`.html`/`.css`/`.java`/`.sql`/`.mjs` outside
  `V10__catalogue_expansion.sql`, `DevDataSeeder` and `MenuFixtures`. T-18 adds
  `measure.mjs`'s route list as the fourth sanctioned site; nothing else may.
- **`restoreCatalogueOptOut()` is `ON CONFLICT DO NOTHING`** — by design, so it
  never rewrites a row a test left deliberately. Consequence a future writer
  must know: it **cannot flip an `enabled = true` row back**. A test that
  enables a fixture must reset that row itself (`CatalogueExpansionTest`'s
  `disableAtPropertyScope` does).
- `%dev` delivers 13 menu items; `%test` delivers 5. The split is
  `@IfBuildProfile("dev")` at augmentation, and `CatalogueExpansionTest`'s
  five-key case is the proof.
- Sidebar consequence of the fixture set is as the PRD predicted: `home`,
  `info`, `umgebung`, `smart-home` + the overflow entry, with **`services`
  dropping into the popover** — so REQ-932 is live, not hypothetical.

## What must happen next

1. ~~Fix round on 🟠 1, 🟠 2, 🟠 3, 🟡 4, 🟡 5 and the deviation-2 plan
   correction.~~ **Done 2026-08-27** — see the fix-round table at the top. The
   lead commits; nothing is staged.
2. Phase 4 (T-10 → T-12: i18n keys, the parameterised route table,
   `SectionPlaceholderComponent`), Phase 5, Phase 6.
3. `T-12` needs `routeMatches()` from `menu-split.ts` (T-13) — land T-13 first
   or add the helper with T-12.
4. **One commit at the end (T-21); the user commits.** Nothing is staged. Stage
   **explicitly** — the tree also carries Part B/C planning work
   (`docs/design/screens-inhouse/connection.md`, the `modular-menu-catalogue`
   retirement, `docs/handoff.md`, `docs/features/index.md`).
5. **Until Phase 4 lands, `%dev` is mid-refactor** — 13 menu items against a
   router that knows four section paths. `%test`/`%prod` are unaffected.

## Still true from the planning handoff

- `n = 5` lives only in `SIDEBAR_SLOTS`; the popover has no capacity limit.
- `splitMenu` is five lines satisfying six requirements — do not branch it.
- REQ-932 is resolved by promotion ⇒ a popover tile is never active ⇒ no active
  variant in the panel.
- `CdkMenu` is 1-D and stays that way (`role="menu"`'s contract).
- The token is `--size-touch-min` (2.75 rem); `--size-touch-target` does not exist.
- Stop-and-report triggers: a `GuestModule` union or module registry (Part C);
  `guest-data.service.ts`, the two guest fetches or the ETag (Part B); a new
  dependency, a nested `CLAUDE.md` entry or a constitution amendment (propose
  exact wording and end the turn).
