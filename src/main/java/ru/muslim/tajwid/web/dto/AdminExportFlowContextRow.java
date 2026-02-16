package ru.muslim.tajwid.web.dto;

import java.time.Instant;
import ru.muslim.tajwid.domain.FlowStep;
import ru.muslim.tajwid.domain.FlowType;
import ru.muslim.tajwid.domain.ReadingLevel;

public record AdminExportFlowContextRow(
    Long userId,
    String telegramFirstName,
    FlowStep currentStep,
    FlowType flowType,
    String userName,
    Integer age,
    Integer childrenCount,
    String childrenAges,
    Boolean childrenStudyQuran,
    Integer childrenAgeIndex,
    String phone,
    ReadingLevel readingLevel,
    boolean consentGiven,
    boolean schoolChannelSubscribed,
    long referrerUserId,
    String referralEntrySource,
    Instant referralEntryAt,
    String tempTags,
    Instant createdAt,
    Instant updatedAt
) {
}
