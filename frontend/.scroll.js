const { chromium } = require('playwright');
(async () => {
  const routes = ['/', '/info', '/umgebung', '/smart-home', '/services', '/einstellungen'];
  const sizes = [[1024, 768], [1280, 832], [1440, 900]];
  const b = await chromium.launch();
  for (const [w, h] of sizes) {
    const p = await b.newPage({ viewport: { width: w, height: h }, deviceScaleFactor: 1 });
    for (const r of routes) {
      await p.goto('http://localhost:4300' + r, { waitUntil: 'networkidle' });
      await p.waitForTimeout(300);
      const m = await p.evaluate(() => ({
        sw: document.documentElement.scrollWidth, cw: document.documentElement.clientWidth,
        sh: document.documentElement.scrollHeight, ch: document.documentElement.clientHeight,
        bsw: document.body.scrollWidth, bsh: document.body.scrollHeight,
        htmlOverflow: getComputedStyle(document.documentElement).overflow,
        bodyOverflow: getComputedStyle(document.body).overflow,
        fs: getComputedStyle(document.documentElement).fontSize,
      }));
      const ok = m.sw <= m.cw && m.sh <= m.ch;
      console.log(`${w}x${h} ${r.padEnd(14)} scrollW=${m.sw} clientW=${m.cw} scrollH=${m.sh} clientH=${m.ch} root=${m.fs} overflow=${m.htmlOverflow}/${m.bodyOverflow} ${ok ? 'OK' : 'OVERFLOW'}`);
    }
    await p.close();
  }
  await b.close();
})();
