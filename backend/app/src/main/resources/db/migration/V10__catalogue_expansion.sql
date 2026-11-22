-- sidebar-catalogue-overflow (REQ-903, REQ-905, REQ-907, REQ-908, REQ-910;
-- 02-design.md §3.1). No DDL — three INSERT groups into tables V2 created.
--
-- WHAT THESE ROWS ARE: **disposable test fixtures**, not product. They exist
-- so overflow, promotion and locale-aware collation are exercised against a
-- catalogue that genuinely exceeds the sidebar's five slots. The owner was
-- explicit (2026-08-27): they will never be implemented under the names they
-- carry — 'wlan', 'anreise', 'hausordnung' and 'muell' belong under Info if
-- they are ever built at all. No product justification is recorded for any
-- row, deliberately.
--
-- Only THREE properties of this set may be relied on anywhere: the total
-- catalogue count is >= 12, at least one label starts with an umlaut
-- ('Ärztliche Versorgung'), and alphabetical order differs visibly from
-- default_sort_index order in both offered languages. Any row may be renamed,
-- re-iconed or dropped as long as those hold.
--
-- REMOVING THEM LATER IS THIS MIGRATION REVERSED PLUS ONE EDIT TO
-- frontend/src/app/core/icons/registered-icons.ts. That is REQ-903 and it is a
-- constraint on every future change: no code path, no test and no design
-- decision may come to depend on a particular key here. The greppable property
-- is that no fixture KEY or LABEL appears outside this file, DevDataSeeder and
-- MenuFixtures. Four test classes additionally pin the catalogue COUNT at 13
-- (CatalogueExpansionTest twice, MenuCatalogueAndConfigTest, FirstRunSeedTest,
-- DevDataSeederTest — five assertions); removing or adding rows means updating
-- those five numbers and nothing else.
--
-- WHY THE enabled = false ROWS IN GROUP 3 EXIST (owner decision D5, REQ-908):
-- MenuControl resolves the ABSENCE of a menu_item_config row as *enabled*.
-- Since constitution v1.8.1 instances are patched automatically through the
-- signed update channel, so a release that adds catalogue rows would otherwise
-- put new sections on a running property's guest screens behind the manager's
-- back. The opt-out is therefore written explicitly, at property scope, and
-- the manager turns a section on when they want it. The %dev seeder overrides
-- these eight rows to true (DevDataSeeder) so overflow is the default
-- developer and review experience.
--
-- introduced_release = '1.1.0' is written for traceability only and STAYS
-- INERT: nothing reads it, joins on it or gates on it (D5's rejected
-- alternative). Do not make it load-bearing.

-- 1. definitions — default_sort_index continues 5..12 after the existing 0..4
INSERT INTO menu_item_definition (key, default_icon, route, default_sort_index, introduced_release)
VALUES ('wlan',         'phosphorWifiHigh',          '/wlan',          5, '1.1.0'),
       ('anreise',      'phosphorClock',             '/anreise',       6, '1.1.0'),
       ('hausordnung',  'phosphorFileText',          '/hausordnung',   7, '1.1.0'),
       ('muell',        'phosphorRecycle',           '/muell',         8, '1.1.0'),
       ('aerztliches',  'phosphorFirstAid',          '/aerztliches',   9, '1.1.0'),
       ('parken',       'phosphorCar',               '/parken',       10, '1.1.0'),
       ('gastronomie',  'phosphorForkKnife',         '/gastronomie',  11, '1.1.0'),
       ('aktivitaeten', 'phosphorPersonSimpleHike',  '/aktivitaeten', 12, '1.1.0');

-- 2. labels in both offered languages (the column is NOT NULL CHECK (language IN ('de','en')))
INSERT INTO menu_item_definition_translation (key, language, label)
VALUES ('wlan',         'de', 'WLAN'),                 ('wlan',         'en', 'Wi-Fi'),
       ('anreise',      'de', 'Anreise & Abreise'),    ('anreise',      'en', 'Arrival & departure'),
       ('hausordnung',  'de', 'Hausordnung'),          ('hausordnung',  'en', 'House rules'),
       ('muell',        'de', 'Müll & Recycling'),     ('muell',        'en', 'Waste & recycling'),
       ('aerztliches',  'de', 'Ärztliche Versorgung'), ('aerztliches',  'en', 'Medical care'),
       ('parken',       'de', 'Parken'),               ('parken',       'en', 'Parking'),
       ('gastronomie',  'de', 'Essen & Trinken'),      ('gastronomie',  'en', 'Food & drink'),
       ('aktivitaeten', 'de', 'Aktivitäten'),          ('aktivitaeten', 'en', 'Things to do');

-- 3. D5's explicit property-scope opt-out. room_id IS NULL = property scope;
--    every other column stays NULL so per-field inheritance is untouched.
--    uq_menu_item_config_scope UNIQUE NULLS NOT DISTINCT accepts these.
INSERT INTO menu_item_config (menu_item_key, room_id, enabled)
VALUES ('wlan',         NULL, false),
       ('anreise',      NULL, false),
       ('hausordnung',  NULL, false),
       ('muell',        NULL, false),
       ('aerztliches',  NULL, false),
       ('parken',       NULL, false),
       ('gastronomie',  NULL, false),
       ('aktivitaeten', NULL, false);
