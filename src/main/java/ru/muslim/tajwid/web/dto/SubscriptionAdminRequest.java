package ru.muslim.tajwid.web.dto;

public record SubscriptionAdminRequest(
    boolean schoolSubscribed,
    boolean courseSubscribed
) {
}
