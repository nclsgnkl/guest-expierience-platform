/**
 * Browser measurement instrument for the three full-screen states — NOT a
 * test suite (the `measure.mjs` charter applies verbatim: it reports, always
 * exits 0, asserts nothing; playwright is review-time tooling only).
 *
 * Covers what `measure.mjs`'s route grid cannot reach, because these states
 * depend on kiosk/backend state (guest-frontend-integration REQ-521/522,
 * specs: docs/design/screens-inhouse/*.md):
 *
 *   PAIRING     entered by seeding an INVALID device token: the 401 wipes it
 *               (REQ-509) and the kiosk lands on /pairing — no backend
 *               mutation, the dev seed device stays valid.
 *   CONNECTION  entered by blocking /api at the browser with a valid token
 *               and no cache.
 *   STALE pill  entered by loading once online (cache written), then
 *               reloading with /api blocked — bootstrap falls back to the
 *               cache and the pill appears immediately.
 *   BANNER      the same, with the cached record's `storedAt` rewritten to 61
 *               minutes ago before the reload — past the escalation threshold
 *               on both environments (an hour in production, a minute under
 *               the `development` configuration this instrument runs against).
 *               The escalation (REQ-952) is a function of the cache's own age,
 *               so this measures the cold-boot case at the same time. Both
 *               hosts — the shell (occupied, the guest wording) and standby
 *               (vacant, the staff wording), the latter needing
 *               GXP_SMOKE_ADMIN_PW like the STANDBY cell.
 *               A third variant renames the active stay to a 37-character
 *               UNBROKEN display name (and renames it back): Home's headline
 *               is manager-authored with `{stay.displayName}` interpolated,
 *               so the one input that can push it under the bar comes from
 *               whoever books the room, and no other cell varies it. Needs
 *               GXP_SMOKE_ADMIN_PW.
 *   STANDBY     REQUIRES the manager API (deletes the active stay, recreates
 *               it afterwards): dev-only, needs GXP_SMOKE_ADMIN_PW.
 *   ARTWORK     Home with a property artwork configured — the variant the dev
 *               seed does NOT produce, which is exactly how a horizontally
 *               scrolling Home shipped past 72/72 measure cells (review
 *               🔴-1). Uploads a throwaway ARTWORK asset through the manager
 *               API, points the property at it, measures, and restores both.
 *               Also needs GXP_SMOKE_ADMIN_PW.
 *
 * The theme axis on pairing/connection is driven by writing
 * `data-theme`/`lang` directly (those screens deliberately carry no controls;
 * an unpaired kiosk runs on the catalogue-default language by design — the
 * EN/dark variants measured here are the states a previously-configured,
 * later-revoked kiosk can be in). Standby drives both axes through its real
 * status bar.
 *
 * Usage (backend + serve must be up):
 *   GXP_SMOKE_ADMIN_PW=… node tools/measure-states.mjs [baseUrl]
 */
import { deflateSync, crc32 } from 'node:zlib';

import { chromium } from 'playwright';

const baseUrl = process.argv[2] ?? 'http://localhost:4200';
const API = process.env.GXP_API_URL ?? 'http://localhost:8080';
const ADMIN_PW = process.env.GXP_SMOKE_ADMIN_PW ?? null;
const ADMIN_USER = process.env.GXP_SMOKE_ADMIN_USER ?? 'admin';
const VIEWPORTS = [
  [1024, 768],
  [1280, 832],
];
const THEMES = ['light', 'dark'];
const LANGUAGES = ['de', 'en'];
const DEV_TOKEN = 'gxp-dev-device-token';

const findings = [];
const note = (kind, detail) => {
  findings.push(`${kind}: ${detail}`);
  console.log(`  !! ${kind}: ${detail}`);
};

const box = (r) =>
  r
    ? {
        x: Math.round(r.x),
        y: Math.round(r.y),
        w: Math.round(r.width),
        h: Math.round(r.height),
        right: Math.round(r.right),
        bottom: Math.round(r.bottom),
      }
    : null;

const headlineSummary = (h) =>
  h
    ? `{lines:${h.lines},right:${Math.round(h.right)},top:${Math.round(h.top)},bottom:${Math.round(h.bottom)},chars:${h.text.length}}`
    : null;

