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
 * a failing suite is that decision, and it has not been made. Every line that
 * looks like a verdict (`NO-SCROLL OK`, `TRUNCATED`, `COLLISION`) is a label
 * printed next to the number it came from, not an assertion.
 *
 * Usage (a server must already be serving the app):
 *   node tools/measure.mjs [baseUrl] [--screenshot <file>] [--route <path>]
 *                          [--viewport <w>x<h>] [--theme light|dark]
 *                          [--lang de|en] [--verbose]
 *
 * Covers:
 *   REQ-024 / REQ-123 — nothing scrolls, over the full appearance x language
 *                       grid: 14 routes x 2 themes x 2 languages x 3 viewports
 *   REQ-022           — sidebar box, nav item, group gaps
 *   REQ-014           — heading block, card row, widget column (must not move
 *                       when the status bar is added: it overlays, it does not
 *                       push)
 *   REQ-112           — the status bar's box on every route, and its
 *                       non-collision with Home's heading and card row
 *   REQ-115           — the open dropdown's overlay box (rendered into the CDK
 *                       overlay container on document.body, NOT inside .shell)
 *   REQ-918 / REQ-935 — the open overflow popover: its panel box, every tile
 *                       box against --size-touch-min, the glyph each tile
 *                       actually rendered, and the DOCUMENT's scroll
 *                       dimensions while the panel is open
 *   REQ-116 … REQ-118 — the Settings screen's boxes
 *   REQ-007 / REQ-009 — nothing wraps or truncates, in either language
 *   REQ-104           — sampled dark/light composites vs. 02-design.md §3.4
 *   REQ-011           — logo decodes
 *
 * The appearance and language axes are driven **through the UI** (the Settings
 * screen's option buttons), not by seeding storage, so the grid exercises the
 * real write path. The choice survives `page.goto()` because the stand-in
 * gateway persists it in `sessionStorage`, which is per browser context.
 */
import { chromium } from 'playwright';

/**
 * Every route the `%dev` catalogue can name (sidebar-catalogue-overflow
 * REQ-935). **This list must track that catalogue**: it is hard-coded rather
 * than derived from the DOM because deriving it is a bigger change to a review
 * instrument than the problem warrants, and a route the list forgets is simply
 * a route this run does not measure. The eight after `/services` are the
 * disposable placeholder fixtures — when they are removed
 * (`V10__catalogue_expansion.sql` reversed plus a `registered-icons.ts` edit),
 * this line goes back to six.
 */
const ROUTES = [
  '/',
  '/info',
  '/umgebung',
  '/smart-home',
  '/services',
  '/einstellungen',
  '/wlan',
  '/anreise',
  '/hausordnung',
  '/muell',
  '/aerztliches',
  '/parken',
  '/gastronomie',
  '/aktivitaeten',
];
const VIEWPORTS = [
  [1024, 768],
  [1280, 832],
  [1440, 900],
];
const THEMES = ['light', 'dark'];
const LANGUAGES = ['de', 'en'];

/** Index of each option button on the Settings screen, per the shipped
    `settingsConfig` (example-property.data.ts): languages [de, en],
    appearances [light, dark]. Order, not label, because the labels change
    with the very axis being driven. */
const LANGUAGE_INDEX = { de: 0, en: 1 };
const APPEARANCE_INDEX = { light: 0, dark: 1 };

/** 02-design.md §3.4 composites each surface over the mesh's *average*
    colour. Those two constants are the document's, reproduced here so the
    numbers this prints are comparable to the table line by line. */
const MESH_AVERAGE = { light: [0xde, 0xa3, 0xb4], dark: [0x3d, 0x1e, 0x2b] };

/** The table itself, for a side-by-side print. Purely informational. */
const DESIGN_CONTRAST = {
  'primary text on glass': { light: 10.89, dark: 8.91 },
  /* The two light `secondary` rows read 3.99 and 3.48 until 06-review.md
     finding #3: --ink-600 was below WCAG AA on every light surface. It was
     darkened #6e6486 -> #5a516e on 2026-08-19 (02-design.md §3.4), and these
     are the composites that darkening produces. Dark is untouched. */
  'secondary text on glass': { light: 5.38, dark: 5.42 },
  'primary text on sidebar': { light: 9.52, dark: 10.4 },
  'secondary text on sidebar': { light: 4.7, dark: 6.32 },
  'primary text on the mesh': { light: 7.15, dark: 13.4 },
  /* A range, not a point: the bar has no surface of its own, so its text sits
     on the mesh *where the bar is*, and the mesh is not one colour across that
     box. §3.4 recorded a single 10.49 / 10.90 taken at mesh grid point 4 until
     06-review.md finding #2; these are the band scan printed below. */
  'status-bar text in its corner': { light: '7.66-8.71', dark: '11.80-12.39' },
  'glass panel vs. its backdrop': { light: 1.52, dark: 1.5 },
  'sidebar vs. its backdrop': { light: 1.33, dark: 1.29 },
  'active nav pill vs. sidebar': { light: 1.41, dark: 1.69 },
};

/**
 * `CONTENT_FALLBACK_ICON` is `phosphorCircle` (`core/icons/registered-icons.ts`),
 * which is what an UNREGISTERED wire icon name renders as. REQ-904 says no
 * catalogue tile may fall back to it, and the only way to see that in a browser
 * is to compare the glyph the DOM actually holds — `[name]` is a property
 * binding, so the name is not reflected as an attribute.
 *
 * The WHOLE `d`, and the path count with it. A prefix is not enough:
 * `phosphorClock` is a clock face, so its first path is the identical
 * 104-radius circle and a 25-character prefix flags it as a fallback (measured
 * 2026-08-27 — it produced eight false findings before this was tightened).
 */
const FALLBACK_GLYPH_PATH =
  'M128,24A104,104,0,1,0,232,128,104.11,104.11,0,0,0,128,24Zm0,192a88,88,0,1,1,88-88A88.1,88.1,0,0,1,128,216Z';

/** Home's signed-off geometry from `home-screen-prototype` (06-review.md).
    The status bar overlays rather than pushes, so these must not have moved. */
const HOME_BASELINE = { headingY: 88, cardRowBottom: 744, viewport: '1280x832' };

const argv = process.argv.slice(2);
const flag = (name) => {
  const i = argv.indexOf(name);
  return i === -1 ? null : argv[i + 1];
};
const has = (name) => argv.includes(name);
const baseUrl = (argv[0] && !argv[0].startsWith('--') ? argv[0] : null) ?? 'http://localhost:4300';
const screenshot = flag('--screenshot');
const oneRoute = flag('--route');
const oneViewport = flag('--viewport');
const oneTheme = flag('--theme');
const oneLang = flag('--lang');
const verbose = has('--verbose');

/** PRODUCTION-build measurement (guest-frontend-integration): a prod bundle
    carries no dev token fallback (REQ-505 — that is the point), so an empty
    profile boots to /pairing and the grid cannot run. Seeding the public
    dev-only token per context reproduces what the dev build does implicitly.
    Usage: GXP_MEASURE_DEVICE_TOKEN=gxp-dev-device-token node tools/measure.mjs … */
const seedToken = process.env.GXP_MEASURE_DEVICE_TOKEN ?? null;
const seedContext = async (context) => {
  if (seedToken) {
    await context.addInitScript((token) => {
      if (!localStorage.getItem('gxp.device-token')) {
        localStorage.setItem('gxp.device-token', token);
      }
    }, seedToken);
  }
  return context;
};

const round = (n, places = 2) => {
  const f = 10 ** places;
  return n === null || n === undefined ? null : Math.round(n * f) / f;
};

/* ---------------------------------------------------------------------- *
 * Colour maths (WCAG 2.x), run in node on values read out of the page.
 * ---------------------------------------------------------------------- */

const parseColor = (value) => {
  const nums = String(value)
    .replace(/^[a-z]+\(/i, '')
    .replace(/\)$/, '')
    .split(/[\s,/]+/)
    .filter(Boolean)
    .map(Number);
  if (nums.length < 3 || nums.some(Number.isNaN)) return null;
  return [nums[0], nums[1], nums[2], nums.length > 3 ? nums[3] : 1];
};
/** Source-over composite of a translucent colour on an opaque backdrop. */
const over = (rgba, backdrop) =>
  rgba.slice(0, 3).map((c, i) => c * rgba[3] + backdrop[i] * (1 - rgba[3]));
const toLinear = (c) => {
  const v = c / 255;
  return v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
};
const luminance = ([r, g, b]) => 0.2126 * toLinear(r) + 0.7152 * toLinear(g) + 0.0722 * toLinear(b);
const contrast = (a, b) => {
  const [l1, l2] = [luminance(a), luminance(b)];
  const [hi, lo] = l1 > l2 ? [l1, l2] : [l2, l1];
  return (hi + 0.05) / (lo + 0.05);
};
const hex = (rgb) => '#' + rgb.map((c) => Math.round(c).toString(16).padStart(2, '0')).join('');

/* ---------------------------------------------------------------------- *
 * In-page collection.
 * ---------------------------------------------------------------------- */

/** Runs in the page: everything this repo's acceptance criteria measure. */
function collect() {
  const box = (el) => {
    if (!el) return null;
    const r = el.getBoundingClientRect();
    return {
      x: Math.round(r.x),
      y: Math.round(r.y),
      w: Math.round(r.width),
      h: Math.round(r.height),
      right: Math.round(r.right),
      bottom: Math.round(r.bottom),
    };
  };
  const range = (el) => {
    const r = document.createRange();
    r.selectNodeContents(el);
    return r;
  };
  const lineCount = (el) => (el ? range(el).getClientRects().length : null);
  /** Advance width of the laid-out text itself — `scrollWidth` on a block
      element only reports its own box, so it cannot see an ellipsis. */
  const inkWidth = (el) =>
    el ? Math.round(range(el).getBoundingClientRect().width * 100) / 100 : null;
  /** True when the text is wider than the box that paints it, i.e. it is
      ellipsized or clipped. Inline elements have `clientWidth === 0` and a
      rect that is the ink itself, so for those the nearest clipping ancestor
      is what decides. */
  const truncated = (el) => {
    if (!el) return null;
    const ink = inkWidth(el);
    const own = el.clientWidth || el.getBoundingClientRect().width;
    if (ink > own + 0.5) return true;
    const rect = el.getBoundingClientRect();
    for (let p = el.parentElement; p; p = p.parentElement) {
      const style = getComputedStyle(p);
      if (style.overflowX !== 'visible' || style.overflowY !== 'visible') {
        const pr = p.getBoundingClientRect();
        return rect.right > pr.right + 0.5 || rect.left < pr.left - 0.5;
      }
    }
    return false;
  };
  /** One record per piece of text worth checking for wrap/truncation. */
  const text = (el, where, singleLine = true) =>
    el
      ? {
          where,
          text: el.textContent.trim(),
          lines: lineCount(el),
          inkW: inkWidth(el),
          boxW: Math.round((el.clientWidth || el.getBoundingClientRect().width) * 100) / 100,
          truncated: truncated(el),
          singleLine,
        }
      : null;
  const all = (selector, where, singleLine = true) =>
    [...document.querySelectorAll(selector)].map((el, i) => text(el, `${where}[${i}]`, singleLine));

  const doc = document.documentElement;
  const items = [...document.querySelectorAll('.sidebar-item')];
  const navItems = [...document.querySelectorAll('.sidebar__menu-items .sidebar-item')];
  const settingsItem = document.querySelector('.sidebar__settings .sidebar-item');
  const logo = document.querySelector('.sidebar__logo');
  const logoImg = document.querySelector('.sidebar__logo-img');
  const gap = (a, b) =>
    a && b ? Math.round(b.getBoundingClientRect().top - a.getBoundingClientRect().bottom) : null;

  const statusBar = document.querySelector('.status-bar');
  const triggers = [...document.querySelectorAll('.status-bar .status-bar-menu__trigger')];
  /** The CDK renders the panel into an overlay container on `document.body`,
      outside `.shell` — so it is queried from the document, never from the
      route's subtree (02-design.md §8.4). */
  const overlayPanel = document.querySelector('.cdk-overlay-container .status-bar-menu__panel');

  /** The overflow popover, also in the CDK overlay container on document.body
      (sidebar-catalogue-overflow 02-design.md §7.2). The trigger is identified
      by `aria-haspopup`, which the CDK writes — the sidebar carries no other
      marker distinguishing it from an ordinary slot, deliberately: it IS an
      ordinary `.sidebar-item`. */
  const overflowTrigger = document.querySelector('.sidebar__menu-items [aria-haspopup]');
  const overflowPanel = document.querySelector('.cdk-overlay-container .sidebar-overflow');
  const overflowGrid = overflowPanel
    ? overflowPanel.querySelector('.sidebar-overflow__grid')
    : null;

  const settingsRoot = document.querySelector('.settings');
  const choiceGroups = [...document.querySelectorAll('.setting-choice')].map((group, gi) => ({
    label: text(group.querySelector('.setting-choice__label'), `settings.group[${gi}].label`),
    hint: text(group.querySelector('.setting-choice__hint'), `settings.group[${gi}].hint`, false),
    options: [...group.querySelectorAll('.setting-choice__option')].map((opt, oi) => ({
      box: box(opt),
      selected: opt.classList.contains('setting-choice__option--selected'),
      label: text(
        opt.querySelector('.setting-choice__option-label'),
        `settings.group[${gi}].option[${oi}]`,
      ),
    })),
  }));

  /** Resolved (used) colours, read off real elements rather than off custom
      properties, so what is reported is what the browser painted. */
  const usedColor = (selector, prop) => {
    const el = document.querySelector(selector);
    return el ? getComputedStyle(el)[prop] : null;
  };

  return {
    doc: {
      theme: doc.dataset.theme ?? null,
      lang: doc.getAttribute('lang'),
      path: location.pathname,
    },
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
    gapLastNavToSettings: gap(navItems[navItems.length - 1], settingsItem),
    labels: items.map((i, idx) => text(i.querySelector('.sidebar-item__label'), `sidebar[${idx}]`)),

    statusBar: statusBar
      ? {
          box: box(statusBar),
          triggers: triggers.map((t) => box(t)),
          divider: box(document.querySelector('.status-bar__divider')),
          clock: text(document.querySelector('.status-bar__clock'), 'statusBar.clock'),
          languageCode: text(
            document.querySelector('.status-bar__language-code'),
            'statusBar.langCode',
          ),
          triggerNames: triggers.map((t) => t.getAttribute('aria-label')),
        }
      : null,

    overlay: overlayPanel
      ? {
          inShell: !!document.querySelector('.shell .status-bar-menu__panel'),
          onBody: !!document.querySelector('body > .cdk-overlay-container .status-bar-menu__panel'),
          panel: box(overlayPanel),
          role: overlayPanel.getAttribute('role'),
          options: [...overlayPanel.querySelectorAll('.status-bar-menu__option')].map(
            (opt, oi) => ({
              box: box(opt),
              selected: opt.classList.contains('status-bar-menu__option--selected'),
              checked: !!opt.querySelector('.status-bar-menu__option-check'),
              label: text(
                opt.querySelector('.status-bar-menu__option-label'),
                `menu.option[${oi}]`,
              ),
            }),
          ),
        }
      : null,

    menuOverflow: {
      trigger: overflowTrigger
        ? {
            box: box(overflowTrigger),
            label: text(overflowTrigger.querySelector('.sidebar-item__label'), 'overflow.trigger'),
            expanded: overflowTrigger.getAttribute('aria-expanded'),
            hasPopup: overflowTrigger.getAttribute('aria-haspopup'),
            ariaLabel: overflowTrigger.getAttribute('aria-label'),
            active: overflowTrigger.classList.contains('sidebar-item--active'),
          }
        : null,
      panel: overflowPanel
        ? {
            inShell: !!document.querySelector('.shell .sidebar-overflow'),
            onBody: !!document.querySelector('body > .cdk-overlay-container .sidebar-overflow'),
            box: box(overflowPanel),
            role: overflowPanel.getAttribute('role'),
            ariaLabel: overflowPanel.getAttribute('aria-label'),
            maxHeight: getComputedStyle(overflowPanel).maxHeight,
            columns: overflowGrid ? getComputedStyle(overflowGrid).gridTemplateColumns : null,
            /* The GRID may scroll inside the panel — legal since constitution
               v1.8.0, which binds the document, not a bounded sub-component. */
            gridScrolls: overflowGrid
              ? overflowGrid.scrollHeight > overflowGrid.clientHeight + 0.5
              : null,
            tiles: [...overflowPanel.querySelectorAll('.sidebar-overflow__tile')].map(
              (tile, i) => ({
                box: box(tile),
                role: tile.getAttribute('role'),
                label: text(
                  tile.querySelector('.sidebar-overflow__tile-label'),
                  `overflow.tile[${i}]`,
                  false,
                ),
                /* `-webkit-line-clamp` hides the overflow without changing
                   the ink width, so the generic `truncated()` heuristic
                   cannot see it. This is the direct question: is there a
                   third line the clamp is eating? */
                clamped: (() => {
                  const label = tile.querySelector('.sidebar-overflow__tile-label');
                  return label ? label.scrollHeight > label.clientHeight + 0.5 : null;
                })(),
                glyph: (() => {
                  const paths = [...tile.querySelectorAll('ng-icon svg path')];
                  return {
                    paths: paths.length,
                    d: paths.length ? (paths[0].getAttribute('d') ?? '') : null,
                  };
                })(),
              }),
            ),
          }
        : null,
    },

    heading: box(document.querySelector('.heading-block')),
    headlineLines: lineCount(document.querySelector('.heading-block__headline')),
    paragraphLines: lineCount(document.querySelector('.heading-block__paragraph')),
    headlineText: text(document.querySelector('.heading-block__headline'), 'home.headline', false),
    paragraphText: text(
      document.querySelector('.heading-block__paragraph'),
      'home.paragraph',
      false,
    ),
    cardRow: box(document.querySelector('.home-content__cards')),
    widgets: box(document.querySelector('.home-content__widgets')),
    cards: [...document.querySelectorAll('.action-card')].map((c, ci) => {
      const title = c.querySelector('.action-card__header');
      const desc = c.querySelector('.action-card__description');
      const arrow = c.querySelector('.action-card__arrow');
      return {
        box: box(c),
        title: text(title, `card[${ci}].title`),
        description: text(desc, `card[${ci}].description`, false),
        titleY: Math.round(title.getBoundingClientRect().y),
        descY: desc ? Math.round(desc.getBoundingClientRect().y) : null,
        arrowY: arrow ? Math.round(arrow.getBoundingClientRect().y) : null,
      };
    }),

    settings: settingsRoot
      ? {
          root: box(settingsRoot),
          title: text(document.querySelector('.settings__title'), 'settings.title'),
          titleBox: box(document.querySelector('.settings__title')),
          panels: [...document.querySelectorAll('.settings__panel')].map((p) => box(p)),
          groups: choiceGroups,
        }
      : null,

    placeholder: all(
      '.section-placeholder__title, .section-placeholder__note',
      'placeholder',
      false,
    ),

    colors: {
      textPrimary: usedColor('.sidebar-item__label', 'color') ?? usedColor('.shell', 'color'),
      textSecondary:
        usedColor('.setting-choice__hint', 'color') ??
        usedColor('.action-card__description', 'color'),
      glass:
        usedColor('.action-card', 'backgroundColor') ??
        usedColor('.settings__panel', 'backgroundColor'),
      glassPanelSettings: usedColor('.settings__panel', 'backgroundColor'),
      sidebar: usedColor('.sidebar', 'backgroundColor'),
      shellBase: usedColor('.shell', 'backgroundColor'),
      statusBarText: usedColor('.status-bar__clock', 'color'),
      pillGradient: usedColor('.sidebar-item--active', 'backgroundImage'),
      divider: usedColor('.status-bar__divider', 'backgroundColor'),
    },

    logo: logoImg
      ? {
          complete: logoImg.complete,
          naturalWidth: logoImg.naturalWidth,
          src: logoImg.getAttribute('src'),
        }
      : null,
  };
}

/* ---------------------------------------------------------------------- *
 * Driving the two preference axes through the UI.
 * ---------------------------------------------------------------------- */

/**
 * Sets appearance and language by clicking the Settings screen's own option
 * buttons — the real write path. Since `guest-frontend-integration` the
 * choice persists SERVER-side (the stay's preference record), so surviving
 * the route loop's `goto()`s needs the fire-and-forget `PUT` to have landed
 * before the next reload hydrates from the session — the second wait below
 * polls the session until the server holds the chosen values (the old
 * sessionStorage stand-in was synchronous and needed no such wait; without
 * it, a reload can race the write and re-render the previous preference).
 */
async function applyPreferences(page, theme, language) {
  await page.goto(baseUrl + '/einstellungen', { waitUntil: 'load' });
  await page.waitForSelector('.setting-choice', { timeout: 60000 });
  const groups = page.locator('.setting-choice');
  await groups.nth(0).locator('.setting-choice__option').nth(LANGUAGE_INDEX[language]).click();
  await groups.nth(1).locator('.setting-choice__option').nth(APPEARANCE_INDEX[theme]).click();
  await page.waitForFunction(
    ([t, l]) =>
      document.documentElement.dataset.theme === t &&
      document.documentElement.getAttribute('lang') === l,
    [theme, language],
    { timeout: 5000 },
  );
  try {
    await page.waitForFunction(
      async ([t, l]) => {
        const token = localStorage.getItem('gxp.device-token');
        if (!token) return true; // unpaired — nothing to persist against
        const res = await fetch('/api/guest/session', {
          headers: { Authorization: 'Bearer ' + token },
        });
        if (!res.ok) return true; // offline states measure what they render
        const session = await res.json();
        return session.preference?.appearance === t && session.preference?.language === l;
      },
      [theme, language],
      { timeout: 10000, polling: 500 },
    );
  } catch {
    note('PREFERENCE WRITE NOT CONFIRMED', `${theme}/${language} — reloads may show stale values`);
  }
}

/** Opens one of the status bar's dropdowns and waits for the overlay panel. */
async function openDropdown(page, which /* 0 = language, 1 = appearance */) {
  await page.locator('.status-bar .status-bar-menu__trigger').nth(which).click();
  await page.waitForSelector('.cdk-overlay-container .status-bar-menu__panel', { timeout: 5000 });
}

/** Opens the sidebar's overflow popover and waits for the panel. */
async function openOverflow(page) {
  await page.locator('.sidebar__menu-items [aria-haspopup]').click();
  await page.waitForSelector('.cdk-overlay-container .sidebar-overflow', { timeout: 5000 });
}

/**
 * A grid of sample points inside a box, inset from its edges. Sampling a
 * *patch* rather than a single pixel is what makes the surface readings
 * robust: one point can land on a corner radius, a shadow, a glyph or a
 * glass border and report a colour the surface never has. The caller takes
 * the median, so a minority of ink pixels cannot move the answer.
 */
const patch = (box, inset = 10, n = 5) => {
  const points = [];
  for (let i = 0; i < n; i += 1) {
    for (let j = 0; j < n; j += 1) {
      points.push({
        x: box.x + inset + ((box.w - 2 * inset) * i) / (n - 1),
        y: box.y + inset + ((box.h - 2 * inset) * j) / (n - 1),
      });
    }
  }
  return points;
};

/** Median by relative luminance — the representative pixel of a patch. */
const median = (pixels) => {
  const sorted = [...pixels].sort((a, b) => luminance(a.rgb) - luminance(b.rgb));
  return sorted[Math.floor(sorted.length / 2)].rgb;
};

/**
 * Reads real pixels out of a screenshot. Playwright hands back an encoded
 * PNG and node has no decoder, so the decode happens in the page itself
 * (`createImageBitmap` + `OffscreenCanvas`) — no dependency is added.
 * `deviceScaleFactor: 1` is what makes CSS pixels and image pixels the same.
 */
async function samplePixels(page, points) {
  const png = (await page.screenshot({ type: 'png' })).toString('base64');
  return page.evaluate(
    async ({ png, points }) => {
      const blob = await (await fetch('data:image/png;base64,' + png)).blob();
      const bitmap = await createImageBitmap(blob);
      const canvas = new OffscreenCanvas(bitmap.width, bitmap.height);
      const ctx = canvas.getContext('2d');
      ctx.drawImage(bitmap, 0, 0);
      return points.map((p) => {
        const d = ctx.getImageData(Math.round(p.x), Math.round(p.y), 1, 1).data;
        return {
          name: p.name ?? null,
          x: Math.round(p.x),
          y: Math.round(p.y),
          rgb: [d[0], d[1], d[2]],
        };
      });
    },
    { png, points },
  );
}

/* ---------------------------------------------------------------------- *
 * Reporting helpers.
 * ---------------------------------------------------------------------- */

const findings = [];
const note = (kind, detail) => {
  findings.push({ kind, detail });
  console.log(`  !! ${kind}: ${detail}`);
};

/** Overlap of two boxes, or null. Used for the status bar vs. Home. */
const overlap = (a, b) => {
  if (!a || !b) return null;
  const x = Math.min(a.right, b.right) - Math.max(a.x, b.x);
  const y = Math.min(a.bottom, b.bottom) - Math.max(a.y, b.y);
  return x > 0 && y > 0 ? { x, y } : null;
};

const wrapProblems = (records) =>
  records
    .filter(Boolean)
    .filter((r) => r.truncated || (r.singleLine && r.lines !== 1))
    .map(
      (r) =>
        `${r.where} "${r.text}" lines=${r.lines} ink=${r.inkW} box=${r.boxW}${r.truncated ? ' TRUNCATED' : ''}`,
    );

const textRecords = (m) => [
  ...m.labels,
  ...(m.statusBar ? [m.statusBar.clock, m.statusBar.languageCode] : []),
  ...m.cards.flatMap((c) => [c.title, c.description]),
  ...(m.headlineText ? [m.headlineText, m.paragraphText] : []),
  ...(m.settings
    ? [
        m.settings.title,
        ...m.settings.groups.flatMap((g) => [g.label, g.hint, ...g.options.map((o) => o.label)]),
      ]
    : []),
  ...m.placeholder,
];

/* ---------------------------------------------------------------------- *
 * Run.
 * ---------------------------------------------------------------------- */

const routes = oneRoute ? [oneRoute] : ROUTES;
const viewports = oneViewport ? [oneViewport.split('x').map(Number)] : VIEWPORTS;
const themes = oneTheme ? [oneTheme] : THEMES;
const languages = oneLang ? [oneLang] : LANGUAGES;

const browser = await chromium.launch();
let combinations = 0;
let overflowing = 0;
let truncatedCount = 0;

console.log(`base ${baseUrl}`);
console.log(
  `grid: ${routes.length} routes x ${themes.length} themes x ${languages.length} languages x ` +
    `${viewports.length} viewports = ${routes.length * themes.length * languages.length * viewports.length} combinations`,
);

console.log('\n############ PASS 1 — no-scroll grid, status-bar box, wrapping ############');

for (const [w, h] of viewports) {
  const context = await seedContext(
    await browser.newContext({
      viewport: { width: w, height: h },
      deviceScaleFactor: 1,
    }),
  );
  const page = await context.newPage();
  for (const theme of themes) {
    for (const language of languages) {
      await applyPreferences(page, theme, language);
      console.log(`\n=== ${w} x ${h} · ${theme} · ${language} ===`);
      for (const route of routes) {
        await page.goto(baseUrl + route, { waitUntil: 'load' });
        await page.waitForSelector('.shell');
        await page.waitForTimeout(120);
        const m = await page.evaluate(collect);
        combinations += 1;
        const s = m.scroll;
        const ok = s.scrollW <= s.clientW && s.scrollH <= s.clientH;
        if (!ok) overflowing += 1;
        console.log(
          `${route.padEnd(14)} theme=${m.doc.theme} lang=${m.doc.lang} ` +
            `scrollW=${s.scrollW}/${s.clientW} scrollH=${s.scrollH}/${s.clientH} ` +
            `root=${m.rootFontSize} overflow=${s.htmlOverflow}/${s.bodyOverflow} ${ok ? 'NO-SCROLL OK' : 'OVERFLOW'}`,
        );
        if (m.doc.theme !== theme || m.doc.lang !== language) {
          note(
            'PREFERENCE NOT APPLIED',
            `${route} asked ${theme}/${language} got ${m.doc.theme}/${m.doc.lang}`,
          );
        }
        if (!ok)
          note(
            'OVERFLOW',
            `${route} ${w}x${h} ${theme}/${language} ${s.scrollW}/${s.clientW} ${s.scrollH}/${s.clientH}`,
          );

        // The status bar is app chrome: it must be on every route, in the
        // reserved top-right corner (REQ-112).
        if (!m.statusBar) {
          note('STATUS BAR MISSING', `${route} ${w}x${h} ${theme}/${language}`);
        } else {
          const b = m.statusBar.box;
          console.log(
            `  statusBar ${JSON.stringify(b)} rightInset=${s.clientW - b.right} topInset=${b.y} ` +
              `triggers=${m.statusBar.triggers.map((t) => `${t.w}x${t.h}`).join(',')} ` +
              `divider=${m.statusBar.divider ? `${m.statusBar.divider.w}x${m.statusBar.divider.h}` : 'none'} ` +
              `clock="${m.statusBar.clock.text}" code="${m.statusBar.languageCode?.text ?? '-'}"`,
          );
          if (b.right > s.clientW || b.y < 0 || b.bottom > s.clientH) {
            note('STATUS BAR OUT OF VIEWPORT', `${route} ${w}x${h} ${JSON.stringify(b)}`);
          }
        }

        // Route-specific key geometry, so the grid itself carries the numbers
        // that must not have moved.
        if (m.heading) {
          console.log(
            `  heading ${JSON.stringify(m.heading)} lines h/p=${m.headlineLines}/${m.paragraphLines} ` +
              `cardRow ${JSON.stringify(m.cardRow)} widgets w=${m.widgets?.w} cards=${m.cards.length}`,
          );
        }
        if (m.settings) {
          const p = m.settings.panels;
          console.log(
            `  settings root ${JSON.stringify(m.settings.root)} title ${JSON.stringify(m.settings.titleBox)} ` +
              `panels ${p.map((x) => `${x.w}x${x.h}@${x.x},${x.y}`).join(' ')} ` +
              `optionH=${m.settings.groups.flatMap((g) => g.options.map((o) => o.box.h)).join(',')}`,
          );
        }

        const problems = wrapProblems(textRecords(m));
        truncatedCount += textRecords(m).filter((r) => r && r.truncated).length;
        console.log(
          `  text: ${textRecords(m).filter(Boolean).length} strings checked, ${problems.length ? 'PROBLEMS' : 'none wrapping or truncated'}`,
        );
        for (const p of problems) note('TEXT', `${route} ${w}x${h} ${theme}/${language} ${p}`);

        if (m.logo && (!m.logo.complete || m.logo.naturalWidth === 0)) {
          note('LOGO DID NOT DECODE', JSON.stringify(m.logo));
        }
        if (verbose) console.log('  full: ' + JSON.stringify(m));
        if (screenshot && route === routes[0] && theme === themes[0] && language === languages[0]) {
          await page.screenshot({ path: screenshot });
        }
      }
    }
  }
  await context.close();
}

console.log(
  '\n############ PASS 2 — status bar vs. Home (collision budget, 02-design.md §8.2) ############',
);

for (const [w, h] of viewports.filter(([vw]) => vw === 1024 || vw === 1280)) {
  for (const theme of themes) {
    for (const language of languages) {
      const context = await seedContext(
        await browser.newContext({
          viewport: { width: w, height: h },
          deviceScaleFactor: 1,
        }),
      );
      const page = await context.newPage();
      await applyPreferences(page, theme, language);
      await page.goto(baseUrl + '/', { waitUntil: 'load' });
      await page.waitForSelector('.status-bar');
      await page.waitForTimeout(120);
      const m = await page.evaluate(collect);
      const bar = m.statusBar.box;
      const cards = m.cardRow;
      const heading = m.heading;
      const cardOverlap = overlap(bar, cards);
      const headingOverlap = overlap(bar, heading);
      console.log(
        `\n${w}x${h} · ${theme} · ${language}\n` +
          `  bar      ${JSON.stringify(bar)}\n` +
          `  heading  ${JSON.stringify(heading)}\n` +
          `  cardRow  ${JSON.stringify(cards)}\n` +
          `  horizontal clearance bar.x - cardRow.right = ${bar.x - cards.right}\n` +
          `  vertical clearance heading.y - bar.bottom  = ${heading.y - bar.bottom}\n` +
          `  overlap vs cardRow: ${cardOverlap ? JSON.stringify(cardOverlap) : 'none'} ` +
          `vs heading: ${headingOverlap ? JSON.stringify(headingOverlap) : 'none'}`,
      );
      if (cardOverlap) note('COLLISION', `status bar x cardRow at ${w}x${h} ${theme}/${language}`);
      if (headingOverlap)
        note('COLLISION', `status bar x heading at ${w}x${h} ${theme}/${language}`);
      if (w === 1280 && h === 832) {
        console.log(
          `  baseline check (${HOME_BASELINE.viewport}): heading.y ${heading.y} vs ${HOME_BASELINE.headingY}, ` +
            `cardRow.bottom ${cards.bottom} vs ${HOME_BASELINE.cardRowBottom}`,
        );
        if (heading.y !== HOME_BASELINE.headingY || cards.bottom !== HOME_BASELINE.cardRowBottom) {
          note(
            'HOME GEOMETRY MOVED',
            `${theme}/${language}: ${heading.y}/${cards.bottom} vs ${HOME_BASELINE.headingY}/${HOME_BASELINE.cardRowBottom}`,
          );
        }
      }
      await context.close();
    }
  }
}

console.log('\n############ PASS 3 — open dropdown overlay (REQ-115, REQ-123) ############');

for (const [w, h] of viewports) {
  for (const theme of themes) {
    for (const language of languages) {
      const context = await seedContext(
        await browser.newContext({
          viewport: { width: w, height: h },
          deviceScaleFactor: 1,
        }),
      );
      const page = await context.newPage();
      await applyPreferences(page, theme, language);
      await page.goto(baseUrl + '/', { waitUntil: 'load' });
      await page.waitForSelector('.status-bar');
      for (const [which, name] of [
        [0, 'language'],
        [1, 'appearance'],
      ]) {
        await openDropdown(page, which);
        await page.waitForTimeout(120);
        const m = await page.evaluate(collect);
        const o = m.overlay;
        const s = m.scroll;
        const inside =
          o.panel.x >= 0 &&
          o.panel.y >= 0 &&
          o.panel.right <= s.clientW &&
          o.panel.bottom <= s.clientH;
        const ok = s.scrollW <= s.clientW && s.scrollH <= s.clientH;
        console.log(
          `${w}x${h} · ${theme} · ${language} · ${name}: panel ${JSON.stringify(o.panel)} ` +
            `options=${o.options.map((x) => `${x.box.w}x${x.box.h}`).join(',')} ` +
            `labels=[${o.options.map((x) => `"${x.label.text}"${x.checked ? '✓' : ''}`).join(' ')}] ` +
            `onBody=${o.onBody} inShell=${o.inShell} withinViewport=${inside} ` +
            `scroll ${s.scrollW}/${s.clientW} ${s.scrollH}/${s.clientH} ${ok ? 'NO-SCROLL OK' : 'OVERFLOW'}`,
        );
        if (!inside)
          note(
            'OVERLAY OUTSIDE VIEWPORT',
            `${name} ${w}x${h} ${theme}/${language} ${JSON.stringify(o.panel)}`,
          );
        if (!ok) note('OVERLAY CAUSED SCROLL', `${name} ${w}x${h} ${theme}/${language}`);
        if (o.inShell)
          note(
            'OVERLAY IN SHELL',
            'the panel is expected in the CDK overlay container on document.body',
          );
        for (const p of wrapProblems(o.options.map((x) => x.label)))
          note('TEXT', `menu ${name} ${w}x${h} ${theme}/${language} ${p}`);
        await page.keyboard.press('Escape');
        await page.waitForTimeout(80);
      }
      await context.close();
    }
  }
}

/*
 * The overflow popover (REQ-918, REQ-935). Only the two constitutional
 * viewports: the no-scroll rule is stated at 1024x768 and 1280x832, and this
 * pass is about the panel's own geometry rather than about the grid.
 *
 * `--size-touch-min` is 2.75rem and the root font size is viewport
 * proportional, so the minimum is read off the live document rather than
 * hard-coded — `--size-touch-target` does not exist.
 */
console.log('\n############ PASS 3B — open overflow popover (REQ-916…919, REQ-935) ############');

for (const [w, h] of viewports.filter(([vw]) => vw === 1024 || vw === 1280)) {
  for (const theme of themes) {
    for (const language of languages) {
      const context = await seedContext(
        await browser.newContext({
          viewport: { width: w, height: h },
          deviceScaleFactor: 1,
        }),
      );
      const page = await context.newPage();
      await applyPreferences(page, theme, language);
      await page.goto(baseUrl + '/', { waitUntil: 'load' });
      await page.waitForSelector('.sidebar');
      const closed = await page.evaluate(collect);
      if (!closed.menuOverflow.trigger) {
        console.log(
          `${w}x${h} · ${theme} · ${language}: no overflow trigger — the property offers ` +
            `${closed.labels.length - 1} sections, at or below the sidebar's slot count`,
        );
        await context.close();
        continue;
      }
      await openOverflow(page);
      await page.waitForTimeout(120);
      const m = await page.evaluate(collect);
      const o = m.menuOverflow.panel;
      const t = m.menuOverflow.trigger;
      const s = m.scroll;
      const rootPx = parseFloat(m.rootFontSize);
      const touchMin = 2.75 * rootPx;
      const inside =
        o.box.x >= 0 && o.box.y >= 0 && o.box.right <= s.clientW && o.box.bottom <= s.clientH;
      const noScroll = s.scrollW <= s.clientW && s.scrollH <= s.clientH;
      const contentLeft = m.sidebar ? m.sidebar.right : 0;
      const smallest = Math.min(...o.tiles.map((x) => Math.min(x.box.w, x.box.h)));
      const fallbacks = o.tiles.filter(
        (x) => x.glyph.paths === 1 && x.glyph.d === FALLBACK_GLYPH_PATH,
      );
      const glyphless = o.tiles.filter((x) => x.glyph.paths === 0);
      /* Vertically centred on the trigger is the preferred position; a
         fallback shifts it, which is legitimate and is printed rather than
         judged. */
      const centreDelta = Math.round((o.box.y + o.box.h / 2 - (t.box.y + t.box.h / 2)) * 100) / 100;
      console.log(
        `\n${w}x${h} · ${theme} · ${language}  (root ${m.rootFontSize}, touch-min ${round(touchMin)}px)\n` +
          `  trigger  ${JSON.stringify(t.box)} label="${t.label.text}" ` +
          `haspopup=${t.hasPopup} expanded=${t.expanded} aria-label=${t.ariaLabel} active=${t.active}\n` +
          `  panel    ${JSON.stringify(o.box)} role=${o.role} aria-label="${o.ariaLabel}"\n` +
          `  columns  ${o.columns}\n` +
          `  max-h    ${o.maxHeight}, grid scrolls internally: ${o.gridScrolls}\n` +
          `  gap to trigger: panel.x - trigger.right = ${round(o.box.x - t.box.right)}, ` +
          `centre offset ${centreDelta}\n` +
          `  right of the sidebar: panel.x - sidebar.right = ${round(o.box.x - contentLeft)}\n` +
          `  tiles    ${o.tiles.length}: ${o.tiles.map((x) => `${x.box.w}x${x.box.h}`).join(',')} ` +
          `smallest ${round(smallest)}px ${smallest >= touchMin ? '>= touch-min OK' : 'BELOW TOUCH-MIN'}\n` +
          `  labels   [${o.tiles.map((x) => `"${x.label.text}"(${x.label.lines}L${x.clamped ? ',CLAMPED' : ''})`).join(' ')}]\n` +
          `  glyphs   ${o.tiles.length - glyphless.length}/${o.tiles.length} rendered, ` +
          `${fallbacks.length === 0 ? 'none fell back to phosphorCircle OK' : `${fallbacks.length} FELL BACK`}\n` +
          `  onBody=${o.onBody} inShell=${o.inShell} withinViewport=${inside} ` +
          `scroll ${s.scrollW}/${s.clientW} ${s.scrollH}/${s.clientH} ${noScroll ? 'NO-SCROLL OK' : 'OVERFLOW'}`,
      );
      if (!noScroll)
        note('OVERFLOW PANEL CAUSED DOCUMENT SCROLL', `${w}x${h} ${theme}/${language}`);
      if (!inside)
        note(
          'OVERFLOW PANEL OUTSIDE VIEWPORT',
          `${w}x${h} ${theme}/${language} ${JSON.stringify(o.box)}`,
        );
      if (o.inShell)
        note('OVERFLOW PANEL IN SHELL', 'expected in the CDK overlay container on document.body');
      if (smallest < touchMin)
        note(
          'TILE BELOW TOUCH TARGET',
          `${w}x${h} ${theme}/${language}: ${round(smallest)}px < ${round(touchMin)}px`,
        );
      if (fallbacks.length)
        note(
          'FALLBACK GLYPH',
          `${fallbacks.length} tile(s) at ${w}x${h} ${theme}/${language} render phosphorCircle`,
        );
      if (glyphless.length)
        note('MISSING GLYPH', `${glyphless.length} tile(s) at ${w}x${h} ${theme}/${language}`);
      for (const p of wrapProblems(o.tiles.map((x) => x.label)))
        note('TEXT', `overflow tile ${w}x${h} ${theme}/${language} ${p}`);
      for (const tile of o.tiles.filter((x) => x.clamped))
        note(
          'TILE LABEL CLAMPED',
          `${w}x${h} ${theme}/${language} "${tile.label.text}" needs more than 2 lines`,
        );
      if (o.box.x < contentLeft)
        note('OVERFLOW PANEL OVERLAPS THE SIDEBAR', `${w}x${h} ${theme}/${language}`);
      await page.keyboard.press('Escape');
      await page.waitForTimeout(80);
      await context.close();
    }
  }
}

console.log('\n############ PASS 4 — composites vs. 02-design.md §3.4 ############');

for (const theme of themes) {
  const [w, h] = [1280, 832];
  const context = await seedContext(
    await browser.newContext({
      viewport: { width: w, height: h },
      deviceScaleFactor: 1,
    }),
  );
  const page = await context.newPage();
  await applyPreferences(page, theme, 'de');
  await page.goto(baseUrl + '/', { waitUntil: 'load' });
  await page.waitForSelector('.action-card');
  await page.waitForTimeout(250);
  const m = await page.evaluate(collect);
  const c = m.colors;
  const primary = parseColor(c.textPrimary);
  const secondary = parseColor(c.textSecondary);
  const glass = parseColor(c.glass);
  const sidebarSurface = parseColor(c.sidebar);
  const mesh = MESH_AVERAGE[theme];

  const bar = m.statusBar.box;
  const card = m.cards[0].box;
  const sidebarBox = m.sidebar;
  const pill = m.activePill;
  /** The empty middle band of Home: mesh, nothing on it. */
  const emptyBand = {
    x: m.cardRow.x,
    y: m.heading.bottom + 20,
    w: m.cardRow.w,
    h: m.cardRow.y - m.heading.bottom - 40,
  };
  const patches = {
    statusBarCorner: patch(bar, 6),
    glassCard: patch(card, 14),
    meshBesideCard: patch(emptyBand, 8),
    sidebarSurface: patch(
      { ...sidebarBox, y: sidebarBox.y + sidebarBox.h * 0.6, h: sidebarBox.h * 0.3 },
      8,
    ),
    meshBesideSidebar: patch(
      { x: sidebarBox.right + 8, y: sidebarBox.y + 40, w: 60, h: sidebarBox.h - 80 },
      4,
    ),
    activePill: patch(pill, 8),
    sidebarBesidePill: patch({ x: sidebarBox.x, y: pill.bottom + 6, w: sidebarBox.w, h: 40 }, 4),
  };
  const flat = Object.entries(patches).flatMap(([name, points]) =>
    points.map((p) => ({ ...p, name })),
  );
  const raw = await samplePixels(page, flat);
  const sampled = Object.fromEntries(
    Object.keys(patches).map((name) => {
      const pixels = raw.filter((p) => p.name === name);
      return [name, { name, rgb: median(pixels), n: pixels.length }];
    }),
  );

  console.log(`\n--- ${theme} · ${w}x${h} · used colours read off painted elements ---`);
  console.log(`  text primary   ${c.textPrimary}`);
  console.log(`  text secondary ${c.textSecondary}`);
  console.log(`  glass surface  ${c.glass}   (settings panel ${c.glassPanelSettings})`);
  console.log(`  sidebar        ${c.sidebar}`);
  console.log(`  .shell base    ${c.shellBase}`);
  console.log(`  divider        ${c.divider}`);
  console.log(`  active pill    ${c.pillGradient}`);
  console.log(`  mesh average used for the analytic column: ${hex(mesh)} (02-design.md §3.4)`);
  console.log(
    '  sampled patches (median of 25 real pixels each): ' +
      Object.values(sampled)
        .map((p) => `${p.name}=${hex(p.rgb)}`)
        .join(' '),
  );

  const glassOverMesh = over(glass, mesh);
  const sidebarOverMesh = over(sidebarSurface, mesh);
  /** The active pill is a gradient; §3.4's number is its **first** stop over
      the composited sidebar (0.9 in light, 0.18 in dark — the strong end). */
  const pillStop = parseColor((c.pillGradient ?? '').match(/rgba?\([^)]*\)/)?.[0] ?? '');
  const pillOverSidebar = pillStop ? over(pillStop, sidebarOverMesh) : null;
  const rows = [
    [
      'primary text on glass',
      contrast(primary, glassOverMesh),
      contrast(primary, sampled.glassCard.rgb),
    ],
    [
      'secondary text on glass',
      contrast(secondary, glassOverMesh),
      contrast(secondary, sampled.glassCard.rgb),
    ],
    [
      'primary text on sidebar',
      contrast(primary, sidebarOverMesh),
      contrast(primary, sampled.sidebarSurface.rgb),
    ],
    [
      'secondary text on sidebar',
      contrast(secondary, sidebarOverMesh),
      contrast(secondary, sampled.sidebarSurface.rgb),
    ],
    [
      'primary text on the mesh',
      contrast(primary, mesh),
      contrast(primary, sampled.meshBesideCard.rgb),
    ],
    [
      'status-bar text in its corner',
      null,
      contrast(parseColor(c.statusBarText), sampled.statusBarCorner.rgb),
    ],
    [
      'glass panel vs. its backdrop',
      contrast(glassOverMesh, mesh),
      contrast(sampled.glassCard.rgb, sampled.meshBesideCard.rgb),
    ],
    [
      'sidebar vs. its backdrop',
      contrast(sidebarOverMesh, mesh),
      contrast(sampled.sidebarSurface.rgb, sampled.meshBesideSidebar.rgb),
    ],
    [
      'active nav pill vs. sidebar',
      pillOverSidebar ? contrast(pillOverSidebar, sidebarOverMesh) : null,
      contrast(sampled.activePill.rgb, sampled.sidebarBesidePill.rgb),
    ],
  ];
  console.log('  pair                                §3.4   analytic  sampled   Δ(analytic)');
  for (const [name, analytic, sampledRatio] of rows) {
    const expected = DESIGN_CONTRAST[name][theme];
    const delta = analytic === null ? null : round(analytic - expected);
    console.log(
      `  ${name.padEnd(30)} ${String(expected).padStart(9)}  ${String(round(analytic) ?? '   —').padStart(8)}  ` +
        `${String(round(sampledRatio)).padStart(7)}  ${delta === null ? '     —' : String(delta).padStart(6)}`,
    );
    if (delta !== null && Math.abs(delta) > 0.06) {
      note(
        'CONTRAST OFF SPEC',
        `${theme} ${name}: analytic ${round(analytic)} vs §3.4 ${expected}`,
      );
    }
  }

  // The bar has no surface of its own, so its text sits straight on the mesh
  // — and the mesh is not one colour across a 204 px box. A single sample
  // would be a number nobody can reproduce, so scan the band and report the
  // range. Ink pixels (the glyphs, the clock) are dropped by keeping only the
  // lighter/darker half that the surface occupies: the extreme outlier is the
  // ink, the rest is the background.
  const bandPoints = [];
  for (let x = bar.x; x <= bar.right; x += 4) bandPoints.push({ x, y: bar.y + 2, name: 'band' });
  const bandPixels = await samplePixels(page, bandPoints);
  const bandRatios = bandPixels
    .map((p) => ({ x: p.x, rgb: p.rgb, ratio: contrast(parseColor(c.statusBarText), p.rgb) }))
    .sort((a, b) => a.ratio - b.ratio);
  // Trim the 10% most extreme samples at each end: those are the glyph pixels.
  const trim = Math.ceil(bandRatios.length * 0.1);
  const band = bandRatios.slice(trim, bandRatios.length - trim);
  console.log(
    `  status bar band scan (${bandPoints.length} pixels across its own box, glyph pixels trimmed): ` +
      `contrast ${round(band[0].ratio)} … ${round(band[band.length - 1].ratio)} ` +
      `(mesh ${hex(band[0].rgb)} … ${hex(band[band.length - 1].rgb)}) vs §3.4's ${DESIGN_CONTRAST['status-bar text in its corner'][theme]}`,
  );

  console.log(
    "  (analytic = the token composited over §3.4's mesh average; sampled = real pixels at one\n" +
      '   place on screen, where the mesh is not its average — the two agree only where the mesh\n' +
      '   happens to be average. The status-bar row has no analytic value: the bar has no surface\n' +
      '   of its own, so §3.4 records the band scan above rather than a composite.)',
  );

  // The Settings screen's panel uses the same glass recipe; measured rather
  // than assumed, because "it inherits dark mode with no extra work"
  // (02-design.md §9.1) is a claim about a surface nobody had rendered yet.
  await page.goto(baseUrl + '/einstellungen', { waitUntil: 'load' });
  await page.waitForSelector('.settings__panel');
  await page.waitForTimeout(200);
  const sm = await page.evaluate(collect);
  const panelColor = parseColor(sm.colors.glassPanelSettings);
  const panelPatch = await samplePixels(
    page,
    patch(sm.settings.panels[0], 16).map((p) => ({ ...p, name: 'panel' })),
  );
  const panelPixel = median(panelPatch);
  console.log(
    `  settings panel: css ${sm.colors.glassPanelSettings} (same recipe as the card: ` +
      `${sm.colors.glassPanelSettings === c.glass ? 'yes' : 'NO'}), sampled ${hex(panelPixel)}, ` +
      `primary text on it: analytic ${round(contrast(primary, over(panelColor, mesh)))} sampled ` +
      `${round(contrast(primary, panelPixel))}, hint text sampled ${round(contrast(secondary, panelPixel))}`,
  );
  await context.close();
}

await browser.close();

console.log('\n############ SUMMARY ############');
console.log(
  `${combinations} route x theme x language x viewport combinations measured, ` +
    `${overflowing} overflowing, ${truncatedCount} truncated strings.`,
);
console.log(`${findings.length} finding(s):`);
for (const f of findings) console.log(`  - ${f.kind}: ${f.detail}`);
console.log('Reported, not asserted — this script always exits 0 (see the header comment).');
