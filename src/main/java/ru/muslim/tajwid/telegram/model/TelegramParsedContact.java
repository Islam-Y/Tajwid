package ru.muslim.tajwid.telegram.model;

import lombok.Data;

@Data
public class TelegramParsedContact {

    private Long userId;
    private String phoneNumber;
}
