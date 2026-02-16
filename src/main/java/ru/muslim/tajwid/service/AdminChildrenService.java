package ru.muslim.tajwid.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import ru.muslim.tajwid.domain.ReadingLevel;
import ru.muslim.tajwid.domain.UserEntity;
import ru.muslim.tajwid.repository.UserRepository;
import ru.muslim.tajwid.web.dto.AdminChildrenSearchRequest;
import ru.muslim.tajwid.web.dto.AdminChildrenUserResponse;

@Service
@RequiredArgsConstructor
public class AdminChildrenService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    private final UserRepository userRepository;

    public Optional<AdminChildrenUserResponse> getByUserId(Long userId) {
        return userRepository.findByUserId(userId)
            .map(this::toResponse);
    }

    public List<AdminChildrenUserResponse> getUsers(boolean onlyWithChildren,
                                                    Boolean childrenStudyQuran,
                                                    Integer minChildrenCount,
                                                    Integer maxChildrenCount,
                                                    Integer limit) {
        return searchInternal(
            null,
            minChildrenCount,
            maxChildrenCount,
            childrenStudyQuran,
            null,
            null,
            onlyWithChildren,
            limit
        );
    }

    public List<AdminChildrenUserResponse> search(AdminChildrenSearchRequest request) {
        AdminChildrenSearchRequest safeRequest = request == null ? AdminChildrenSearchRequest.empty() : request;
        return searchInternal(
            safeRequest.userIds(),
            safeRequest.minChildrenCount(),
            safeRequest.maxChildrenCount(),
            safeRequest.childrenStudyQuran(),
            safeRequest.readingLevels(),
            safeRequest.registrationCompleted(),
            safeRequest.onlyWithChildren(),
            safeRequest.limit()
        );
    }

    private List<AdminChildrenUserResponse> searchInternal(List<Long> userIds,
                                                           Integer minChildrenCount,
                                                           Integer maxChildrenCount,
                                                           Boolean childrenStudyQuran,
                                                           List<ReadingLevel> readingLevels,
                                                           Boolean registrationCompleted,
                                                           Boolean onlyWithChildren,
                                                           Integer limit) {
        int normalizedLimit = normalizeLimit(limit);
        int minCount = minChildrenCount == null ? 0 : minChildrenCount;
        int maxCount = maxChildrenCount == null ? Integer.MAX_VALUE : maxChildrenCount;
        boolean requireChildren = onlyWithChildren == null || onlyWithChildren;
        Set<Long> userIdFilter = userIds == null ? Set.of() : new HashSet<>(userIds);
        Set<ReadingLevel> readingLevelFilter = readingLevels == null
            ? Set.of()
            : new HashSet<>(readingLevels);

        if (minCount > maxCount) {
            return List.of();
        }

        return userRepository.findAll(Sort.by("userId")).stream()
            .filter(user -> userIdFilter.isEmpty() || userIdFilter.contains(user.getUserId()))
            .filter(user -> {
                int childrenCount = valueOrZero(user.getChildrenCount());
                if (childrenCount < minCount || childrenCount > maxCount) {
                    return false;
                }
                return !requireChildren || childrenCount > 0;
            })
            .filter(user -> childrenStudyQuran == null
                || Objects.equals(childrenStudyQuran, user.getChildrenStudyQuran()))
            .filter(user -> readingLevelFilter.isEmpty()
                || (user.getReadingLevel() != null && readingLevelFilter.contains(user.getReadingLevel())))
            .filter(user -> registrationCompleted == null
                || registrationCompleted.equals(user.isRegistrationCompleted()))
            .limit(normalizedLimit)
            .map(this::toResponse)
            .toList();
    }

    private AdminChildrenUserResponse toResponse(UserEntity entity) {
        return new AdminChildrenUserResponse(
            entity.getUserId(),
            entity.getTelegramFirstName(),
            entity.getUserName(),
            entity.getAge(),
            entity.getChildrenCount(),
            parseChildrenAges(entity.getChildrenAges()),
            entity.getChildrenStudyQuran(),
            entity.getPhone(),
            entity.getReadingLevel(),
            entity.isRegistrationCompleted(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
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
                // Skip malformed values in legacy rows.
            }
        }
        return List.copyOf(result);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit <= 0) {
            return 1;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
