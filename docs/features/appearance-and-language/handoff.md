# Handoff: Appearance & Language

## CURRENT STATE (2026-08-19, `dev` — 06-review.md finding #3 fixed; one newly measured defect is open)

**Phase:** `07_improve`. The user approved option 1, *"darken the colour a bit"*.
Done and verified against real pixels. **Nothing was committed.**

### What changed

`--ink-600` in the light theme: **`#6e6486` → `#5a516e`**. Derived by scaling the
old value by **0.65 in linear-light space**, which preserves hue — the same
purple-grey, one step deeper, so the ink ramp still reads as one family. This is
the value the brief recommended; the pixels agreed with its analytic estimate to
within 0.15, so no adjustment was needed. **The dark theme was not touched** —
`--color-text-secondary` maps to `--ink-300` there.

`--ink-600` feeds **nothing but** `--color-text-secondary`, and that role has
exactly **three** consumers — the brief's list was correct and was re-verified
by grep over all of `src/`.

### Measured, light mode, sampled from real pixels at 1280 x 832

| Consumer | Surface | Before | After | AA |
|---|---|---|---|---|
| `action-card__description` | glass card | 4.01 | **5.41** | pass |
| `setting-choice__hint` (the finding) | Settings panel | 3.80 | **5.12** | pass |
| `section-placeholder__note` | **raw mesh, no surface** | 2.79 | **3.77** | **still fails** |

Sidebar composite (no consumer today, but carried in the design record):
3.48 → **4.70** analytic, 3.60 → **4.85** sampled.

**Dark re-measured, unchanged to the hundredth:** secondary on glass
5.42/5.43/5.60 (§3.4 / analytic / sampled), secondary on sidebar 6.32/6.34/6.48,
Settings hint 5.31 — identical to the pre-change run.

### OPEN — the third consumer cannot be fixed by this role

`.section-placeholder__note` (the "coming soon" line on `/info`, `/umgebung`,
`/smart-home`, `/services`) is the **only** `--color-text-secondary` consumer
with **no surface under it**: it composites straight onto the mesh. It reads
2.79 → 3.77, stable at 2.79–2.82 → 3.77 across all four routes x three
viewports. At `--text-body-lg` (1.125rem / 18 px, weight 400) it is **not** WCAG
large text — that needs 24 px, or 18.66 px bold — so 4.5 : 1 applies.

