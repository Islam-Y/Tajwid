ALTER TABLE flow_contexts
  ADD COLUMN IF NOT EXISTS has_children BOOLEAN,
  ADD COLUMN IF NOT EXISTS telegram_username VARCHAR(255),
  ADD COLUMN IF NOT EXISTS referral_announcement_due_at TIMESTAMP,
  ADD COLUMN IF NOT EXISTS referral_announcement_sent BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE flow_contexts
SET has_children = CASE
    WHEN children_count IS NULL THEN NULL
    WHEN children_count > 0 THEN TRUE
    ELSE FALSE
END
WHERE has_children IS NULL;
