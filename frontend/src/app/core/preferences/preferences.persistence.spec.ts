import { ApplicationInitStatus, provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { App } from '../../app.component';
import { appConfig } from '../../app.config';
import { ShellComponent } from '../../layout/shell/shell.component';
import { DeviceTokenStore } from '../api/device-token.store';
import { GuestDataService } from '../guest/guest-data.service';
import type { PropertyConfig, PropertySettingsConfig } from '../content/property.model';
import {
  PREFERENCE_GATEWAY,
  PREFERENCE_LOAD_TIMEOUT_MS,
  PREFERENCE_WRITE_ATTEMPTS,
  PREFERENCE_WRITE_BACKOFF_MS,
  type PreferenceGateway,
  type StoredPreferences,
} from './preference-gateway';
import { PreferencesService } from './preferences.service';

/**
 * S39 (part 2) — persistence and async behaviour (REQ-121, REQ-122, REQ-126,
 * REQ-127).
 *
 * **`PreferencesService` is not edited to make any of this pass, and that is
 * the point** (REQ-122): every rule in `02-design.md` §6.4/§6.5 is reachable
 * by swapping the transport alone, because all the policy lives above it.
 * If a future change to this file needs a hook inside the service, the
 * architecture has regressed, not the test.
 *
 * The state layer (`override ?? default`, validation, `reset()`) is S28's
 * `preferences.service.spec.ts` and is deliberately not repeated here; that
 * spec keeps its own inert gateway so it stays free of persistence.
 */

interface Deferred<T> {
  promise: Promise<T>;
  resolve: (value: T) => void;
}

function deferred<T>(): Deferred<T> {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}

/**
 * The whole persistence surface in one double: it answers `load()` with a
 * scripted value — immediately, on command, never, or by rejecting — records
 * every `save()`/`clear()`, can be told to reject the next N writes, and can
 * hold a write open so the coalescing rule is observable.
 */
class FakePreferenceGateway implements PreferenceGateway {
  /** What a successful `load()` answers. */
  stored: StoredPreferences | null = null;
  /** How `load()` behaves. `deferred` waits for `answerLoad()`. */
  loadBehaviour: 'immediate' | 'delayed' | 'deferred' | 'never' | 'reject' = 'immediate';
  /** How long a `delayed` load takes to answer. */
  loadDelayMs = 25;
  /** The next N writes reject; everything after that succeeds. */
  failingWrites = 0;
  /** While true, each write stays pending until `settleWrite()`. */
  holdWrites = false;

  loadCalls = 0;
  clears = 0;
  readonly saves: StoredPreferences[] = [];

  private readonly pendingLoad = deferred<StoredPreferences | null>();
  private readonly heldWrites: Deferred<void>[] = [];

  load(): Promise<StoredPreferences | null> {
    this.loadCalls += 1;
    switch (this.loadBehaviour) {
      case 'reject':
        return Promise.reject(new Error('gateway unavailable'));
      case 'never':
        return new Promise<StoredPreferences | null>(() => {});
      case 'deferred':
        return this.pendingLoad.promise;
      case 'delayed':
        return new Promise<StoredPreferences | null>((resolve) =>
          setTimeout(() => resolve(this.stored), this.loadDelayMs),
        );
      default:
        return Promise.resolve(this.stored);
    }
  }

  /** Answers a `deferred` load, whenever the test decides it arrives. */
  answerLoad(value: StoredPreferences | null = this.stored): void {
    this.pendingLoad.resolve(value);
  }

  save(value: StoredPreferences): Promise<void> {
    this.saves.push({ ...value });
    return this.write();
  }

  clear(): Promise<void> {
    this.clears += 1;
    return this.write();
  }

  /** The oldest held write succeeds. */
  settleWrite(): void {
    this.heldWrites.shift()?.resolve();
  }

  get lastSave(): StoredPreferences | undefined {
    return this.saves[this.saves.length - 1];
  }

  private write(): Promise<void> {
    if (this.failingWrites > 0) {
      this.failingWrites -= 1;
      return Promise.reject(new Error('write failed'));
    }
    if (!this.holdWrites) {
      return Promise.resolve();
    }
    const held = deferred<void>();
    this.heldWrites.push(held);
    return held.promise;
  }
}

/** Matches the shipped property: no `system`, so `fr`/`system` are untrusted input. */
const TEST_CONFIG: PropertySettingsConfig = {
  languages: [
    { code: 'de', shortLabel: 'DE', label: 'Deutsch', locale: 'de-DE' },
    { code: 'en', shortLabel: 'EN', label: 'English', locale: 'en-GB' },
  ],
  defaultLanguage: 'de',
  appearances: [
    { mode: 'light', icon: 'phosphorSun' },
    { mode: 'dark', icon: 'phosphorMoon' },
  ],
  defaultAppearance: 'light',
};

/** Drains the microtask queue, whichever timer implementation is installed. */
async function flush(): Promise<void> {
  if (vi.isFakeTimers()) {
    await vi.advanceTimersByTimeAsync(0);
    return;
  }
  await new Promise<void>((resolve) => setTimeout(resolve, 0));
}

/** Every backoff plus a margin, so a whole retry cycle has certainly run out. */
const FULL_RETRY_WINDOW = PREFERENCE_WRITE_BACKOFF_MS.reduce((sum, ms) => sum + ms, 0) + 100;

describe('PreferencesService — hydrate', () => {
  let gateway: FakePreferenceGateway;

  function setup(config: PropertySettingsConfig = TEST_CONFIG): PreferencesService {
    TestBed.configureTestingModule({
      providers: [{ provide: PREFERENCE_GATEWAY, useValue: gateway }],
    });
    const service = TestBed.inject(PreferencesService);
    service.applyPropertyDefaults(config);
    return service;
  }

  beforeEach(() => {
    gateway = new FakePreferenceGateway();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.useRealTimers();
  });

  it('applies a stored record', async () => {
    gateway.stored = { language: 'en', appearance: 'dark' };
    const service = setup();

    await service.hydrate();

    expect(service.language()).toBe('en');
    expect(service.appearance()).toBe('dark');
  });

  it('leaves the property defaults alone when nothing is stored', async () => {
    gateway.stored = null;
    const service = setup();

    await service.hydrate();

    expect(gateway.loadCalls).toBe(1);
    expect(service.language()).toBe('de');
    expect(service.appearance()).toBe('light');
  });

  it('discards a stored value the property does not configure, per field', async () => {
    // `fr` was removed from this property's languages, and `system` is not
    // offered at all — an old record must not resurrect either (REQ-126 c).
    gateway.stored = { language: 'fr', appearance: 'dark' };
    const service = setup();

    await service.hydrate();

    expect(service.language()).toBe('de');
    expect(service.appearance()).toBe('dark');
  });

  it('discards an unconfigured appearance too', async () => {
    gateway.stored = { language: 'en', appearance: 'system' };
    const service = setup();

    await service.hydrate();

    expect(service.language()).toBe('en');
    expect(service.appearance()).toBe('light');
  });

  it('resolves — never rejects — when load() rejects, and keeps the defaults', async () => {
    gateway.loadBehaviour = 'reject';
    const service = setup();

    await expect(service.hydrate()).resolves.toBeUndefined();

    expect(service.language()).toBe('de');
    expect(service.appearance()).toBe('light');
  });

  it('resolves after the bounded timeout when load() never answers', async () => {
    vi.useFakeTimers();
    gateway.loadBehaviour = 'never';
    const service = setup();

    let settled = false;
    void service.hydrate().then(() => {
      settled = true;
    });

    await vi.advanceTimersByTimeAsync(PREFERENCE_LOAD_TIMEOUT_MS - 1);
    expect(settled).toBe(false);

    await vi.advanceTimersByTimeAsync(1);
    expect(settled).toBe(true);
    expect(service.language()).toBe('de');
    expect(service.appearance()).toBe('light');
  });

  it("a late answer never overwrites the guest's choice — and does fill the setting they left alone", async () => {
    gateway.loadBehaviour = 'deferred';
    const service = setup();
    const hydrated = service.hydrate();

    // The guest taps while the read is still in flight.
    service.setLanguage('en');

    gateway.answerLoad({ language: 'de', appearance: 'dark' });
    await hydrated;

    expect(service.language()).toBe('en'); // the tap wins
    expect(service.appearance()).toBe('dark'); // no choice was in effect here
  });

  it('applies an answer that arrives after the timeout, under the same rule', async () => {
    vi.useFakeTimers();
    gateway.loadBehaviour = 'deferred';
    const service = setup();

    void service.hydrate();
    await vi.advanceTimersByTimeAsync(PREFERENCE_LOAD_TIMEOUT_MS);
    expect(service.appearance()).toBe('light');

    gateway.answerLoad({ appearance: 'dark' });
    await flush();

    expect(service.appearance()).toBe('dark');
  });

  it('reads once, and writes nothing while hydrating', async () => {
    gateway.stored = { language: 'en' };
    const service = setup();

    await service.hydrate();

    expect(gateway.loadCalls).toBe(1);
    expect(gateway.saves).toEqual([]);
    expect(gateway.clears).toBe(0);
  });
});

describe('PreferencesService — the write path', () => {
  let gateway: FakePreferenceGateway;

  function setup(): PreferencesService {
    TestBed.configureTestingModule({
      providers: [{ provide: PREFERENCE_GATEWAY, useValue: gateway }],
    });
    const service = TestBed.inject(PreferencesService);
    service.applyPropertyDefaults(TEST_CONFIG);
    return service;
  }

  beforeEach(() => {
    gateway = new FakePreferenceGateway();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.useRealTimers();
  });

  it('persists once per change, carrying the whole record rather than a patch', async () => {
    const service = setup();

    service.setLanguage('en');
    await flush();

    expect(gateway.saves).toEqual([{ language: 'en' }]);

    service.setAppearance('dark');
    await flush();

    expect(gateway.saves.length).toBe(2);
    // Both settings, not just the one that moved (02-design.md §6.3).
    expect(gateway.lastSave).toEqual({ language: 'en', appearance: 'dark' });
  });

  it('does not persist a value the property does not configure', async () => {
    const service = setup();

    service.setLanguage('fr');
    await flush();

    expect(gateway.saves).toEqual([]);
  });

  it('coalesces three fast changes into the latest state, never a race of three', async () => {
    gateway.holdWrites = true;
    const service = setup();

    service.setLanguage('en');
    await flush();
    expect(gateway.saves.length).toBe(1); // in flight, holding

    service.setAppearance('dark');
    service.setLanguage('de');
    await flush();
    expect(gateway.saves.length).toBe(1); // still one — the rest coalesced

    gateway.settleWrite();
    await flush();

    expect(gateway.saves.length).toBe(2);
    // The follow-up carries a fresh snapshot of *both* settings, so arrival
    // order can never decide what the store ends up holding.
    expect(gateway.lastSave).toEqual({ language: 'de', appearance: 'dark' });

    gateway.settleWrite();
    await flush();
    expect(gateway.saves.length).toBe(2);
  });

  it('retries a failing write exactly PREFERENCE_WRITE_ATTEMPTS times and then stops', async () => {
    vi.useFakeTimers();
    gateway.failingWrites = Number.MAX_SAFE_INTEGER;
    const service = setup();

    service.setLanguage('en');
    await flush();
    expect(gateway.saves.length).toBe(1);

    await vi.advanceTimersByTimeAsync(PREFERENCE_WRITE_BACKOFF_MS[0]!);
    expect(gateway.saves.length).toBe(2);

    await vi.advanceTimersByTimeAsync(PREFERENCE_WRITE_BACKOFF_MS[1]!);
    expect(gateway.saves.length).toBe(PREFERENCE_WRITE_ATTEMPTS);

    // And then it surrenders: no fourth attempt, ever.
    await vi.advanceTimersByTimeAsync(60_000);
    expect(gateway.saves.length).toBe(PREFERENCE_WRITE_ATTEMPTS);

    // The screen keeps the guest's choice regardless of the store.
    expect(service.language()).toBe('en');
  });

  it('starts a fresh cycle on the next change, with no retry timer in between', async () => {
    vi.useFakeTimers();
    gateway.failingWrites = PREFERENCE_WRITE_ATTEMPTS;
    const service = setup();

    service.setLanguage('en');
    await vi.advanceTimersByTimeAsync(FULL_RETRY_WINDOW);
    expect(gateway.saves.length).toBe(PREFERENCE_WRITE_ATTEMPTS);

    service.setAppearance('dark');
    await flush();

    expect(gateway.saves.length).toBe(PREFERENCE_WRITE_ATTEMPTS + 1);
    expect(gateway.lastSave).toEqual({ language: 'en', appearance: 'dark' });
    expect(service.appearance()).toBe('dark');
  });

  it('reset() clears the stored record instead of saving an empty one', async () => {
    const service = setup();
    service.setLanguage('en');
    await flush();
    const savesBefore = gateway.saves.length;

    service.reset();
    await flush();

    expect(gateway.clears).toBe(1);
    expect(gateway.saves.length).toBe(savesBefore);
    expect(service.language()).toBe('de');
    expect(service.appearance()).toBe('light');
  });

  it('makes no console call on any path — not a load failure, not a write failure', async () => {
    vi.useFakeTimers();
    const spies = (['log', 'info', 'warn', 'error', 'debug', 'trace'] as const).map((method) =>
      vi.spyOn(console, method).mockImplementation(() => undefined),
    );

    try {
      gateway.loadBehaviour = 'reject';
      gateway.failingWrites = Number.MAX_SAFE_INTEGER;
      const service = setup();

      await service.hydrate();
      service.setLanguage('en');
      service.setAppearance('dark');
      await vi.advanceTimersByTimeAsync(FULL_RETRY_WINDOW);
      service.reset();
      await vi.advanceTimersByTimeAsync(FULL_RETRY_WINDOW);

      for (const spy of spies) {
        expect(spy).not.toHaveBeenCalled();
      }
    } finally {
      for (const spy of spies) {
        spy.mockRestore();
      }
    }
  });

  it('never leaks a timer past teardown', async () => {
    vi.useFakeTimers();
    gateway.loadBehaviour = 'never';
    gateway.failingWrites = Number.MAX_SAFE_INTEGER;
    const service = setup();

    void service.hydrate();
    service.setLanguage('en');
    await flush();
    expect(vi.getTimerCount()).toBeGreaterThan(0);

    TestBed.resetTestingModule(); // this is what runs DestroyRef.onDestroy

    expect(vi.getTimerCount()).toBe(0);
  });
});

/**
 * The bootstrap contract (REQ-126 a–d → REQ-518, wire edition), through the
 * **real** `app.config.ts` providers — the app initializer
 * (`GuestBootstrapService.run()`), its ordering and `App`'s effects are
 * exactly what is under test, so none of them is stubbed. Two seams are
 * doubled: the preference transport (the whole claim of REQ-122) and
 * `GuestDataService` (a fake whose `bootstrap()` delivers a config
 * asynchronously, like the wire — the wire itself is `guest-data.service.
 * spec.ts`'s job). The no-flash guarantee is therefore proven end to end:
 * token check → content fetch → defaults → hydration → first paint.
 */
const BOOT_CONFIG: PropertyConfig = {
  propertyName: 'Testhaus',
  logoAssetId: null,
  branding: null,
  menuItems: [
    { id: 'home', label: { de: 'Home', en: 'Home' }, icon: 'phosphorHouse', route: '', module: null },
  ],
  settings: {
    id: 'einstellungen',
    label: { de: 'Einstellungen', en: 'Settings' },
    icon: 'phosphorGearSix',
    route: '/einstellungen',
    module: null,
  },
  settingsConfig: {
    languages: [
      { code: 'de', shortLabel: 'DE', label: 'Deutsch', locale: 'de-DE' },
      { code: 'en', shortLabel: 'EN', label: 'English', locale: 'en-GB' },
    ],
    defaultLanguage: 'de',
    appearances: [
      { mode: 'light', icon: 'phosphorSun' },
      { mode: 'dark', icon: 'phosphorMoon' },
    ],
    defaultAppearance: 'light',
  },
  standbyHeadline: { de: 'Testhaus', en: 'Testhaus' },
  standbyMessage: { de: 'Standby', en: 'Standby' },
  home: null,
};

/** A successful wire bootstrap, delivered asynchronously like the real one. */
class FakeGuestDataService {
  readonly config = signal<PropertyConfig | null>(null);
  readonly payload = signal<null>(null);
  readonly occupancy = signal<'OCCUPIED' | 'VACANT' | null>(null);
  readonly connection = signal<'ONLINE' | 'STALE' | 'DISCONNECTED'>('DISCONNECTED');
  readonly staleSince = signal<number | null>(null);

  async bootstrap(): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve, 10));
    this.config.set(BOOT_CONFIG);
    this.occupancy.set('OCCUPIED');
    this.connection.set('ONLINE');
  }

  preferenceOverride(): null {
    return null;
  }

  handleUnauthorized(): void {}
}

