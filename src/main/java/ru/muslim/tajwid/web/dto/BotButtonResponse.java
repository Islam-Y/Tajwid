package ru.muslim.tajwid.web.dto;

public record BotButtonResponse(
    String text,
    ButtonType type,
    String value
) {
}
