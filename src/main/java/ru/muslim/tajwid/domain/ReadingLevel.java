package ru.muslim.tajwid.domain;

import java.util.Arrays;

public enum ReadingLevel {
    START_FROM_ZERO("Начинаю с нуля"),
    READ_BY_SYLLABLES("Читаю по слогам"),
    KNOW_BASICS("Знаю основы"),
    READ_WITH_MISTAKES("Читаю с ошибками"),
    READ_CONFIDENTLY("Читаю уверенно");

    private final String label;

    ReadingLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ReadingLevel fromLabel(String label) {
        return Arrays.stream(values())
            .filter(value -> value.label.equals(label))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown reading level: " + label));
    }
}
