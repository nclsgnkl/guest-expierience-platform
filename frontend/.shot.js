const { chromium } = require('playwright');
(async () => {
  const url = process.argv[2] || 'http://localhost:4300/';
  const out = process.argv[3] || '/tmp/shot.png';
  const w = parseInt(process.argv[4] || '1280', 10);
  const h = parseInt(process.argv[5] || '832', 10);
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: w, height: h }, deviceScaleFactor: 1 });
  await p.goto(url, { waitUntil: 'networkidle' });
  await p.waitForTimeout(600);
  const m = await p.evaluate(() => ({
    scrollW: document.documentElement.scrollWidth,
    clientW: document.documentElement.clientWidth,
    scrollH: document.documentElement.scrollHeight,
    clientH: document.documentElement.clientHeight,
    sidebar: (() => { const e = document.querySelector('.sidebar'); if(!e) return null; const r = e.getBoundingClientRect(); return {x:Math.round(r.x),y:Math.round(r.y),w:Math.round(r.width),h:Math.round(r.height)}; })(),
    shell: (() => { const e = document.querySelector('.shell'); if(!e) return null; const r = e.getBoundingClientRect(); return {x:Math.round(r.x),y:Math.round(r.y),w:Math.round(r.width),h:Math.round(r.height)}; })(),
    heading: (() => { const e = document.querySelector('.heading-block'); if(!e) return null; const r = e.getBoundingClientRect(); return {x:Math.round(r.x),y:Math.round(r.y),w:Math.round(r.width),h:Math.round(r.height)}; })(),
    cards: (() => { const e = document.querySelector('.home-content__cards'); if(!e) return null; const r = e.getBoundingClientRect(); return {x:Math.round(r.x),y:Math.round(r.y),w:Math.round(r.width),h:Math.round(r.height)}; })(),
  }));
  console.log(JSON.stringify(m, null, 1));
  await p.screenshot({ path: out });
  await b.close();
})();
