-- V2: domain & configuration (backend-domain-config, REQ-302..REQ-311, REQ-334)
-- Tables + checks + seeds per docs/features/backend-domain-config/02-design.md
-- §Data model. V1 / instance_info is untouched (REQ-302): it is PLATFORM
-- metadata behind /api/system/info, distinct from the property's name.
--
-- Seed content is deliberately NEUTRAL placeholder copy (constitution
-- principle 1: nothing property-specific in code or seed) — the manager edits
-- it. The menu catalogue seed mirrors the five real kiosk sections verbatim
-- from frontend/src/app/core/content/example-property.data.ts. The
-- structurally fixed settings entry (einstellungen) is NOT in the catalogue:
-- it is app chrome a manager must not be able to disable.

-- ---------------------------------------------------------------- asset
CREATE TABLE asset (
    id          bigserial PRIMARY KEY,
    mime_type   text    NOT NULL CHECK (mime_type IN ('image/png', 'image/jpeg')),
    byte_size   integer NOT NULL,
    width       integer NOT NULL,
    height      integer NOT NULL,
    sha256      text    NOT NULL,
    kind        text    NOT NULL CHECK (kind IN ('LOGO', 'ARTWORK')),
    data        bytea   NOT NULL
);

-- ---------------------------------------------------------------- property
-- Invariant 4: exactly one property row, DB-enforced.
CREATE TABLE property (
    id                  smallint PRIMARY KEY CHECK (id = 1),
    name                text NOT NULL,
    main_language       text NOT NULL CHECK (main_language IN ('de', 'en')),
    timezone            text NOT NULL,  -- IANA id, ZoneId-validated in the Control
    brand_primary       text NOT NULL CHECK (brand_primary      ~ '^#[0-9a-fA-F]{6}$'),
    brand_primary_deep  text NOT NULL CHECK (brand_primary_deep ~ '^#[0-9a-fA-F]{6}$'),
    brand_secondary     text NOT NULL CHECK (brand_secondary    ~ '^#[0-9a-fA-F]{6}$'),
    default_appearance  text NOT NULL CHECK (default_appearance IN ('light', 'dark', 'system')),
    logo_asset_id       bigint NULL REFERENCES asset (id) ON DELETE RESTRICT,
    artwork_asset_id    bigint NULL REFERENCES asset (id) ON DELETE RESTRICT
);

CREATE TABLE property_translation (
    property_id       smallint NOT NULL REFERENCES property (id) ON DELETE CASCADE,
    language          text     NOT NULL CHECK (language IN ('de', 'en')),
    hero_subtitle     text     NOT NULL,
    headline          text     NOT NULL,  -- raw greeting template
    paragraph         text     NOT NULL,
    standby_headline  text     NOT NULL,  -- raw standby template ({property.name} only)
    standby_message   text     NOT NULL,
    PRIMARY KEY (property_id, language)
);

CREATE TABLE property_language_option (
    property_id  smallint NOT NULL REFERENCES property (id) ON DELETE CASCADE,
    code         text     NOT NULL CHECK (code IN ('de', 'en')),
    short_label  text     NOT NULL,
    label        text     NOT NULL,  -- endonym, never translated
    locale       text     NOT NULL,
    sort_index   integer  NOT NULL,
    PRIMARY KEY (property_id, code)
);

CREATE TABLE property_appearance_option (
    property_id  smallint NOT NULL REFERENCES property (id) ON DELETE CASCADE,
    mode         text     NOT NULL CHECK (mode IN ('light', 'dark', 'system')),
    icon         text     NOT NULL,
    sort_index   integer  NOT NULL,
    PRIMARY KEY (property_id, mode)
);

-- ---------------------------------------------------------------- room
CREATE TABLE room (
    id                  bigserial PRIMARY KEY,
    code                text NOT NULL UNIQUE CHECK (code <> ''),
    display_name        text NOT NULL,
    artwork_asset_id    bigint NULL REFERENCES asset (id) ON DELETE RESTRICT,
    default_language    text NULL CHECK (default_language   IN ('de', 'en')),
    default_appearance  text NULL CHECK (default_appearance IN ('light', 'dark', 'system'))
);

-- ---------------------------------------------------------------- menu
CREATE TABLE menu_item_definition (
    key                 text PRIMARY KEY,
    default_icon        text    NOT NULL,
    route               text    NOT NULL,  -- home's route is the empty string
    default_sort_index  integer NOT NULL,
    introduced_release  text    NOT NULL
);

CREATE TABLE menu_item_definition_translation (
    key       text NOT NULL REFERENCES menu_item_definition (key) ON DELETE CASCADE,
    language  text NOT NULL CHECK (language IN ('de', 'en')),
    label     text NOT NULL,
    PRIMARY KEY (key, language)
);

-- Both scopes in one table: room_id NULL = property scope. Uniqueness per
-- (menu item, scope) must include the NULL scope -> NULLS NOT DISTINCT
-- (core PostgreSQL 15+ syntax; the shipped image is 17.x).
CREATE TABLE menu_item_config (
    id             bigserial PRIMARY KEY,
    menu_item_key  text   NOT NULL REFERENCES menu_item_definition (key) ON DELETE CASCADE,
    room_id        bigint NULL REFERENCES room (id) ON DELETE CASCADE,
    enabled        boolean NULL,   -- each field nullable: absent = inherit per-field
    sort_index     integer NULL,
    icon_override  text    NULL,
    CONSTRAINT uq_menu_item_config_scope UNIQUE NULLS NOT DISTINCT (menu_item_key, room_id)
);

