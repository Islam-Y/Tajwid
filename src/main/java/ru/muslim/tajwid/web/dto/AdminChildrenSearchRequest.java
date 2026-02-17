package ru.muslim.tajwid.web.dto;

import java.util.List;
import ru.muslim.tajwid.domain.ReadingLevel;

public record AdminChildrenSearchRequest(
    List<Long> userIds,
    Boolean hasChildren,
    Boolean childrenStudyQuran,
    List<ReadingLevel> readingLevels,
    Boolean registrationCompleted,
    Integer limit
) {
    public static AdminChildrenSearchRequest empty() {
        return new AdminChildrenSearchRequest(null, null, null, null, null, null);
    }
}
