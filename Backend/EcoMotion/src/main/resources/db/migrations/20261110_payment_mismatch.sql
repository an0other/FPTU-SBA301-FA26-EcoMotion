-- Áp dụng tự động bởi SchemaCompatibilityPatcher khi khởi động; giữ bản này để chạy tay nếu cần.
BEGIN;
ALTER TABLE payment_evidence ALTER COLUMN proof_image_url DROP NOT NULL;
ALTER TABLE dispute DROP CONSTRAINT IF EXISTS dispute_category_check;
ALTER TABLE dispute DROP CONSTRAINT IF EXISTS dispute_decision_check;
COMMIT;
