import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { provideIcons } from '@ng-icons/core';
import { phosphorCheck, phosphorMoon, phosphorSun } from '@ng-icons/phosphor-icons/regular';
import { beforeEach, describe, expect, it } from 'vitest';

import { PREFERENCE_GATEWAY } from '../../../core/preferences/preference-gateway';
import { SettingChoiceComponent, type SettingChoiceOption } from './setting-choice.component';

/**
 * S34 (part 1) — `SettingChoiceComponent` (REQ-124, REQ-117).
 *
 * The Settings screen's half of REQ-117: **every option visible at once**, no
 * dropdown. Selection is signalled three ways; two of them — the background
 * and the weight — are CSS on the `--selected` class, which jsdom neither
 * loads a token layer for nor computes. So the class and the check glyph are
 * what is asserted (`frontend/CLAUDE.md` -> Tests); the paint is a
 * review-time measurement.
 *
 * No persistence is asserted, so the gateway is the inert stand-in.
 */
const OPTIONS: SettingChoiceOption[] = [
  { value: 'light', label: 'Hell', icon: 'phosphorSun' },
  { value: 'dark', label: 'Dunkel', icon: 'phosphorMoon' },
];

describe('SettingChoiceComponent', () => {
  let fixture: ComponentFixture<SettingChoiceComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SettingChoiceComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideIcons({ phosphorCheck, phosphorMoon, phosphorSun }),
        {
          provide: PREFERENCE_GATEWAY,
          useValue: {
            load: () => Promise.resolve(null),
            save: () => Promise.resolve(),
            clear: () => Promise.resolve(),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SettingChoiceComponent);
    fixture.componentRef.setInput('label', 'Darstellung');
    fixture.componentRef.setInput('hint', 'Helle oder dunkle Darstellung.');
    fixture.componentRef.setInput('options', OPTIONS);
    fixture.componentRef.setInput('selectedValue', 'light');
    fixture.detectChanges();
  });

  function options(): HTMLButtonElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('.setting-choice__option'));
  }

  it('renders the label and the hint it is given', () => {
    expect(fixture.nativeElement.querySelector('.setting-choice__label').textContent.trim()).toBe(
      'Darstellung',
    );
    expect(fixture.nativeElement.querySelector('.setting-choice__hint').textContent.trim()).toBe(
      'Helle oder dunkle Darstellung.',
    );
  });

  it('groups the options under the label for assistive technology', () => {
    const group = fixture.nativeElement.querySelector('.setting-choice');
    expect(group.getAttribute('role')).toBe('group');
    expect(group.getAttribute('aria-label')).toBe('Darstellung');
  });

  it('renders every option at once, in order, as real buttons', () => {
    expect(options().length).toBe(OPTIONS.length);
    expect(options().map((option) => option.textContent!.trim())).toEqual([
      expect.stringContaining('Hell'),
      expect.stringContaining('Dunkel'),
    ]);
    expect(options().every((option) => option.getAttribute('type') === 'button')).toBe(true);
  });

  it("renders each option's own configured glyph", () => {
    const glyphs = options().map(
      (option) => option.querySelector('.setting-choice__option-icon svg')?.innerHTML,
    );
    expect(glyphs[0]).toBeTruthy();
    expect(glyphs[1]).toBeTruthy();
    expect(glyphs[0]).not.toBe(glyphs[1]);
  });

  it('renders no glyph for an option that configures none', () => {
    fixture.componentRef.setInput('options', [{ value: 'de', label: 'Deutsch' }]);
    fixture.detectChanges();

    expect(options()[0].querySelector('.setting-choice__option-icon')).toBeNull();
  });

  it('marks exactly the selected option, with both the class and the check glyph', () => {
    const selected = options().filter((option) =>
      option.classList.contains('setting-choice__option--selected'),
    );
    expect(selected.length).toBe(1);
    expect(selected[0].textContent).toContain('Hell');
    expect(selected[0].querySelector('.setting-choice__option-check')).toBeTruthy();
    expect(options()[1].querySelector('.setting-choice__option-check')).toBeNull();
  });

  it('gives the check glyph a translated accessible name', () => {
    const check = fixture.nativeElement.querySelector('.setting-choice__option-check');
    expect(check.getAttribute('aria-label')).toBe('Ausgewählt');
    expect(check.getAttribute('role')).toBe('img');
  });

  it('moves the mark when selectedValue changes', () => {
    fixture.componentRef.setInput('selectedValue', 'dark');
    fixture.detectChanges();

    const selected = options().filter((option) =>
      option.classList.contains('setting-choice__option--selected'),
    );
    expect(selected.length).toBe(1);
    expect(selected[0].textContent).toContain('Dunkel');
  });

  it('marks nothing when the current value is not among the options', () => {
    fixture.componentRef.setInput('selectedValue', 'system');
    fixture.detectChanges();

    expect(
      options().filter((option) => option.classList.contains('setting-choice__option--selected'))
        .length,
    ).toBe(0);
  });

  it('emits the option value on click', () => {
    const emitted: string[] = [];
    fixture.componentInstance.optionSelected.subscribe((value) => emitted.push(value));

    options()[1].click();
    options()[0].click();

    expect(emitted).toEqual(['dark', 'light']);
  });
});
