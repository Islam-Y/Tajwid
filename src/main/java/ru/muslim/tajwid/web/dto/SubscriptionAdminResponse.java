package ru.muslim.tajwid.web.dto;

public record SubscriptionAdminResponse(
    Long userId,
    boolean schoolSubscribed,
    boolean courseSubscribed
) {
}
