-- Paramètres globaux de l'application (ligne unique)
CREATE TABLE app_settings (
    id         BIGINT PRIMARY KEY CHECK (id = 1),
    currency   VARCHAR(3)   NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_by VARCHAR(50)
);

INSERT INTO app_settings (id, currency, updated_by) VALUES (1, 'MAD', 'system');
