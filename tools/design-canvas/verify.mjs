/**
 * Renders each artboard and diffs it against the ground-truth screenshot of
 * the real app. The artboard is unwrapped from the .dc.html envelope into a
 * plain page first — the <x-dc>/<helmet> shell is the editor's, not the
 * design's, and carries no styling of its own.
 */
import { readFile, writeFile, readdir } from 'node:fs/promises';
import { chromium } from '/home/nclsgnkl/dev/guest-expierience-platform/frontend/node_modules/playwright/index.mjs';

const SHOT = f => `./shots/${f}.png`;
const nameToShot = n => n.replace(/(Light|Dark)$/, m => `-${m.toLowerCase()}`)
  .replace(/^Home/, 'home').replace(/^Settings/, 'settings').replace(/^InfoModule/, 'info')
  .replace(/^Placeholder/, 'placeholder').replace(/^MenuOverflow/, 'menu-overflow')
  .replace(/^Standby/, 'standby').replace(/^Connection/, 'connection').replace(/^Pairing/, 'pairing');

const browser = await chromium.launch();
const files = (await readdir('./boards')).filter(f => f.endsWith('.dc.html')).sort();
const results = [];

for (const file of files) {
  const name = file.replace('.dc.html', '');
  const src = await readFile(`./boards/${file}`, 'utf8');
  const style = src.match(/<helmet>\s*<style>([\s\S]*?)<\/style>\s*<\/helmet>/)[1];
  const div = src.match(/<\/helmet>([\s\S]*?)<\/x-dc>/)[1];
  const page = await browser.newPage({ viewport: { width: 1280, height: 832 }, deviceScaleFactor: 2 });
  await page.setContent(
    `<!doctype html><html><head><meta charset="utf-8"><style>${style}</style></head><body>${div}</body></html>`,
    { waitUntil: 'load' });
  await page.waitForTimeout(1200);
  await page.screenshot({ path: `./verify/${name}.png` });

  // pixel diff against ground truth, in the browser (no image deps on the machine)
  const a = (await readFile(`./verify/${name}.png`)).toString('base64');
  const b = (await readFile(SHOT(nameToShot(name)))).toString('base64');
  const diff = await page.evaluate(async ([a, b]) => {
    const load = s => new Promise(r => { const i = new Image(); i.onload = () => r(i); i.src = 'data:image/png;base64,' + s; });
    const [ia, ib] = await Promise.all([load(a), load(b)]);
    const W = Math.min(ia.width, ib.width), H = Math.min(ia.height, ib.height);
    const px = img => { const c = new OffscreenCanvas(W, H); const x = c.getContext('2d'); x.drawImage(img, 0, 0); return x.getImageData(0, 0, W, H).data; };
    const [pa, pb] = [px(ia), px(ib)];
    let bad = 0;
    for (let i = 0; i < pa.length; i += 4) {
      if (Math.abs(pa[i]-pb[i]) + Math.abs(pa[i+1]-pb[i+1]) + Math.abs(pa[i+2]-pb[i+2]) > 24) bad++;
    }
    return { pct: +(100 * bad / (W*H)).toFixed(2), w: ia.width, h: ia.height, gw: ib.width, gh: ib.height };
  }, [a, b]);
  results.push({ name, ...diff });
  console.log(`${name.padEnd(20)} diff ${String(diff.pct).padStart(6)}%   board ${diff.w}x${diff.h}  truth ${diff.gw}x${diff.gh}`);
  await page.close();
}
await browser.close();
await writeFile('./verify/report.json', JSON.stringify(results, null, 2));
const worst = results.filter(r => r.pct > 1).sort((a,b) => b.pct - a.pct);
console.log(worst.length ? `\nÜber 1% Abweichung: ${worst.map(r => r.name + ' ' + r.pct + '%').join(', ')}` : '\nAlle unter 1%.');
