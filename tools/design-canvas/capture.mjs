/**
 * Ground-truth capture: renders the REAL built app and screenshots every
 * screen archetype in light and dark, so the design-canvas rebuild can be
 * checked against it rather than trusted.
 *
 * The backend is not started. Instead the three things the app needs from the
 * wire are faked at the browser: the device token in localStorage, the single
 * GET /api/guest/content payload, and the logo asset. The fixture below is the
 * one from guest-content.mapper.spec.ts (which mirrors the dev seed), widened
 * to 8 menu items and 4 action cards so the sidebar overflows (SIDEBAR_SLOTS
 * is 5) and the home screen carries its full card row.
 */
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { join, extname } from 'node:path';
import { chromium } from '/home/nclsgnkl/dev/guest-expierience-platform/frontend/node_modules/playwright/index.mjs';

const ROOT = '/home/nclsgnkl/dev/guest-expierience-platform/frontend/dist/frontend/browser';
const OUT = '/tmp/claude-1000/-home-nclsgnkl-dev-guest-expierience-platform/8f6b7107-8141-4f96-bd29-b306bab3ef9b/scratchpad/shots';
const PORT = 8123;
const MIME = { '.js':'text/javascript', '.css':'text/css', '.html':'text/html',
  '.svg':'image/svg+xml', '.woff2':'font/woff2', '.ico':'image/x-icon', '.png':'image/png' };

// --- static server with SPA fallback ---------------------------------------
const server = createServer(async (req, res) => {
  const url = req.url.split('?')[0];
  const file = url === '/' ? 'index.html' : url;   // mime must follow the RESOLVED file:
  try {                                            // extname('/') is empty and served index.html as a download
    const body = await readFile(join(ROOT, file));
    res.writeHead(200, { 'Content-Type': MIME[extname(file)] ?? 'application/octet-stream' });
    res.end(body);
  } catch {
    res.writeHead(200, { 'Content-Type': 'text/html' });
    res.end(await readFile(join(ROOT, 'index.html')));   // SPA deep links
  }
});
await new Promise(r => server.listen(PORT, r));

// --- the payload -----------------------------------------------------------
const L = (de, en) => ({ de, en });
const menuItem = (key, de, en, icon, route, sortIndex, module = null) =>
  ({ key, label: L(de, en), icon, route, sortIndex, ...(module ? { module } : {}) });

const infoModule = {
  type: 'info',
  rows: [
    { label: L('WLAN', 'Wi-Fi'), value: L('Ferienwohnung-Gast', 'Ferienwohnung-Gast') },
    { label: L('Passwort', 'Password'), value: L('sonne-meer-2026', 'sonne-meer-2026') },
    { label: L('Check-out', 'Check-out'), value: L('Bis 11:00 Uhr', 'By 11:00') },
    { label: L('Müllabfuhr', 'Waste collection'), value: L('Dienstags, gelbe Tonne', 'Tuesdays, yellow bin') },
    { label: L('Notruf', 'Emergency'), value: L('112', '112') },
  ],
};