function collectState() {
  const b = (el) => (el ? el.getBoundingClientRect().toJSON() : null);
  const doc = document.documentElement;
  const text = (selector) => {
    const el = document.querySelector(selector);
    if (!el) return null;
    const range = document.createRange();
    range.selectNodeContents(el);
    return {
      text: el.textContent.trim(),
      lines: range.getClientRects().length,
      box: b(el),
    };
  };
  return {
    url: location.pathname,
    theme: doc.dataset.theme ?? null,
    lang: doc.getAttribute('lang'),
    scroll: {
      scrollW: doc.scrollWidth,
      clientW: doc.clientWidth,
      scrollH: doc.scrollHeight,
      clientH: doc.clientHeight,
    },
    pairing: document.querySelector('.pairing')
      ? {
          panel: b(document.querySelector('.pairing__panel')),
          title: text('.pairing__title'),
          instruction: text('.pairing__instruction'),
          code: text('.pairing__code'),
          // The text SPAN, not the flex <p>: a range over the flex row counts
          // the decorative dot as an extra client rect and misreads as a wrap.
          status: text('.pairing__status-text'),
          dot: b(document.querySelector('.pairing__status-dot')),
        }
      : null,
    connection: document.querySelector('.connection')
      ? {
          panel: b(document.querySelector('.connection__panel')),
          glyph: b(document.querySelector('.connection__glyph')),
          title: text('.connection__title'),
          body: text('.connection__body'),
        }
      : null,
    standby: document.querySelector('.standby')
      ? {
          block: b(document.querySelector('.standby__block')),
          headline: text('.standby__headline'),
          message: text('.standby__message'),
          logo: b(document.querySelector('.standby__logo')),
          statusBar: b(document.querySelector('.status-bar')),
          sidebar: !!document.querySelector('app-sidebar'),
          buttonsOutsideStatusBar: [...document.querySelectorAll('button')].filter(
            (btn) => !btn.closest('app-status-bar'),
          ).length,
        }
      : null,
    artwork: document.querySelector('.hero-artwork__img')
      ? {
          img: b(document.querySelector('.hero-artwork__img')),
          content: b(document.querySelector('.home-content')),
          heading: b(document.querySelector('.heading-block')),
          cards: b(document.querySelector('.home-content__cards')),
          // The rendered card, not the `display: contents` component host
          // (which has no box of its own).
          lastCard: b([...document.querySelectorAll('.action-card')].pop() ?? null),
          sidebar: b(document.querySelector('app-sidebar')),
          /* Which element the guest's finger reaches where the artwork
             overlaps the card row: the artwork is decorative and must never
             be it. */
          topmostOverCards: (() => {
            const cards = document.querySelector('.home-content__cards');
            if (!cards) return null;
            const r = cards.getBoundingClientRect();
            const el = document.elementFromPoint(r.right - 8, r.top + 8);
            return el ? el.className || el.tagName : null;
          })(),
        }
      : null,
    banner: document.querySelector('.staleness-banner')
      ? {
          box: b(document.querySelector('.staleness-banner')),
          title: text('.staleness-banner__title'),
          body: text('.staleness-banner__body'),
          asOf: text('.staleness-banner__as-of'),
          glyph: b(document.querySelector('.staleness-banner__glyph')),
          statusBar: b(document.querySelector('.status-bar')),
          cards: b(document.querySelector('.home-content__cards')),
          /* The headline's OWN bound, which is what the bar's left edge has
             to clear. `.heading-block` is a fixed `--size-heading` wide in
             every cell and for every name; `.home-content__screen` is the
             whole left flex item and is sized by the card row at its bottom
             (measured x137.6…793.6 at 1024), so guarding against that one
             reports a collision in every correct build. */
          headingBlock: b(document.querySelector('.heading-block')),
          /* Tighter still: the rendered text extent line by line. The
             element's box is the column's full width whatever the copy does,
             so only the line rects say how far the headline actually reaches
             — including a long unbroken display name that overflows. */
          headlineText: (() => {
            const el = document.querySelector('.heading-block__headline');
            if (!el) return null;
            const range = document.createRange();
            range.selectNodeContents(el);
            const rects = [...range.getClientRects()];
            if (!rects.length) return null;
            return {
              lines: rects.length,
              text: el.textContent.trim(),
              top: Math.min(...rects.map((r) => r.top)),
              bottom: Math.max(...rects.map((r) => r.bottom)),
              right: Math.max(...rects.map((r) => r.right)),
            };
          })(),
          /* Home's two flex items — recorded, not guarded. The widget column
             is only x819.2…998.4 at 1024 (179 px), and `.home-content__screen`
             runs to x793.6 because the card row at its bottom sizes it, not
             the heading: the bar legitimately lies over the empty band
             between the two. Guarding against either reports a collision in
             every correct build. */
          widgetColumn: b(document.querySelector('.home-content__widgets')),
          screenColumn: b(document.querySelector('.home-content__screen')),
          standbyBlock: b(document.querySelector('.standby__block')),
          /* The tri-state, measured rather than assumed: the pill must be
             gone while the banner is up (REQ-952). */
          pillStillUp: !!document.querySelector('.staleness-pill'),
          /* module-content REQ-978: `/info` is the first SECTION route on
             which the banner and real content are on screen together, and
             info.md computes the near-miss it produces (the bar clears the
             panel by 1.2 px at 1280 and overlaps by 0.2 px at 1024). Those
             are arithmetic; these are the boxes. Null on every other route. */
          infoTitle: b(document.querySelector('.section-host__title')),
          infoPanel: b(document.querySelector('.info-module')),
          /* The rendered extent of the values, not their element boxes: the
             `<dd>` is the panel's full inner width whatever the copy does, so
             only the line rects say how far a value actually reaches — which
             is the number info.md's third reason rests on (the values stay in
             the left half, the bar lands on the empty right-hand side). */
          infoValues: [...document.querySelectorAll('.info-module__value')].map((el) => {
            const range = document.createRange();
            range.selectNodeContents(el);
            const rects = [...range.getClientRects()];
            return {
              text: el.textContent.trim(),
              lines: rects.length,
              right: rects.length ? Math.max(...rects.map((r) => r.right)) : null,
            };
          }),
        }
      : null,
    pill: document.querySelector('.staleness-pill')
      ? {
          box: b(document.querySelector('.staleness-pill')),
          text: document.querySelector('.staleness-pill').textContent.trim(),
          statusBar: b(document.querySelector('.status-bar')),
          cards: b(document.querySelector('.home-content__cards')),
          heading: b(document.querySelector('.heading-block')),
        }
      : null,
  };
}

function reportScroll(m, label) {
  const s = m.scroll;
  const ok = s.scrollW <= s.clientW && s.scrollH <= s.clientH;
  console.log(
    `${label.padEnd(38)} theme=${m.theme} lang=${m.lang} scroll ${s.scrollW}/${s.clientW} ${s.scrollH}/${s.clientH} ${ok ? 'NO-SCROLL OK' : 'OVERFLOW'}`,
  );
  if (!ok) note('OVERFLOW', label);
  return ok;
}

/**
 * One manager login for every cell that needs the manager API. `username` is
 * required since the authentication hardening (2026-08-27) — the three call
 * sites that still posted `{ password }` alone got a `400 validation-failed`
 * and crashed the run on `JSON.parse`.
 */
