-- V5: named manager accounts (auth-hardening A2, REQ-607 / REQ-613, design D16)
-- Replaces the single-row admin_credential (V3) with manager_user. The existing
-- credential row is CARRIED OVER as the active user `admin` with its hash
-- unchanged, so an upgraded instance logs in with the same password; a fresh
-- instance has zero rows until FirstUserSeeder writes the first one from
-- GXP_ADMIN_USERNAME / GXP_ADMIN_PASSWORD. password_hash keeps the
-- self-describing PBKDF2 format pbkdf2-sha256$<iterations>$<salt>$<hash>.
CREATE TABLE manager_user (
    id            bigserial   PRIMARY KEY,
    username      text        NOT NULL UNIQUE CHECK (username ~ '^[a-z0-9][a-z0-9._-]{0,63}$'),
    password_hash text        NOT NULL,
    active        boolean     NOT NULL DEFAULT true,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL
);
INSERT INTO manager_user (username, password_hash, active, created_at, updated_at)
    SELECT 'admin', password_hash, true, updated_at, updated_at FROM admin_credential;
DROP TABLE admin_credential;
