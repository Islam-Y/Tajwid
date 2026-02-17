package ru.muslim.tajwid.web.dto;

import java.time.Instant;
import ru.muslim.tajwid.domain.ReadingLevel;

public record AdminChildrenUserResponse(
    Long userId,
    String telegramFirstName,
    String telegramUsername,
    String userName,
    Integer age,
    Boolean hasChildren,
    Boolean childrenStudyQuran,
    String phone,
    ReadingLevel readingLevel,
    boolean registrationCompleted,
    Instant createdAt,
    Instant updatedAt
) {
}
