-- Demo login accounts, one per role. Passwords are BCrypt hashes (cost 10).
-- Demo passwords (documented in the README, for testing only):
--   STUDENT  username = student number  ->  Student123!
--   BANK     bank                       ->  Bank123!
--   ADMIN    admin                      ->  Admin123!

INSERT INTO users (username, password_hash, role, student_no) VALUES
    ('20230001', '$2a$10$PaLfOvj8SPntfnwy1mN2luhkGkoVMkIFmLz9QzizGEbyTUJF.Uxhm', 'STUDENT', '20230001'),
    ('20230002', '$2a$10$PaLfOvj8SPntfnwy1mN2luhkGkoVMkIFmLz9QzizGEbyTUJF.Uxhm', 'STUDENT', '20230002'),
    ('20230003', '$2a$10$PaLfOvj8SPntfnwy1mN2luhkGkoVMkIFmLz9QzizGEbyTUJF.Uxhm', 'STUDENT', '20230003'),
    ('20230004', '$2a$10$PaLfOvj8SPntfnwy1mN2luhkGkoVMkIFmLz9QzizGEbyTUJF.Uxhm', 'STUDENT', '20230004'),
    ('bank',     '$2a$10$ev9MnfQDrk3S3fNVcdG6QeygfbrY8/HRCYqxzL2yfgoHV.CMTHt6K', 'BANK',    NULL),
    ('admin',    '$2a$10$.CItYzn/E.dq5MuY04UJDOIoQ71of2gzrgdlGGmOgJs9pYNoy4bbW', 'ADMIN',   NULL);
