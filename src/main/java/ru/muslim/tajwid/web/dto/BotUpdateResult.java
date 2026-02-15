package ru.muslim.tajwid.web.dto;

import java.util.List;

public record BotUpdateResult(
    List<BotMessageResponse> messages
) {
}
