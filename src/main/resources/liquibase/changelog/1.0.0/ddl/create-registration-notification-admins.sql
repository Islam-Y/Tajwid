CREATE TABLE IF NOT EXISTS registration_notification_admins (
  id BIGSERIAL PRIMARY KEY,
  telegram_username VARCHAR(255) NOT NULL UNIQUE,
  telegram_user_id BIGINT,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_registration_notification_admins_user_id
  ON registration_notification_admins (telegram_user_id);
