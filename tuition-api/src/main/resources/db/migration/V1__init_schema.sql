-- Tuition API schema. Matches docs/er-diagram.md.

CREATE TABLE students (
    student_no  VARCHAR(20)  PRIMARY KEY,
    full_name   VARCHAR(100),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tuitions (
    id           BIGSERIAL      PRIMARY KEY,
    student_no   VARCHAR(20)    NOT NULL REFERENCES students (student_no),
    term         VARCHAR(11)    NOT NULL,
    amount       NUMERIC(12, 2) NOT NULL,
    paid_amount  NUMERIC(12, 2) NOT NULL DEFAULT 0,
    balance      NUMERIC(12, 2) GENERATED ALWAYS AS (amount - paid_amount) STORED,
    currency     CHAR(3)        NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uk_tuitions_student_term UNIQUE (student_no, term),
    CONSTRAINT ck_tuitions_term         CHECK (term ~ '^[0-9]{4}-(FALL|SPRING)$'),
    CONSTRAINT ck_tuitions_amount       CHECK (amount > 0),
    CONSTRAINT ck_tuitions_paid_amount  CHECK (paid_amount >= 0 AND paid_amount <= amount),
    CONSTRAINT ck_tuitions_currency     CHECK (currency ~ '^[A-Z]{3}$')
);

-- Unpaid Tuition Status: page over unpaid rows of one term, ordered by student number.
CREATE INDEX ix_tuitions_unpaid ON tuitions (term, student_no) WHERE balance > 0;

CREATE TABLE payments (
    id              BIGSERIAL      PRIMARY KEY,
    tuition_id      BIGINT         NOT NULL REFERENCES tuitions (id),
    transaction_id  VARCHAR(64)    NOT NULL,
    amount          NUMERIC(12, 2) NOT NULL,
    paid_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uk_payments_transaction_id UNIQUE (transaction_id),
    CONSTRAINT ck_payments_amount         CHECK (amount > 0)
);

CREATE INDEX ix_payments_tuition_id ON payments (tuition_id);

-- Login accounts. Rows are seeded in the authentication phase.
CREATE TABLE users (
    id             BIGSERIAL     PRIMARY KEY,
    username       VARCHAR(50)   NOT NULL,
    password_hash  VARCHAR(100)  NOT NULL,
    role           VARCHAR(10)   NOT NULL,
    student_no     VARCHAR(20)   REFERENCES students (student_no),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_users_username     UNIQUE (username),
    CONSTRAINT uk_users_student_no   UNIQUE (student_no),
    CONSTRAINT ck_users_role         CHECK (role IN ('STUDENT', 'BANK', 'ADMIN')),
    CONSTRAINT ck_users_student_link CHECK ((role = 'STUDENT') = (student_no IS NOT NULL))
);
