CREATE TABLE referral_link_usage (
  referral_event_id UUID PRIMARY KEY,
  referral_link VARCHAR(512),
  referrer_user_id BIGINT NOT NULL,
  invitee_user_id BIGINT NOT NULL,
  idempotency_key VARCHAR(255) NOT NULL,
  is_already_counted BOOLEAN NOT NULL DEFAULT FALSE,
  counted_at TIMESTAMP NOT NULL,
  trigger_source VARCHAR(128) NOT NULL,
  triggered_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE referral_link_usage
  ADD CONSTRAINT uq_referral_link_usage_idempotency UNIQUE (idempotency_key);

ALTER TABLE referral_link_usage
  ADD CONSTRAINT uq_referral_link_usage_referrer_invitee UNIQUE (referrer_user_id, invitee_user_id);

ALTER TABLE referral_link_usage
  ADD CONSTRAINT fk_referral_usage_referrer FOREIGN KEY (referrer_user_id)
  REFERENCES users(user_id)
  ON DELETE RESTRICT;

ALTER TABLE referral_link_usage
  ADD CONSTRAINT fk_referral_usage_invitee FOREIGN KEY (invitee_user_id)
  REFERENCES users(user_id)
  ON DELETE RESTRICT;
