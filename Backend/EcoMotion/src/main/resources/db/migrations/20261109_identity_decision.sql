BEGIN;
ALTER TABLE identity_verification_attempt
    ADD COLUMN IF NOT EXISTS submitted_at timestamptz,
    ADD COLUMN IF NOT EXISTS decided_at timestamptz,
    ADD COLUMN IF NOT EXISTS decision_actor varchar(20),
    ADD COLUMN IF NOT EXISTS reviewed_by bigint REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS decision_reason varchar(100),
    ADD COLUMN IF NOT EXISTS decision_policy_version varchar(100),
    ADD COLUMN IF NOT EXISTS resulting_identity_id bigint REFERENCES person_identity(id);
ALTER TABLE identity_verification_attempt DROP CONSTRAINT IF EXISTS ck_identity_attempt_status;
ALTER TABLE identity_verification_attempt ADD CONSTRAINT ck_identity_attempt_status CHECK (
    status IN ('DRAFT','PROCESSING','DOCUMENT_READY','READY_TO_SUBMIT','ERROR','CANCELLED','EXPIRED',
               'SUBMITTED','APPROVED','NEEDS_CHANGES','REJECTED'));
CREATE INDEX IF NOT EXISTS idx_identity_attempt_review ON identity_verification_attempt(role, status, submitted_at, id);
COMMIT;
