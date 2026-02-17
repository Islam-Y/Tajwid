package ru.muslim.tajwid.web.dto;

import ru.muslim.tajwid.domain.ReadingLevel;

public record PublicChildrenSelfResponse(
    Long userId,
    Boolean hasChildren,
    Boolean childrenStudyQuran,
    ReadingLevel readingLevel
) {
}
