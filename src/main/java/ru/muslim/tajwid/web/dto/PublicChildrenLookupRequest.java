package ru.muslim.tajwid.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PublicChildrenLookupRequest(
    @NotNull Long userId,
    @NotBlank String phone
) {
}
