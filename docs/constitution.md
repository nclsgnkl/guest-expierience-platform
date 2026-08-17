# Constitution: Guest Experience Platform
**Status:** Ratified
**Version:** 1.1.0 | **Last amended:** 2026-08-17

> **1.1.0** — added principle 4 (self-hosted on premises, untrusted local
> network, no central patching) and the two security constraints that follow
> from it. Additive, nothing contradicted.
>
> **1.0.0** — **ratified.** Reviewed and set in force, so the `04_analyze` gate
> now checks compliance with every principle and constraint below, not only with
> the approval gates. A major bump because enforcement itself changed, which is
> the kind of change that can make previously acceptable work non-compliant.
>
> **0.3.0** — recorded Vitest as the unit-test runner and noted that no
> end-to-end framework is chosen yet.
>
> **0.2.0** — filled in the project name, the stack baseline and the first
> cross-cutting conventions, following the design import and the token
> architecture decisions of 2026-08-17. Nothing pre-existing was contradicted,
> hence a minor bump.

> Project-level governance. Created once via `/constitution` and amended rarely.
> Every feature phase (`01_prd` … `07_improve`) must respect it; the `04_analyze`
> gate checks compliance before implementation. A `Draft` constitution is treated
> as "not yet enforced" — set `Status: Ratified` once you've reviewed it.
>
> **Exception:** "Decisions Requiring Your Approval" below is in force from day
> one, in `Draft` just as much as in `Ratified`. A brand-new project is exactly
> when the biggest decisions (stack, datastore, first dependencies) get made —
> `Draft` never licenses an agent to decide those alone.

## Principles

1. **Property-specific content and branding are data, never code.** Anything
   that differs between one property and the next — texts, WiFi details, house
   rules, contacts, recommendations, brand colors, artwork — arrives as
   configuration. Onboarding an additional property must never require a code
   change or a deploy. Components stay presentational and read from a config
   source; they never hardcode one property's values.
2. **Content is reached through a service, never imported into a component.**
   Local UI state uses Angular **signals**; content and configuration are
   provided via Angular DI. This keeps today's static JSON swappable for a real
   backend later without touching components.
3. **Guest-sensitive data is never logged.** This app deliberately holds door
   and key-box codes, alarm codes, WiFi passwords and host contact details.
   None of it may reach a log, an analytics event, or an error report — not even
   at debug level, and not in a stack trace's captured state.
4. **Self-hosted on premises, on a network we do not control.** Every property
   runs its **own instance** on a local server in the building; there is no
   central service holding many properties. Three consequences bind every
   change:
   - **It must run without internet.** No runtime dependency on an external
     host — no CDN fonts or stylesheets, no third-party scripts, no analytics or
     telemetry, no remote images. Fonts, icons and assets ship inside the build
     artifact. A feature that only works online must state that dependency
     explicitly and degrade gracefully without it.
   - **The local network is not trusted.** The server shares a LAN with guest
     devices, and possibly with neighbours. Anything reachable over HTTP is
     reachable by anyone on that network — "not linked in the UI" is never a
     protection.
   - **It cannot be patched centrally.** Instances run in buildings we may have
     no access to, so an unpatched vulnerability can persist for a long time.
     Prefer fewer dependencies, and prefer platform features over libraries. A
     dependency is not just code you add, it is code you must keep updating in
     the field.
5. **Components reference semantic design tokens only.** Never a raw hex value,
   never a raw pixel size. Raw values live once, in the token layer
   (`docs/design/tokens.md`), which is what makes theming and per-property
   branding possible at all.
6. **Tests accompany code** — every implemented behavior has a test.
7. **Traceability** — every PRD requirement has a stable `REQ-ID`; every TODO task traces to one.

## Constraints

- **Angular + TypeScript** (strict mode), standalone components, Angular Router,
  signals for local UI state. No backend, auth or admin area in the first step —
  content comes from static JSON/TS files.
- **Styling is plain CSS with custom properties.** No Tailwind, no SCSS, no
  component library as a visual base. Sizes are expressed in `rem` against a
  16px root, never in fixed pixels — this is what lets the UI scale to other
  display sizes later. Exceptions: hairline borders and blur radii stay in `px`.
- **Approved dependencies:** `@angular/cdk` (overlay, dialog, menu, focus trap,
  a11y — used unstyled), `@ng-icons/core` + `@ng-icons/phosphor-icons`
  (Phosphor icon set), **`vitest` + `jsdom`** (unit tests — the Angular CLI
  default since v21, installed by `ng new` rather than added by hand).
  Anything beyond these needs approval as below.
- **Tests run on Vitest.** Karma/Jasmine are not used; Karma has been deprecated
  since 2023 and Angular's default moved to Vitest. No end-to-end framework is
  chosen yet — that is a separate decision, still open.
- **No dependency-manifest edits** (`package.json`, `pom.xml`, `requirements.txt`, …) without explicit approval.
- **Branch discipline** — no write work on `main`/`master`; use a feature branch.
- Secrets never hardcoded, never logged.
- Input validated at every system boundary.
- **No external runtime requests.** Self-host fonts (Plus Jakarta Sans), bundle
  icons, embed images. Never a `<link>` or `fetch` to Google Fonts, a CDN, or
  any third-party host in shipped code. This is checkable at review time and is
  the most common way the on-premise principle gets broken by accident.
- **Security is a review category, not a phase.** Every change is assessed for
  what it exposes on a shared network: what the server serves without
  authentication, what ends up in the client bundle, and what an unauthenticated
  device on the same LAN can reach. The `reviewer` flags this like any other
  defect; `architect` is escalated to when a change alters the exposure model.
- **Design-tool (MCP) requests are metered and scarce.** Only the
  `design-import` skill may issue one, only on explicit user invocation
  (`/design-import`), and only after confirming the cost. Every other agent
  reads the extracted specs under `docs/design/**` instead. Treat a missing
  detail as a question for the user, not a reason to query.

## Decisions Requiring Your Approval

Never decided by an agent alone — always proposed with a recommendation and
held until you confirm, no matter how far into the project you are (this is
not just a greenfield/`00_constitution` rule):

- A new dependency, or replacing/removing an existing one
- A datastore, messaging system, hosting choice, or other infrastructure
- Spending a metered design-tool request
- **Changing** how design tokens are realised in code. The original choice was
  made on 2026-08-17 (CSS custom properties, two layers — see Constraints and
  `docs/design/tokens.md` → "Implementation"); revisiting it is a fresh
  approval, not an implementation detail
- A new architectural pattern or cross-cutting convention not already covered
  by this constitution or an existing nested `CLAUDE.md`
- Any new entry a subagent wants to add to a nested `CLAUDE.md` — the exact
  text is proposed first, written only after you confirm it
- Anything that would contradict or replace an existing nested `CLAUDE.md` entry

**How this works technically:** a subagent (`dev`, `planner`, `architect`)
cannot pause mid-task and wait for your answer — it stops, states its
recommendation, and ends its turn without implementing or writing anything
for that decision. `lead` relays the question and recommendation to you, then
re-delegates to the same subagent once you've answered. Outside the `lead`
workflow (e.g. a direct `/build`), the session you're talking to relays it
directly.

## Quality bar ("done")

- Builds and all tests green.
- Conventions from nested `CLAUDE.md` files followed.
- No sensitive data in logs.
- `06-review.md` result is PASS with every `REQ-ID` implemented.

## Governance

- Amendments require your own review and a version bump (semver).
- Record each amendment date above and the rationale in a CHANGELOG, if you keep one.
