-- auth-hardening A4 (REQ-620): the append-only audit trail. Who, when, from
-- which attributed address, which action on which entity, which FIELD changed.
-- There is deliberately NO column for a value, before or after (constitution
-- principle 3): the schema itself is the guarantee that a door code, a WiFi
-- password or a password hash can never end up here.
CREATE TABLE audit_entry (
    id          bigserial   PRIMARY KEY,
    occurred_at timestamptz NOT NULL,
    actor       text,
    address     text        NOT NULL,
    via_proxy   boolean     NOT NULL,
    action      text        NOT NULL,
    entity      text        NOT NULL,
    entity_id   text,
    field       text
);
CREATE INDEX audit_entry_occurred ON audit_entry(occurred_at DESC, id DESC);
