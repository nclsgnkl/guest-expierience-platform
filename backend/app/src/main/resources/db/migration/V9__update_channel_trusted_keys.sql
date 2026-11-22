-- update-channel review A-1 (T-20a): the ROTATED trust set. NULL = the set
-- compiled into the running image applies. When a verified manifest carries
-- `trustedKeys`, the app persists that set together with the manifest's
-- version; the persisted set wins over the image's whenever its version is
-- newer than the running one (so a rolled-back older image does not re-trust
-- a retired key), and the image's set wins when the image is newer (a
-- hand-installed release carries its own set). Both columns move together.
ALTER TABLE update_channel
    ADD COLUMN trusted_keys         text,
    ADD COLUMN trusted_keys_version text,
    ADD CONSTRAINT update_channel_trusted_keys_pair
        CHECK ((trusted_keys IS NULL) = (trusted_keys_version IS NULL));
