BEGIN;
ALTER TABLE users ADD COLUMN IF NOT EXISTS identity_reviewed_at timestamptz;
UPDATE users u SET identity_reviewed_at = i.reviewed_at
FROM person_identity i, identity_verification_attempt a
WHERE u.identity_id = i.id AND i.approved_attempt_id = a.id AND a.user_id = u.id
AND u.role = 'PROVIDER' AND i.verification_method = 'ADMIN_REVIEW' AND a.status = 'APPROVED'
AND u.identity_reviewed_at IS NULL;

CREATE TABLE IF NOT EXISTS identity_account_link (
    id uuid PRIMARY KEY,
    source_user_id bigint NOT NULL REFERENCES users(id),
    target_user_id bigint NOT NULL REFERENCES users(id),
    identity_id bigint NOT NULL REFERENCES person_identity(id),
    source_credential_stamp varchar(64) NOT NULL,
    status varchar(20) NOT NULL,
    expires_at timestamptz NOT NULL,
    confirmed_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_identity_link_accounts CHECK (source_user_id <> target_user_id),
    CONSTRAINT ck_identity_link_status CHECK (status IN ('PENDING','CONFIRMED','CANCELLED'))
);
CREATE INDEX IF NOT EXISTS idx_identity_link_source ON identity_account_link(source_user_id, expires_at DESC);
CREATE INDEX IF NOT EXISTS idx_identity_link_target ON identity_account_link(target_user_id, expires_at DESC);

ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_users_phone;
DROP INDEX IF EXISTS uk_users_phone;
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_phone_role ON users(phone, role) WHERE phone IS NOT NULL;

CREATE OR REPLACE FUNCTION enforce_identity_phone_sharing() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.phone IS NULL THEN RETURN NEW; END IF;
    PERFORM pg_advisory_xact_lock(hashtextextended(NEW.phone, 0));
    IF EXISTS (
        SELECT 1 FROM users u WHERE u.phone = NEW.phone AND u.id <> NEW.id
        AND (NEW.identity_id IS NULL OR u.identity_id IS NULL OR NEW.identity_id <> u.identity_id
             OR NEW.role NOT IN ('RENTER','PROVIDER') OR u.role NOT IN ('RENTER','PROVIDER') OR NEW.role = u.role)
    ) THEN
        RAISE EXCEPTION 'Phone number belongs to another identity' USING ERRCODE = '23505';
    END IF;
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_identity_phone_sharing ON users;
CREATE TRIGGER trg_identity_phone_sharing BEFORE INSERT OR UPDATE OF phone, identity_id, role ON users
FOR EACH ROW EXECUTE FUNCTION enforce_identity_phone_sharing();
COMMIT;
