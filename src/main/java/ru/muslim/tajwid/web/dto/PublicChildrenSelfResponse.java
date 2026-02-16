package ru.muslim.tajwid.web.dto;

import java.util.List;
import ru.muslim.tajwid.domain.ReadingLevel;

public record PublicChildrenSelfResponse(
    Long userId,
    Integer childrenCount,
    List<Integer> childrenAges,
    Boolean childrenStudyQuran,
    ReadingLevel readingLevel
) {
}
