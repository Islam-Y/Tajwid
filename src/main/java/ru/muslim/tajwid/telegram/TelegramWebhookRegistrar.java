package ru.muslim.tajwid.telegram;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import ru.muslim.tajwid.config.TajwidBotProperties;

@Component
@ConditionalOnProperty(prefix = "tajwid.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class TelegramWebhookRegistrar {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookRegistrar.class);

    private final TelegramApiClient telegramApiClient;
    private final TajwidBotProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void registerWebhook() {
        String webhookUrl = properties.getTelegram().getWebhookUrl();
        if (!StringUtils.hasText(webhookUrl)) {
            log.info("Telegram webhook-url is empty; webhook auto-registration is skipped");
            return;
        }

        String normalized = webhookUrl.trim();
        if (!normalized.startsWith("https://")) {
            throw new IllegalStateException("tajwid.telegram.webhook-url must start with https://");
        }

        telegramApiClient.setWebhook(normalized, properties.getTelegram().getWebhookSecret());
        log.info("Telegram webhook registered: {}", normalized);
    }
}
