ALTER TABLE flow_contexts
  DROP COLUMN IF EXISTS referral_announcement_sent,
  DROP COLUMN IF EXISTS referral_announcement_due_at,
  DROP COLUMN IF EXISTS telegram_username,
  DROP COLUMN IF EXISTS has_children;
