package ru.muslim.tajwid.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import ru.muslim.tajwid.service.StubChannelSubscriptionService;
import ru.muslim.tajwid.web.dto.SubscriptionAdminRequest;
import ru.muslim.tajwid.web.dto.SubscriptionAdminResponse;

@RestController
@RequestMapping("/api/admin/subscriptions")
@ConditionalOnBean(StubChannelSubscriptionService.class)
@RequiredArgsConstructor
public class SubscriptionAdminController {

    private final StubChannelSubscriptionService subscriptionService;

    @PutMapping("/{userId}")
    public SubscriptionAdminResponse update(@PathVariable Long userId,
                                            @RequestBody SubscriptionAdminRequest request) {
        subscriptionService.setSubscriptionState(userId, request.schoolSubscribed(), request.courseSubscribed());
        return new SubscriptionAdminResponse(userId, request.schoolSubscribed(), request.courseSubscribed());
    }

    @GetMapping("/{userId}")
    public SubscriptionAdminResponse get(@PathVariable Long userId) {
        StubChannelSubscriptionService.SubscriptionState state = subscriptionService.getSubscriptionState(userId);
        return new SubscriptionAdminResponse(userId, state.schoolSubscribed(), state.courseSubscribed());
    }
}
