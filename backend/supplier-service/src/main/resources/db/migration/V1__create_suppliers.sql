CREATE TABLE suppliers (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(30)  NOT NULL UNIQUE,
    name         VARCHAR(150) NOT NULL,
    contact_name VARCHAR(120),
    email        VARCHAR(120),
    phone        VARCHAR(30),
    address      VARCHAR(255),
    city         VARCHAR(80),
    country      VARCHAR(80),
    ice          VARCHAR(15) UNIQUE,
    notes        VARCHAR(1000),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version      BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX ix_suppliers_name ON suppliers (lower(name));
