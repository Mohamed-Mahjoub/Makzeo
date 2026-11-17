CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500)
);
CREATE UNIQUE INDEX ux_categories_name ON categories (lower(name));

CREATE SEQUENCE barcode_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE products (
    id             BIGSERIAL PRIMARY KEY,
    reference      VARCHAR(50)    NOT NULL UNIQUE,
    name           VARCHAR(150)   NOT NULL,
    description    VARCHAR(1000),
    category_id    BIGINT         NOT NULL REFERENCES categories (id),
    barcode        VARCHAR(32)    NOT NULL UNIQUE,
    unit           VARCHAR(20)    NOT NULL,
    purchase_price NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (purchase_price >= 0),
    sale_price     NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (sale_price >= 0),
    min_stock      INTEGER        NOT NULL DEFAULT 0 CHECK (min_stock >= 0),
    max_stock      INTEGER CHECK (max_stock IS NULL OR max_stock >= min_stock),
    supplier_id    BIGINT,
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version        BIGINT         NOT NULL DEFAULT 0
);
CREATE INDEX ix_products_category ON products (category_id);
CREATE INDEX ix_products_supplier ON products (supplier_id);
CREATE INDEX ix_products_name ON products (lower(name));
