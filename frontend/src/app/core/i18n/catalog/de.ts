/**
 * German catalogue — **the source of truth for the key set** (REQ-111).
 * `TranslationKey` is derived from it (`translation.model.ts`), so every other
 * catalogue is typed `Record<TranslationKey, string>` and a missing key fails
 * `ng build` rather than degrading silently on a kiosk nobody is watching.
 *
 * App chrome only. Property-authored text (headline, card copy, nav labels)
 * lives in the property config and is resolved by `PropertyContentService` —
 * these two never mix (REQ-107, REQ-108).
 *
 * Plain TypeScript, bundled: nothing here is fetched, imported dynamically or
 * loaded over HTTP, so a language switch renders before anything could leave
 * the device (REQ-106; constitution principle 4).
 */
export const de = {
  'nav.aria': 'Hauptnavigation',
  'section.comingSoon': 'Bald verfügbar.',
  'statusBar.language.aria': 'Sprache wählen',
  'statusBar.appearance.aria': 'Darstellung wählen',
  'statusBar.time.aria': 'Aktuelle Uhrzeit',
  'appearance.light': 'Hell',
  'appearance.dark': 'Dunkel',
  'appearance.system': 'Automatisch',
  'settings.title': 'Einstellungen',
  'settings.language.title': 'Sprache',
  'settings.language.hint': 'Sprache für Menüs, Texte und Hinweise auf diesem Bildschirm.',
  'settings.appearance.title': 'Darstellung',
  'settings.appearance.hint': 'Helle oder dunkle Darstellung, abends oft angenehmer.',
  'settings.selected.aria': 'Ausgewählt',
  /* guest-frontend-integration (REQ-523): the three new screens + the
     staleness pill. Copy verbatim from the in-repo specs
     (docs/design/screens-inhouse/): pairing addresses the MANAGER
     (imperative-neutral, no ihr/euch), connection copy is guest-facing. */
  'pairing.title': 'Gerät koppeln',
  'pairing.instruction':
    'Diesen Code im Verwaltungsbereich bestätigen, um das Gerät einem Zimmer zuzuweisen.',
  'pairing.waiting': 'Warten auf Bestätigung …',
  'pairing.renewing': 'Neuer Code wird erstellt …',
  'pairing.offline': 'Keine Verbindung zum Server. Verbindet automatisch neu.',
  'connection.title': 'Keine Verbindung',
  'connection.body':
    'Die Anzeige verbindet sich automatisch neu, sobald der Server erreichbar ist.',
  'stale.asOf': 'Stand {time}',
} as const;
