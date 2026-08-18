const { chromium } = require('playwright');
(async () => {
  const url = process.argv[2] || 'http://localhost:4300/';
  const w = parseInt(process.argv[3] || '1280', 10);
  const h = parseInt(process.argv[4] || '832', 10);
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: w, height: h }, deviceScaleFactor: 1 });
  await p.goto(url, { waitUntil: 'networkidle' });
  await p.waitForTimeout(500);
  const m = await p.evaluate(() => {
    const box = (e) => { if (!e) return null; const r = e.getBoundingClientRect(); return { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height), bottom: Math.round(r.bottom) }; };
    const lines = (e) => { if (!e) return null; const r = document.createRange(); r.selectNodeContents(e); return r.getClientRects().length; };
    const items = [...document.querySelectorAll('.sidebar-item')];
    const logo = document.querySelector('.sidebar__logo');
    const settings = document.querySelector('.sidebar__settings .sidebar-item');
    const active = document.querySelector('.sidebar-item--active');
    const cards = [...document.querySelectorAll('.action-card')];
    return {
      root: { scrollW: document.documentElement.scrollWidth, clientW: document.documentElement.clientWidth, scrollH: document.documentElement.scrollHeight, clientH: document.documentElement.clientHeight, bodyScrollW: document.body.scrollWidth, bodyScrollH: document.body.scrollHeight },
      rootFontSize: getComputedStyle(document.documentElement).fontSize,
      labelFontSizes: items.map(i => getComputedStyle(i.querySelector('.sidebar-item__label')).fontSize),
      sidebar: box(document.querySelector('.sidebar')),
      logo: box(logo),
      firstNav: box(items[0]),
      lastNav: box(items[items.length - 2]),
      settings: box(settings),
      activePill: box(active),
      gapLogoToFirstNav: logo && items[0] ? Math.round(items[0].getBoundingClientRect().top - logo.getBoundingClientRect().bottom) : null,
      gapLastNavToSettings: settings && items.length ? Math.round(settings.getBoundingClientRect().top - items[items.length - 2].getBoundingClientRect().bottom) : null,
      navLabels: items.map(i => ({ text: i.querySelector('.sidebar-item__label').textContent.trim(), rects: lines(i.querySelector('.sidebar-item__label')), h: Math.round(i.querySelector('.sidebar-item__label').getBoundingClientRect().height) })),
      heading: box(document.querySelector('.heading-block')),
      cardRow: box(document.querySelector('.home-content__cards')),
      cards: cards.map(c => ({
        title: c.querySelector('.action-card__header').textContent.trim(),
        titleRects: lines(c.querySelector('.action-card__header')),
        titleY: Math.round(c.querySelector('.action-card__header').getBoundingClientRect().y),
        descY: Math.round(c.querySelector('.action-card__description').getBoundingClientRect().y),
        arrowY: Math.round(c.querySelector('.action-card__arrow').getBoundingClientRect().y),
        box: box(c),
      })),
      activeIconFill: active ? getComputedStyle(active.querySelector('ng-icon svg')).fill : null,
      inactiveIconFill: (() => { const i = items.find(x => !x.classList.contains('sidebar-item--active')); const s = i && i.querySelector('ng-icon svg'); return s ? getComputedStyle(s).fill : null; })(),
      gradientDef: !!document.querySelector('#icon-gradient-active'),
      gradientStops: [...document.querySelectorAll('#icon-gradient-active stop')].map(s => getComputedStyle(s).stopColor),
      logoImg: (() => { const i = document.querySelector('.sidebar__logo-img'); return i ? { complete: i.complete, naturalWidth: i.naturalWidth, naturalHeight: i.naturalHeight, src: i.getAttribute('src') } : null; })(),
    };
  });
  console.log(JSON.stringify(m));
  await b.close();
})();
