package ru.muslim.tajwid.telegram;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import ru.muslim.tajwid.config.TajwidBotProperties;
import ru.muslim.tajwid.web.dto.BotButtonResponse;
import ru.muslim.tajwid.web.dto.BotMessageResponse;
import ru.muslim.tajwid.web.dto.ButtonType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

@Component
@ConditionalOnProperty(prefix = "tajwid.telegram", name = "enabled", havingValue = "true")
public class TelegramApiClientImpl implements TelegramApiClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramApiClientImpl.class);

    private final RestClient restClient;
    private final TajwidBotProperties properties;

    public TelegramApiClientImpl(RestClient.Builder restClientBuilder,
                                 TajwidBotProperties properties) {
        this.restClient = restClientBuilder
            .baseUrl(properties.getTelegram().getApiBaseUrl())
            .build();
        this.properties = properties;
    }

    @Override
    public void sendMessage(BotMessageResponse message) {
        if (message == null || message.recipientUserId() == null || !StringUtils.hasText(message.text())) {
            return;
        }

        String token = botToken();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_id", message.recipientUserId());
        payload.put("text", message.text());

        Map<String, Object> replyMarkup = buildReplyMarkup(message.buttons());
        if (replyMarkup != null) {
            payload.put("reply_markup", replyMarkup);
        }

        TelegramApiEnvelope response = restClient.post()
            .uri("/bot{token}/sendMessage", token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .body(TelegramApiEnvelope.class);

        if (response == null || !Boolean.TRUE.equals(response.ok())) {
            throw new IllegalStateException("Telegram sendMessage failed: "
                + (response == null ? "empty response" : response.description()));
        }
    }

    @Override
    public String getChatMemberStatus(String chatId, long userId) {
        String token = botToken();

        TelegramGetChatMemberEnvelope response = restClient.get()
            .uri("/bot{token}/getChatMember?chat_id={chatId}&user_id={userId}", token, chatId, userId)
            .retrieve()
            .body(TelegramGetChatMemberEnvelope.class);

        if (response == null || !Boolean.TRUE.equals(response.ok()) || response.result() == null) {
            log.warn("Telegram getChatMember failed for user {} in chat {}. Description={}",
                userId, chatId, response == null ? "empty response" : response.description());
            throw new IllegalStateException("Telegram getChatMember failed");
        }

        return response.result().status();
    }

    @Override
    public void setWebhook(String webhookUrl, String secretToken) {
        String token = botToken();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("url", webhookUrl);
        if (StringUtils.hasText(secretToken)) {
            payload.put("secret_token", secretToken);
        }
        payload.put("allowed_updates",
            List.of("message", "callback_query", "channel_post", "my_chat_member", "chat_member"));

        TelegramApiEnvelope response = restClient.post()
            .uri("/bot{token}/setWebhook", token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .body(TelegramApiEnvelope.class);

        if (response == null || !Boolean.TRUE.equals(response.ok())) {
            throw new IllegalStateException("Telegram setWebhook failed: "
                + (response == null ? "empty response" : response.description()));
        }
    }

    private String botToken() {
        String token = properties.getTelegram().getBotToken();
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("tajwid.telegram.bot-token is empty");
        }
        return token;
    }

    private Map<String, Object> buildReplyMarkup(List<BotButtonResponse> buttons) {
        if (buttons == null || buttons.isEmpty()) {
            return null;
        }

        boolean containsContactButton = buttons.stream()
            .anyMatch(button -> button.type() == ButtonType.REQUEST_CONTACT);

        if (containsContactButton) {
            List<Map<String, Object>> row = new ArrayList<>();
            for (BotButtonResponse button : buttons) {
                if (button.type() != ButtonType.REQUEST_CONTACT) {
                    continue;
                }
                Map<String, Object> buttonMap = new LinkedHashMap<>();
                buttonMap.put("text", button.text());
                buttonMap.put("request_contact", true);
                row.add(buttonMap);
            }

            if (row.isEmpty()) {
                return null;
            }

            Map<String, Object> markup = new LinkedHashMap<>();
            markup.put("keyboard", List.of(row));
            markup.put("resize_keyboard", true);
            markup.put("one_time_keyboard", true);
            return markup;
        }

        List<Map<String, Object>> row = new ArrayList<>();
        for (BotButtonResponse button : buttons) {
            Map<String, Object> buttonMap = new LinkedHashMap<>();
            buttonMap.put("text", button.text());

            if (button.type() == ButtonType.CALLBACK) {
                buttonMap.put("callback_data", button.value());
                row.add(buttonMap);
                continue;
            }

            if (button.type() == ButtonType.URL) {
                buttonMap.put("url", button.value());
                row.add(buttonMap);
            }
        }

        if (row.isEmpty()) {
            return null;
        }

        Map<String, Object> markup = new LinkedHashMap<>();
        markup.put("inline_keyboard", List.of(row));
        return markup;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TelegramApiEnvelope(Boolean ok, JsonNode result, String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TelegramGetChatMemberEnvelope(Boolean ok, TelegramChatMember result, String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TelegramChatMember(String status) {
    }
}