const content = (over = {}) => ({
  property: { name: 'Haus Seeblick', brand: { primary: '#f45aa8', primaryDeep: '#cc49af', secondary: '#8a5cf6' }, logoAssetId: 7 },
  room: { code: '1', displayName: 'Zimmer 1' },
  occupancy: 'OCCUPIED',
  guestDisplayName: 'Anna & Tom',
  settings: {
    languages: [
      { code: 'de', shortLabel: 'DE', label: 'Deutsch', locale: 'de-DE' },
      { code: 'en', shortLabel: 'EN', label: 'English', locale: 'en-GB' },
    ],
    appearances: [{ mode: 'light', icon: 'phosphorSun' }, { mode: 'dark', icon: 'phosphorMoon' }],
    defaultLanguage: 'de',
    defaultAppearance: 'light',
  },
  menu: {
    items: [
      menuItem('home', 'Home', 'Home', 'phosphorHouse', '', 0),
      menuItem('info', 'Info', 'Info', 'phosphorInfo', '/info', 1, infoModule),
      menuItem('umgebung', 'Umgebung', 'Nearby', 'phosphorMapPin', '/umgebung', 2),
      menuItem('smart-home', 'Smart Home', 'Smart home', 'phosphorLightbulbFilament', '/smart-home', 3),
      menuItem('services', 'Services', 'Services', 'phosphorShoppingBag', '/services', 4),
      menuItem('wlan', 'WLAN', 'Wi-Fi', 'phosphorWifiHigh', '/wlan', 5),
      menuItem('gastronomie', 'Gastronomie', 'Dining', 'phosphorForkKnife', '/gastronomie', 6),
      menuItem('aktivitaeten', 'Aktivitäten', 'Activities', 'phosphorPersonSimpleHike', '/aktivitaeten', 7),
    ],
    settingsEntry: { id: 'einstellungen', label: L('Einstellungen', 'Settings'), icon: 'phosphorGearSix', route: '/einstellungen' },
  },
  standby: { headline: L('Haus Seeblick', 'Haus Seeblick'), message: L('Herzlich willkommen', 'Welcome') },
  home: {
    headline: L('Willkommen im Haus Seeblick, Anna & Tom!', 'Welcome to Haus Seeblick, Anna & Tom!'),
    subtitle: L('Schön, dass ihr da seid', 'Great to have you here'),
    paragraph: L('Hier findet ihr alles Wichtige für euren Aufenthalt.', 'Everything you need for your stay.'),
    actionCards: [
      { icon: 'phosphorKey', title: L('Check-Out', 'Check-out'), description: L('Bis 11:00 Uhr', 'By 11:00'), targetKey: 'info', targetRoute: '/info' },
      { icon: 'phosphorWifiHigh', title: L('WLAN', 'Wi-Fi'), description: L('Netzwerk & Passwort', 'Network & password'), targetKey: 'wlan', targetRoute: '/wlan' },
      { icon: 'phosphorForkKnife', title: L('Frühstück', 'Breakfast'), description: L('Ab 7:30 Uhr', 'From 7:30'), targetKey: 'gastronomie', targetRoute: '/gastronomie' },
      { icon: 'phosphorMapPin', title: L('Umgebung', 'Nearby'), description: L('Was gibt es in der Nähe', "What's around"), targetKey: 'umgebung', targetRoute: '/umgebung' },
    ],
  },
  ...over,
});

const vacant = () => { const c = content(); delete c.guestDisplayName; delete c.home; return { ...c, occupancy: 'VACANT' }; };

// --- harness ---------------------------------------------------------------
const logo = await readFile('/home/nclsgnkl/dev/guest-expierience-platform/frontend/src/assets/branding/logo-placeholder.svg');
const browser = await chromium.launch();

async function shot(name, { token = 'dev-token', payload = null, theme = 'light', route = '/', blockApi = false, after = null }) {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 832 }, deviceScaleFactor: 2 });
  await ctx.addInitScript(t => { if (t) localStorage.setItem('gxp.device-token', t); else localStorage.clear(); }, token);
  await ctx.route('**/api/guest/assets/**', r => r.fulfill({ status: 200, contentType: 'image/svg+xml', body: logo }));
  await ctx.route('**/api/guest/preferences', r => r.fulfill({ status: 204, body: '' }));
  await ctx.route('**/api/guest/content', r => blockApi || !payload
    ? r.abort('connectionfailed')
    : r.fulfill({ status: 200, contentType: 'application/json', headers: { etag: '"x"' },
        body: JSON.stringify({ ...payload, settings: { ...payload.settings, defaultAppearance: theme } }) }));
  const page = await ctx.newPage();
  await page.goto(`http://localhost:${PORT}${route}`, { waitUntil: 'load' }).catch(() => {});
  await page.waitForTimeout(2200);
  if (after) { await after(page); await page.waitForTimeout(900); }
  await page.screenshot({ path: `${OUT}/${name}.png` });
  const url = new URL(page.url()).pathname;
  const head = (await page.innerText('body')).replace(/\s+/g, ' ').slice(0, 90);
  console.log(`${name.padEnd(24)} ${url.padEnd(16)} ${head}`);
  await ctx.close();
}

const openOverflow = async page => {
  await page.getByRole('button', { name: /mehr|more/i }).first().click().catch(async () => {
    await page.locator('.sidebar__menu-items .sidebar-item').last().click();
  });
};

for (const theme of ['light', 'dark']) {
  const p = content();
  await shot(`home-${theme}`,          { payload: p, theme });
  await shot(`settings-${theme}`,      { payload: p, theme, route: '/einstellungen' });
  await shot(`info-${theme}`,          { payload: p, theme, route: '/info' });
  await shot(`placeholder-${theme}`,   { payload: p, theme, route: '/umgebung' });
  await shot(`menu-overflow-${theme}`, { payload: p, theme, after: openOverflow });
  await shot(`standby-${theme}`,       { payload: vacant(), theme });
  await shot(`connection-${theme}`,    { payload: p, theme, blockApi: true });
  await shot(`pairing-${theme}`,       { payload: p, theme, token: null });
}

await browser.close();
server.close();
