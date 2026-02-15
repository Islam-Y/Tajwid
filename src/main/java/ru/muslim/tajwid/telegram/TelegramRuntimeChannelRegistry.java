package ru.muslim.tajwid.telegram;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "tajwid.telegram", name = "enabled", havingValue = "true")
public class TelegramRuntimeChannelRegistry {

    private static final Logger log = LoggerFactory.getLogger(TelegramRuntimeChannelRegistry.class);

    private final AtomicReference<String> detectedCourseChannelId = new AtomicReference<>();

    public Optional<String> getDetectedCourseChannelId() {
        return Optional.ofNullable(detectedCourseChannelId.get());
    }

    public void rememberCourseChannelId(String chatId, String sourceField, String title, String username) {
        if (!StringUtils.hasText(chatId)) {
            return;
        }

        String normalizedChatId = chatId.trim();
        String previous = detectedCourseChannelId.getAndSet(normalizedChatId);

        if (normalizedChatId.equals(previous)) {
            return;
        }

        log.info(
            "Runtime course channel id updated from {}: id={}, title='{}', username='{}'",
            sourceField,
            normalizedChatId,
            title,
            username
        );
    }
}
