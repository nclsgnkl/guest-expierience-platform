-- V4: device pairing (device-pairing-and-guest-api, REQ-402)
-- Tables per docs/features/device-pairing-and-guest-api/02-design.md §Data
-- model, verbatim. V1–V3 untouched. No seeds — a fresh instance has zero
-- devices by definition.
--
-- device.token_hash holds the SHA-256 hex of the device token, NEVER the raw
-- value (the raw token exists exactly once, in the claiming poll response).
-- room_id is ON DELETE RESTRICT as the DB backstop of the Control-level
-- delete guard (409 room-has-devices naming active devices; revoked rows are
-- deleted with the room in the same transaction — domain-config precedent).

CREATE TABLE device (
    id            bigserial   PRIMARY KEY,
    room_id       bigint      NOT NULL REFERENCES room (id) ON DELETE RESTRICT,
    name          text        NOT NULL CHECK (name <> ''),
    token_hash    text        NOT NULL UNIQUE,
    created_at    timestamptz NOT NULL,
    last_seen_at  timestamptz NULL,
    revoked_at    timestamptz NULL
);

-- pairing_request.poll_secret_hash: SHA-256 hex of the poll secret (the
-- kiosk's machine channel — the display code alone never yields a token).
-- room_id/device_name are set at manager confirm; claimed_at makes the
-- token hand-out claim-once (atomic UPDATE ... WHERE claimed_at IS NULL).
-- Expired rows are deleted opportunistically on each create (no scheduler).

CREATE TABLE pairing_request (
    id                bigserial   PRIMARY KEY,
    code              text        NOT NULL UNIQUE CHECK (code <> ''),
    poll_secret_hash  text        NOT NULL,
    room_id           bigint      NULL REFERENCES room (id) ON DELETE CASCADE,
    device_name       text        NULL,
    created_at        timestamptz NOT NULL,
    expires_at        timestamptz NOT NULL,
    confirmed_at      timestamptz NULL,
    claimed_at        timestamptz NULL
);
