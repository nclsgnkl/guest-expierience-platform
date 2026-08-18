/**
 * Browser measurement instrument — NOT a test suite.
 *
 * `frontend/CLAUDE.md` and `docs/constitution.md` (Constraints -> "Nothing
 * scrolls") require geometry, wrapping and asset rendering to be checked in a
 * real browser, because jsdom computes no layout and decodes no images. This
 * script is the repeatable way to do that, so each review measures the same
 * things rather than re-inventing a throwaway.
 *
 * It deliberately only *reports*: it always exits 0, carries no assertion
 * library and is not wired into `npm test`. `docs/constitution.md` v1.3.0
 * approves `playwright` as review-time tooling only and leaves the
 * end-to-end-framework decision explicitly open — turning these numbers into
 * a failing suite is that decision, and it has not been made.
 *
 * Usage (dev server must already be running):
 *   node tools/measure.mjs [baseUrl] [--screenshot <file>] [--route <path>]
 *                          [--viewport <w>x<h>]
 *
 * Covers: REQ-024 (no scroll, 6 routes x 3 viewports), REQ-022 (sidebar box,
 * nav item, group gaps), REQ-014 (heading block, card row, widget column),
 * REQ-007 / REQ-009 (nothing wraps or truncates), REQ-011 (logo decodes).
 */
import { chromium } from 'playwright';

const ROUTES = ['/', '/info', '/umgebung', '/smart-home', '/services', '/einstellungen'];
const VIEWPORTS = [
  [1024, 768],
  [1280, 832],
  [1440, 900],
];

const argv = process.argv.slice(2);
const flag = (name) => {
  const i = argv.indexOf(name);
  return i === -1 ? null : argv[i + 1];
};
const baseUrl = (argv[0] && !argv[0].startsWith('--') ? argv[0] : null) ?? 'http://localhost:4300';
const screenshot = flag('--screenshot');
const oneRoute = flag('--route');
const oneViewport = flag('--viewport');

const round = (n) => Math.round(n * 100) / 100;

/** Runs in the page: everything this repo's acceptance criteria measure. */
function collect() {
  const box = (el) => {
    if (!el) return null;
    const r = el.getBoundingClientRect();
    return { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height), bottom: Math.round(r.bottom) };
  };
  const range = (el) => {
    const r = document.createRange();
    r.selectNodeContents(el);
    return r;
  };
  const lineCount = (el) => (el ? range(el).getClientRects().length : null);
  /** Advance width of the laid-out text itself — `scrollWidth` on a block
      element only reports its own box, so it cannot see an ellipsis. */
  const inkWidth = (el) => (el ? Math.round(range(el).getBoundingClientRect().width * 100) / 100 : null);
  const doc = document.documentElement;
  const items = [...document.querySelectorAll('.sidebar-item')];
  const navItems = [...document.querySelectorAll('.sidebar__menu-items .sidebar-item')];
  const settings = document.querySelector('.sidebar__settings .sidebar-item');
  const logo = document.querySelector('.sidebar__logo');
  const logoImg = document.querySelector('.sidebar__logo-img');
  const gap = (a, b) => (a && b ? Math.round(b.getBoundingClientRect().top - a.getBoundingClientRect().bottom) : null);

  return {
    scroll: {
      scrollW: doc.scrollWidth,
      clientW: doc.clientWidth,
      scrollH: doc.scrollHeight,
      clientH: doc.clientHeight,
      htmlOverflow: getComputedStyle(doc).overflow,
      bodyOverflow: getComputedStyle(document.body).overflow,
    },
    rootFontSize: getComputedStyle(doc).fontSize,
    sidebar: box(document.querySelector('.sidebar')),
    navItem: box(navItems[0]),
    activePill: box(document.querySelector('.sidebar-item--active')),
    gapLogoToFirstNav: gap(logo, navItems[0]),
    gapLastNavToSettings: gap(navItems[navItems.length - 1], settings),
    labels: items.map((i) => {
      const el = i.querySelector('.sidebar-item__label');
      return { text: el.textContent.trim(), lines: lineCount(el), truncated: inkWidth(el) > el.clientWidth + 0.5 };
    }),
    heading: box(document.querySelector('.heading-block')),
    headlineLines: lineCount(document.querySelector('.heading-block__headline')),
    paragraphLines: lineCount(document.querySelector('.heading-block__paragraph')),
    cardRow: box(document.querySelector('.home-content__cards')),
    widgets: box(document.querySelector('.home-content__widgets')),
    cards: [...document.querySelectorAll('.action-card')].map((c) => {
      const title = c.querySelector('.action-card__header');
      const desc = c.querySelector('.action-card__description');
      const arrow = c.querySelector('.action-card__arrow');
      return {
        title: title.textContent.trim(),
        titleLines: lineCount(title),
        titleInkW: inkWidth(title),
        titleBoxW: title.clientWidth,
        truncated: inkWidth(title) > title.clientWidth + 0.5,
        box: box(c),
        titleY: Math.round(title.getBoundingClientRect().y),
        descY: desc ? Math.round(desc.getBoundingClientRect().y) : null,
        arrowY: arrow ? Math.round(arrow.getBoundingClientRect().y) : null,
      };
    }),
    logo: logoImg ? { complete: logoImg.complete, naturalWidth: logoImg.naturalWidth, src: logoImg.getAttribute('src') } : null,
  };
}

