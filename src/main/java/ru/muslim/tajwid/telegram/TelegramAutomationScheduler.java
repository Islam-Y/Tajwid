package ru.muslim.tajwid.telegram;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.muslim.tajwid.service.BotFlowService;
import ru.muslim.tajwid.web.dto.BotUpdateResult;

@Component
@ConditionalOnProperty(prefix = "tajwid.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class TelegramAutomationScheduler {

    private static final Logger log = LoggerFactory.getLogger(TelegramAutomationScheduler.class);

    private final BotFlowService botFlowService;
    private final TelegramApiClient telegramApiClient;

    @Scheduled(fixedDelayString = "${tajwid.automation-tick-delay:PT30S}")
    public void tick() {
        BotUpdateResult result = botFlowService.processScheduledAutomations();
        if (result == null || result.messages() == null || result.messages().isEmpty()) {
            return;
        }

        result.messages().forEach(message -> {
            try {
                telegramApiClient.sendMessage(message);
            } catch (RuntimeException ex) {
                log.error("Failed to deliver automation message to {}", message.recipientUserId(), ex);
            }
        });
    }
}
