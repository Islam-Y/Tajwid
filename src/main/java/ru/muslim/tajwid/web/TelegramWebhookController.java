package ru.muslim.tajwid.web;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.muslim.tajwid.config.TajwidBotProperties;
import ru.muslim.tajwid.telegram.TelegramWebhookService;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/telegram")
@ConditionalOnProperty(prefix = "tajwid.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class TelegramWebhookController {

    private static final String TELEGRAM_SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final TelegramWebhookService telegramWebhookService;
    private final TajwidBotProperties properties;

    @PostMapping("/webhook")
    public ResponseEntity<Void> onWebhook(@RequestBody JsonNode update,
                                          @RequestHeader(value = TELEGRAM_SECRET_HEADER,
                                              required = false) String secretHeader) {
        String expectedSecret = properties.getTelegram().getWebhookSecret();
        if (StringUtils.hasText(expectedSecret) && !expectedSecret.equals(secretHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        telegramWebhookService.processUpdate(update);
        return ResponseEntity.ok().build();
    }
}
