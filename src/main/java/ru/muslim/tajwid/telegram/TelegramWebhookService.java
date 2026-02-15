package ru.muslim.tajwid.telegram;

import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import ru.muslim.tajwid.config.TajwidBotProperties;
import ru.muslim.tajwid.service.BotFlowService;
import ru.muslim.tajwid.web.dto.BotUpdateRequest;
import ru.muslim.tajwid.web.dto.BotUpdateResult;
import tools.jackson.databind.JsonNode;

@Service
@ConditionalOnProperty(prefix = "tajwid.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class TelegramWebhookService {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookService.class);
    private static final Set<String> SUBSCRIBED_STATUSES = Set.of("member", "administrator", "creator");

    private final TelegramUpdateMapper telegramUpdateMapper;
    private final BotFlowService botFlowService;
    private final TelegramApiClient telegramApiClient;
    private final TelegramRuntimeChannelRegistry channelRegistry;
    private final TajwidBotProperties properties;

    public void processUpdate(JsonNode telegramUpdate) {
        logDetectedChannelChatId(telegramUpdate, "channel_post");
        logDetectedChannelChatId(telegramUpdate, "my_chat_member");
        logDetectedChannelChatId(telegramUpdate, "chat_member");

        processCourseChannelMembershipUpdate(telegramUpdate);

        Optional<BotUpdateRequest> maybeRequest = telegramUpdateMapper.map(telegramUpdate);
        if (maybeRequest.isEmpty()) {
            return;
        }

        BotUpdateResult result = botFlowService.handleUpdate(maybeRequest.get());
        sendMessages(result);
    }

    private void processCourseChannelMembershipUpdate(JsonNode update) {
        JsonNode chatMember = update.path("chat_member");
        if (!chatMember.isObject()) {
            return;
        }

        JsonNode chat = chatMember.path("chat");
        if (!chat.isObject() || !"channel".equals(chat.path("type").asText())) {
            return;
        }

        Long chatId = parseLong(chat.path("id"));
        if (chatId == null) {
            return;
        }

        String chatIdAsString = String.valueOf(chatId);
        if (!isCourseChannelChat(chatIdAsString)) {
            return;
        }

        JsonNode newChatMember = chatMember.path("new_chat_member");
        Long memberUserId = parseLong(newChatMember.path("user").path("id"));
        String newStatus = textOrFallback(newChatMember.path("status"), "");
        if (memberUserId == null || !SUBSCRIBED_STATUSES.contains(newStatus)) {
            return;
        }

        BotUpdateResult autoResult = botFlowService.handleCourseChannelSubscriptionConfirmed(memberUserId);
        sendMessages(autoResult);
    }

    private boolean isCourseChannelChat(String chatId) {
        String configuredCourseId = normalize(properties.getTelegram().getCourseChannelId());
        if (StringUtils.hasText(configuredCourseId)) {
            return configuredCourseId.equals(chatId);
        }

        String runtimeCourseId = channelRegistry.getDetectedCourseChannelId().orElse("");
        return StringUtils.hasText(runtimeCourseId) && runtimeCourseId.equals(chatId);
    }

    private void sendMessages(BotUpdateResult result) {
        if (result == null || result.messages() == null || result.messages().isEmpty()) {
            return;
        }

        result.messages().forEach(message -> {
            try {
                telegramApiClient.sendMessage(message);
            } catch (RuntimeException ex) {
                log.error("Failed to deliver Telegram message to {}", message.recipientUserId(), ex);
            }
        });
    }

    private void logDetectedChannelChatId(JsonNode update, String sourceField) {
        JsonNode sourceNode = update.path(sourceField);
        if (!sourceNode.isObject()) {
            return;
        }

        JsonNode chat = sourceNode.path("chat");
        if (!chat.isObject()) {
            return;
        }

        if (!"channel".equals(chat.path("type").asText())) {
            return;
        }

        Long chatId = parseLong(chat.path("id"));
        if (chatId == null) {
            return;
        }

        String title = textOrFallback(chat.path("title"), "unknown");
        String username = textOrFallback(chat.path("username"), "none");
        String detectedChatId = String.valueOf(chatId);

        log.info(
            "Detected Telegram channel from {}: id={}, title='{}', username='{}'. "
                + "Put this id into TAJWID_TELEGRAM_COURSE_CHANNEL_ID if this is the course channel",
            sourceField,
            chatId,
            title,
            username
        );

        maybeRememberRuntimeCourseChannelId(detectedChatId, sourceField, title, username);
    }

    private void maybeRememberRuntimeCourseChannelId(String detectedChatId,
                                                     String sourceField,
                                                     String title,
                                                     String username) {
        String configuredCourseChatId = normalize(properties.getTelegram().getCourseChannelId());
        if (StringUtils.hasText(configuredCourseChatId)) {
            return;
        }

        String configuredSchoolChatId = normalize(properties.getTelegram().getSchoolChannelId());
        if (StringUtils.hasText(configuredSchoolChatId) && configuredSchoolChatId.equals(detectedChatId)) {
            return;
        }

        channelRegistry.rememberCourseChannelId(detectedChatId, sourceField, title, username);
    }

    private Long parseLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        if (node.isNumber()) {
            return node.longValue();
        }

        if (node.isTextual()) {
            try {
                return Long.parseLong(node.textValue());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String textOrFallback(JsonNode node, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }

        String value = node.asText();
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
