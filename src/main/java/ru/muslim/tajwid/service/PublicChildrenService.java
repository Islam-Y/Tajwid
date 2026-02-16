package ru.muslim.tajwid.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import ru.muslim.tajwid.domain.UserEntity;
import ru.muslim.tajwid.repository.UserRepository;
import ru.muslim.tajwid.web.dto.PublicChildrenLookupRequest;
import ru.muslim.tajwid.web.dto.PublicChildrenSelfResponse;
import ru.muslim.tajwid.web.dto.PublicChildrenStatsResponse;

@Service
@RequiredArgsConstructor
public class PublicChildrenService {

    private final UserRepository userRepository;

    public PublicChildrenStatsResponse getStats() {
        List<UserEntity> users = userRepository.findAll(Sort.by("userId"));
        long totalUsers = users.size();
        long usersWithChildren = users.stream()
            .filter(user -> valueOrZero(user.getChildrenCount()) > 0)
            .count();
        long usersWithoutChildren = totalUsers - usersWithChildren;

        long studyTrue = users.stream()
            .filter(user -> valueOrZero(user.getChildrenCount()) > 0)
            .filter(user -> Boolean.TRUE.equals(user.getChildrenStudyQuran()))
            .count();
        long studyFalse = users.stream()
            .filter(user -> valueOrZero(user.getChildrenCount()) > 0)
            .filter(user -> Boolean.FALSE.equals(user.getChildrenStudyQuran()))
            .count();
        long studyUnknown = usersWithChildren - studyTrue - studyFalse;

        return new PublicChildrenStatsResponse(
            totalUsers,
            usersWithChildren,
            usersWithoutChildren,
            studyTrue,
            studyFalse,
            studyUnknown
        );
    }

    public boolean hasAccess(Long userId, String phone) {
        if (userId == null || phone == null || phone.isBlank()) {
            return false;
        }
        return userRepository.findByUserId(userId)
            .map(user -> phonesMatch(user.getPhone(), phone))
            .orElse(false);
    }

    public Optional<PublicChildrenSelfResponse> lookupSelf(PublicChildrenLookupRequest request) {
        return userRepository.findByUserId(request.userId())
            .filter(user -> phonesMatch(user.getPhone(), request.phone()))
            .map(this::toSelfResponse);
    }

    private PublicChildrenSelfResponse toSelfResponse(UserEntity user) {
        return new PublicChildrenSelfResponse(
            user.getUserId(),
            user.getChildrenCount(),
            parseChildrenAges(user.getChildrenAges()),
            user.getChildrenStudyQuran(),
            user.getReadingLevel()
        );
    }

    private List<Integer> parseChildrenAges(String rawChildrenAges) {
        if (rawChildrenAges == null || rawChildrenAges.isBlank()) {
            return List.of();
        }

        List<Integer> result = new ArrayList<>();
        String[] parts = rawChildrenAges.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                result.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException ignored) {
                // Skip malformed legacy values.
            }
        }
        return List.copyOf(result);
    }

    private boolean phonesMatch(String leftPhone, String rightPhone) {
        String normalizedLeft = normalizePhone(leftPhone);
        String normalizedRight = normalizePhone(rightPhone);
        return !normalizedLeft.isEmpty() && normalizedLeft.equals(normalizedRight);
    }

    private String normalizePhone(String phone) {
        if (phone == null) {
            return "";
        }
        return phone.replaceAll("\\D", "");
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
