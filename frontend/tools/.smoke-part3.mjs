/* T-22 part 3 — the self-heal loop, isolated: /api blocked at the browser →
 * connection screen; unblock → the kiosk recovers unaided within the pinned
 * 5→30 s backoff. Backend must be UP. */
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

let blocked = true;
await context.route('**/api/**', (route) =>
  blocked ? route.abort('connectionrefused') : route.continue(),
);

// Empty cache is this scenario's precondition (with a cache the kiosk
// correctly renders STALE instead — part 2's proof).
await page.goto(BASE + '/pairing', { waitUntil: 'load', timeout: 120000 }).catch(() => {});
await page.evaluate(() => {
  localStorage.removeItem('gxp.cache.session');
  localStorage.removeItem('gxp.cache.home');
});
await page.goto(BASE + '/', { waitUntil: 'load', timeout: 120000 });
try {
  await page.waitForURL('**/connection', { timeout: 30000 });
  report('dead network + no cache → connection screen', true);
} catch {
  report('dead network + no cache → connection screen', false, page.url());
}

const healStart = Date.now();
blocked = false;
try {
  await page.waitForSelector('.home-content', { timeout: 60000 });
  report(
    'heals unaided within the retry backoff',
    true,
    `${Math.round((Date.now() - healStart) / 1000)}s after the network returned`,
  );
} catch {
  report('heals unaided within the retry backoff', false, page.url());
}

await context.close();
const failed = results.filter((r) => !r).length;
console.log(`\n${results.length - failed}/${results.length} part-3 steps OK`);
process.exit(failed === 0 ? 0 : 1);