async function managerToken() {
  const login = await fetch(API + '/api/manager/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: ADMIN_USER, password: ADMIN_PW }),
  });
  if (!login.ok) {
    throw new Error(`manager login failed: ${login.status} ${await login.text()}`);
  }
  return (await login.json()).token;
}

/**
 * A manager-driven block must cost only its own cells. Part A's review raised
 * this class against `measure.mjs` and it recurred here: a failing manager
 * call took the whole run down with it, which is exactly how the STANDBY and
 * ARTWORK cells stayed dead for a commit without anyone noticing. The header's
 * charter — it reports, it always exits 0 — only holds if a broken block
 * reports itself and lets the other cells run. Each block's own `finally`
 * still restores whatever it changed before the failure surfaces here.
 */
async function guarded(label, run) {
  try {
    await run();
  } catch (error) {
    const reason = error?.message ?? String(error);
    console.log(`  !! ${label} skipped: ${reason}`);
    note('INSTRUMENT', `${label} skipped: ${reason}`);
  }
}

/**
 * A restore inside a block's `finally` is the one call whose failure outlives
 * the run: a 4xx there leaves the dev property renamed or a stay missing for
 * every later run, on the kiosk and in the next reviewer's screenshots, while
 * the log claims it was put back. It must never throw — that would mask
 * whatever sent us into the `finally` — so it reports through `note()`, which
 * `guarded()` already makes impossible to mistake for a clean run.
 */
async function restore(description, call) {
  try {
    const response = await call();
    if (response.ok) {
      console.log(`  (${description})`);
      return;
    }
    const detail = await response.text().catch(() => '');
    note('INSTRUMENT', `restore FAILED — ${description}: ${response.status} ${detail}`);
  } catch (error) {
    note('INSTRUMENT', `restore FAILED — ${description}: ${error?.message ?? String(error)}`);
  }
}

const browser = await chromium.launch();

/* --------------------------- PAIRING --------------------------------- */
console.log('\n######## PAIRING (invalid token → 401 wipe → /pairing) ########');
for (const [w, h] of VIEWPORTS) {
  const context = await browser.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: 1 });
  const page = await context.newPage();
  await page.addInitScript(() => {
    if (!localStorage.getItem('gxp.device-token')) {
      localStorage.setItem('gxp.device-token', 'measure-states-invalid-token');
    }
  });
  await page.goto(baseUrl + '/', { waitUntil: 'load', timeout: 120000 });
  await page.waitForSelector('.pairing__code', { timeout: 30000 });
  await page.waitForFunction(() => /\S{4} \S{4}/.test(document.querySelector('.pairing__code')?.textContent ?? ''), { timeout: 20000 });
  for (const theme of THEMES) {
    await page.evaluate((t) => (document.documentElement.dataset.theme = t), theme);
    await page.waitForTimeout(150);
    const m = await page.evaluate(collectState);
    reportScroll(m, `pairing ${w}x${h} ${theme}`);
    const p = m.pairing;
    console.log(
      `  panel ${JSON.stringify(box(p.panel))} title-lines=${p.title.lines} instr-lines=${p.instruction.lines} code="${p.code.text}" code-lines=${p.code.lines} status-lines=${p.status.lines}`,
    );
    if (p.instruction.lines > 2) note('WRAP', `pairing instruction ${p.instruction.lines} lines at ${w}x${h}`);
    if (p.code.lines !== 1) note('WRAP', `pairing code wrapped at ${w}x${h}`);
    if (p.status.lines !== 1) note('WRAP', `pairing status line wrapped at ${w}x${h}`);
  }
  await context.close();
}

/* -------------------------- CONNECTION -------------------------------- */
console.log('\n######## CONNECTION (valid token, /api blocked, no cache) ########');
for (const [w, h] of VIEWPORTS) {
  const context = await browser.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: 1 });
  await context.route('**/api/**', (route) => route.abort('connectionrefused'));
  const page = await context.newPage();
  await page.addInitScript((token) => localStorage.setItem('gxp.device-token', token), DEV_TOKEN);
  await page.goto(baseUrl + '/', { waitUntil: 'load', timeout: 120000 });
  await page.waitForSelector('.connection__panel', { timeout: 30000 });
  for (const theme of THEMES) {
    await page.evaluate((t) => (document.documentElement.dataset.theme = t), theme);
    await page.waitForTimeout(150);
    const m = await page.evaluate(collectState);
    reportScroll(m, `connection ${w}x${h} ${theme}`);
    const c = m.connection;
    console.log(
      `  panel ${JSON.stringify(box(c.panel))} glyph ${JSON.stringify(box(c.glyph))} title-lines=${c.title.lines} body-lines=${c.body.lines}`,
    );
    if (c.body.lines > 2) note('WRAP', `connection body ${c.body.lines} lines at ${w}x${h}`);
  }
  await context.close();
}

