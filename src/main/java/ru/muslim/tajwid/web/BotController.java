package ru.muslim.tajwid.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.muslim.tajwid.service.BotFlowService;
import ru.muslim.tajwid.web.dto.BotUpdateRequest;
import ru.muslim.tajwid.web.dto.BotUpdateResult;

@RestController
@RequestMapping("/api/bot")
@RequiredArgsConstructor
public class BotController {

    private final BotFlowService botFlowService;

    @PostMapping("/updates")
    public BotUpdateResult handleUpdate(@Valid @RequestBody BotUpdateRequest request) {
        return botFlowService.handleUpdate(request);
    }
}
