-- Demo data so results can be reproduced. All values are fictitious.
--
-- 20230001  2026-FALL partially paid (2000 of 5000 USD), 2027-SPRING unpaid
-- 20230002  2026-FALL fully paid
-- 20230003  2026-FALL unpaid
-- 20230004  2026-FALL unpaid (USD), 2027-SPRING unpaid (EUR) -> mixed currencies

INSERT INTO students (student_no, full_name) VALUES
    ('20230001', 'Ayse Yilmaz'),
    ('20230002', 'Mehmet Kaya'),
    ('20230003', 'Zeynep Demir'),
    ('20230004', 'Can Ozturk');

INSERT INTO tuitions (student_no, term, amount, paid_amount, currency) VALUES
    ('20230001', '2026-FALL',   5000.00, 2000.00, 'USD'),
    ('20230001', '2027-SPRING', 5000.00,    0.00, 'USD'),
    ('20230002', '2026-FALL',   4500.00, 4500.00, 'USD'),
    ('20230003', '2026-FALL',   6000.00,    0.00, 'USD'),
    ('20230004', '2026-FALL',   3000.00,    0.00, 'USD'),
    ('20230004', '2027-SPRING', 3200.00,    0.00, 'EUR');

-- Payments that explain the paid amounts above.
INSERT INTO payments (tuition_id, transaction_id, amount, paid_at)
SELECT id, 'SEED-TXN-0001', 2000.00, TIMESTAMPTZ '2026-09-10 10:00:00+03'
FROM tuitions WHERE student_no = '20230001' AND term = '2026-FALL';

INSERT INTO payments (tuition_id, transaction_id, amount, paid_at)
SELECT id, 'SEED-TXN-0002', 4500.00, TIMESTAMPTZ '2026-09-15 14:30:00+03'
FROM tuitions WHERE student_no = '20230002' AND term = '2026-FALL';
