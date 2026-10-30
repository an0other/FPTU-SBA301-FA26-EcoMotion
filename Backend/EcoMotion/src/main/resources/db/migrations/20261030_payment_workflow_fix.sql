BEGIN;

-- 1. Add transaction_code to payment_transaction
ALTER TABLE payment_transaction ADD COLUMN IF NOT EXISTS transaction_code varchar(32);
CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_transaction_code 
    ON payment_transaction (transaction_code) 
    WHERE transaction_code IS NOT NULL;

-- 2. Add payment_code to payment_request
ALTER TABLE payment_request ADD COLUMN IF NOT EXISTS payment_code varchar(32);
CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_request_code 
    ON payment_request (payment_code) 
    WHERE payment_code IS NOT NULL;

-- 3. Create sequence table for thread-safe atomic transaction code generation
CREATE TABLE IF NOT EXISTS payment_code_sequence (
    code_prefix varchar(10) PRIMARY KEY,
    next_val bigint NOT NULL
);

-- 4. Backfill existing payment_transaction codes
UPDATE payment_transaction 
SET transaction_code = CASE 
    WHEN method = 'VNPAY' THEN 'VP' || LPAD(id::text, 6, '0')
    ELSE 'VQ' || LPAD(id::text, 6, '0')
END
WHERE transaction_code IS NULL;

-- 5. Backfill existing payment_request codes
UPDATE payment_request
SET payment_code = 'VQ' || LPAD(id::text, 6, '0')
WHERE payment_code IS NULL;

-- 6. Initialize sequence values past maximum existing IDs
INSERT INTO payment_code_sequence (code_prefix, next_val)
VALUES 
    ('VP', COALESCE((SELECT MAX(id) FROM payment_transaction WHERE method = 'VNPAY'), 0) + 1),
    ('VQ', GREATEST(
        COALESCE((SELECT MAX(id) FROM payment_transaction WHERE method <> 'VNPAY'), 0),
        COALESCE((SELECT MAX(id) FROM payment_request), 0)
    ) + 1)
ON CONFLICT (code_prefix) DO NOTHING;

COMMIT;
