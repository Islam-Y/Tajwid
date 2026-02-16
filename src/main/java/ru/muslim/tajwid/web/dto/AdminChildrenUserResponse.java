package ru.muslim.tajwid.web.dto;

import java.time.Instant;
import java.util.List;
import ru.muslim.tajwid.domain.ReadingLevel;

public record AdminChildrenUserResponse(
    Long userId,
    String telegramFirstName,
    String userName,
    Integer age,
    Integer childrenCount,
    List<Integer> childrenAges,
    Boolean childrenStudyQuran,
    String phone,
    ReadingLevel readingLevel,
    boolean registrationCompleted,
    Instant createdAt,
    Instant updatedAt
) {
}
