package ru.muslim.tajwid.telegram;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import ru.muslim.tajwid.config.TajwidBotProperties;
import ru.muslim.tajwid.service.ChannelSubscriptionService;
import ru.muslim.tajwid.service.SubscriptionCheckResult;

@Service
@ConditionalOnProperty(prefix = "tajwid.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class TelegramChannelSubscriptionService implements ChannelSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(TelegramChannelSubscriptionService.class);

    private static final Set<String> SUBSCRIBED_STATUSES =
        Set.of("member", "administrator", "creator");

    private final TelegramApiClient telegramApiClient;
    private final TajwidBotProperties properties;
    private final TelegramRuntimeChannelRegistry channelRegistry;

    @Override
    public SubscriptionCheckResult checkSchoolChannelSubscription(long userId) {
        return checkChannelMembership(properties.getTelegram().getSchoolChannelId(), userId);
    }

    @Override
    public SubscriptionCheckResult checkCourseChannelSubscription(long userId) {
        String configuredChatId = properties.getTelegram().getCourseChannelId();
        if (StringUtils.hasText(configuredChatId)) {
            return checkChannelMembership(configuredChatId, userId);
        }

        String runtimeChatId = channelRegistry.getDetectedCourseChannelId().orElse(null);
        return checkChannelMembership(runtimeChatId, userId);
    }

    private SubscriptionCheckResult checkChannelMembership(String chatId, long userId) {
        if (!StringUtils.hasText(chatId)) {
            log.warn("Telegram chat id is not configured for membership check");
            return SubscriptionCheckResult.ERROR;
        }

        try {
            String status = telegramApiClient.getChatMemberStatus(chatId, userId);
            return SUBSCRIBED_STATUSES.contains(status)
                ? SubscriptionCheckResult.SUBSCRIBED
                : SubscriptionCheckResult.NOT_SUBSCRIBED;
        } catch (RuntimeException ex) {
            log.warn("Failed to check Telegram membership for user {} in chat {}", userId, chatId, ex);
            return SubscriptionCheckResult.ERROR;
        }
    }
}
