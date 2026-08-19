import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { provideIcons } from '@ng-icons/core';
import {
  phosphorCaretDown,
  phosphorCheck,
  phosphorMoon,
  phosphorSun,
} from '@ng-icons/phosphor-icons/regular';
import { beforeEach, describe, expect, it } from 'vitest';

import { PREFERENCE_GATEWAY } from '../../../core/preferences/preference-gateway';
import { SessionPreferenceGateway } from '../../../core/preferences/session-preference-gateway';
import { StatusBarMenuComponent, type StatusBarMenuOption } from './status-bar-menu.component';

/**
 * S32 — `StatusBarMenuComponent` (REQ-124, REQ-115).
 *
 * The CDK supplies the keyboard model and the focus contract; this spec
 * asserts that this component is wired to it correctly, not that the CDK
 * works. The panel is rendered into the CDK's **overlay container**, i.e.
 * outside the fixture's DOM subtree, so every panel query goes through
 * `document`.
 *
 * The selected row's *background* is a class (`--selected`, carrying
 * `--gradient-menu-active`), and its second, non-colour signal is the check
 * glyph. jsdom loads no token layer and computes no layout, so the class and
 * the glyph are what is asserted here — the painted result is a review-time
 * measurement (`frontend/CLAUDE.md` -> Tests).
 *
 * No persistence is asserted, so the gateway is the same inert stand-in the
 * other component specs use, not S39's `FakePreferenceGateway`.
 */
const OPTIONS: StatusBarMenuOption[] = [
  { value: 'light', label: 'Hell', icon: 'phosphorSun' },
  { value: 'dark', label: 'Dunkel', icon: 'phosphorMoon' },
];

/** CDK keyboard handling reads `keyCode`, which `KeyboardEvent`'s init does not carry. */
function dispatchKey(target: Element, key: string, keyCode: number): void {
  const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true });
  Object.defineProperty(event, 'keyCode', { get: () => keyCode });
  target.dispatchEvent(event);
}

const ENTER = 13;
const SPACE = 32;
const ESCAPE = 27;

