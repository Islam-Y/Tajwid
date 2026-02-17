ALTER TABLE users
  DROP COLUMN IF EXISTS telegram_username,
  DROP COLUMN IF EXISTS has_children;
