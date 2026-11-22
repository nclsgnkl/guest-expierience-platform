-- V1: foundation baseline (backend-foundation, REQ-205 / REQ-211)
-- Deliberately minimal, but real enough to prove the mechanism end to end:
-- DDL + seed + a consumer (GET /api/system/info reads the seeded row).
--
-- `name` is PLATFORM metadata (the product name), not the property's name --
-- nothing property-specific is code or seed here (constitution principle 1).
-- backend-domain-config (V2+) may absorb or drop this table once Property exists.

CREATE TABLE instance_info (
    id          smallint PRIMARY KEY CHECK (id = 1),  -- single row, spec §5 style
    name        text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);

INSERT INTO instance_info (id, name) VALUES (1, 'Guest Experience Platform');
