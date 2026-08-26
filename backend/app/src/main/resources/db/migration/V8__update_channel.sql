-- update-channel (REQ-709, D8): the single-row update channel state.
-- Carries the VERIFIED raw manifest bytes and signature so the desired-update
-- file can be rebuilt after a restart without re-fetching, and so the
-- available_* columns are provably derived from bytes that verified (they
-- are written in the same statement). The CHECK keeps the two in step.
CREATE TABLE update_channel (
    id                       smallint    PRIMARY KEY CHECK (id = 1),
    auto_update_enabled      boolean     NOT NULL DEFAULT true,
    apply_at                 time        NOT NULL DEFAULT '04:00',
    last_check_at            timestamptz,
    last_check_outcome       text        NOT NULL DEFAULT 'never-checked',
    available_version        text,
    available_artifact_ref   text,
    available_sha256         text,
    available_published_at   timestamptz,
    available_manifest       bytea,
    available_signature      bytea,
    manual_requested_version text,
    manual_requested_at      timestamptz,
    updated_at               timestamptz NOT NULL,
    CHECK ((available_version IS NULL) = (available_manifest IS NULL))
);
INSERT INTO update_channel (id, updated_at) VALUES (1, now());
