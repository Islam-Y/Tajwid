package ru.muslim.tajwid.web.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminExportReferralLinkUsageRow(
    UUID referralEventId,
    String referralLink,
    Long referrerUserId,
    Long inviteeUserId,
    String idempotencyKey,
    boolean alreadyCounted,
    Instant countedAt,
    String triggerSource,
    Instant triggeredAt,
    Instant createdAt
) {
}
