/* T-22 dev smoke, part 1 (backend up): seeded render, live asset swap,
 * preference write-back, vacancy flip + route restore, revocation → pairing
 * → confirm → claim without reload. Reports; exits 1 on a failed step. */
import { chromium } from 'playwright';

const BASE = 'http://localhost:4200';
const API = 'http://localhost:8080';
const ADMIN_PW = 'dev-smoke-admin-pw-1';
const PROFILE = '/tmp/gxp-kiosk-profile';

const results = [];
function report(name, ok, detail = '') {
  results.push({ name, ok });
  console.log(`${ok ? 'OK ' : 'FAIL'} ${name}${detail ? ' — ' + detail : ''}`);
}

async function api(path, options = {}) {
  const res = await fetch(API + path, options);
  return res;
}

async function adminToken() {
  const res = await api('/api/manager/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ password: ADMIN_PW }),
  });
  const body = await res.json();
  return body.token;
}

const context = await chromium.launchPersistentContext(PROFILE, {
  viewport: { width: 1280, height: 832 },
  deviceScaleFactor: 1,
});
const page = context.pages()[0] ?? (await context.newPage());

// ---- 1. seeded content renders --------------------------------------------
await page.goto(BASE + '/', { waitUntil: 'load', timeout: 180000 });
await page.waitForSelector('.home-content', { timeout: 120000 });
const text = await page.textContent('body');
report('seeded headline renders', text.includes('Willkommen im Airbnb von Anna & Tom!'));
report('4 action cards', (await page.locator('app-action-card').count()) === 4);
report('5 menu + settings items', (await page.locator('.sidebar-item').count()) === 6);
const brand = await page.evaluate(() =>
  document.documentElement.style.getPropertyValue('--brand-primary'),
);
report('pink brand applied to :root', brand === '#f45aa8', brand);
await page.evaluate(() => ((window.__gxpNoReloadMarker = 'part1'), undefined));

// ---- 2. live logo asset via authorized blob fetch -------------------------
const token = await adminToken();
const png = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64',
);
const upload = await api('/api/manager/assets?kind=LOGO', {
  method: 'POST',
  headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'image/png' },
  body: png,
});
const asset = await upload.json();
const property = await (
  await api('/api/manager/property', { headers: { Authorization: `Bearer ${token}` } })
).json();
property.logoAssetId = asset.id;
const putProp = await api('/api/manager/property', {
  method: 'PUT',
  headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
  body: JSON.stringify(property),
});
report('logo uploaded + property updated', upload.status === 201 && putProp.ok);
try {
  await page.waitForFunction(
    () => document.querySelector('.sidebar__logo-img')?.getAttribute('src')?.startsWith('blob:'),
    { timeout: 45000 },
  );
  report('sidebar logo swapped to authorized object URL within one poll', true);
} catch {
  report('sidebar logo swapped to authorized object URL within one poll', false);
}

// ---- 3. preference write-back lands on the stay ---------------------------
await page.locator('.status-bar .status-bar-menu__trigger').nth(0).click();
await page.waitForSelector('.cdk-overlay-container .status-bar-menu__panel');
await page
  .locator('.cdk-overlay-container .status-bar-menu__option', { hasText: 'English' })
  .click();
await page.waitForTimeout(1500);
const session1 = await (
  await api('/api/guest/session', {
    headers: { Authorization: 'Bearer gxp-dev-device-token' },
  })
).json();
report(
  "language choice lands on the stay (session preference = 'en')",
  session1.preference?.language === 'en',
  JSON.stringify(session1.preference),
);
report('<html lang> followed', (await page.getAttribute('html', 'lang')) === 'en');

// ---- 4. vacancy flip + previous-route restore -----------------------------
await page.locator('.sidebar__settings .sidebar-item').click();
await page.waitForURL('**/einstellungen');
const stays = await (
  await api('/api/manager/stays', { headers: { Authorization: `Bearer ${token}` } })
).json();
const active = stays.find((s) => s.status === 'BOOKED');
const del = await api(`/api/manager/stays/${active.id}`, {
  method: 'DELETE',
  headers: { Authorization: `Bearer ${token}` },
});
report('stay deleted via manager API', del.status === 204, String(del.status));
try {
  await page.waitForURL('**/standby', { timeout: 45000 });
  report('VACANT flip → /standby within one poll', true);
} catch {
  report('VACANT flip → /standby within one poll', false, page.url());
}
const standbyText = await page.textContent('body');
report('standby shows property texts', standbyText.includes('Meine Unterkunft'));
report('standby carries no guest-derived value', !standbyText.includes('Anna'));
report('standby has no sidebar', (await page.locator('app-sidebar').count()) === 0);

const recreate = await api('/api/manager/stays', {
  method: 'POST',
  headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({
    roomId: 1,
    displayName: 'Anna & Tom',
    checkIn: new Date(Date.now() - 3600e3).toISOString(),
    checkOut: new Date(Date.now() + 30 * 86400e3).toISOString(),
    status: 'BOOKED',
  }),
});
report('stay recreated', recreate.status === 201, String(recreate.status));
try {
  await page.waitForURL('**/einstellungen', { timeout: 45000 });
  report('OCCUPIED again → previous route (/einstellungen) restored', true);
} catch {
  report('OCCUPIED again → previous route (/einstellungen) restored', false, page.url());
}

// ---- 5. revocation → pairing, storage verifiably empty --------------------
const devices = await (
  await api('/api/manager/devices', { headers: { Authorization: `Bearer ${token}` } })
).json();
await api(`/api/manager/devices/${devices[0].id}/revoke`, {
  method: 'POST',
  headers: { Authorization: `Bearer ${token}` },
});
try {
  await page.waitForURL('**/pairing', { timeout: 45000 });
  report('revocation → /pairing on the next poll', true);
} catch {
  report('revocation → /pairing on the next poll', false, page.url());
}
const storage = await page.evaluate(() => ({
  token: localStorage.getItem('gxp.device-token'),
  session: localStorage.getItem('gxp.cache.session'),
  home: localStorage.getItem('gxp.cache.home'),
}));
report(
  'token AND caches wiped',
  storage.token === null && storage.session === null && storage.home === null,
  JSON.stringify(storage).slice(0, 80),
);
await page.waitForSelector('.pairing__code', { timeout: 15000 });
const codeShown = (await page.textContent('.pairing__code')).trim();
report('pairing code displayed grouped', /^[A-Z2-9]{4} [A-Z2-9]{4}$/.test(codeShown), codeShown);

// ---- 6. confirm → claim-once → app enters WITHOUT reload ------------------
await page.evaluate(() => ((window.__gxpNoReloadMarker = 'pairing'), undefined));
const code = codeShown.replace(' ', '');
const confirm = await api(`/api/manager/pairings/${code}/confirm`, {
  method: 'POST',
  headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({ roomId: 1 }),
});
report('manager confirmed the code', confirm.status === 204, String(confirm.status));
try {
  await page.waitForSelector('.home-content', { timeout: 120000 });
  report('kiosk entered the app after claim', true);
} catch {
  report('kiosk entered the app after claim', false, page.url());
}
const marker = await page.evaluate(() => window.__gxpNoReloadMarker);
report('no reload happened across the claim', marker === 'pairing', String(marker));
const newToken = await page.evaluate(() => localStorage.getItem('gxp.device-token'));
report(
  'a fresh (non-dev) device token is persisted',
  typeof newToken === 'string' && newToken.length > 20 && newToken !== 'gxp-dev-device-token',
);

await context.close();
const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} smoke steps OK`);
process.exit(failed.length === 0 ? 0 : 1);
