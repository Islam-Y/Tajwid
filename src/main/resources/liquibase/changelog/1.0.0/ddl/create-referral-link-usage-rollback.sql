ALTER TABLE IF EXISTS referral_link_usage DROP CONSTRAINT IF EXISTS fk_referral_usage_invitee;
ALTER TABLE IF EXISTS referral_link_usage DROP CONSTRAINT IF EXISTS fk_referral_usage_referrer;
ALTER TABLE IF EXISTS referral_link_usage DROP CONSTRAINT IF EXISTS uq_referral_link_usage_referrer_invitee;
ALTER TABLE IF EXISTS referral_link_usage DROP CONSTRAINT IF EXISTS uq_referral_link_usage_idempotency;
DROP TABLE IF EXISTS referral_link_usage;