/* ------------------------- STALENESS PILL ------------------------------ */
console.log('\n######## STALE (cache present, /api blocked) — the pill ########');
for (const [w, h] of VIEWPORTS) {
  const context = await browser.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: 1 });
  const page = await context.newPage();
  await page.addInitScript((token) => localStorage.setItem('gxp.device-token', token), DEV_TOKEN);
  await page.goto(baseUrl + '/', { waitUntil: 'load', timeout: 120000 });
  await page.waitForSelector('.home-content', { timeout: 60000 }); // online — cache written
  await context.route('**/api/**', (route) => route.abort('connectionrefused'));
  /* Re-stamp the record as of NOW, the exact inverse of what the BANNER
     blocks do. The pill only exists BELOW the threshold, and under the
     `development` configuration that window is one minute wide (owner,
     2026-08-28) — so a slow reload between the online load and this
     measurement would silently escalate the cell to the banner and hang
     `waitForSelector` below. Nothing measured changes: `storedAt` moves by
     the couple of seconds the online load took, and the rendered `asOf` is
     minute-formatted. */
  if (!(await page.evaluate(ageTheCache, 0))) {
    note('NO CACHE', `nothing cached to re-stamp at ${w}x${h}`);
  }
  await page.reload({ waitUntil: 'load', timeout: 120000 });
  await page.waitForSelector('.staleness-pill', { timeout: 30000 });
  for (const theme of THEMES) {
    await page.evaluate((t) => (document.documentElement.dataset.theme = t), theme);
    await page.waitForTimeout(150);
    const m = await page.evaluate(collectState);
    reportScroll(m, `stale-pill ${w}x${h} ${theme}`);
    const pill = box(m.pill.box);
    const bar = box(m.pill.statusBar);
    const cards = box(m.pill.cards);
    const heading = box(m.pill.heading);
    console.log(
      `  pill ${JSON.stringify(pill)} "${m.pill.text}" | bar bottom=${bar.bottom} right=${bar.right} | heading.y=${heading?.y} cards.y=${cards?.y}`,
    );
    if (pill.y < bar.bottom) note('COLLISION', `pill overlaps status bar at ${w}x${h}`);
    if (heading && pill.bottom > heading.y && pill.x < heading.right)
      note('COLLISION', `pill x heading at ${w}x${h}`);
    if (pill.h > 28.5) note('SPEC', `pill taller than 1.75rem envelope at ${w}x${h}: ${pill.h}px`);
  }
  await context.close();
}

/* ------------------------ ESCALATED BANNER ----------------------------- */
/**
 * REQ-952: after the escalation threshold without successful backend contact
 * the pill is REPLACED by a panel. Driven through the cache, not through a
 * clock hack — `staleSince` is seeded from the cached record's `storedAt`, so
 * ageing the record past the threshold is exactly the cold-boot case the spec
 * calls out.
 *
 * **61 minutes is an offset, not the threshold**, and deliberately not derived
 * from one. The threshold now lives in `src/environments/environment*.ts` —
 * 60 minutes in production, ONE MINUTE under the `development` configuration
 * (which is what `ng serve`, and therefore this instrument, runs). A `.mjs`
 * script cannot import that TypeScript module without a transpile step, and
 * scraping the literal out of the file would couple the instrument to the
 * source's formatting for no gain: the block needs any age that CLEARS the
 * threshold, and 61 minutes clears both values with room to spare. If a
 * future threshold ever exceeds an hour this number must grow with it.
 */
const AGED_CACHE_MINUTES = 61;

/**
 * `minutes` ago for ageing past the threshold; `0` to re-stamp the record as
 * of NOW, which is how the PILL block keeps its own window open — see there.
 */
function ageTheCache(minutes) {
  const raw = localStorage.getItem('gxp.cache.content');
  if (!raw) return false;
  const record = JSON.parse(raw);
  record.storedAt = Date.now() - minutes * 60_000;
  localStorage.setItem('gxp.cache.content', JSON.stringify(record));
  return true;
}

/** Both axes through the REAL status bar — it stays functional behind the banner. */
async function setThemeAndLanguage(page, theme, language) {
  await page.locator('.status-bar .status-bar-menu__trigger').nth(0).click();
  await page.waitForSelector('.cdk-overlay-container .status-bar-menu__panel');
  await page
    .locator('.cdk-overlay-container .status-bar-menu__option')
    .nth(language === 'de' ? 0 : 1)
    .click();
  await page.locator('.status-bar .status-bar-menu__trigger').nth(1).click();
  await page.waitForSelector('.cdk-overlay-container .status-bar-menu__panel');
  await page
    .locator('.cdk-overlay-container .status-bar-menu__option')
    .nth(theme === 'light' ? 0 : 1)
    .click();
  await page.waitForTimeout(200);
}

