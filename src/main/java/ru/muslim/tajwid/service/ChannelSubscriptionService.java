package ru.muslim.tajwid.service;

public interface ChannelSubscriptionService {

    SubscriptionCheckResult checkSchoolChannelSubscription(long userId);

    SubscriptionCheckResult checkCourseChannelSubscription(long userId);
}
