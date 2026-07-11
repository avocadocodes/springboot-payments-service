CREATE TABLE payments (
    id          UUID            NOT NULL DEFAULT gen_random_uuid(),
    amount      NUMERIC(19, 4)  NOT NULL,
    currency    CHAR(3)         NOT NULL,
    customer_id VARCHAR(255)    NOT NULL,
    method      VARCHAR(50)     NOT NULL,
    status      VARCHAR(20)     NOT NULL,
    gateway_reference VARCHAR(255),
    failure_reason    VARCHAR(1000),
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    version     BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'REFUNDED')),
    CONSTRAINT ck_payments_amount CHECK (amount > 0)
);

CREATE INDEX idx_payments_customer_id ON payments(customer_id);
CREATE INDEX idx_payments_status      ON payments(status);
CREATE INDEX idx_payments_created_at  ON payments(created_at DESC);
