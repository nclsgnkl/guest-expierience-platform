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
 *   STANDBY     REQUIRES the manager API (deletes the active stay, recreates
 *               it afterwards): dev-only, needs GXP_SMOKE_ADMIN_PW.
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
import { chromium } from 'playwright';

const baseUrl = process.argv[2] ?? 'http://localhost:4200';
const API = process.env.GXP_API_URL ?? 'http://localhost:8080';
const ADMIN_PW = process.env.GXP_SMOKE_ADMIN_PW ?? null;
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
    if (heading && pill.bottom > heading.y && pill.x < heading.right) note('COLLISION', `pill x heading at ${w}x${h}`);
    if (pill.h > 28.5) note('SPEC', `pill taller than 1.75rem envelope at ${w}x${h}: ${pill.h}px`);
  }
  await context.close();
}

/* --------------------------- STANDBY ----------------------------------- */
if (ADMIN_PW === null) {
  console.log('\n######## STANDBY skipped — set GXP_SMOKE_ADMIN_PW to drive vacancy ########');
} else {
  console.log('\n######## STANDBY (stay deleted via manager API, recreated after) ########');
  const login = await fetch(API + '/api/manager/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ password: ADMIN_PW }),
  });
  const token = (await login.json()).token;
  const stays = await (
    await fetch(API + '/api/manager/stays', { headers: { Authorization: `Bearer ${token}` } })
  ).json();
  const active = stays.find((s) => s.status === 'BOOKED');
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
            note('PREFERENCE NOT APPLIED', `standby asked ${theme}/${language} got ${m.theme}/${m.lang}`);
          }
          if (m.standby.sidebar || m.standby.buttonsOutsideStatusBar > 0) {
            note('NAV SURFACE', `standby exposes navigation at ${w}x${h}`);
          }
        }
      }
      await context.close();
    }
  } finally {
    await fetch(API + '/api/manager/stays', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        roomId: active.roomId,
        displayName: active.displayName,
        checkIn: active.checkIn,
        checkOut: active.checkOut,
        status: 'BOOKED',
      }),
    });
    console.log('  (stay recreated)');
  }
}

await browser.close();
console.log(`\n${findings.length} finding(s):`);
for (const f of findings) console.log(`  - ${f}`);
console.log('Reported, not asserted — this script always exits 0 (see the header comment).');
