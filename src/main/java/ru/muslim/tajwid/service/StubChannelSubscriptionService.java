package ru.muslim.tajwid.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "tajwid.telegram", name = "enabled", havingValue = "false", matchIfMissing = true)
public class StubChannelSubscriptionService implements ChannelSubscriptionService {

    private final Map<Long, SubscriptionState> stateByUserId = new ConcurrentHashMap<>();

    @Override
    public SubscriptionCheckResult checkSchoolChannelSubscription(long userId) {
        SubscriptionState state = stateByUserId.get(userId);
        if (state == null) {
            return SubscriptionCheckResult.NOT_SUBSCRIBED;
        }
        return state.schoolSubscribed ? SubscriptionCheckResult.SUBSCRIBED : SubscriptionCheckResult.NOT_SUBSCRIBED;
    }

    @Override
    public SubscriptionCheckResult checkCourseChannelSubscription(long userId) {
        SubscriptionState state = stateByUserId.get(userId);
        if (state == null) {
            return SubscriptionCheckResult.NOT_SUBSCRIBED;
        }
        return state.courseSubscribed ? SubscriptionCheckResult.SUBSCRIBED : SubscriptionCheckResult.NOT_SUBSCRIBED;
    }

    public void setSubscriptionState(long userId, boolean schoolSubscribed, boolean courseSubscribed) {
        stateByUserId.put(userId, new SubscriptionState(schoolSubscribed, courseSubscribed));
    }

    public SubscriptionState getSubscriptionState(long userId) {
        return stateByUserId.getOrDefault(userId, new SubscriptionState(false, false));
    }

    public record SubscriptionState(boolean schoolSubscribed, boolean courseSubscribed) {
    }
}
