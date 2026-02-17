package ru.muslim.tajwid.web.dto;

import java.time.Instant;
import ru.muslim.tajwid.domain.ReadingLevel;
import ru.muslim.tajwid.domain.ReferralStatus;

public record AdminExportUserRow(
    Long userId,
    String telegramFirstName,
    String telegramUsername,
    String userName,
    Integer age,
    Boolean hasChildren,
    Integer childrenCount,
    String childrenAges,
    Boolean childrenStudyQuran,
    String phone,
    ReadingLevel readingLevel,
    boolean schoolChannelSubscribed,
    boolean courseChannelSubscribed,
    boolean consentGiven,
    boolean registrationCompleted,
    Instant registrationCompletedAt,
    long referrerUserId,
    ReferralStatus referralStatus,
    Instant referralCountedAt,
    int referralPoints,
    String referralLinkCp,
    Instant createdAt,
    Instant updatedAt
) {
}
