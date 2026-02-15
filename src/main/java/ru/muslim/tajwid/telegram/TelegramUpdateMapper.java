package ru.muslim.tajwid.telegram;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import ru.muslim.tajwid.telegram.model.TelegramParsedContact;
import ru.muslim.tajwid.telegram.model.TelegramParsedUpdate;
import ru.muslim.tajwid.web.dto.BotUpdateRequest;
import tools.jackson.databind.JsonNode;

@Component
@ConditionalOnProperty(prefix = "tajwid.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class TelegramUpdateMapper {

    private final TelegramUpdateRequestMapper updateRequestMapper;

    public Optional<BotUpdateRequest> map(JsonNode update) {
        if (update == null || update.isMissingNode() || update.isNull()) {
            return Optional.empty();
        }

        JsonNode callbackQuery = update.path("callback_query");
        if (callbackQuery.isObject()) {
            return mapCallback(callbackQuery);
        }

        JsonNode message = update.path("message");
        if (message.isObject()) {
            return mapMessage(message);
        }

        return Optional.empty();
    }

    private Optional<BotUpdateRequest> mapMessage(JsonNode message) {
        JsonNode from = message.path("from");
        Long userId = parseLong(from.path("id"));
        if (userId == null) {
            return Optional.empty();
        }

        TelegramParsedUpdate parsedUpdate = new TelegramParsedUpdate();
        parsedUpdate.setUserId(userId);
        parsedUpdate.setFirstName(resolveFirstName(from));
        parsedUpdate.setText(textOrNull(message.path("text")));
        parsedUpdate.setContact(parseContact(message.path("contact")));

        return Optional.of(updateRequestMapper.toBotUpdateRequest(parsedUpdate));
    }

    private Optional<BotUpdateRequest> mapCallback(JsonNode callbackQuery) {
        JsonNode from = callbackQuery.path("from");
        Long userId = parseLong(from.path("id"));
        if (userId == null) {
            return Optional.empty();
        }

        String firstName = resolveFirstName(from);
        String callbackData = textOrNull(callbackQuery.path("data"));
        if (!StringUtils.hasText(callbackData)) {
            return Optional.empty();
        }

        TelegramParsedUpdate parsedUpdate = new TelegramParsedUpdate();
        parsedUpdate.setUserId(userId);
        parsedUpdate.setFirstName(firstName);
        parsedUpdate.setCallbackData(callbackData);

        return Optional.of(updateRequestMapper.toBotUpdateRequest(parsedUpdate));
    }

    private TelegramParsedContact parseContact(JsonNode contactNode) {
        if (contactNode == null || !contactNode.isObject()) {
            return null;
        }

        Long userId = parseLong(contactNode.path("user_id"));
        String phone = textOrNull(contactNode.path("phone_number"));

        if (userId == null || !StringUtils.hasText(phone)) {
            return null;
        }

        TelegramParsedContact contact = new TelegramParsedContact();
        contact.setUserId(userId);
        contact.setPhoneNumber(phone);
        return contact;
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

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        String value = node.asText();
        return StringUtils.hasText(value) ? value : null;
    }

    private String resolveFirstName(JsonNode from) {
        String firstName = textOrNull(from.path("first_name"));
        if (StringUtils.hasText(firstName)) {
            return firstName;
        }

        String username = textOrNull(from.path("username"));
        if (StringUtils.hasText(username)) {
            return username;
        }

        return "user";
    }
}
