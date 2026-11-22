// --- the payload -----------------------------------------------------------
const L = (de, en) => ({ de, en });
const menuItem = (key, de, en, icon, route, sortIndex, module = null) =>
  ({ key, label: L(de, en), icon, route, sortIndex, ...(module ? { module } : {}) });

const infoModule = {
  type: 'info',
  rows: [
    { label: L('WLAN', 'Wi-Fi'), value: L('Ferienwohnung-Gast', 'Ferienwohnung-Gast') },
    { label: L('Passwort', 'Password'), value: L('sonne-meer-2026', 'sonne-meer-2026') },
    { label: L('Check-out', 'Check-out'), value: L('Bis 11:00 Uhr', 'By 11:00') },
    { label: L('Müllabfuhr', 'Waste collection'), value: L('Dienstags, gelbe Tonne', 'Tuesdays, yellow bin') },
    { label: L('Notruf', 'Emergency'), value: L('112', '112') },
  ],
};

const content = (over = {}) => ({
  property: { name: 'Haus Seeblick', brand: { primary: '#f45aa8', primaryDeep: '#cc49af', secondary: '#8a5cf6' }, logoAssetId: 7 },
  room: { code: '1', displayName: 'Zimmer 1' },
  occupancy: 'OCCUPIED',
  guestDisplayName: 'Anna & Tom',
  settings: {
    languages: [
      { code: 'de', shortLabel: 'DE', label: 'Deutsch', locale: 'de-DE' },
      { code: 'en', shortLabel: 'EN', label: 'English', locale: 'en-GB' },
    ],
    appearances: [{ mode: 'light', icon: 'phosphorSun' }, { mode: 'dark', icon: 'phosphorMoon' }],
    defaultLanguage: 'de',
    defaultAppearance: 'light',
  },
  menu: {
    items: [
      menuItem('home', 'Home', 'Home', 'phosphorHouse', '', 0),
      menuItem('info', 'Info', 'Info', 'phosphorInfo', '/info', 1, infoModule),
      menuItem('umgebung', 'Umgebung', 'Nearby', 'phosphorMapPin', '/umgebung', 2),
      menuItem('smart-home', 'Smart Home', 'Smart home', 'phosphorLightbulbFilament', '/smart-home', 3),
      menuItem('services', 'Services', 'Services', 'phosphorShoppingBag', '/services', 4),
      menuItem('wlan', 'WLAN', 'Wi-Fi', 'phosphorWifiHigh', '/wlan', 5),
      menuItem('gastronomie', 'Gastronomie', 'Dining', 'phosphorForkKnife', '/gastronomie', 6),
      menuItem('aktivitaeten', 'Aktivitäten', 'Activities', 'phosphorPersonSimpleHike', '/aktivitaeten', 7),
    ],
    settingsEntry: { id: 'einstellungen', label: L('Einstellungen', 'Settings'), icon: 'phosphorGearSix', route: '/einstellungen' },
  },
  standby: { headline: L('Haus Seeblick', 'Haus Seeblick'), message: L('Herzlich willkommen', 'Welcome') },
  home: {
    headline: L('Willkommen im Haus Seeblick, Anna & Tom!', 'Welcome to Haus Seeblick, Anna & Tom!'),
    subtitle: L('Schön, dass ihr da seid', 'Great to have you here'),
    paragraph: L('Hier findet ihr alles Wichtige für euren Aufenthalt.', 'Everything you need for your stay.'),
    actionCards: [
      { icon: 'phosphorKey', title: L('Check-Out', 'Check-out'), description: L('Bis 11:00 Uhr', 'By 11:00'), targetKey: 'info', targetRoute: '/info' },
      { icon: 'phosphorWifiHigh', title: L('WLAN', 'Wi-Fi'), description: L('Netzwerk & Passwort', 'Network & password'), targetKey: 'wlan', targetRoute: '/wlan' },
      { icon: 'phosphorForkKnife', title: L('Frühstück', 'Breakfast'), description: L('Ab 7:30 Uhr', 'From 7:30'), targetKey: 'gastronomie', targetRoute: '/gastronomie' },
      { icon: 'phosphorMapPin', title: L('Umgebung', 'Nearby'), description: L('Was gibt es in der Nähe', "What's around"), targetKey: 'umgebung', targetRoute: '/umgebung' },
    ],
  },
  ...over,
});

const vacant = () => { const c = content(); delete c.guestDisplayName; delete c.home; return { ...c, occupancy: 'VACANT' }; };


export { content, vacant };
