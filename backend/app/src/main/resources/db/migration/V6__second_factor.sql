-- V6: second factor (auth-hardening A3, REQ-614..REQ-616, design D9/D10/D16)
-- The TOTP shared secret is necessarily stored as the secret itself (an HMAC
-- needs it; it is not a password and cannot be hashed) — it never reaches a
-- DTO or a log (REQ-619). totp_confirmed_at NULL with a secret = enrollment
-- pending; totp_last_step is the replay guard (a code for a step <= it is
-- refused). Recovery codes are stored SHA-256-hex and are single-use.
ALTER TABLE manager_user
    ADD COLUMN totp_secret       text,
    ADD COLUMN totp_confirmed_at timestamptz,
    ADD COLUMN totp_last_step    bigint;
CREATE TABLE manager_recovery_code (
    id        bigserial PRIMARY KEY,
    user_id   bigint    NOT NULL REFERENCES manager_user(id) ON DELETE CASCADE,
    code_hash text      NOT NULL,
    used_at   timestamptz
);
CREATE INDEX manager_recovery_code_user ON manager_recovery_code(user_id);