function reportBanner(m, label, w, h, host, theme, language) {
  reportScroll(m, label);
  const bn = m.banner;
  if (!bn) {
    note('MISSING', `banner not rendered for ${label}`);
    return;
  }
  const banner = box(bn.box);
  const bar = box(bn.statusBar);
  const cards = box(bn.cards);
  const block = box(bn.standbyBlock);
  console.log(
    `  banner ${JSON.stringify(banner)} title-lines=${bn.title.lines} body ${JSON.stringify(box(bn.body.box))} lines=${bn.body.lines} ` +
      `asOf="${bn.asOf.text}" | bar bottom=${bar?.bottom} right=${bar?.right} | cards ${JSON.stringify(cards)} block ${JSON.stringify(block)} ` +
      `| heading block ${JSON.stringify(box(bn.headingBlock))} headline ${headlineSummary(bn.headlineText)} ` +
      `screen col ${JSON.stringify(box(bn.screenColumn))} widget col ${JSON.stringify(box(bn.widgetColumn))}`,
  );
  if (m.theme !== theme || m.lang !== language) {
    note('PREFERENCE NOT APPLIED', `${label} asked ${theme}/${language} got ${m.theme}/${m.lang}`);
  }
  if (bn.pillStillUp) note('TRI-STATE', `pill and banner both up at ${label}`);
  // One line, since the 2026-08-27 redesign: the bar widens until the body
  // fits on one (connection.md -> "The escalated staleness banner").
  if (bn.body.lines !== 1) note('WRAP', `banner body ${bn.body.lines} lines at ${label}`);
  if (bn.title.lines > 1) note('WRAP', `banner title ${bn.title.lines} lines at ${label}`);
  if (bn.asOf.lines !== 1) note('WRAP', `banner timestamp wrapped at ${label}`);
  if (bar && banner.y < bar.bottom) note('COLLISION', `banner overlaps the status-bar cluster at ${label}`);
  if (banner.right > w || banner.bottom > h || banner.x < 0) {
    note('CLIPPED', `banner ${JSON.stringify(banner)} leaves the ${w}x${h} viewport at ${label}`);
  }
  if (cards && banner.bottom > cards.y && banner.x < cards.right) {
    note('COLLISION', `banner x Home's card row at ${label}`);
  }
  if (block && banner.bottom > block.y && banner.x < block.right && banner.right > block.x) {
    note('COLLISION', `banner x the standby block at ${label}`);
  }
  /* The bar is far wider than the pill it replaced, so the thing worth
     watching is its LEFT edge. It sits in the empty band between Home's
     heading and its card row (connection.md -> "Where the bar lands"), so
     what it must clear is the HEADING BLOCK — `--size-heading` wide,
     constant in every cell — and, tighter, the headline's own rendered
     lines, which is where an unbroken display name would show up. */
  const headingBlock = box(bn.headingBlock);
  if (
    headingBlock &&
    banner.bottom > headingBlock.y &&
    banner.y < headingBlock.bottom &&
    banner.x < headingBlock.right
  ) {
    note('COLLISION', `banner x Home's heading block (right ${headingBlock.right}) at ${label}`);
  }
  const headline = bn.headlineText;
  if (
    headline &&
    banner.bottom > headline.top &&
    banner.y < headline.bottom &&
    banner.x < Math.round(headline.right)
  ) {
    note(
      'COLLISION',
      `banner x Home's headline text (reaches x${Math.round(headline.right)}) at ${label}`,
    );
  }
  /* The ratchet under `overflow-wrap: anywhere` on the headline: the guest
     name is manager-supplied width, and without that one line an unbroken
     token ignores the column entirely. Independent of the bar, so it fires
     even where the two do not overlap. */
  if (headline && headingBlock && Math.round(headline.right) > headingBlock.right) {
    note(
      'OVERFLOW',
      `Home's headline runs past its own column (x${Math.round(headline.right)} > x${headingBlock.right}) at ${label}`,
    );
  }
  const expected = host === 'shell' ? 'Gastgeber|host' : 'Verwaltung|property manager';
  const shown = bn.body.text;
  const wrongAudience = host === 'shell' ? /Verwaltung|property manager/ : /Gastgeber|your host/;
  if (wrongAudience.test(shown)) {
    note('COPY', `${host} banner shows the other audience's wording: "${shown}" (want ${expected})`);
  }
}

/** The `/info` variant's own report: the two boxes info.md's arithmetic names. */
function reportInfoBanner(m, label) {
  reportScroll(m, label);
  const bn = m.banner;
  if (!bn) {
    note('MISSING', `banner not rendered for ${label}`);
    return;
  }
  const banner = box(bn.box);
  const panel = box(bn.infoPanel);
  if (!panel) {
    note('MISSING', `the info panel is not rendered from cache at ${label} (REQ-979)`);
    return;
  }
  /* Computed from the RAW rects, not from `box()`'s rounded ones: info.md's
     figure is 0.2 px, so a report rounded to whole pixels could not see it. */
  const clearance = +(bn.infoPanel.y - bn.box.bottom).toFixed(3);
  const valueRight = Math.max(...bn.infoValues.map((v) => v.right ?? 0));
  console.log(
    `  banner ${JSON.stringify(banner)} | title ${JSON.stringify(box(bn.infoTitle))} | panel ${JSON.stringify(panel)} ` +
      `| clearance panel.y - banner.bottom = ${clearance}px (${clearance >= 0 ? 'CLEARS' : 'OVERLAPS'}) ` +
      `| values ${JSON.stringify(bn.infoValues)} rightmost=${+valueRight.toFixed(2)} vs banner.x=${+bn.box.x.toFixed(2)} ` +
      `| horizontal gap = ${+(bn.box.x - valueRight).toFixed(2)}px`,
  );
  if (bn.infoValues.length !== 2) {
    note('CACHE', `${label} rendered ${bn.infoValues.length} value rows from cache, expected 2 (REQ-979)`);
  }
  if (valueRight > bn.box.x && clearance < 0) {
    /* The ONLY combination that turns the designed overlap into a real one:
       a value actually reaching the band the bar occupies. */
    note('COLLISION', `${label} a value reaches x${valueRight}, inside the bar's band from x${bn.box.x}`);
  }
  if (bn.pillStillUp) note('TRI-STATE', `pill and banner both up at ${label}`);
}

console.log('\n######## BANNER — shell (occupied): cache aged 61 min, /api blocked ########');
for (const [w, h] of VIEWPORTS) {
  const context = await browser.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: 1 });
  const page = await context.newPage();
  await page.addInitScript((token) => localStorage.setItem('gxp.device-token', token), DEV_TOKEN);
  await page.goto(baseUrl + '/', { waitUntil: 'load', timeout: 120000 });
  await page.waitForSelector('.home-content', { timeout: 60000 }); // online — cache written
  if (!(await page.evaluate(ageTheCache, AGED_CACHE_MINUTES))) {
    note('NO CACHE', `nothing cached to age at ${w}x${h}`);
  }
  await context.route('**/api/**', (route) => route.abort('connectionrefused'));
  await page.reload({ waitUntil: 'load', timeout: 120000 });
  await page.waitForSelector('.staleness-banner', { timeout: 30000 }); // the cold-boot case
  /* Both axes AFTER the offline boot, through the real status bar — it stays
     functional behind the banner. Deliberately no reload per cell: the
     preference override lives IN the payload, so a reload would drop the
     choice (and while vacant the write 409s by design), which is how an
     earlier version of this cell measured de/light four times over. */
  for (const theme of THEMES) {
    for (const language of LANGUAGES) {
      await setThemeAndLanguage(page, theme, language);
      const m = await page.evaluate(collectState);
      reportBanner(m, `banner-shell ${w}x${h} ${theme} ${language}`, w, h, 'shell', theme, language);
    }
  }
  await context.close();
}

