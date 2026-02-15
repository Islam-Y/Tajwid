package ru.muslim.tajwid.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BotUpdateRequest(
    @NotNull Long userId,
    @NotBlank String firstName,
    String text,
    String callbackData,
    @Valid BotContactPayload contact
) {
}
