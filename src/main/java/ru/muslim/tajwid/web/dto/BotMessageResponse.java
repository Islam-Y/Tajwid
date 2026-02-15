package ru.muslim.tajwid.web.dto;

import java.util.List;

public record BotMessageResponse(
    Long recipientUserId,
    String text,
    List<BotButtonResponse> buttons
) {
}