/* --------------- BANNER on /info (module-content REQ-978) --------------- */
/**
 * The same cell, on a SECTION route. `measure.mjs`'s grid already covers
 * `/info` online, but the banner is a kiosk *state* and no route grid can
 * reach it — and `/info` is the first screen where the escalated bar and real
 * property content (the WiFi row) are up together by design.
 *
 * `docs/design/screens-inhouse/info.md` -> "No-scroll and the banner" computes
 * that the bar CLEARS the panel by 1.2 px at 1280 and OVERLAPS it by 0.2 px at
 * 1024, and says plainly that both are arithmetic and must be measured. This
 * block prints both boxes and the signed clearance.
 *
 * **An overlap here is not a defect and must not be "fixed" by moving
 * anything.** The banner is an overlay by construction (constitution v1.9.0,
 * `connection.md`), the 0.2 px is against its padding rather than its copy, and
 * the values are left-aligned so the bar lands on the panel's empty right-hand
 * side. What is worth knowing is whether the figure is a fifth of a pixel or a
 * centimetre — and whether a *value* ever reaches the band the bar occupies,
 * which is the only thing that would make it real.
 */
console.log('\n######## BANNER — /info (occupied): the section route, cache aged 61 min ########');
for (const [w, h] of VIEWPORTS) {
  const context = await browser.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: 1 });
  const page = await context.newPage();
  await page.addInitScript((token) => localStorage.setItem('gxp.device-token', token), DEV_TOKEN);
  await page.goto(baseUrl + '/info', { waitUntil: 'load', timeout: 120000 });
  await page.waitForSelector('.info-module', { timeout: 60000 }); // online — cache written
  if (!(await page.evaluate(ageTheCache, AGED_CACHE_MINUTES))) {
    note('NO CACHE', `nothing cached to age at /info ${w}x${h}`);
  }
  await context.route('**/api/**', (route) => route.abort('connectionrefused'));
  await page.reload({ waitUntil: 'load', timeout: 120000 });
  await page.waitForSelector('.staleness-banner', { timeout: 30000 });
  /* REQ-979: the module must render from the cache exactly as it did online —
     no degradation, no hidden row. */
  await page.waitForSelector('.info-module', { timeout: 30000 });
  for (const theme of THEMES) {
    for (const language of LANGUAGES) {
      await setThemeAndLanguage(page, theme, language);
      const m = await page.evaluate(collectState);
      reportInfoBanner(m, `banner-info ${w}x${h} ${theme} ${language}`);
    }
  }
  await context.close();
}

/* ------------- BANNER × the display-name axis (the ratchet) ------------- */
/**
 * Home's headline is manager-authored with `{stay.displayName}` interpolated,
 * so its rendered width is supplied by whoever books the room — the one input
 * to this screen the design cannot fix. An UNBROKEN token is the hard case:
 * before `heading-block.component.css` gained `overflow-wrap: anywhere` a
 * 37-character name ran to x878 at 1024 (429 px past its own column) and
 * passed under the bar. This cell renames the active stay, measures, and
 * renames it back; `reportBanner`'s OVERFLOW and headline COLLISION checks
 * are what it exists to exercise. Deliberately no hyphen and no space in the
 * name: both are break opportunities and would make the cell pass for the
 * wrong reason.
 */
const LONG_DISPLAY_NAME = 'Konstanzevonundzuhohenzollernsteinaue'; // 37 characters, unbroken

