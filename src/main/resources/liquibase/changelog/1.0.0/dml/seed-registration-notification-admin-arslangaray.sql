INSERT INTO registration_notification_admins (
  telegram_username,
  is_active
)
VALUES (
  'arslangaray',
  TRUE
)
ON CONFLICT (telegram_username) DO UPDATE
SET
  is_active = EXCLUDED.is_active,
  updated_at = CURRENT_TIMESTAMP;
