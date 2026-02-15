package ru.muslim.tajwid.web.dto;

import java.time.Instant;

public record AdminExportUserTagRow(
    Long userId,
    String tag,
    Instant createdAt
) {
}