if (ADMIN_PW === null) {
  console.log('\n######## BANNER × long display name skipped — set GXP_SMOKE_ADMIN_PW ########');
} else {
  await guarded('BANNER x long display name', async () => {
    console.log(
      `\n######## BANNER — a ${LONG_DISPLAY_NAME.length}-character unbroken display name ########`,
    );
    const token = await managerToken();
    const auth = { Authorization: `Bearer ${token}` };
    const stays = await (await fetch(API + '/api/manager/stays', { headers: auth })).json();
    const active = Array.isArray(stays) ? stays.find((s) => s.status === 'BOOKED') : null;
    if (!active) {
      throw new Error('no BOOKED stay to rename — is the dev seed loaded?');
    }
    const putStay = (body) =>
      fetch(API + `/api/manager/stays/${active.id}`, {
        method: 'PUT',
        headers: { ...auth, 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
    const renamed = await putStay({ ...active, displayName: LONG_DISPLAY_NAME });
    if (!renamed.ok) {
      throw new Error(`rename failed: ${renamed.status} ${await renamed.text()}`);
    }
    try {
      for (const [w, h] of VIEWPORTS) {
        const context = await browser.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: 1 });
        const page = await context.newPage();
        await page.addInitScript((t) => localStorage.setItem('gxp.device-token', t), DEV_TOKEN);
        await page.goto(baseUrl + '/', { waitUntil: 'load', timeout: 120000 });
        await page.waitForSelector('.home-content', { timeout: 60000 }); // online — cache written
        if (!(await page.evaluate(ageTheCache, AGED_CACHE_MINUTES))) {
          note('NO CACHE', `nothing cached to age at ${w}x${h} (long name)`);
        }
        await context.route('**/api/**', (route) => route.abort('connectionrefused'));
        await page.reload({ waitUntil: 'load', timeout: 120000 });
        await page.waitForSelector('.staleness-banner', { timeout: 30000 });
        // Language only: the headline's extent is a font-and-copy question,
        // and the theme axis is already driven by the two cells above.
        for (const language of LANGUAGES) {
          await setThemeAndLanguage(page, 'light', language);
          const m = await page.evaluate(collectState);
          if (!m.banner?.headlineText?.text.includes(LONG_DISPLAY_NAME)) {
            note('NAME NOT APPLIED', `the long name is not in the headline at ${w}x${h} ${language}`);
          }
          reportBanner(m, `banner-longname ${w}x${h} light ${language}`, w, h, 'shell', 'light', language);
        }
        await context.close();
      }
    } finally {
      await restore(`display name restored to "${active.displayName}"`, () => putStay(active));
    }
  });
}

if (ADMIN_PW === null) {
  console.log('\n######## BANNER on standby skipped — set GXP_SMOKE_ADMIN_PW to drive vacancy ########');
} else {
  await guarded('BANNER on standby', async () => {
    console.log('\n######## BANNER — standby (vacant): the staff wording ########');
    const token = await managerToken();
    const stays = await (
      await fetch(API + '/api/manager/stays', { headers: { Authorization: `Bearer ${token}` } })
    ).json();
    const active = Array.isArray(stays) ? stays.find((s) => s.status === 'BOOKED') : null;
    if (!active) {
      throw new Error('no BOOKED stay to delete — is the dev seed loaded?');
    }
    await fetch(API + `/api/manager/stays/${active.id}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}` },
    });
    try {
      for (const [w, h] of VIEWPORTS) {
        const context = await browser.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: 1 });
        const page = await context.newPage();
        await page.addInitScript((t) => localStorage.setItem('gxp.device-token', t), DEV_TOKEN);
        await page.goto(baseUrl + '/', { waitUntil: 'load', timeout: 120000 });
        await page.waitForSelector('.standby__headline', { timeout: 45000 });
        if (!(await page.evaluate(ageTheCache, AGED_CACHE_MINUTES))) {
          note('NO CACHE', `nothing cached to age on standby at ${w}x${h}`);
        }
        await context.route('**/api/**', (route) => route.abort('connectionrefused'));
        await page.reload({ waitUntil: 'load', timeout: 120000 });
        await page.waitForSelector('.staleness-banner', { timeout: 30000 });
        for (const theme of THEMES) {
          for (const language of LANGUAGES) {
            await setThemeAndLanguage(page, theme, language);
            const m = await page.evaluate(collectState);
            reportBanner(m, `banner-standby ${w}x${h} ${theme} ${language}`, w, h, 'standby', theme, language);
          }
        }
        await context.close();
      }
    } finally {
      await restore('stay recreated', () =>
        fetch(API + '/api/manager/stays', {
          method: 'POST',
          headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
          body: JSON.stringify({
            roomId: active.roomId,
            displayName: active.displayName,
            checkIn: active.checkIn,
            checkOut: active.checkOut,
            status: 'BOOKED',
          }),
        }),
      );
    }
  });
}

/* --------------------------- STANDBY ----------------------------------- */
if (ADMIN_PW === null) {
  console.log('\n######## STANDBY skipped — set GXP_SMOKE_ADMIN_PW to drive vacancy ########');
} else {
  await guarded('STANDBY', async () => {
    console.log('\n######## STANDBY (stay deleted via manager API, recreated after) ########');
    const token = await managerToken();
    const stays = await (
      await fetch(API + '/api/manager/stays', { headers: { Authorization: `Bearer ${token}` } })
    ).json();
    const active = Array.isArray(stays) ? stays.find((s) => s.status === 'BOOKED') : null;
    if (!active) {
      throw new Error('no BOOKED stay to delete — is the dev seed loaded?');
    }
    await fetch(API + `/api/manager/stays/${active.id}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}` },
    });
    try {
      for (const [w, h] of VIEWPORTS) {
        const context = await browser.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: 1 });
        const page = await context.newPage();
        await page.addInitScript((t) => localStorage.setItem('gxp.device-token', t), DEV_TOKEN);
        await page.goto(baseUrl + '/', { waitUntil: 'load', timeout: 120000 });
        await page.waitForSelector('.standby__headline', { timeout: 45000 });
        for (const theme of THEMES) {
          for (const language of LANGUAGES) {
            // Both axes through the REAL status bar — it must stay functional here.
            await page.locator('.status-bar .status-bar-menu__trigger').nth(0).click();
            await page.waitForSelector('.cdk-overlay-container .status-bar-menu__panel');
            await page
              .locator('.cdk-overlay-container .status-bar-menu__option')
              .nth(language === 'de' ? 0 : 1)
              .click();
            await page.locator('.status-bar .status-bar-menu__trigger').nth(1).click();
            await page.waitForSelector('.cdk-overlay-container .status-bar-menu__panel');
            await page
              .locator('.cdk-overlay-container .status-bar-menu__option')
              .nth(theme === 'light' ? 0 : 1)
              .click();
            await page.waitForTimeout(200);
            const m = await page.evaluate(collectState);
            reportScroll(m, `standby ${w}x${h} ${theme} ${language}`);
            console.log(
              `  block ${JSON.stringify(box(m.standby.block))} headline="${m.standby.headline.text}" message-lines=${m.standby.message.lines} ` +
                `bar ${JSON.stringify(box(m.standby.statusBar))} sidebar=${m.standby.sidebar} buttonsOutsideBar=${m.standby.buttonsOutsideStatusBar}`,
            );
            if (m.theme !== theme || m.lang !== language) {
              note(
                'PREFERENCE NOT APPLIED',
                `standby asked ${theme}/${language} got ${m.theme}/${m.lang}`,
              );
            }
            if (m.standby.sidebar || m.standby.buttonsOutsideStatusBar > 0) {
              note('NAV SURFACE', `standby exposes navigation at ${w}x${h}`);
            }
          }
        }
        await context.close();
      }
    } finally {
      await restore('stay recreated', () =>
        fetch(API + '/api/manager/stays', {
          method: 'POST',
          headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
          body: JSON.stringify({
            roomId: active.roomId,
            displayName: active.displayName,
            checkIn: active.checkIn,
            checkOut: active.checkOut,
            status: 'BOOKED',
          }),
        }),
      );
    }
  });
}

