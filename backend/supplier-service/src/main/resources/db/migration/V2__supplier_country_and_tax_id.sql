-- Fournisseurs de tous pays : code pays ISO 3166-1 alpha-2 et identifiant fiscal générique
-- (ICE au Maroc, matricule fiscal en Tunisie, n° TVA dans l'Union européenne, EIN aux États-Unis...)
ALTER TABLE suppliers RENAME COLUMN ice TO tax_id;
ALTER TABLE suppliers ALTER COLUMN tax_id TYPE VARCHAR(30);
ALTER TABLE suppliers DROP CONSTRAINT IF EXISTS suppliers_ice_key;

UPDATE suppliers SET country = CASE
    WHEN country IS NULL OR btrim(country) = '' THEN NULL
    WHEN upper(btrim(country)) IN ('MAROC', 'MOROCCO') THEN 'MA'
    WHEN upper(btrim(country)) IN ('TUNISIE', 'TUNISIA') THEN 'TN'
    WHEN upper(btrim(country)) IN ('ALGERIE', 'ALGÉRIE', 'ALGERIA') THEN 'DZ'
    WHEN upper(btrim(country)) = 'FRANCE' THEN 'FR'
    WHEN length(btrim(country)) = 2 THEN upper(btrim(country))
    ELSE NULL
END;
ALTER TABLE suppliers ALTER COLUMN country TYPE VARCHAR(2);

-- L'identifiant fiscal est unique au sein d'un même pays
CREATE UNIQUE INDEX ux_suppliers_country_tax_id ON suppliers (country, tax_id) WHERE tax_id IS NOT NULL;
CREATE INDEX ix_suppliers_country ON suppliers (country);
