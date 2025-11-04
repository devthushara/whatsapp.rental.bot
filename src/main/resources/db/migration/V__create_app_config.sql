-- Create app_config key/value table for runtime configuration
CREATE TABLE IF NOT EXISTS app_config (
  key_text   text PRIMARY KEY,
  value_text text NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- ensure default currency is USD
INSERT INTO app_config (key_text, value_text)
VALUES ('default_currency', 'USD')
ON CONFLICT (key_text) DO UPDATE SET value_text = EXCLUDED.value_text, updated_at = now();

