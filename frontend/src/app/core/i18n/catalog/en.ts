import type { TranslationCatalog } from '../translation.model';

/**
 * English catalogue. Typed against the German key set, so a missing or
 * misspelled key is a build error (REQ-111) — never a runtime guess.
 */
export const en: TranslationCatalog = {
  'nav.aria': 'Main navigation',
  'section.comingSoon': 'Coming soon.',
  'statusBar.language.aria': 'Choose language',
  'statusBar.appearance.aria': 'Choose appearance',
  'statusBar.time.aria': 'Current time',
  'appearance.light': 'Light',
  'appearance.dark': 'Dark',
  'appearance.system': 'Automatic',
  'settings.title': 'Settings',
  'settings.language.title': 'Language',
  'settings.language.hint': 'Language for menus, text and notices on this screen.',
  'settings.appearance.title': 'Appearance',
  'settings.appearance.hint': 'Light or dark appearance, often easier on the eyes at night.',
  'settings.selected.aria': 'Selected',
};
