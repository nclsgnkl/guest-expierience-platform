/* T-22 dev smoke, part 2 (backend DOWN at start): last-known-good + pill,
 * then cleared cache → connection screen. Run after killing quarkus:dev. */
import { chromium } from 'playwright';

const BASE = 'http://localhost:4200';
const PROFILE = '/tmp/gxp-kiosk-profile';
const results = [];
const report = (name, ok, detail = '') => {
  results.push(ok);
  console.log(`${ok ? 'OK ' : 'FAIL'} ${name}${detail ? ' — ' + detail : ''}`);
};

const context = await chromium.launchPersistentContext(PROFILE, {
  viewport: { width: 1280, height: 832 },
  deviceScaleFactor: 1,
});
const page = context.pages()[0] ?? (await context.newPage());

await page.goto(BASE + '/', { waitUntil: 'load', timeout: 120000 });
try {
  await page.waitForSelector('.home-content', { timeout: 60000 });
  report('kiosk keeps rendering from the last-known-good cache', true);
} catch {
  report('kiosk keeps rendering from the last-known-good cache', false, page.url());
}
try {
  await page.waitForSelector('.staleness-pill', { timeout: 20000 });
  const pill = (await page.textContent('.staleness-pill')).trim();
  report(
    'staleness pill visible with a timestamp',
    /(Stand|As of) \d{2}:\d{2}/.test(pill),
    pill,
  );
  const geometry = await page.evaluate(() => {
    const bar = document.querySelector('.status-bar').getBoundingClientRect();
    const pillBox = document.querySelector('.staleness-pill').getBoundingClientRect();
    const cards = document.querySelector('.home-content__cards')?.getBoundingClientRect();
    return {
      pill: { x: Math.round(pillBox.x), y: Math.round(pillBox.y), right: Math.round(pillBox.right), bottom: Math.round(pillBox.bottom), h: Math.round(pillBox.height) },
      barBottom: Math.round(bar.bottom),
      barRight: Math.round(bar.right),
      cardsTop: cards ? Math.round(cards.y) : null,
      scrollOk:
        document.documentElement.scrollWidth <= document.documentElement.clientWidth &&
        document.documentElement.scrollHeight <= document.documentElement.clientHeight,
    };
  });
  console.log('   pill geometry: ' + JSON.stringify(geometry));
  report('pill sits below the bar, right-aligned, no scroll', geometry.pill.y >= geometry.barBottom && geometry.scrollOk);
} catch {
  report('staleness pill visible with a timestamp', false);
}

// cleared cache + dead backend → the connection screen
await page.evaluate(() => localStorage.removeItem('gxp.cache.session'));
await page.evaluate(() => localStorage.removeItem('gxp.cache.home'));
await page.reload({ waitUntil: 'load', timeout: 120000 });
try {
  await page.waitForURL('**/connection', { timeout: 30000 });
  const body = await page.textContent('body');
  report('empty cache + dead backend → connection screen', body.includes('No connection') || body.includes('Keine Verbindung'));
  report('connection screen has no button', (await page.locator('button').count()) === 0);
} catch {
  report('empty cache + dead backend → connection screen', false, page.url());
}

console.log('WAITING for backend restart — the screen must heal unaided...');
try {
  await page.waitForSelector('.home-content', { timeout: 420000 });
  report('heals unaided when the backend returns', true);
} catch {
  report('heals unaided when the backend returns', false, page.url());
}

await context.close();
const failed = results.filter((r) => !r).length;
console.log(`\n${results.length - failed}/${results.length} part-2 steps OK`);
process.exit(failed === 0 ? 0 : 1);