describe('Bootstrap with a stored preference', () => {
  const root = document.documentElement;
  let gateway: FakePreferenceGateway;

  /** Every value `<html data-theme>` / `<html lang>` ever takes, in order. */
  let themes: string[];
  let langs: string[];
  let observer: MutationObserver;

  beforeEach(() => {
    gateway = new FakePreferenceGateway();
    themes = [];
    langs = [];
    observer = new MutationObserver((records) => {
      for (const record of records) {
        if (record.attributeName === 'data-theme') {
          themes.push(root.getAttribute('data-theme') ?? '');
        }
        if (record.attributeName === 'lang') {
          langs.push(root.getAttribute('lang') ?? '');
        }
      }
    });
    observer.observe(root, { attributes: true, attributeFilter: ['data-theme', 'lang'] });
  });

  afterEach(() => {
    observer.disconnect();
    TestBed.resetTestingModule();
    root.removeAttribute('data-theme');
    root.removeAttribute('lang');
  });

  async function boot(): Promise<ComponentFixture<App>> {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [
        ...appConfig.providers,
        provideZonelessChangeDetection(),
        { provide: PREFERENCE_GATEWAY, useValue: gateway },
        { provide: DeviceTokenStore, useValue: { token: () => 'a-device-token' } },
        { provide: GuestDataService, useClass: FakeGuestDataService },
      ],
    });

    // What Angular does at bootstrap: hold everything until the initializer's
    // promise settles. `TestBed` instantiates initializers but does not await.
    await TestBed.inject(ApplicationInitStatus).donePromise;

    const fixture = TestBed.createComponent(App);
    /* Synchronously, on purpose: the claim is that <html> is already final at
       the component's *first* render, not that it settles a few frames later.
       Waiting for stability first would let a late hydration sneak in ahead of
       the first effect run and hide exactly the flash this asserts against. */
    fixture.detectChanges();
    await fixture.whenStable();
    // MutationObserver delivers in a microtask; take whatever is still queued.
    observer.takeRecords().forEach((record) => {
      if (record.attributeName === 'data-theme') themes.push(root.getAttribute('data-theme') ?? '');
      if (record.attributeName === 'lang') langs.push(root.getAttribute('lang') ?? '');
    });
    return fixture;
  }

  it('writes the stored values as the FIRST thing <html> ever sees — no flash, no correction', async () => {
    /* The store answers *late* on purpose. With an instant answer this case
       passes even if the initializer forgets to `await` — the microtask
       ordering happens to hide the bug. A real delay is what makes this an
       assertion about ordering rather than about speed (02-design.md §6.4). */
    gateway.loadBehaviour = 'delayed';
    gateway.stored = { language: 'en', appearance: 'dark' };

    await boot();

    expect(themes).toEqual(['dark']);
    expect(langs).toEqual(['en']);
    expect(root.dataset['theme']).toBe('dark');
    expect(root.lang).toBe('en');
  });

  it('has the property defaults in effect before any stored value is applied', async () => {
    /* The initializer's job (02-design.md §6.4). If `applyPropertyDefaults()`
       did not run there, no option list would exist to validate `en` against
       and `applyStored()` would discard it — so the assertion below observes
       the ordering rather than arguing it.

       What it does *not* pin down is the order of the two statements inside
       the initializer: while `load()` is asynchronous, a synchronous
       `applyPropertyDefaults()` still lands first either way. Moving it out
       of the initializer is the regression that exists, and this catches it. */
    gateway.loadBehaviour = 'delayed';
    gateway.stored = { language: 'en' };

    await boot();

    expect(TestBed.inject(PreferencesService).language()).toBe('en');
    expect(langs).toEqual(['en']);
  });

  it('writes the property defaults, once, when nothing is stored', async () => {
    gateway.stored = null;

    await boot();

    expect(themes).toEqual(['light']);
    expect(langs).toEqual(['de']);
  });

  it('boots normally when load() rejects', async () => {
    gateway.loadBehaviour = 'reject';

    const fixture = await boot();

    expect(fixture.componentInstance).toBeTruthy();
    expect(themes).toEqual(['light']);
    expect(langs).toEqual(['de']);
  });

  it('boots after the bounded timeout when load() never answers', async () => {
    gateway.loadBehaviour = 'never';

    const started = Date.now();
    const fixture = await boot();

    expect(fixture.componentInstance).toBeTruthy();
    expect(Date.now() - started).toBeGreaterThanOrEqual(PREFERENCE_LOAD_TIMEOUT_MS);
    expect(themes).toEqual(['light']);
    expect(langs).toEqual(['de']);
  }, 15_000);

  it("a failing write leaves the guest's choice on screen and adds no error node", async () => {
    gateway.failingWrites = Number.MAX_SAFE_INTEGER;
    TestBed.configureTestingModule({
      providers: [
        ...appConfig.providers,
        provideZonelessChangeDetection(),
        { provide: PREFERENCE_GATEWAY, useValue: gateway },
        { provide: DeviceTokenStore, useValue: { token: () => 'a-device-token' } },
        { provide: GuestDataService, useClass: FakeGuestDataService },
      ],
    });
    await TestBed.inject(ApplicationInitStatus).donePromise;

    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/einstellungen', ShellComponent);
    const el: HTMLElement = harness.routeNativeElement!;

    const appearanceOptions: HTMLButtonElement[] = Array.from(
      el.querySelectorAll('.setting-choice')[1].querySelectorAll('.setting-choice__option'),
    );
    appearanceOptions[1].click();
    await flush();
    harness.detectChanges();

    // The write failed; the screen does not care, and neither does the guest.
    expect(gateway.saves.length).toBeGreaterThan(0);
    expect(TestBed.inject(PreferencesService).appearance()).toBe('dark');
    const stillSelected = Array.from(
      el
        .querySelectorAll('.setting-choice')[1]
        .querySelectorAll('.setting-choice__option--selected'),
    ).map((option) => option.textContent!.trim());
    expect(stillSelected.length).toBe(1);
    expect(stillSelected[0]).toContain('Dunkel');
    expect(document.querySelector('[role="alert"]')).toBeNull();
    expect(document.querySelector('[role="status"]')).toBeNull();
    expect(document.querySelector('.error')).toBeNull();
  });
});
