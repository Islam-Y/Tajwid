package ru.muslim.tajwid.web;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.muslim.tajwid.domain.ReadingLevel;
import ru.muslim.tajwid.service.AdminChildrenService;
import ru.muslim.tajwid.web.dto.AdminChildrenSearchRequest;
import ru.muslim.tajwid.web.dto.AdminChildrenUserResponse;

@RestController
@RequestMapping("/api/admin/children")
@RequiredArgsConstructor
public class AdminChildrenController {

    private final AdminChildrenService childrenService;

    @GetMapping("/users/{userId}")
    public AdminChildrenUserResponse getByUserId(@PathVariable Long userId) {
        return childrenService.getByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));
    }

    @GetMapping("/users")
    public List<AdminChildrenUserResponse> getUsers(
        @RequestParam(required = false) Boolean hasChildren,
        @RequestParam(required = false) Boolean childrenStudyQuran,
        @RequestParam(required = false) List<ReadingLevel> readingLevels,
        @RequestParam(required = false) Boolean registrationCompleted,
        @RequestParam(defaultValue = "100") Integer limit
    ) {
        return childrenService.getUsers(
            hasChildren,
            childrenStudyQuran,
            readingLevels,
            registrationCompleted,
            limit
        );
    }

    @PostMapping("/search")
    public List<AdminChildrenUserResponse> search(@RequestBody(required = false) AdminChildrenSearchRequest request) {
        return childrenService.search(request);
    }
}
