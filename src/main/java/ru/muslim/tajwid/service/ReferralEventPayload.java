package ru.muslim.tajwid.service;

import java.time.Instant;
import java.util.UUID;

public record ReferralEventPayload(
    UUID referralEventId,
    Long referrerUserId,
    Long inviteeUserId,
    String idempotencyKey,
    String triggerSource,
    Instant triggeredAt,
    String referralLink
) {
}
