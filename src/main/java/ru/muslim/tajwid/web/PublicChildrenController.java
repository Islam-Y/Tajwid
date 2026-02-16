package ru.muslim.tajwid.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.muslim.tajwid.service.PublicChildrenService;
import ru.muslim.tajwid.web.dto.PublicChildrenLookupRequest;
import ru.muslim.tajwid.web.dto.PublicChildrenSelfResponse;
import ru.muslim.tajwid.web.dto.PublicChildrenStatsResponse;

@RestController
@RequestMapping("/api/public/children")
@RequiredArgsConstructor
public class PublicChildrenController {

    private final PublicChildrenService publicChildrenService;

    @GetMapping("/stats")
    public PublicChildrenStatsResponse stats(@RequestParam Long userId, @RequestParam String phone) {
        if (!publicChildrenService.hasAccess(userId, phone)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден");
        }
        return publicChildrenService.getStats();
    }

    @PostMapping("/self")
    public PublicChildrenSelfResponse self(@Valid @RequestBody PublicChildrenLookupRequest request) {
        return publicChildrenService.lookupSelf(request)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));
    }
}
