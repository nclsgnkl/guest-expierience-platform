# design-sync notes — Guest Experience Platform

Findings that should survive between syncs. Human-editable; `build.sh` never
reads this file.

## Why this repo does not use the /design-sync converter

`frontend/` is an Angular 22 application, not a React component library. There
is no Storybook, no `*.stories.*`, and no publishable component package
anywhere in the repo (`packages/` holds only a generated OpenAPI client).
Claude Design renders designs from React, reading components off a global that
a compiled `_ds_bundle.js` defines — so there is nothing here that runtime
could render. The sync therefore ships the design system's **look** only:
`styles.css` and its imports, the self-hosted type, and the `docs/design`
spec as guidelines. `.design-sync/build.sh` assembles exactly that, and no
`_ds_sync.json` anchor is written — with no components to verify, every sync
simply rebuilds and re-uploads, which is cheap.

If a React component library is ever added to this repo, drop this approach
and re-run `/design-sync` from scratch: the converter path is strictly better.

## Adaptations made to the shipped stylesheets

- **Font URLs.** `global.css` references `/assets/fonts/plus-jakarta-sans/…`,
  an absolute path that only resolves under the Angular app's asset pipeline.
  `build.sh` rewrites these to `../fonts/…`, relative to the bundle's own
  layout, and hard-fails if any rewrite is missed — an unrewritten path would
  render every design in fallback sans-serif with no visible error.

Nothing else is modified. The stylesheets are otherwise byte-identical to
`frontend/src/styles/`.

## Known fidelity gaps in the uploaded bundle

- **`.icon-gradient-active` needs a paint server that does not ship.** The rule
  fills an SVG glyph via `url('#icon-gradient-active')`, but the
  `<linearGradient>` it points at is declared in
  `frontend/src/app/layout/shell/shell.component.html`, not in any stylesheet.
  In the design project the glyph falls back to flat `currentColor`. The
  conventions header carries a standalone `<defs>` snippet as the workaround.
- **The kiosk root-scaling rule ships verbatim.** `html { font-size:
  min(1.25vw, 1.9231vh) }` and `html, body { min-width: 1024px }` are real
  product behaviour, so they were kept rather than neutralised — but they mean
  designs only look right at or above 1024×768 landscape, and `rem` sizes
  track the artboard rather than being absolute. The conventions header says
  so explicitly.
- **Reference renders are not uploaded.** `docs/design/assets/*.png` are
  excluded; images referenced from the guideline markdown will not resolve.
