/**
 * Turns each real, rendered screen into a self-contained .dc.html artboard.
 *
 * Fidelity comes from not re-authoring anything: the markup is the app's own
 * rendered DOM and the CSS is the app's own stylesheets, both lifted out of a
 * live page. Only three things are rewritten, because an artboard has no
 * network and no Angular runtime behind it:
 *   - the three self-hosted woff2 faces become data: URIs
 *   - the logo <img>, which the app fetches from /api/guest/assets/7
 *   - the root font-size rule, pinned to the 16px the viewport-proportional
 *     root resolves to at the 1280x832 design size
 */
import { createServer } from 'node:http';
import { readFile, writeFile } from 'node:fs/promises';
import { join, extname } from 'node:path';
import { chromium } from '/home/nclsgnkl/dev/guest-expierience-platform/frontend/node_modules/playwright/index.mjs';
import { content, vacant } from './fixture.mjs';

const APP = '/home/nclsgnkl/dev/guest-expierience-platform/frontend';
const ROOT = `${APP}/dist/frontend/browser`;
const OUT = './boards';
const PORT = 8125;
const MIME = { '.js':'text/javascript', '.css':'text/css', '.html':'text/html',
  '.svg':'image/svg+xml', '.woff2':'font/woff2', '.ico':'image/x-icon', '.png':'image/png' };

const server = createServer(async (req, res) => {
  const url = req.url.split('?')[0];
  const file = url === '/' ? 'index.html' : url;
  try {
    const body = await readFile(join(ROOT, file));
    res.writeHead(200, { 'Content-Type': MIME[extname(file)] ?? 'application/octet-stream' });
    res.end(body);
  } catch {
    res.writeHead(200, { 'Content-Type': 'text/html' });
    res.end(await readFile(join(ROOT, 'index.html')));
  }
});
await new Promise(r => server.listen(PORT, r));

// --- assets that must travel inside the artboard ---------------------------
const fontDataUri = async w =>
  `data:font/woff2;base64,${(await readFile(`${APP}/src/assets/fonts/plus-jakarta-sans/plus-jakarta-sans-${w}.woff2`)).toString('base64')}`;
const FONTS = { 400: await fontDataUri(400), 700: await fontDataUri(700), 800: await fontDataUri(800) };
const logoSvg = await readFile(`${APP}/src/assets/branding/logo-placeholder.svg`);
const logoUri = `data:image/svg+xml;base64,${logoSvg.toString('base64')}`;

const browser = await chromium.launch();

async function snapshot(name, { token = 'dev-token', payload, theme, route = '/', blockApi = false, after = null }) {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 832 } });
  await ctx.addInitScript(t => { if (t) localStorage.setItem('gxp.device-token', t); }, token);
  await ctx.route('**/api/guest/assets/**', r => r.fulfill({ status: 200, contentType: 'image/svg+xml', body: logoSvg }));
  await ctx.route('**/api/guest/preferences', r => r.fulfill({ status: 204, body: '' }));
  await ctx.route('**/api/guest/content', r => blockApi
    ? r.abort('connectionfailed')
    : r.fulfill({ status: 200, contentType: 'application/json', headers: { etag: '"x"' },
        body: JSON.stringify({ ...payload, settings: { ...payload.settings, defaultAppearance: theme } }) }));
  const page = await ctx.newPage();
  await page.goto(`http://localhost:${PORT}${route}`, { waitUntil: 'load' }).catch(() => {});
  await page.waitForTimeout(2200);
  if (after) { await after(page); await page.waitForTimeout(900); }

  const { css, html } = await page.evaluate(() => {
    const css = [...document.styleSheets].map(s => {
      try { return [...s.cssRules].map(r => r.cssText).join('\n'); } catch { return ''; }
    }).join('\n');
    // The CDK drops the overflow panel in an overlay container outside <app-root>;
    // it has to travel with the shell or the open-menu screen loses its menu.
    const overlay = document.querySelector('.cdk-overlay-container');
    return { css, html: document.body.innerHTML, overlayPresent: !!overlay };
  });

  await ctx.close();
  return { css, html };
}

// --- assembling the artboard ----------------------------------------------
function board(name, theme, css, html) {
  // The theme cannot ride on an attribute here: an artboard owns no <html>, and
  // a wrapper element between the root and the glass surfaces changes what
  // backdrop-filter samples — measured as a visible shift on every dark screen
  // with a glass panel. So the dark block is promoted to :root instead, where
  // it still wins on source order, and the content sits directly in the root.
  const themed = theme === 'dark'
    ? css.replace(/\[data-theme=["']dark["']\]/g, ':root')
    : css;
  const styled = themed
    .replace(/url\(["']?\/assets\/fonts\/plus-jakarta-sans\/plus-jakarta-sans-400\.woff2["']?\)/g, `url("${FONTS[400]}")`)
    .replace(/url\(["']?\/assets\/fonts\/plus-jakarta-sans\/plus-jakarta-sans-700\.woff2["']?\)/g, `url("${FONTS[700]}")`)
    .replace(/url\(["']?\/assets\/fonts\/plus-jakarta-sans\/plus-jakarta-sans-800\.woff2["']?\)/g, `url("${FONTS[800]}")`)
    // the kiosk's viewport-proportional root, pinned to what it resolves to at 1280x832
    .replace(/font-size:\s*min\([^)]*\)/g, 'font-size: 16px')
    .replace(/min-width:\s*1024px/g, 'min-width: 0');

  // GuestAssetService fetches assets and hands the <img> an object URL, which
  // cannot survive the snapshot — inline the bytes instead.
  const body = html
    .replace(/src="blob:[^"]*"/g, `src="${logoUri}"`)
    .replace(/src="\/api\/guest\/assets\/\d+"/g, `src="${logoUri}"`)
    .replace(/<!--[\s\S]*?-->/g, '')          // Angular comment anchors
    .replace(/\s(ng-reflect-\S+)="[^"]*"/g, '')
    // Angular's own bundle tag rides along in body.innerHTML. An artboard is a
    // static mockup with no network, so it could only ever 404 — and must never
    // get the chance to boot a second copy of the app inside the canvas.
    .replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, '');

  if (/blob:/.test(body)) console.error(`  WARNUNG ${name}: blob:-Referenz nicht ersetzt`);
  return `<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
  <style>
${styled}
  </style>
</helmet>
${body}
</x-dc>
</body>
</html>
`;
}

const openOverflow = async page => {
  await page.getByRole('button', { name: /mehr|more/i }).first().click();
};

const SCREENS = [
  ['Home',        {}],
  ['Settings',    { route: '/einstellungen' }],
  ['InfoModule',  { route: '/info' }],
  ['Placeholder', { route: '/umgebung' }],
  ['MenuOverflow',{ after: openOverflow }],
  ['Standby',     { vacantPayload: true }],
  ['Connection',  { blockApi: true }],
  ['Pairing',     { token: null }],
];

for (const theme of ['light', 'dark']) {
  for (const [screen, opts] of SCREENS) {
    const { vacantPayload, ...rest } = opts;
    const name = `${screen}${theme === 'light' ? 'Light' : 'Dark'}`;
    const { css, html } = await snapshot(name, { payload: vacantPayload ? vacant() : content(), theme, ...rest });
    await writeFile(`${OUT}/${name}.dc.html`, board(name, theme, css, html));
    console.log(`${name.padEnd(20)} ${(css.length/1024).toFixed(0).padStart(4)} KB css  ${(html.length/1024).toFixed(0).padStart(4)} KB html`);
  }
}

await browser.close();
server.close();
