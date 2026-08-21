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
  'pairing.title': 'Pair this device',
  'pairing.instruction': 'Confirm this code in the management area to assign this device to a room.',
  'pairing.waiting': 'Waiting for confirmation …',
  'pairing.renewing': 'Requesting a new code …',
  'pairing.cooldown': 'Too many attempts — pausing briefly …',
  'pairing.offline': 'No connection to the server. Reconnecting automatically.',
  'connection.title': 'No connection',
  'connection.body': 'This display reconnects automatically as soon as the server is reachable.',
  'stale.asOf': 'As of {time}',
};