describe('StatusBarMenuComponent', () => {
  let fixture: ComponentFixture<StatusBarMenuComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StatusBarMenuComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideIcons({ phosphorCaretDown, phosphorCheck, phosphorMoon, phosphorSun }),
        { provide: PREFERENCE_GATEWAY, useClass: SessionPreferenceGateway },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(StatusBarMenuComponent);
    fixture.componentRef.setInput('options', OPTIONS);
    fixture.componentRef.setInput('selectedValue', 'dark');
    fixture.componentRef.setInput('triggerLabel', 'Darstellung wählen');
    fixture.detectChanges();
  });

  function trigger(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('.status-bar-menu__trigger');
  }

  function panel(): HTMLElement | null {
    return document.querySelector('.status-bar-menu__panel');
  }

  function rows(): HTMLButtonElement[] {
    return Array.from(document.querySelectorAll('.status-bar-menu__option'));
  }

  async function open(): Promise<void> {
    trigger().click();
    await fixture.whenStable();
  }

  it('renders a labelled trigger and no panel until it is opened', () => {
    expect(trigger().getAttribute('aria-label')).toBe('Darstellung wählen');
    expect(panel()).toBeNull();
  });

  it('opens on click', async () => {
    await open();

    expect(panel()).not.toBeNull();
    expect(rows().length).toBe(OPTIONS.length);
  });

  it('opens on Enter', async () => {
    dispatchKey(trigger(), 'Enter', ENTER);
    await fixture.whenStable();

    expect(panel()).not.toBeNull();
  });

  it('opens on Space', async () => {
    dispatchKey(trigger(), ' ', SPACE);
    await fixture.whenStable();

    expect(panel()).not.toBeNull();
  });

  it('renders every option with its label, in configuration order', async () => {
    await open();

    const labels = rows().map((row) =>
      (row.querySelector('.status-bar-menu__option-label')!.textContent as string).trim(),
    );
    expect(labels).toEqual(['Hell', 'Dunkel']);
  });

  it("renders each option's own configured glyph", async () => {
    await open();

    /* `name` is a property binding, so it is not readable as a DOM attribute,
       and @ng-icons injects the <svg> with Renderer2 rather than from the
       template. What is assertable is that a glyph was drawn per row and that
       the two are *different* — i.e. the configured name reached the icon
       instead of one fixed glyph being rendered twice. */
    const glyphs = rows().map(
      (row) => row.querySelector('.status-bar-menu__option-icon svg')?.innerHTML,
    );
    expect(glyphs[0]).toBeTruthy();
    expect(glyphs[1]).toBeTruthy();
    expect(glyphs[0]).not.toBe(glyphs[1]);
  });

  it('renders no glyph for an option that configures none', async () => {
    fixture.componentRef.setInput('options', [{ value: 'de', label: 'Deutsch' }]);
    fixture.detectChanges();
    await open();

    expect(rows().length).toBe(1);
    expect(rows()[0].querySelector('.status-bar-menu__option-icon')).toBeNull();
    expect(rows()[0].textContent).toContain('Deutsch');
  });

  it('marks the selected row with both the background class and the check glyph', async () => {
    await open();

    const selected = rows().filter((row) =>
      row.classList.contains('status-bar-menu__option--selected'),
    );
    expect(selected.length).toBe(1);
    expect(selected[0].textContent).toContain('Dunkel');
    expect(selected[0].querySelector('.status-bar-menu__option-check')).toBeTruthy();

    // The unselected row carries neither signal.
    expect(rows()[0].classList.contains('status-bar-menu__option--selected')).toBe(false);
    expect(rows()[0].querySelector('.status-bar-menu__option-check')).toBeNull();
  });

  it('gives the check glyph a translated accessible name, so the state is announced', async () => {
    await open();

    const check = document.querySelector('.status-bar-menu__option-check')!;
    expect(check.getAttribute('aria-label')).toBe('Ausgewählt');
    expect(check.getAttribute('role')).toBe('img');
  });

  it('follows selectedValue when it changes', async () => {
    await open();
    fixture.componentRef.setInput('selectedValue', 'light');
    fixture.detectChanges();
    await fixture.whenStable();

    const selected = rows().filter((row) =>
      row.classList.contains('status-bar-menu__option--selected'),
    );
    expect(selected.length).toBe(1);
    expect(selected[0].textContent).toContain('Hell');
  });

  it('emits the chosen value and closes', async () => {
    const emitted: string[] = [];
    fixture.componentInstance.optionSelected.subscribe((value) => emitted.push(value));

    await open();
    rows()[0].click();
    await fixture.whenStable();

    expect(emitted).toEqual(['light']);
    expect(panel()).toBeNull();
  });

  it('closes on Escape and returns focus to the trigger', async () => {
    trigger().focus();
    // Opened from the keyboard, so the CDK moves focus into the panel — which
    // is what makes "focus came back" a real assertion rather than a vacuous
    // one about focus that never left.
    dispatchKey(trigger(), 'Enter', ENTER);
    await fixture.whenStable();
    expect(panel()).not.toBeNull();
    expect(panel()!.contains(document.activeElement)).toBe(true);

    dispatchKey(panel()!, 'Escape', ESCAPE);
    await fixture.whenStable();

    expect(panel()).toBeNull();
    expect(document.activeElement).toBe(trigger());
  });

  it('emits nothing when the menu is merely opened and dismissed', async () => {
    const emitted: string[] = [];
    fixture.componentInstance.optionSelected.subscribe((value) => emitted.push(value));

    await open();
    dispatchKey(panel()!, 'Escape', ESCAPE);
    await fixture.whenStable();

    expect(emitted).toEqual([]);
  });
});
