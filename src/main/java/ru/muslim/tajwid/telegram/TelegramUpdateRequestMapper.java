package ru.muslim.tajwid.telegram;

import org.mapstruct.Mapper;
import ru.muslim.tajwid.telegram.model.TelegramParsedContact;
import ru.muslim.tajwid.telegram.model.TelegramParsedUpdate;
import ru.muslim.tajwid.web.dto.BotContactPayload;
import ru.muslim.tajwid.web.dto.BotUpdateRequest;

@Mapper(componentModel = "spring")
public interface TelegramUpdateRequestMapper {

    BotUpdateRequest toBotUpdateRequest(TelegramParsedUpdate update);

    BotContactPayload toBotContactPayload(TelegramParsedContact contact);
}
