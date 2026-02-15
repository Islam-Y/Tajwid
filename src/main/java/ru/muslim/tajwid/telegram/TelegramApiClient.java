package ru.muslim.tajwid.telegram;

import ru.muslim.tajwid.web.dto.BotMessageResponse;

public interface TelegramApiClient {

    void sendMessage(BotMessageResponse message);

    String getChatMemberStatus(String chatId, long userId);

    void setWebhook(String webhookUrl, String secretToken);
}