The brief's candidate table listed "sidebar role" as its third column; the real
third consumer is this one, and it starts ~0.8 lower than anything the table
modelled. Clearing 4.5 on the raw mesh needs `--ink-600` near **`#4e4660`**
(linear x0.49) — about **twice** the darkening the user approved — which would
drag the shared secondary role toward the primary on *every* surface (7.2 : 1 on
glass, against primary's 10.9) to compensate for one screen's missing surface.
That was **not** done: it exceeds what was approved, and the cause is the absent
surface, not the role.

Three options for the user, no recommendation acted on:
1. give the placeholder a glass panel (fixes the cause; it is the only
   full-bleed screen without one);
2. put the note on `--color-text-primary` (7.75 : 1 on the mesh today);
3. darken `--ink-600` further to ~`#4e4660` (fixes it, repaints everything,
   flattens the primary/secondary distinction).

Recorded as an open ⚠️ row in `02-design.md` §3.4 and `dark-theme.md` §4 rather
than quietly fixed or quietly dropped.

### Records updated

- **`docs/features/appearance-and-language/02-design.md`** §3.3 role row and
  §3.4 table: new measured values, the old 3.99 / 3.48 / 3.80 quoted in a dated
  note, plus new rows for the Settings panel and the placeholder-on-mesh.
- **`docs/design/dark-theme.md`** §3 and §4: same treatment; the "light mode has
  two [failures]" claim was corrected.
- **`docs/design/tokens.md`**: `--color-text-secondary` marked ⚠️ as a
  **deliberate deviation from Figma** in the file's existing provenance style —
  Figma still says `#6e6486`, the build ships `#5a516e`, with the accessibility
  reason. Not overwritten as though Figma said so. The `Shape` code block
  carries the same one-line note.
- **`frontend/tools/measure.mjs`**: `DESIGN_CONTRAST` light secondary rows
  3.99 → 5.38 and 3.48 → 4.70, with a comment, exactly as the previous round did
  for the status-bar row. Still **Prettier-clean** (`--check` passes).

### Verification

- `ng build` green — main **351.08 kB**, styles **7.49 kB** (both as expected).
- `ng test` green — **22 files / 178 tests / 0 failures**, no stderr warnings.
- `measure.mjs` full grid: **72/72**, 0 overflowing, 0 truncated, **0 findings**
  (the two `CONTRAST OFF SPEC` notes the change raised are gone now that the
  table is corrected).
- **Geometry did not move**: heading y **88**, card row bottom **744** at
  1280 x 832 — the tool's own baseline check prints `88 vs 88`, `744 vs 744`.
- Dev server was started on :4300, the served `styles.css` was confirmed to
  carry `--ink-600: #5a516e` before any number was trusted, and the server was
  killed afterwards.

### Untouched, deliberately

`frontend/CLAUDE.md` — the three approved entries and the S13 text are final.
The declined fourth entry (reserved width for selection states) was **not**
added and is **not** re-proposed.

**Next:** user decides on the open placeholder defect above (options 1–3), then
commits. No new convention was established, so nothing is proposed for a nested
`CLAUDE.md`.

---

## CURRENT STATE (2026-08-19, `dev` — two approved housekeeping tasks, done)

**Phase:** post-`07_improve` housekeeping. Both tasks the user approved are
finished; **nothing else was touched** and **nothing was committed**.

### 1. `frontend/CLAUDE.md` — the three approved entries are written

The user answered *"yes you can add all of them"*. All three are in, under
**Tests**:

- a service's collaborator is a **DI double built from writable signals**, not
  the real service over a stand-in gateway — `vi.mock` is rejected by the
  Angular unit-test system for relative imports, so a *broken* config is tested
  through the exported pure function;
- a **test double for an injected transport lives inside the spec that needs
  it** — never in `src/`, never imported from another spec
  (`FakePreferenceGateway`, `installMatchMedia`);
- the `measure.mjs` sentence now reads **"no-scroll on every route × theme ×
  language × viewport"**, naming the axes the tool actually drives.

The existing Tests paragraph was split in two so the new entries sit beside the
test-double sentence they extend — **no existing word was changed**, only the
paragraph break and the re-wrap of the `measure.mjs` sentence.

**The S13 text is untouched** (the *Structure* mesh bullet and the whole
*Appearance & language* section, `02-design.md` §4): still a byte-for-byte
match against the approved wording.

**The fourth proposal — reserving the width a selection state changes — was
NOT added.** It was never shown to the user and is still unapproved. It remains
worth proposing on its own.

### 2. `frontend/tools/measure.mjs` is Prettier-clean

`npx prettier --write tools/measure.mjs` with the project's own `.prettierrc`
(`printWidth: 100`, `singleQuote: true`) — **no option added, `package.json`
untouched**. `npx prettier --check tools/measure.mjs` now passes. The file grew
777 → **908 lines** (48 lines replaced by 179): every hunk is a line break,
plus one string whose escaped `\'` became a double-quoted literal.

**Proved formatting-only, not argued.** The full report was captured against
the same `ng serve` before and after the rewrite. Normalising only
`clock="HH:MM"` (which advances between runs) makes the two logs
**md5-identical** — `c239b078…` for both, 425 lines each: same 72 combinations,
same boxes, same contrast table, same `0 finding(s)`.
Logs: `/tmp/measure-before.log`, `/tmp/measure-after.log`.

### Verification

| Check | Result |
|---|---|
| `ng build` | green — main **351.08 kB**, styles **7.49 kB** (as expected) |
| `ng test` | green — **22 files / 178 tests / 0 failures**, **0** `No icon named` warnings |
| `prettier --check tools/measure.mjs` | **passes** |
| `node tools/measure.mjs` | 72/72, 0 overflowing, 0 truncated, **0 findings**; identical to the pre-format run apart from the clock |

### Unchanged and still open

- **Finding #3** (light Settings hint at 3.80 : 1) — deliberately untouched,
  still awaiting the user's decision, still tracked in `docs/features/index.md`.
- **Nothing committed.** The user commits.
- The `ng serve` on port 4300 used for the two measurement runs was **killed**;
  port 4300 verified free. (The old hung headless-Chromium from an earlier
  session is still resident and was left alone.)

---

## CURRENT STATE (2026-08-19, `dev` — `07_improve`, four of the five 🟡 fixed)

**Phase:** `07_improve` → **done for this round.** `06-review.md`'s findings
**#1, #2, #4 and #5 are fixed**; **#3 is deliberately untouched** and is tracked
in `docs/features/index.md` pending a user decision (changing
`--color-text-secondary` for light alters a shipped role on every screen — a
design call, not an improve-round edit).

**Nothing was committed.** Branch `feature/home-screen-prototype` unchanged.
`frontend/CLAUDE.md` **not edited** — the three optional proposals are still
unanswered, and its S13 text is approved verbatim.

### Numbers after the round

| | Before (06-review) | After |
|---|---|---|
| `ng build` main | 350.39 kB | **351.08 kB** (+0.69 kB — the extra check, the reserved-width markup) |
| `ng build` styles | 7.49 kB | **7.49 kB** (unchanged; component CSS lives in `main`) |
| `ng test` | 22 files / 177 tests | **22 files / 178 tests / 0 failures** |
| Test runs green | 4 of 5 (one 7.73 s timeout) | **5 of 5**, and **0 bytes of stderr** on every run |
| `tools/measure.mjs` full grid | 72/72, 0 overflowing, 0 truncated | **72/72, 0 overflowing, 0 truncated, 0 findings** |
| Home baseline | heading y 88 · card row bottom 744 | **88 / 744, unchanged** (tool's own baseline check) |

### What each fix was

**#1 — `defaultAppearance` was never validated.** `validateConfig()` gained a
fourth check, symmetric with the language one:
`defaultAppearance ∈ appearances[].mode`, same throw style. It is not
theoretical — `AppearanceMode` includes `'system'`, so the bad config
type-checks. New test `throws when defaultAppearance is not one of the
configured appearances` sits with the other validation throws (this is why
`validateConfig` is exported — S29). **`02-design.md` §5.2 corrected too**: the
gap was in the design, which enumerated three checks; it now enumerates four
and says why.

**#2 — the light status-bar contrast figure.** Re-measured here, not copied:
`tools/measure.mjs`'s band scan gives **7.66 – 8.71** light (mesh
`#fba390 … #fcb599`) and **11.80 – 12.39** dark (`#4c271c … #4a221d`), 52
pixels across the bar's own box at 1280 x 832, glyph pixels trimmed. Exactly
reproduces the reviewer's numbers. The row now records a **range** in **both**
`02-design.md` §3.4 and `docs/design/dark-theme.md` §4, each with a note giving
the method and stating that the old 10.49 / 10.90 came from mesh **grid point
4** — a lighter point than where the bar actually sits, because the bar has no
surface of its own. **No accessibility consequence**: 7.66 still clears AAA.
`tools/measure.mjs`'s `DESIGN_CONTRAST` carried the same 10.49, and printed it
back as "vs §3.4's 10.49", so it was corrected as well — otherwise the next run
would have contradicted the documents it cites.

**#4 — the shell spec's missing glyphs.** `phosphorGlobe`, `phosphorSun` and
`phosphorCaretDown` added to `shell.component.spec.ts`'s `provideIcons()`, with
a comment saying why. **The timeout was not touched.** Result: five full runs,
**zero stderr**, no recurrence of the 5 s timeout. Five runs is evidence, not
proof, for an intermittent failure — if it ever returns, the next move is
stubbing `StatusBarComponent` in this spec, still not widening the limit.

**#5 — the option row reflowed on tap.** Measured before and after in a real
browser (the fix was temporarily reverted to get the "before", then restored
and re-confirmed byte-identical):

```
before  *Deutsch x197 w117 |  English x326 w81  | *Hell x197 w113 |  Dunkel x322 w106
 tap →  *Deutsch x197 w117 |  English x326 w81  |  Hell x197 w85  | *Dunkel x294 w133
        → "Hell" -28 px wide, "Dunkel" moved 28 px left and grew 27 px

after   *Deutsch x197 w117 |  English x326 w108 | *Hell x197 w113 |  Dunkel x322 w133
 tap →  *Deutsch x197 w117 |  English x326 w108 |  Hell x197 w113 | *Dunkel x322 w133
        → every delta 0: nothing moves, nothing resizes
```

**Both** width-changing signals are reserved, because fixing only the weight
would have left the row still reflowing:

- the label is an `inline-grid` whose `::after` repeats the text (via
  `data-label`) at `--text-title-weight`, `height: 0`, `visibility: hidden` —
  the column is therefore always as wide as the bold string;
- the check glyph sits in a permanent `.setting-choice__option-check-slot` of
  `--size-icon-xs`; only the glyph inside it is conditional, so the accessible
  name still exists **only** on the selected option (the spec asserts this and
  passes).

The designed appearance is kept: all three selection signals stay
(`02-design.md` §9.1 names background + weight + check), so no design call was
needed. **The one visible consequence:** unselected options are now ~27 px
wider, carrying the reserved space — visible as trailing room in
`/tmp/fix-settings-light.png`, and consistent with §9.1's own sketch
(`[✓ Deutsch] [ English ]`). Panel geometry, option height (44 px at the design
size) and the no-scroll result are all unchanged. The two comments that claimed
box stability now describe what the code actually does.

### Files changed in this round

| File | Why |
|---|---|
| `frontend/src/app/core/content/property-content.service.ts` | #1 — fourth validation check |
| `frontend/src/app/core/content/property-content.service.spec.ts` | #1 — its test; describe title says "four" |
| `frontend/src/app/layout/shell/shell.component.spec.ts` | #4 — three glyphs registered |
| `frontend/src/app/features/settings/setting-choice/setting-choice.component.html` | #5 — `data-label`, permanent check slot |
| `frontend/src/app/features/settings/setting-choice/setting-choice.component.css` | #5 — bold-width reservation, slot, corrected comments |
| `frontend/tools/measure.mjs` | #2 — the stale 10.49 / 10.90 it printed back at the docs |
| `docs/features/appearance-and-language/02-design.md` | #1 (§5.2), #2 (§3.4) |
| `docs/design/dark-theme.md` | #2 (§4) — the permanent record |

`frontend/tools/measure.mjs` is **not** Prettier-clean, and was not before this
round either (the committed version fails `--check` too). Running
`prettier --write` on it rewrites ~670 lines and buries the four-line fix, so it
was left as found. Worth a separate decision, not an improve-round side effect.

### Still open with the user

- **Finding #3** (light Settings hint at 3.80 : 1) — untouched by design;
  tracked in `docs/features/index.md`.
- The **three optional `frontend/CLAUDE.md` proposals** from implementation are
  still unanswered. This round adds no new required entry.
- **Nothing is committed.** The user commits.

One `ng serve` on port 4300 was started for the measurements and **killed**;
port 4300 verified free afterwards. (Unrelated: a hung headless-Chromium from
an *earlier* session is still resident — pid ~3231065, a `node -e` reading a
PNG. Not started here, harmless, kill at leisure.)

---

## CURRENT STATE (2026-08-19, `reviewer` — `06_review` complete)

**Phase:** `06_review` → **finished. Result: PASS ✅** against constitution
v1.3.0 — **🔴 0 · 🟠 0 · 🟡 5 · 🟢 10**. Full report:
[`06-review.md`](06-review.md) (note the filename: `06-`, per
`docs/features/_template/` and the previous feature, not `05-`).

**All 27 requirements (REQ-101 … REQ-127) independently confirmed delivered**,
0 orphans — matching `04-analyze.md`, but re-derived from the code, the build,
a real browser and the measurement grid rather than from the step list.

**Nothing was fixed and nothing was committed.** The working tree is exactly as
implementation left it. **Next action: the user commits.** No finding blocks it.

### What the review verified for itself (not taken on trust)

- `ng build` green — main **350.39 kB**, styles **7.49 kB**, matching the
  reported figures exactly.
- `ng test` — **22 files / 177 tests / 0 failures** on 4 of 5 runs; run 1 hit
  the `shell.component.spec.ts` timeout (see finding #4).
- `tools/measure.mjs` full grid — **72/72, 0 overflowing, 0 truncated, 0
  findings**, reproduced against a dev server (the handoff measured a
  production build; same numbers).
- **Visual check done** — 5 screenshots (`/tmp/rev-*.png`), compared against
  `home-concept-ai.png` and `screens/home.md`. Two findings came *only* from
  looking at pixels (#3, #5).
- **Two mutations run against the real source and reverted** (`cmp`-identical,
  mtimes preserved): both bite exactly as the handoff claimed. REQ-122's
  "`PreferencesService` was never edited to make the tests pass" **holds**.
- **Live Chromium session** — Escape closes the menu and returns focus to the
  trigger; Enter/ArrowDown/Enter selects; storage holds exactly
  `gxp.preferences = {"language":"en"}`, `localStorage` empty, **0 cookies**.

### The 5 🟡 Minor findings (none blocking)

1. **`validateConfig()` never checks `defaultAppearance ∈ appearances`**, though
   it does check `defaultLanguage ∈ languages`. A `system` default with
   light/dark options is accepted and leaves the Settings screen with **no
   option marked selected**. The gap is in `02-design.md` §5.2 too, not only in
   the code.
2. **The light status-bar contrast figure (10.49) is also wrong in
   `dark-theme.md` §4**, not just in `02-design.md` §3.4. That file is the
   *permanent* design record. Measured range: 7.66 – 8.71. No accessibility
   consequence.
3. **Settings hints measure 3.80 : 1 in light — below WCAG AA.** Agreed that
   nothing regressed, but this needs to become a **tracked** item, not a
   handoff note: it is a new screen on a known-weak role, and the next feature
   will not read this file.
4. **`shell.component.spec.ts` renders `StatusBarComponent` without registering
   its three glyphs** → permanent `No icon named …` stderr noise, and this is
   the spec that intermittently exceeds 5 s. **The flake is not pre-existing**
   (it appears in no `home-screen-prototype` record). Registering the icons
   removed the warnings and gave 3/3 green runs. **Do not loosen the timeout.**
5. **The selected option's `font-weight` jump (400 → 800) changes its width**,
   so the option row reflows on every tap — despite the adjacent comment
   claiming the box is stable.

### Disposition of the items this handoff already recorded

Agreed, verified independently: the exported `resolveProperty()` /
`validateConfig()` (**right call** — the alternative was approval-gated, and
only the spec imports them), `node-builtins.d.ts` (`tsconfig.app.json`
exclusion confirmed by reading it), both "went past the step text" edits (both
*prevented* problems — one legend was genuinely false, the other asserted an
unmade datastore choice), `--shadow-panel` (**the code is canonical**;
`dark-theme.md` agrees with it and §3.3 abbreviates the light row too), and the
pre-existing `tokens.md` image link (confirmed present in `HEAD`).

**Extended or partially disagreed:** the §3.4 contrast error (also in
`dark-theme.md` — finding #2), the Settings-hint contrast (needs tracking —
finding #3), and the "known hardware flake" (not pre-existing, and it has a
testable cause — finding #4).

### Still open with the user

The **three optional `frontend/CLAUDE.md` proposals** remain unanswered and
were **not** treated as conventions anywhere in the review. `frontend/CLAUDE.md`
contains only the S13 text, which I diffed against `02-design.md` §4's approved
wording — **verbatim match**, approval gate satisfied.

### Deliberately not done by the review

Nothing fixed, nothing committed, `frontend/CLAUDE.md` untouched, and
**`docs/features/index.md` left unchanged** (its row still reads
`05_implement` / owner `dev`) so the record stays clean — update it with this
result when committing. One `ng serve` was started for the visual check and
**killed**; the 44xx port range was verified clear afterwards.

---

## CURRENT STATE (2026-08-19, generic session orchestrating `dev` — sections H and J, implementation complete)

**Phase:** `05_implement` → **finished. All 39 of 39 steps are ticked.**
Sections A–J are closed. Nothing is blocked. **Ready for `06_review`.**

**Build:** `ng build` green — main **350.39 kB**, styles **7.49 kB**, identical
to the foundation baseline. **Tests:** `ng test` green — **22 files, 177 tests,
0 failures**. Section H changed documentation only and section J changed only
`frontend/tools/measure.mjs`, so no application source moved after section I.

**Nothing committed**, branch `feature/home-screen-prototype` unchanged.

### Section H (S22–S26) — documentation

`docs/design/dark-theme.md` is **new** (untracked): provenance header, the
theme-axis-touches-roles-only rule, the `--night-1…16` grid with light
counterparts, the full role table, the measured contrast table, the active-nav-
glyph case, and what is deliberately absent. `docs/design/tokens.md` gained a
dated correction of the dark-mode trigger (it never writes the absent state;
startup is the backend-delivered default, **not** `prefers-color-scheme`;
`system` remains authorable and is the only path to `prefers-color-scheme`), a
resolution of the "no dark palette" block, and a new "In-house tokens — no
Figma counterpart" section for the seven S14 tokens.
`docs/design/design-sheet.md` gained two 📋 cross-references,
`docs/design/screens/home.md` records the top bar as committed, and
`docs/system-context.md` was rewritten per `02-design.md` §13.

**S26's three mechanical checks passed:** 18 component identifiers each resolve
to a real non-spec source file (0 missing); every table claim was verified
against source rather than assumed; and the no-datastore check is clean against
both a 20-product pattern and a generic one.

**Two edits deliberately went past the literal step text**, both to make a
Validate clause honest: `design-sheet.md`'s **legend** was widened (📋 meant
"proposed, not yet drawn", which is false for two things that are now built),
and `system-context.md`'s "Once the preference lives in the database" became
"is stored server-side" — it was the only phrase tripping the no-datastore
check, and it asserted a choice nobody has made.

### Section J (S36/S37) — measurement

`frontend/tools/measure.mjs` (+645 / −52) gained the appearance and language
axes, both driven **through the UI** (it clicks the Settings screen's own
buttons by index and waits for `<html>` to actually carry the pair before
measuring, so a failed switch shows up as a number rather than a silent
mismeasurement), plus collision, overlay and composite passes. It still
**reports and asserts nothing**, exits 0, and is not wired into `npm test`.
Measured against a **production build** served statically, not the dev server;
two full runs are geometry-identical.

- **No-scroll: 72/72**, `overflow: visible` on `html`/`body` everywhere, zero
  overflow — 96 OK lines counting the 24 with a dropdown open.
- **Home's geometry is unchanged**: heading `y 88`, card row bottom `y 744` at
  1280 × 832 in all four theme × language combinations. The bar overlays.
- **Collisions: none, 8/8.** Clearances 52 / 20 at 1280 (budgeted 48 / 20) and
  40 / 16 at 1024 (budgeted ≈38 / 16).
- **Dropdown, 24 openings:** `onBody=true`, `inShell=false` every time — the
  panel is in the CDK overlay container, never in the shell subtree.
- **900 strings measured, 0 wraps of single-line labels, 0 truncations**, in
  either language, including English at the 1024 floor.
- **Composites:** 8 of 9 rows reproduce `02-design.md` §3.4 within ±0.02.

### ⚠️ Two findings from the measurement, deliberately not patched

1. **§3.4's status-bar row does not reproduce in light mode.** The bar has no
   surface of its own, so its text sits on the mesh where the bar actually is.
   A 52-pixel scan across the bar's box gives **7.66 – 8.71** in light against
   §3.4's **10.49** (dark: 11.80 – 12.39 against 10.90). §3.4 measured "locally,
   at grid point 4", evidently a lighter point than where the bar sits. **No
   accessibility consequence** — 7.66 still clears AAA — but the light figure is
   optimistic by roughly 2 points and looks like a spec error.
2. **The known light-mode secondary-text weakness has a new surface.** The
   Settings hints measure **3.80** sampled in light (dark: 5.31) — the same
   weakness §3.4 already records for light secondary text (3.99 / 3.48), on a
   screen that did not exist when that was recorded. Nothing regressed; the
   exposure grew.

### ⚠️ Stale-server hazard is real — it bit twice

The previous handoff said "no server is left running now". **That was wrong** —
a stale `ng serve` from 2026-08-18 was still holding port 4300 and was killed
this session (pids 3301141 / 3301109). Always verify the served file before
trusting a measurement (`curl -s .../styles.css | grep --night-6`). Nothing is
listening on 42xx/43xx now. Unrelated leaked headless-chromium processes from
2026-08-18 are still alive and were left alone.

### ⏸ Open with the user — three optional `frontend/CLAUDE.md` proposals

None blocks anything; the file is **unchanged** and its S13 text stays approved
verbatim. Relayed to the user 2026-08-19, unanswered:

1. *Tests* — a service's collaborator is a DI double built from writable
   signals, not the real service over a stand-in gateway; `vi.mock` does not
   work here (the Angular unit-test system rejects it for relative imports), so
   anything needing a broken config tests the exported pure function instead.
2. *Tests* — a test double for an injected transport lives inside the spec that
   needs it, never in `src/` and never imported from another spec.
3. *Tests* — refresh the `measure.mjs` sentence to "no-scroll on every route ×
   theme × language × viewport", naming the new axes.

### Other loose ends for the reviewer

- **`--shadow-panel` discrepancy:** `02-design.md` §3.3 omits the `0` spread
  that `tokens.css` actually ships. `dark-theme.md` records the code's value. If
  the design doc is canonical, that is a one-line correction there.
- **Pre-existing broken link** in `docs/design/tokens.md`:
  `../assets/home-no-artwork.png` twice, where the file is at
  `assets/home-no-artwork.png`. Predates this feature; left alone.
- **`resolveProperty()` / `validateConfig()` are exported** from
  `property-content.service.ts` (S29) so a broken config is testable. The
  alternative seam — an injection token for the raw config — would have been a
  new cross-cutting pattern and therefore approval-gated, so it was not taken.
- Measurement logs: `/tmp/measure-final.log`, `/tmp/measure-verbose.log`.

---

## CURRENT STATE (2026-08-19, `dev`, section I chunk 2 — S32 · S33 · S34 · S35 · S39, and S19–S21 ticked)

**Phase:** `05_implement`. **Section I is finished.** S32, S33, S34, S35 and
S39 are implemented and ticked, and **S19, S20 and S21 are now ticked too** —
they were already on disk and S34 is what validates them. **32 of 39 steps
done.** Nothing is blocked and no decision is open.

**Build:** `ng build` green — main **350.39 kB**, styles **7.49 kB**,
byte-for-byte the same as the baseline (no application source was touched).
**Tests:** `ng test` green — **22 files, 177 tests, 0 failures** (from 14 / 82).

**Nothing committed**, branch `feature/home-screen-prototype` unchanged.

### No defect found in S19/S20/S21

S34 was written against `features/settings/**` and the `einstellungen` route
exactly as the earlier session left them, and everything it asserts passed on
the first run. **Nothing in those three steps needed fixing.**

### Files added (8) and the one non-spec change (1)

New specs:

| File | Tests | Step |
|---|---|---|
| `layout/status-bar/status-bar-menu/status-bar-menu.component.spec.ts` | 13 | S32 |
| `layout/status-bar/status-bar.component.spec.ts` | 11 | S33 |
| `features/settings/setting-choice/setting-choice.component.spec.ts` | 10 | S34 |
| `features/settings/settings.component.spec.ts` | 17 | S34 (incl. routing) |
| `core/preferences/preference-gateway.spec.ts` | 11 | S39 (the stand-in) |
| `core/preferences/preferences.persistence.spec.ts` | 23 | S39 |
| `core/i18n/catalog/catalog.spec.ts` | 5 | S35 |
| `core/i18n/german-literals.spec.ts` | 5 | S35 |

**One non-spec change, stated openly:** `src/node-builtins.d.ts` gained
`readdirSync` + a minimal `Dirent`, so S35's scan can enumerate `layout/` and
`features/`. That file exists precisely for this (see its own header comment:
declare only the members a test actually uses, and only in the **test**
compilation — `tsconfig.app.json` excludes it by name). No dependency was
added and **no application source was edited at all** — which is why the bundle
is byte-identical.

### S39 was proved, not assumed — `PreferencesService` is untouched

REQ-122's claim is that swapping the transport cannot change behaviour, so the
whole of `preferences.persistence.spec.ts` runs against the **real** service
and the **real** `appConfig.providers` with only `PREFERENCE_GATEWAY` replaced.
`FakePreferenceGateway` lives **inside that spec file** (not in `src/` as a
shared double, which would have been a new convention and approval-gated, and
not exported from a spec, which is the trap the last chunk recorded).

Four mutations were run against the real source and reverted (`cmp`-identical
afterwards) to prove the assertions bite:

| Mutation | What failed |
|---|---|
| initializer stops `await`ing `hydrate()` | "first thing `<html>` ever sees", "defaults in effect before any stored value", "boots after the bounded timeout" |
| `applyPropertyDefaults()` removed from the initializer | the same first two, plus the failing-write screen test |
| `persist()` loses its `dirty` flag | "coalesces three fast changes" |
| a German literal put back into `section-placeholder.component.html` | both nets of the S35 scan |

### Traps found while writing these — read before touching section I again

- **An instant `load()` hides a missing `await`.** The first version of the
  no-flash test passed even with the initializer's `await` removed: microtask
  ordering happened to settle hydration before the first effect run. It only
  bites with (a) a **delayed** `load()` (the fake's `loadBehaviour: 'delayed'`,
  25 ms) **and** (b) a synchronous `fixture.detectChanges()` right after
  `createComponent`, instead of `await fixture.whenStable()`. Waiting for
  stability lets a late hydration sneak in ahead of the first render and hides
  exactly the flash being asserted.
- **The two statements *inside* the initializer are not order-observable.**
  Swapping `applyPropertyDefaults()` and `hydrate()` changes nothing while
  `load()` is asynchronous — a synchronous `applyPropertyDefaults()` lands
  first either way. The regression that *does* exist is moving it out of the
  initializer entirely, and that is what the test catches. The test is named
  and commented accordingly; do not "strengthen" it back into a claim about
  line order.
- **`TestBed` instantiates app initializers but does not await them.**
  `await TestBed.inject(ApplicationInitStatus).donePromise` is the line that
  makes `provideAppInitializer` testable.
- **A CDK menu trigger toggles.** Clicking it a second time to "reopen" closes
  it and yields an empty panel — a status-bar test that opens two menus in one
  case must Escape the open one first.
- **CDK keyboard handling reads `event.keyCode`**, which `KeyboardEvent`'s
  init dictionary does not carry. Every keyboard case builds the event and
  `Object.defineProperty(event, 'keyCode', …)`.
- **`@ng-icons` renders the glyph name as a property binding**, so
  `ng-icon[name="phosphorSun"]` matches nothing and the injected `<svg>` has no
  attribute either. What is assertable is that a glyph was drawn per row and
  that two rows' `svg.innerHTML` **differ** — i.e. the configured name reached
  the icon. The status-bar spec uses the open menu's rows as the reference
  glyphs to prove the trigger follows the *resolved* theme.
- **`vi.spyOn(console, …)` is scoped to the service-level paths**, not to a
  full app boot: Angular itself may log during bootstrap and would turn
  REQ-127's assertion into an unrelated flake.
- The S35 scan strips comments first (they are English but quote German) and
  skips `*.spec.ts` (a test double naming `'Deutsch'` is the point of it). It
  matches catalogue values **case-sensitively**, which is what keeps the
  `einstellungen` / `umgebung` **route paths** from being flagged — there is a
  test asserting exactly that.
- The known hardware flake is unchanged and was seen once in five full runs
  (`shell.component.spec.ts`, 7.9 s against a 5 s limit, green on re-run).
  **Not a code defect; do not loosen a timeout.**

### What section H (S22–S26) should know

- Nothing under `docs/design/**` was touched, and `docs/system-context.md` is
  untouched. S26's "every component named exists in `frontend/src/`" now also
  covers `SettingsComponent`, `SettingChoiceComponent`, `StatusBarComponent`
  and `StatusBarMenuComponent` — all four exist and are now under test.
- `03-todo.md`'s status header was updated to "sections A–G and I done (32 of
  39)". If H ticks S22–S26, update the count with it.

### What section J (S36/S37) should know

- Every geometry, wrapping and contrast claim is still unverified — jsdom
  computes no layout, so nothing above asserts a pixel. The Settings screen's
  panel width (`calc(3 * var(--size-panel) + 2 * var(--space-4))`), the
  reserved top-right corner and the open dropdown's overlay box are all
  measurement work, not test work.
- The dropdown panel renders into the **CDK overlay container**, appended to
  `document.body` — not inside `.shell`. `measure.mjs` must query it from the
  document, not from the route's subtree.
- The language switch is now reachable from the UI in two places (status bar
  dropdown, Settings screen), so S36's 2-languages axis can be driven by
  clicking rather than by seeding `sessionStorage`.

### Not done here, deliberately

Section H and section J are untouched. `frontend/CLAUDE.md` was **not** edited
(a proposal is with the user). Nothing was committed.

---

## CURRENT STATE (2026-08-19, `dev`, section I chunk 1 — S27 · S28 · S29 · S30 · S31)

**Phase:** `05_implement`. **S27, S28, S29, S30 and S31 are implemented and
ticked.** Nothing is blocked and no decision is open.

**Build:** `ng build` green — main **350.39 kB**, styles **7.49 kB** (byte-for-byte
the section-G baseline; the one source change below is tree-shaken away).
**Tests:** `ng test` green — **14 files, 82 tests, 0 failures** (from 11 / 35).
No flake seen in five consecutive full runs on this hardware.

**Nothing committed**, branch `feature/home-screen-prototype` unchanged.

### Files added (4) and the one source change (1)

New specs: `core/i18n/translation.service.spec.ts` (8), 
`core/preferences/preferences.service.spec.ts` (13),
`core/clock/clock.service.spec.ts` (7). Rewritten:
`core/content/property-content.service.spec.ts` (4 → 18),
`app.component.spec.ts` (2 → 7).

**One application-source change, stated openly:** `resolveProperty()` and
`validateConfig()` in `property-content.service.ts` are now `export`ed (two
keywords + a comment; no behaviour, no signature, no DI change). Reason, and it
is not negotiable-by-cleverness: the raw config is a module import
(`EXAMPLE_PROPERTY`), and the Angular unit-test system **refuses** `vi.mock` on
relative imports — it fails the suite with *"The `vi.mock` and related methods
are not supported for relative imports with the Angular unit-test system.
Please use Angular TestBed for mocking dependencies."* The per-field fallback
and the three validation throws (S29's own text) need a *broken* config, which
is therefore unreachable through the service. The alternative — an injection
token for the raw config — is a new cross-cutting convention and would have
been approval-gated; exporting two module-private pure functions is not. If the
reviewer prefers the token, it is a clean follow-up and this spec adapts to it.

### Test-double changes worth knowing (they affect S32–S35 and S39)

- **`property-content.service.spec.ts` and `app.component.spec.ts` no longer
  provide `PREFERENCE_GATEWAY`.** They provide
  `{ provide: PreferencesService, useValue: { language, resolvedTheme } }`
  (writable signals) instead. Those two specs never asserted persistence, and
  the `SessionPreferenceGateway` stand-in was only there to satisfy the DI
  chain. **Six specs still carry the stand-in** — `app.routes`, `home-content`,
  `section-placeholder`, `logo-asset`, `sidebar`, `shell` — and S39 should
  replace it only where persistence is actually asserted.
- **`preferences.service.spec.ts` ships a `SILENT_GATEWAY`** (a three-method
  inert object) and a `TEST_CONFIG` that *does* offer `system`, which is what
  makes `resolvedTheme()`'s `matchMedia` branch reachable at all. S39's
  `FakePreferenceGateway` is a separate, richer double — do not merge them; the
  state-layer spec must stay free of persistence assertions.
- **`installMatchMedia(initial)`** in that spec is the jsdom mock, with a
  working `change` event. If S33 needs one, copy it rather than exporting it
  from a spec file.

### Traps found while writing these

- `resolveProperty()` only throws when **neither** the active language **nor**
  the property's `defaultLanguage` has an entry. A first attempt asserted the
  throw with the active language present and was simply a wrong test, not a
  defect — the field-level throw needs a third language (`'fr'`).
- `TestBed.resetTestingModule()` is what runs `DestroyRef.onDestroy`, so it is
  how the clock's teardown is asserted (`vi.getTimerCount()` → 0). Both the
  pending boundary `setTimeout` and the running `setInterval` are covered.
- Interpolation is asserted through the **key-fallback** path: no shipped
  catalogue value carries a `{placeholder}` today, so `t('Hallo {name}', …)`
  returns the key and interpolates it. If a catalogue ever gains a real
  placeholder, move the assertion onto it.

### Mutation-checked, not assumed

Two assertions were verified to actually bite by temporarily breaking the
source and re-running (both reverted, files `cmp`-identical afterwards):
turning `t` from an arrow-function property into a method fails the detached-`t`
test **and every component spec that holds `t` detached** (19 failures visible
in the first screen of output alone — that binding is load-bearing across the
whole UI); replacing the clock's boundary delay with a flat `60_000` fails
"ticks exactly on the minute boundary".

### Not done here, deliberately

S32 · S33 · S34 · S35 · S39 are untouched, and **S19/S20/S21 are still
unticked** — they are validated by S34, which has not been written. Nothing in
`docs/design/**` was touched. `03-todo.md`'s status header still reads
"sections A–F done (14 of 39 steps)" and is now stale — left alone on purpose,
since sections G–I are being closed by separate delegations.

---

## SESSION RESUME NOTE (2026-08-18, generic session, after a Claude outage)

An implementation session was cut off mid-section-G by the outage. Its work is
**on disk but not recorded** in `03-todo.md`. Verified from the working tree,
not from notes:

- **S14 – S18 are ticked** in `03-todo.md` (status-bar tokens, glyphs,
  `StatusBarMenuComponent`, `StatusBarComponent`, shell wiring).
- **S19 · S20 · S21 are implemented but still unticked.**
  `features/settings/settings.component.{ts,html,css}`,
  `features/settings/setting-choice/setting-choice.component.{ts,html,css}`
  exist and are fully written, and `app.routes.ts` already points
  `einstellungen` at `SettingsComponent` with `data: { sectionId }` dropped and
  `SectionPlaceholderComponent` still in place for the other four routes.
  They are unticked **correctly**: all three steps validate via the S34 unit
  test, which has not been written yet. Do not tick them before S34 passes.
- **Build green:** `ng build` — main **350.39 kB**, styles **7.49 kB**
  (foundation chunk left it at 242.38 / 5.34; section G accounts for the rest,
  the CDK dropdown included).
- **Tests green:** `ng test` — 11 files, 35 tests, 0 failures. The first run of
  this session showed 4 timeouts in `shell.component.spec.ts` at 5 s and was
  fully green on re-run — the known hardware flake described further down, not
  a regression.
- Nothing committed, branch `feature/home-screen-prototype` unchanged.

The foundation chunk's one loose end is **closed**: both German literals are
gone from the components — `sidebar.component.html` uses `t('nav.aria')` and
`section-placeholder.component.html` uses `t('section.comingSoon')`; the only
occurrences left in `frontend/src` are the catalogue entries in
`core/i18n/catalog/de.ts`. S35's scan test will not trip over them.

**Next action: section H (S22 – S26, design docs), then I (S27 – S35 + S39,
the tests — S34 also unblocks ticking S19 – S21), then J (S36/S37).**

## CURRENT STATE (read this first — 2026-08-18, `dev`, foundation chunk)

**Phase:** `05_implement` — **sections A–F are done: 14 of 39 steps.**
S1 · S2 · S3 · S4 · S5 · S6 · S7 · S8 · S9 · S10 · S11 · S12 · S13 · S38 are
ticked in `03-todo.md`. **Nothing is blocked and no decision is open.**

**Next action: sections G–J**, in the TODO's order — G (S14 → S21, status bar
and Settings), H (S22 → S26, design docs), I (S27 → S35 + S39, the tests),
J (S36/S37, measurement). Suggested pairing: write each section-I test with the
code it covers rather than at the end (constitution principle 6).

**Branch:** `feature/home-screen-prototype`, unchanged. **Nothing is
committed** — the user commits. `git status` shows the whole feature dir
untracked plus 29 modified files.

**Build:** `ng build` green — main **242.38 kB** (was 234.03), styles
**5.34 kB** (was 3.18). **Tests:** `ng test` green — **11 files, 35 tests, 0
failures** (unchanged count: no new spec files were added, see "What is
deliberately NOT done" below).

### Verified in a real browser (production build + dev server, playwright)

- **Light mode is byte-identical to `HEAD`.** Home at 1280 × 832, same server,
  same run conditions: identical PNG md5 before and after the whole chunk. S2's
  "pixel-identical" clause holds.
- **Dark paints the night mesh**: `.shell` background `rgb(62, 29, 40)` =
  `--night-6`, first layer `rgb(79, 23, 42)` = `--night-1`; text
  `rgb(247, 242, 251)`, glass `rgba(246, 240, 255, 0.14)`, sidebar
  `rgba(246, 240, 255, 0.09)` (the 2 : 3 ratio), `blur(8px)` unchanged.
- **No-scroll: 36 combinations** (6 routes × 2 themes × 3 viewports 1024×768 /
  1280×832 / 1440×900) — 0 failures, `overflow: visible` on `html` and `body`
  everywhere. (Languages are not yet switchable without a UI; the full 72 is
  S36/S37's job.)
- **REQ-126 (a) proven, not argued.** A `MutationObserver` installed before any
  page script recorded *every* value `<html data-theme>` / `lang` ever takes.
  With `{"appearance":"dark","language":"en"}` in `sessionStorage` the recorded
  sequence is exactly `["dark"]` / `["en"]` — `light`/`de` are never written and
  then corrected. With nothing stored: `["light"]` / `["de"]`.
- **Failure paths:** a malformed stored record and an unconfigured value
  (`system` / `fr`) are both discarded → defaults. With a temporarily provided
  gateway whose `load()` **never resolves**, the app still boots and renders on
  the defaults after the 1 s timeout (temporary gateway removed afterwards).
- **No console output at all** on any boot path (playwright `console` +
  `pageerror` both empty).
- `grep -rn "var(--mesh-\|var(--ink-\|var(--brand-\|var(--night-" frontend/src`
  → **matches only in `tokens.css`** (45). REQ-103 met.

### ⚠️ Order deviation, stated openly

The TODO's order is not implementable as written and was followed with three
swaps — **content unchanged, every step's own Validate clause still run**:

| Step | Depends on | Consequence |
|---|---|---|
| S4 (`TranslationService`) | injects `PreferencesService` (S5) | S5 was done first |
| S5 (`PreferencesService`) | `PropertySettingsConfig` / `AppearanceMode` types (S7) | S7 + S8 were done first |
| S38 (app initializer) | calls `content.settingsConfig()` (S9) | S9/S10 were done first |

Actual order: **S1 · S2 · S3 · S7 · S8 · S5 · S4 · S9 · S10 · S6 · S38 · S11 ·
S12 · S13**. The red-build window the plan sanctions for S7 ("`ng build` fails
until S8 supplies the new shape — that is the point") in fact runs S7 → S10, not
S7 → S8: after S8 the only remaining error is `heading-block`'s
`headline.replace(...)`, which is exactly what S10 fixes. Nothing was committed
while red.

### Two type names the design left ambiguous (decided, worth knowing)

`02-design.md` §5.1 says `PropertyConfig`'s fields **become** `Localized<string>`;
§5.2 types the resolved signal as `Signal<PropertyConfig>`. Both cannot be true.
Resolution, in `property.model.ts`:

- `PropertyConfig` / `MenuItem` / `ActionCard` — **as authored**, per-language
  maps. Only `example-property.data.ts` and `PropertyContentService` see these.
- `ResolvedPropertyConfig` / `ResolvedMenuItem` / `ResolvedActionCard` — **as
  components see it**, one language chosen. `property` is a
  `Signal<ResolvedPropertyConfig>`.
- Each pair comes from one generic shape (`MenuItemShape<TText>`, …) so the two
  cannot drift when a field is added.

`AppearanceMode` is **declared in `core/preferences/preference-gateway.ts`** and
re-exported from `property.model.ts`. Reason: S6's Validate requires the gateway
files to import **nothing** from `core/content/`, and `StoredPreferences` needs
the type. Same trick as `LanguageCode`, which lives in `core/i18n/`.

### What is deliberately NOT done in this chunk

- **No new spec file.** Every test is a section-I step (S27–S35, S39) and a
  separate delegation. Behaviour *was* checked with three throwaway specs
  (language-switch re-render, detached `t()`, the clock's fake timers) which all
  passed and were then deleted — reproduce them in S27/S31/S29, do not trust
  this note as coverage.
- **The existing specs were adapted, not extended** — mechanical only:
  `getProperty()` → `property()`, `MenuItem` → `ResolvedMenuItem`, mocks gained
  `settingsConfig`, and six specs now provide
  `{ provide: PREFERENCE_GATEWAY, useClass: SessionPreferenceGateway }` because
  `PropertyContentService` → `PreferencesService` → the gateway token. S39's
  `FakePreferenceGateway` should replace that stand-in wherever a test asserts
  persistence.
- **The two German literals are still in components** — `"Bald verfügbar."`
  (`section-placeholder.component.html`) and `aria-label="Hauptnavigation"`
  (`sidebar.component.html`). No step in A–F migrates them; their catalogue keys
  (`section.comingSoon`, `nav.aria`) already exist. **Pick this up in section G**
  or S35's scan test will fail — it is the one loose end of this chunk.
- Nothing in `docs/design/**` was touched (that is H), and
  `docs/system-context.md` still carries the planner's round-2 notes (S26).

### Files added (9) and changed (29)

New: `core/i18n/{translation.model.ts,translation.service.ts,catalog/{de,en,index}.ts}`,
`core/preferences/{preferences.service.ts,preference-gateway.ts,session-preference-gateway.ts}`,
`core/clock/clock.service.ts`. Changed: `styles/tokens.css`,
`layout/shell/shell.component.{css,html,ts}`, `app.{component,config}.ts`,
`core/content/*`, the five migrated components + `sidebar`/`action-card` input
types, seven specs, and `frontend/CLAUDE.md` (S13, verbatim).

### Useful to know for the next chunk

- `npx ng serve --port 4300` — no server is left running; a **stale** one bit
  this session (it served pre-edit CSS for ~20 minutes). If a measurement looks
  impossible, check the served file first (`curl -s .../styles.css | grep …`).
- On this hardware a full `ng test` occasionally times out a single test at
  5 s / 10 s under load (seen once, green on re-run). Not a code defect —
  do not "fix" it by loosening a timeout without checking.
- `matchMedia` is guarded (`globalThis.matchMedia?.(…)`) because jsdom omits it;
  S28 must provide its own mock.

---

## PLANNING STATE (2026-08-18, `planner`, round 2 — kept for context)

**Phase:** `04_analyze` — **PASS ✅, nothing blocked.** All four approval gates
were answered by the user on 2026-08-18, the owning artifacts were amended, and
the gate was re-run from scratch. **39 steps, 0 blocked. 27 requirements, 27
covered, 0 orphans.**

**Next action: hand `03-todo.md` to `dev`.** No decision is outstanding.

**Branch:** `feature/home-screen-prototype` — deliberately, on the user's
instruction. This is a continuation of the Home-screen work, not a new branch.

**Still zero application code written, and still zero MCP calls** in either
round. `frontend/src/**`, `frontend/CLAUDE.md`, `docs/design/tokens.md`,
`docs/design/design-sheet.md` and `docs/design/screens/home.md` remain
untouched — every one of them is edited by a TODO step, by `dev`.

### The four answers, and what each one cost

| # | Decision | Answer (2026-08-18) | Impact |
|---|---|---|---|
| 1 | i18n mechanism | **Hand-rolled signal service**, no dependency; languages stay German + English | As recommended. S3/S4 unchanged |
| 2 | Persistence of the guest's choice | **The database owns it** — persisted on change, delivered on load, backend always supplies the defaults (`light`, `de`); stand-in until the backend exists | **Differs from the recommendation, and is the whole of round 2's work.** See below |
| 3 | `frontend/CLAUDE.md` amendment | **Approved verbatim** (*"Ja passt"*) | S2/S13 cleared. The approved text was **not touched** — see the warning below |
| 4 | "Follow system" appearance state | **Approved**, and the startup value is the backend default (`light`), never `prefers-color-scheme` | S5 cleared; S23 gained the `docs/design/tokens.md` correction |

### What round 2 changed, per document

| File | Change |
|---|---|
| `01-prd.md` | "Decisions requiring your approval" → **"Decisions taken"** (options kept, each with the user's own words, the answer and the date). REQ-121/REQ-122 restated for backend-owned persistence; **REQ-126** (async + failure tolerance) and **REQ-127** (no guest identity, nothing logged) added; REQ-103/REQ-105/REQ-119 unblocked; **REQ-106 reconciled** (see the trap below); acceptance criteria written for all of them; 4 new risks |
| `02-design.md` | **§6 rewritten** (6.1 layers · 6.2 the stand-in decision · 6.3 the contract · 6.4 bootstrap + no-flash · 6.5 the write path · 6.6 whose preference · 6.7 the backend's brief). §1 gains the gateway and the app initializer; §5.2 renames a signal; §10/§11/§13 follow; §12 is now a resolved-decision record. §4's approved text is byte-identical |
| `03-todo.md` | Blocked index → **decision record**. S6 rewritten; **S38** (persistence policy + bootstrap) and **S39** (fake gateway + async/persistence tests) added; S2/S3/S4/S5/S13 unblocked; S8/S9/S11/S15/S23/S26/S28/S30 adjusted. IDs are **append-only** — nothing was renumbered |
| `04-analyze.md` | Rewritten. New verdict, a "what changed since round 1" table with the blast radius as predicted vs. as it turned out, a mechanically verified coverage matrix, and an explicit "no constitution amendment needed" assessment |
| `docs/system-context.md` | *Persistence* and *Messaging / async* gained "planned, not built" notes; a fifth **open security question** added (the preference write endpoint); the in-flight component note mentions the gateway. S26 replaces all of it with real entries |
| `docs/features/index.md` | Row updated |

## ⚠️ Do not undo these

- **The `frontend/CLAUDE.md` text in `02-design.md` §4 is approved *as
  drafted*.** Round 2 deliberately added nothing to it, even though the
  persistence answer suggests two rules worth writing down (the gateway must
  stay a DI leaf; only `PreferencesService` touches the guest's preference).
  Editing that text makes the approval stale and re-opens a closed gate. Both
  rules live in `02-design.md` §1/§6 and in `docs/system-context.md` instead.
  If a future round genuinely needs the clause changed: **propose the new
  wording and stop**, do not write it.
- **Step IDs are append-only.** S38/S39 sit in their logical sections, so the
  file does not read in numeric order. Renumbering would break every reference
  in the design, the gate and this file.
- **REQ-IDs start at 101** — the shipped code's comments already carry
  `REQ-003`, `REQ-014`, `REQ-023` … from `home-screen-prototype`.

## The persistence design in six lines (the part with no prior art)

1. The guest's preference is **backend state**, not UI state: `PreferenceGateway`
   = `load()` / `save()` / `clear()`, all `Promise`-returning, whole-record
   writes, one injection token, one provider line.
2. **Policy lives above the transport** (`PreferencesService`), so swapping the
   transport cannot change behaviour — that is what makes REQ-122 testable.
3. **The stand-in is `sessionStorage`**, not in-memory. Argument in §6.2: the
   decided architecture already carries a choice across guests, and a
   forgetting stand-in would leave the whole hydrate path dead code until the
   backend lands. One class + one line to change your mind
   (`InMemoryPreferenceGateway`, named in the design).
4. **No flash, by ordering, not by speed:** a `provideAppInitializer` applies
   the property defaults and `await`s `hydrate()` (bounded, never rejects)
   before `App`'s constructor, its effects and the first paint.
5. **A late answer never overwrites a fresh choice** — applied per setting and
   only while that setting's override is still `null`. A stored value outside
   the configured option list is discarded, not applied.
6. **Failure is normal, not exceptional:** optimistic local update, coalescing
   writer, bounded retry, then stop. No error UI, **nothing logged** (this
   project has no logging convention yet, and a failed preference write is not
   the place to invent one).

## Two contradictions found while grilling, and fixed

- **REQ-106 vs. REQ-121.** REQ-106 promised a language switch causes "no
  network request of any kind"; REQ-121 now requires a persistence write on
  exactly that event. Both rewritten: nothing is fetched *to render* the
  switch, the write is same-origin, fire-and-forget and outside the render
  path, and no external host is ever contacted. Left alone, `06_review` would
  have had to pick which requirement to violate.
- **`settings` twice.** `PropertyConfig.settings` is the sidebar's Einstellungen
  menu item (`home-screen-prototype` REQ-018). The new content signal would
  have given us `content.settings()` and `content.property().settings` side by
  side, meaning different things. Renamed to `settingsConfig` (§5.2, S9); the
  type keeps its name.

## Design decisions worth not re-litigating

- **The dark palette is designed and measured**, not sketched. Headline fact:
  **dark has no text role below 4.5 : 1; light has two** (3.48 and 3.99). One
  dark role scores lower than light (primary on glass, 8.91 vs 10.89) — both
  far above any threshold.
- **The palette lives in a new `docs/design/dark-theme.md`**, not in
  `tokens.md` (the as-built Figma record) and not in `design-sheet.md`.
- **The mesh must become semantic roles.** Branding primitives are written
  **inline on `:root`**, and an inline style beats `[data-theme='dark']`. Leave
  the mesh on primitives and a *branded* property silently gets a light mesh in
  dark mode. This is what forced decision #3.
- **The status bar overlays, it does not push.** A flow row would move Home's
  measured, signed-off geometry (heading ink y = 88, card row bottom y = 744).
- **The DI cycle was designed out, not worked around.** `PreferencesService`
  receives defaults via `applyPropertyDefaults()` and injects only the gateway
  — which must itself stay a leaf and never inject `PropertyContentService`.
- **Two controls per setting is intentional**: a compact dropdown in the status
  bar, all-options-visible in Settings, both bound to the same signals.

## Traps recorded in `04-analyze.md` → "Notes carried into implementation"

Fourteen of them now. The five new ones: `hydrate()` must never reject ·
defaults are applied before hydration · a late value only fills a `null`
override · the gateway is a DI leaf · writes coalesce, they do not queue.

## What this feature hands to someone else

Recorded in `02-design.md` §6.7 and `docs/system-context.md`, **not** decided
here and still approval-gated: the datastore, the preference endpoint's shape,
its server-side validation, **its security** (the product's first
unauthenticated write surface on an untrusted LAN), and what clears a stored
preference at check-out. Guest identity stays open on purpose — the stored
record carries no identifier of any kind.
