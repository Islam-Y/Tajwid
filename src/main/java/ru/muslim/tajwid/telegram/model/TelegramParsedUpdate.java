package ru.muslim.tajwid.telegram.model;

import lombok.Data;

@Data
public class TelegramParsedUpdate {

    private Long userId;
    private String firstName;
    private String text;
    private String callbackData;
    private TelegramParsedContact contact;
}
