package ru.muslim.tajwid.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BotContactPayload(
    @NotNull Long userId,
    @NotBlank String phoneNumber
) {
}
