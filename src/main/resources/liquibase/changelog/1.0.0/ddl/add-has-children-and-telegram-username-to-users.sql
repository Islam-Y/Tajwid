ALTER TABLE users
  ADD COLUMN IF NOT EXISTS has_children BOOLEAN,
  ADD COLUMN IF NOT EXISTS telegram_username VARCHAR(255);

UPDATE users
SET has_children = CASE
    WHEN children_count IS NULL THEN NULL
    WHEN children_count > 0 THEN TRUE
    ELSE FALSE
END
WHERE has_children IS NULL;