const browser = await chromium.launch();
const routes = oneRoute ? [oneRoute] : ROUTES;
const viewports = oneViewport ? [oneViewport.split('x').map(Number)] : VIEWPORTS;
let overflowing = 0;
let truncated = 0;

for (const [w, h] of viewports) {
  const page = await browser.newPage({ viewport: { width: w, height: h }, deviceScaleFactor: 1 });
  console.log(`\n=== ${w} x ${h} ===`);
  for (const route of routes) {
    await page.goto(baseUrl + route, { waitUntil: 'networkidle' });
    await page.waitForTimeout(300);
    const m = await page.evaluate(collect);
    const s = m.scroll;
    const ok = s.scrollW <= s.clientW && s.scrollH <= s.clientH;
    if (!ok) overflowing += 1;
    console.log(
      `${route.padEnd(14)} scrollW=${s.scrollW}/${s.clientW} scrollH=${s.scrollH}/${s.clientH} ` +
        `root=${m.rootFontSize} overflow=${s.htmlOverflow}/${s.bodyOverflow} ${ok ? 'NO-SCROLL OK' : 'OVERFLOW'}`,
    );
    console.log(`  sidebar ${JSON.stringify(m.sidebar)} navItem ${JSON.stringify(m.navItem)}`);
    console.log(`  gaps logo->nav ${m.gapLogoToFirstNav} nav->settings ${m.gapLastNavToSettings} logo ${JSON.stringify(m.logo)}`);
    const wrappedLabels = m.labels.filter((l) => l.lines !== 1 || l.truncated);
    console.log(`  labels ${m.labels.length}, not-one-line-or-truncated: ${wrappedLabels.length ? JSON.stringify(wrappedLabels) : 'none'}`);
    if (m.heading) {
      console.log(`  heading ${JSON.stringify(m.heading)} headlineLines=${m.headlineLines} paragraphLines=${m.paragraphLines}`);
      console.log(`  cardRow ${JSON.stringify(m.cardRow)} widgets ${JSON.stringify(m.widgets)}`);
      for (const c of m.cards) {
        if (c.truncated) truncated += 1;
        console.log(
          `  card "${c.title}" ${JSON.stringify(c.box)} titleLines=${c.titleLines} ` +
            `title ink ${c.titleInkW}px in ${c.titleBoxW}px box ${c.truncated ? 'TRUNCATED' : 'fits'} ` +
            `y title/desc/arrow ${c.titleY}/${c.descY}/${c.arrowY}`,
        );
      }
    }
    if (screenshot && route === routes[0]) await page.screenshot({ path: screenshot });
  }
  await page.close();
}
await browser.close();

console.log(
  `\nSummary: ${routes.length * viewports.length} route x viewport combinations, ` +
    `${overflowing} overflowing, ${truncated} truncated card titles.`,
);
console.log('Reported, not asserted — this script always exits 0 (see the header comment).');