/* --------------------------- ARTWORK ----------------------------------- */
/**
 * A throwaway PNG, generated here rather than committed: a diagonal gradient
 * with a hard border, at the spec's 1550 x 999 artwork proportions, so the
 * screenshot shows unambiguously where the layer sits and whether it is
 * cropped. Hand-rolled because the repo has no image dependency and may not
 * grow one (constitution -> dependency gate).
 */
function pngBytes(width, height) {
  const raw = Buffer.alloc((width * 3 + 1) * height);
  let o = 0;
  for (let y = 0; y < height; y++) {
    raw[o++] = 0; // filter: none
    for (let x = 0; x < width; x++) {
      const edge = x < 6 || y < 6 || x >= width - 6 || y >= height - 6;
      raw[o++] = edge ? 20 : Math.round((x / width) * 255);
      raw[o++] = edge ? 20 : Math.round((y / height) * 255);
      raw[o++] = edge ? 20 : 160;
    }
  }
  const chunk = (type, data) => {
    const head = Buffer.alloc(8);
    head.writeUInt32BE(data.length, 0);
    head.write(type, 4, 'ascii');
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(Buffer.concat([head.subarray(4), data])) >>> 0, 0);
    return Buffer.concat([head, data, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 2; // colour type: truecolour
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw)),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

/**
 * The artwork's specified box in CSS px at the 1280 x 832 reference
 * (docs/design/screens/home.md -> "Hero artwork (2:5)": 1550 x 999 at
 * x793/y155 raw on the 2x canvas, so halved here). The root font size is
 * viewport-proportional, so at 1024x768 every number scales by 1024/1280.
 */
const ARTWORK_SPEC = { x: 396.5, y: 77.5, w: 775, h: 499.5 };

if (ADMIN_PW === null) {
  console.log('\n######## ARTWORK skipped — set GXP_SMOKE_ADMIN_PW to configure one ########');
} else {
  await guarded('ARTWORK', async () => {
    console.log('\n######## ARTWORK (property artwork uploaded via the manager API) ########');
    const token = await managerToken();
    const auth = { Authorization: `Bearer ${token}` };

    const upload = await fetch(API + '/api/manager/assets?kind=ARTWORK', {
      method: 'POST',
      headers: { ...auth, 'Content-Type': 'image/png' },
      body: pngBytes(1550, 999),
    });
    if (!upload.ok) {
      throw new Error(`artwork upload failed: ${upload.status} ${await upload.text()}`);
    }
    const asset = await upload.json();
    const property = await (await fetch(API + '/api/manager/property', { headers: auth })).json();
    const previousArtwork = property.artworkAssetId ?? null;
    console.log(`  uploaded asset id=${asset.id} ${asset.width}x${asset.height} (${asset.byteSize} B)`);

    const putProperty = (body) =>
      fetch(API + '/api/manager/property', {
        method: 'PUT',
        headers: { ...auth, 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
    await putProperty({ ...property, artworkAssetId: asset.id });

    try {
      for (const [w, h] of VIEWPORTS) {
        const context = await browser.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: 1 });
        const page = await context.newPage();
        await page.addInitScript((t) => localStorage.setItem('gxp.device-token', t), DEV_TOKEN);
        await page.goto(baseUrl + '/', { waitUntil: 'load', timeout: 120000 });
        await page.waitForSelector('.hero-artwork__img', { timeout: 60000 });
        await page.waitForFunction(
          () => (document.querySelector('.hero-artwork__img')?.naturalWidth ?? 0) > 0,
          { timeout: 30000 },
        );
        for (const theme of THEMES) {
          await page.evaluate((t) => (document.documentElement.dataset.theme = t), theme);
          await page.waitForTimeout(150);
          const m = await page.evaluate(collectState);
          reportScroll(m, `home+artwork ${w}x${h} ${theme}`);
          const a = m.artwork;
          const img = box(a.img);
          console.log(
            `  img ${JSON.stringify(img)} | heading.x=${box(a.heading).x} cards ${JSON.stringify(box(a.cards))} ` +
              `| lastCard.right=${box(a.lastCard)?.right} viewport=${w} | over-cards=${a.topmostOverCards}`,
          );
          // The spec box, scaled the way every rem in this app scales.
          const s = w / 1280;
          const spec = {
            x: Math.round(ARTWORK_SPEC.x * s),
            y: Math.round(ARTWORK_SPEC.y * s),
            w: Math.round(ARTWORK_SPEC.w * s),
            h: Math.round(ARTWORK_SPEC.h * s),
          };
          for (const key of ['x', 'y', 'w', 'h']) {
            if (Math.abs(img[key] - spec[key]) > 1) {
              note('SPEC', `artwork ${key} ${img[key]} vs spec ${spec[key]} at ${w}x${h}`);
            }
          }
          // The regression itself: the content column must not have moved.
          if (box(a.heading).x !== box(a.cards).x) {
            note('LAYOUT', `heading and cards no longer share x at ${w}x${h}`);
          }
          if (box(a.lastCard) && box(a.lastCard).right > w) {
            note('CLIPPED', `last action card ends at ${box(a.lastCard).right} > viewport ${w}`);
          }
          if (a.topmostOverCards && String(a.topmostOverCards).includes('hero-artwork')) {
            note('LAYER', `artwork sits ABOVE the card row at ${w}x${h}`);
          }
        }
        await context.close();
      }
    } finally {
      await restore('property artwork restored', () =>
        putProperty({ ...property, artworkAssetId: previousArtwork }),
      );
      await restore('throwaway asset deleted', () =>
        fetch(API + `/api/manager/assets/${asset.id}`, { method: 'DELETE', headers: auth }),
      );
    }
  });
}

await browser.close();
console.log(`\n${findings.length} finding(s):`);
for (const f of findings) console.log(`  - ${f}`);
console.log('Reported, not asserted — this script always exits 0 (see the header comment).');
