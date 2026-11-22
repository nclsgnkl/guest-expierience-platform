-- Module content for the `info` module type (module-content REQ-968).
-- Per-type tables, because a module type has a fixed shape per type (A3/D14):
-- the next module type brings its own table, not a column here.
-- NO room scope: module content is property-scoped (A2) and D11 settled that
-- there is no per-room content override.
-- NO seed rows: a release must not put content on a running property's guest
-- screens behind the manager's back (Part A's D5, same reasoning). An empty
-- table means `info` carries no module, which renders the placeholder exactly
-- as it does today (REQ-975). %dev seeds two rows; nothing else writes here in
-- this Part (D16 — the manager write path is the successor feature's).
CREATE TABLE info_module_row (
    id             bigserial PRIMARY KEY,
    menu_item_key  text    NOT NULL REFERENCES menu_item_definition (key) ON DELETE CASCADE,
    sort_index     integer NOT NULL,
    CONSTRAINT uq_info_module_row_order UNIQUE (menu_item_key, sort_index)
);

CREATE TABLE info_module_row_translation (
    row_id    bigint NOT NULL REFERENCES info_module_row (id) ON DELETE CASCADE,
    language  text   NOT NULL CHECK (language IN ('de', 'en')),
    label     text   NOT NULL,  -- raw template
    value     text   NOT NULL,  -- raw template
    PRIMARY KEY (row_id, language)
);
