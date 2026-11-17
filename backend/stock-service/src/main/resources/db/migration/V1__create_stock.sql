-- Niveau de stock courant par produit
CREATE TABLE stock_items (
    id                BIGSERIAL PRIMARY KEY,
    product_id        BIGINT       NOT NULL UNIQUE,
    product_reference VARCHAR(50)  NOT NULL,
    product_name      VARCHAR(150) NOT NULL,
    min_stock         INTEGER      NOT NULL DEFAULT 0,
    quantity          INTEGER      NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    last_movement_at  TIMESTAMP WITH TIME ZONE,
    version           BIGINT       NOT NULL DEFAULT 0
);

-- Historique des mouvements (journal immuable)
CREATE TABLE stock_movements (
    id                 BIGSERIAL PRIMARY KEY,
    product_id         BIGINT       NOT NULL,
    product_reference  VARCHAR(50)  NOT NULL,
    product_name       VARCHAR(150) NOT NULL,
    type               VARCHAR(20)  NOT NULL CHECK (type IN ('IN', 'OUT', 'ADJUSTMENT')),
    quantity           INTEGER      NOT NULL CHECK (quantity > 0),
    quantity_before    INTEGER      NOT NULL,
    quantity_after     INTEGER      NOT NULL CHECK (quantity_after >= 0),
    supplier_id        BIGINT,
    document_reference VARCHAR(100),
    reason             VARCHAR(500),
    inventory_id       BIGINT,
    performed_by       VARCHAR(50)  NOT NULL,
    occurred_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX ix_movements_product ON stock_movements (product_id, occurred_at DESC);
CREATE INDEX ix_movements_date ON stock_movements (occurred_at DESC);
CREATE INDEX ix_movements_type ON stock_movements (type);

-- Alertes (rupture / seuil minimum)
CREATE TABLE stock_alerts (
    id                BIGSERIAL PRIMARY KEY,
    product_id        BIGINT       NOT NULL,
    product_reference VARCHAR(50)  NOT NULL,
    product_name      VARCHAR(150) NOT NULL,
    type              VARCHAR(20)  NOT NULL CHECK (type IN ('RUPTURE', 'SEUIL_MIN')),
    status            VARCHAR(20)  NOT NULL CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
    quantity          INTEGER      NOT NULL,
    threshold         INTEGER      NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    acknowledged_by   VARCHAR(50),
    acknowledged_at   TIMESTAMP WITH TIME ZONE,
    resolved_at       TIMESTAMP WITH TIME ZONE
);
CREATE INDEX ix_alerts_product_status ON stock_alerts (product_id, status);
CREATE INDEX ix_alerts_status ON stock_alerts (status, created_at DESC);

-- Inventaires physiques
CREATE TABLE inventories (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(150) NOT NULL,
    notes      VARCHAR(1000),
    status     VARCHAR(20)  NOT NULL CHECK (status IN ('IN_PROGRESS', 'VALIDATED', 'CANCELLED')),
    created_by VARCHAR(50)  NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    closed_by  VARCHAR(50),
    closed_at  TIMESTAMP WITH TIME ZONE,
    version    BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE inventory_lines (
    inventory_id      BIGINT       NOT NULL REFERENCES inventories (id) ON DELETE CASCADE,
    line_order        INTEGER      NOT NULL,
    product_id        BIGINT       NOT NULL,
    product_reference VARCHAR(50)  NOT NULL,
    product_name      VARCHAR(150) NOT NULL,
    expected_quantity INTEGER      NOT NULL,
    counted_quantity  INTEGER      NOT NULL CHECK (counted_quantity >= 0),
    counted_by        VARCHAR(50),
    counted_at        TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (inventory_id, line_order)
);
