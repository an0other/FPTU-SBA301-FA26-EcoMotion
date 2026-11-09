BEGIN;
ALTER TABLE provider_application ADD COLUMN IF NOT EXISTS submitted_attempt_id uuid REFERENCES identity_verification_attempt(public_id);
ALTER TABLE provider_application_review ADD COLUMN IF NOT EXISTS submitted_attempt_id uuid REFERENCES identity_verification_attempt(public_id);
COMMIT;
