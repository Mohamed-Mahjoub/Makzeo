-- Prix à 3 décimales : compatibilité avec les devises à millièmes (dinar tunisien, dinar libyen...)
ALTER TABLE products ALTER COLUMN purchase_price TYPE NUMERIC(14, 3);
ALTER TABLE products ALTER COLUMN sale_price TYPE NUMERIC(14, 3);
