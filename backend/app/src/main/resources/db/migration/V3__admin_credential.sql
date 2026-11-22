-- V3: admin credential (backend-domain-config, REQ-302 / REQ-330)
-- Single-row table, deliberately NOT seeded here: AdminPasswordSeeder writes
-- the row on first start from GXP_ADMIN_PASSWORD (constitution v1.5.x
-- password lifecycle — env var on first start only, hashed in Postgres from
-- then on). password_hash is the self-describing PBKDF2 format
-- pbkdf2-sha256$<iterations>$<salt-b64>$<hash-b64>.
CREATE TABLE admin_credential (
    id             smallint    PRIMARY KEY CHECK (id = 1),
    password_hash  text        NOT NULL,
    updated_at     timestamptz NOT NULL
);