CREATE TABLE menu_item_config_translation (
    config_id  bigint NOT NULL REFERENCES menu_item_config (id) ON DELETE CASCADE,
    language   text   NOT NULL CHECK (language IN ('de', 'en')),
    label      text   NOT NULL,
    PRIMARY KEY (config_id, language)
);

-- Invariant 1 by construction: sort_index 0..3 + per-scope uniqueness = <=4
-- rows per scope. target key deliberately NOT unique (several cards may
-- target the same item — the shipped frontend data does).
CREATE TABLE action_card_config (
    id                    bigserial PRIMARY KEY,
    room_id               bigint  NULL REFERENCES room (id) ON DELETE CASCADE,
    sort_index            integer NOT NULL CHECK (sort_index BETWEEN 0 AND 3),
    icon                  text    NOT NULL,
    target_menu_item_key  text    NOT NULL REFERENCES menu_item_definition (key),
    CONSTRAINT uq_action_card_scope_sort UNIQUE NULLS NOT DISTINCT (room_id, sort_index)
);

CREATE TABLE action_card_config_translation (
    card_id               bigint NOT NULL REFERENCES action_card_config (id) ON DELETE CASCADE,
    language              text   NOT NULL CHECK (language IN ('de', 'en')),
    title_template        text   NOT NULL,  -- raw
    description_template  text   NOT NULL,  -- raw
    PRIMARY KEY (card_id, language)
);

-- ---------------------------------------------------------------- stay
-- Stays survive a room only deliberately: RESTRICT + Control pre-check (409
-- naming them). "Active" is derived from the clock, never stored.
CREATE TABLE stay (
    id            bigserial   PRIMARY KEY,
    room_id       bigint      NOT NULL REFERENCES room (id) ON DELETE RESTRICT,
    display_name  text        NOT NULL,
    check_in      timestamptz NOT NULL,
    check_out     timestamptz NOT NULL,
    language      text NULL CHECK (language   IN ('de', 'en')),
    appearance    text NULL CHECK (appearance IN ('light', 'dark', 'system')),
    status        text NOT NULL CHECK (status IN ('BOOKED', 'CANCELLED')),
    CONSTRAINT ck_stay_range CHECK (check_in < check_out)
);

-- ================================================================ seeds
-- Neutral property (REQ-334): placeholder copy the manager edits; pinned
-- defaults — main language de, timezone Europe/Berlin, light default.
-- Brand colors are a neutral slate placeholder, not any property's branding.
INSERT INTO property (id, name, main_language, timezone,
                      brand_primary, brand_primary_deep, brand_secondary,
                      default_appearance)
VALUES (1, 'Meine Unterkunft', 'de', 'Europe/Berlin',
        '#5b6472', '#3f4652', '#8892a0', 'light');

INSERT INTO property_translation (property_id, language, hero_subtitle,
                                  headline, paragraph,
                                  standby_headline, standby_message)
VALUES (1, 'de',
        'Herzlich willkommen',
        'Willkommen, {stay.displayName}!',
        'Diese Anzeige ist noch nicht eingerichtet. Bitte passen Sie Texte und Branding im Verwaltungsbereich an.',
        '{property.name}',
        'Herzlich willkommen');

INSERT INTO property_language_option (property_id, code, short_label, label, locale, sort_index)
VALUES (1, 'de', 'DE', 'Deutsch', 'de-DE', 0),
       (1, 'en', 'EN', 'English', 'en-GB', 1);

INSERT INTO property_appearance_option (property_id, mode, icon, sort_index)
VALUES (1, 'light', 'phosphorSun',  0),
       (1, 'dark',  'phosphorMoon', 1);

-- One default room so a fresh instance is usable with zero manual DB work.
INSERT INTO room (code, display_name) VALUES ('1', 'Zimmer 1');

-- Release catalogue: exactly the five real kiosk sections, keys/routes/icons/
-- labels verbatim from example-property.data.ts. No menu_item_config rows
-- (absence = catalogue defaults, enabled) and no cards (0 is a valid set).
INSERT INTO menu_item_definition (key, default_icon, route, default_sort_index, introduced_release)
VALUES ('home',       'phosphorHouse',             '',            0, '1.0.0'),
       ('info',       'phosphorInfo',              '/info',       1, '1.0.0'),
       ('umgebung',   'phosphorMapPin',            '/umgebung',   2, '1.0.0'),
       ('smart-home', 'phosphorLightbulbFilament', '/smart-home', 3, '1.0.0'),
       ('services',   'phosphorShoppingBag',       '/services',   4, '1.0.0');

INSERT INTO menu_item_definition_translation (key, language, label)
VALUES ('home',       'de', 'Home'),       ('home',       'en', 'Home'),
       ('info',       'de', 'Info'),       ('info',       'en', 'Info'),
       ('umgebung',   'de', 'Umgebung'),   ('umgebung',   'en', 'Nearby'),
       ('smart-home', 'de', 'Smart Home'), ('smart-home', 'en', 'Smart Home'),
       ('services',   'de', 'Services'),   ('services',   'en', 'Services');
