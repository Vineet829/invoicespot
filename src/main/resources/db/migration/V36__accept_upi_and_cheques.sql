-- Payments can also come by UPI or by cheque.
ALTER TABLE payments DROP CONSTRAINT chk_payments_method;
ALTER TABLE payments ADD CONSTRAINT chk_payments_method CHECK (payment_method IN
    ('Cash', 'Mobile Money', 'PayPal', 'Credit Card', 'Bank Transfer', 'UPI', 'Cheque', 'Others'));
