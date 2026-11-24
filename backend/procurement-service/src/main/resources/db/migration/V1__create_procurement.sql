-- =============================================================================
-- Module IA achats : veille fournisseurs, demandes de prix, devis, historique et
-- prédiction des prix, recommandations d'achat, exécutions de la veille mensuelle.
-- Montants en dinars tunisiens par défaut (3 décimales : millimes).
-- =============================================================================

-- Fournisseurs du marché : issus du catalogue, détectés par la veille ou saisis
CREATE TABLE market_suppliers (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(150) NOT NULL,
    normalized_key      VARCHAR(150) NOT NULL UNIQUE,
    website             VARCHAR(255),
    email               VARCHAR(150),
    phone               VARCHAR(40),
    city                VARCHAR(80),
    source              VARCHAR(20)  NOT NULL CHECK (source IN ('CATALOG', 'WEB_WATCH', 'MANUAL')),
    status              VARCHAR(20)  NOT NULL CHECK (status IN ('CANDIDATE', 'VALIDATED', 'REJECTED')),
    catalog_supplier_id BIGINT,
    notes               VARCHAR(1000),
    discovered_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX ix_market_suppliers_status ON market_suppliers (status, discovered_at DESC);

-- Exécutions de la veille (une par mois en mode planifié)
CREATE TABLE watch_runs (
    id                   BIGSERIAL PRIMARY KEY,
    period               VARCHAR(7)   NOT NULL,
    trigger_type         VARCHAR(20)  NOT NULL CHECK (trigger_type IN ('SCHEDULED', 'MANUAL')),
    status               VARCHAR(20)  NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    started_by           VARCHAR(50)  NOT NULL,
    started_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    finished_at          TIMESTAMP WITH TIME ZONE,
    products_scanned     INTEGER      NOT NULL DEFAULT 0,
    suppliers_discovered INTEGER      NOT NULL DEFAULT 0,
    leads_found          INTEGER      NOT NULL DEFAULT 0,
    rfqs_drafted         INTEGER      NOT NULL DEFAULT 0,
    recommendations      INTEGER      NOT NULL DEFAULT 0,
    error_message        VARCHAR(1000)
);
CREATE INDEX ix_watch_runs_period ON watch_runs (period, status);
-- Une seule veille en cours à la fois, même avec plusieurs réplicas du service
CREATE UNIQUE INDEX ux_watch_runs_running ON watch_runs (status) WHERE status = 'RUNNING';

-- Opportunités détectées par la veille : un fournisseur propose l'article ou un équivalent
CREATE TABLE sourcing_leads (
    id                 BIGSERIAL PRIMARY KEY,
    product_id         BIGINT        NOT NULL,
    market_supplier_id BIGINT        NOT NULL REFERENCES market_suppliers (id),
    offered_name       VARCHAR(255)  NOT NULL,
    offered_reference  VARCHAR(100),
    url                VARCHAR(500),
    published_price    NUMERIC(14, 3),
    currency           VARCHAR(3),
    equivalence        VARCHAR(20)   NOT NULL CHECK (equivalence IN ('IDENTICAL', 'EQUIVALENT')),
    similarity         INTEGER       NOT NULL CHECK (similarity BETWEEN 0 AND 100),
    rationale          VARCHAR(1000),
    watch_run_id       BIGINT REFERENCES watch_runs (id),
    detected_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX ix_leads_product ON sourcing_leads (product_id, detected_at DESC);

-- Demandes de prix (RFQ)
CREATE TABLE rfqs (
    id                 BIGSERIAL PRIMARY KEY,
    reference          VARCHAR(30)   NOT NULL UNIQUE,
    product_id         BIGINT        NOT NULL,
    product_reference  VARCHAR(50)   NOT NULL,
    product_name       VARCHAR(150)  NOT NULL,
    market_supplier_id BIGINT        NOT NULL REFERENCES market_suppliers (id),
    supplier_name      VARCHAR(150)  NOT NULL,
    supplier_email     VARCHAR(150),
    quantity           INTEGER       NOT NULL CHECK (quantity > 0),
    subject            VARCHAR(255)  NOT NULL,
    body               VARCHAR(8000) NOT NULL,
    status             VARCHAR(20)   NOT NULL
        CHECK (status IN ('DRAFT', 'APPROVED', 'SENT', 'ANSWERED', 'FAILED', 'CANCELLED')),
    response_due_date  DATE,
    created_by         VARCHAR(50)   NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    approved_by        VARCHAR(50),
    approved_at        TIMESTAMP WITH TIME ZONE,
    sent_at            TIMESTAMP WITH TIME ZONE,
    failure_reason     VARCHAR(500)
);
CREATE INDEX ix_rfqs_status ON rfqs (status, created_at DESC);
CREATE INDEX ix_rfqs_product_supplier ON rfqs (product_id, market_supplier_id);

-- Devis et offres collectés (réponse à une RFQ, saisie manuelle, extraction IA, prix publiés)
CREATE TABLE quotes (
    id                 BIGSERIAL PRIMARY KEY,
    rfq_id             BIGINT REFERENCES rfqs (id),
    product_id         BIGINT         NOT NULL,
    market_supplier_id BIGINT         NOT NULL REFERENCES market_suppliers (id),
    supplier_name      VARCHAR(150)   NOT NULL,
    offered_name       VARCHAR(255)   NOT NULL,
    equivalence        VARCHAR(20)    NOT NULL CHECK (equivalence IN ('IDENTICAL', 'EQUIVALENT')),
    unit_price         NUMERIC(14, 3) NOT NULL CHECK (unit_price > 0),
    currency           VARCHAR(3)     NOT NULL,
    min_quantity       INTEGER        NOT NULL CHECK (min_quantity >= 1),
    availability       VARCHAR(20)    NOT NULL CHECK (availability IN ('IN_STOCK', 'ON_ORDER', 'UNAVAILABLE')),
    lead_time_days     INTEGER CHECK (lead_time_days >= 0),
    payment_terms      VARCHAR(255),
    valid_until        DATE,
    quality_score      INTEGER        NOT NULL CHECK (quality_score BETWEEN 1 AND 5),
    source             VARCHAR(20)    NOT NULL CHECK (source IN ('RFQ_REPLY', 'MANUAL', 'WEB', 'AI_EXTRACTED')),
    received_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX ix_quotes_product ON quotes (product_id, received_at DESC);

-- Historique des prix (achats réels, devis, prix publiés, prix du catalogue)
CREATE TABLE price_points (
    id            BIGSERIAL PRIMARY KEY,
    product_id    BIGINT         NOT NULL,
    unit_price    NUMERIC(14, 3) NOT NULL CHECK (unit_price > 0),
    observed_on   DATE           NOT NULL,
    source        VARCHAR(20)    NOT NULL CHECK (source IN ('PURCHASE', 'QUOTE', 'MARKET', 'CATALOG')),
    supplier_name VARCHAR(150),
    quantity      INTEGER CHECK (quantity > 0),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX ix_price_points_product ON price_points (product_id, observed_on);

-- Dernière prédiction de prix par article
CREATE TABLE price_predictions (
    id                BIGSERIAL PRIMARY KEY,
    product_id        BIGINT         NOT NULL UNIQUE,
    product_reference VARCHAR(50)    NOT NULL,
    product_name      VARCHAR(150)   NOT NULL,
    generated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    current_price     NUMERIC(14, 3) NOT NULL,
    predicted_price   NUMERIC(14, 3) NOT NULL,
    change_pct        NUMERIC(7, 2)  NOT NULL,
    horizon_days      INTEGER        NOT NULL,
    confidence        NUMERIC(4, 2)  NOT NULL CHECK (confidence BETWEEN 0 AND 1),
    confidence_level  VARCHAR(10)    NOT NULL CHECK (confidence_level IN ('HIGH', 'MEDIUM', 'LOW')),
    monthly_trend_pct NUMERIC(7, 2)  NOT NULL,
    seasonality_pct   NUMERIC(7, 2)  NOT NULL,
    inflation_pct     NUMERIC(7, 2)  NOT NULL,
    market_signal_pct NUMERIC(7, 2),
    sample_size       INTEGER        NOT NULL,
    rationale         VARCHAR(2000)
);

-- Recommandations d'achat : achat anticipé ou changement de fournisseur
CREATE TABLE purchase_recommendations (
    id                   BIGSERIAL PRIMARY KEY,
    type                 VARCHAR(30)    NOT NULL CHECK (type IN ('ANTICIPATED_PURCHASE', 'SWITCH_SUPPLIER')),
    status               VARCHAR(20)    NOT NULL CHECK (status IN ('OPEN', 'ACCEPTED', 'DISMISSED', 'EXPIRED')),
    product_id           BIGINT         NOT NULL,
    product_reference    VARCHAR(50)    NOT NULL,
    product_name         VARCHAR(150)   NOT NULL,
    supplier_name        VARCHAR(150),
    quote_id             BIGINT REFERENCES quotes (id),
    quantity             INTEGER        NOT NULL CHECK (quantity > 0),
    reference_unit_price NUMERIC(14, 3) NOT NULL,
    recommended_unit_price NUMERIC(14, 3) NOT NULL,
    expected_future_price NUMERIC(14, 3),
    cost_now             NUMERIC(16, 3) NOT NULL,
    cost_later           NUMERIC(16, 3) NOT NULL,
    holding_cost         NUMERIC(16, 3) NOT NULL,
    net_savings          NUMERIC(16, 3) NOT NULL,
    buy_before           DATE,
    confidence           NUMERIC(4, 2),
    rationale            VARCHAR(2000),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    decided_by           VARCHAR(50),
    decided_at           TIMESTAMP WITH TIME ZONE,
    notified_at          TIMESTAMP WITH TIME ZONE
);
CREATE INDEX ix_reco_status ON purchase_recommendations (status, created_at DESC);
CREATE INDEX ix_reco_product ON purchase_recommendations (product_id, type, status);
