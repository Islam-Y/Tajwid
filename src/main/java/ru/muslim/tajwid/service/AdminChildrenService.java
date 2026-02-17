package ru.muslim.tajwid.service;

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

    public List<AdminChildrenUserResponse> getUsers(Boolean hasChildren,
                                                    Boolean childrenStudyQuran,
                                                    List<ReadingLevel> readingLevels,
                                                    Boolean registrationCompleted,
                                                    Integer limit) {
        return searchInternal(
            null,
            hasChildren,
            childrenStudyQuran,
            readingLevels,
            registrationCompleted,
            limit
        );
    }

    public List<AdminChildrenUserResponse> search(AdminChildrenSearchRequest request) {
        AdminChildrenSearchRequest safeRequest = request == null ? AdminChildrenSearchRequest.empty() : request;
        return searchInternal(
            safeRequest.userIds(),
            safeRequest.hasChildren(),
            safeRequest.childrenStudyQuran(),
            safeRequest.readingLevels(),
            safeRequest.registrationCompleted(),
            safeRequest.limit()
        );
    }

    private List<AdminChildrenUserResponse> searchInternal(List<Long> userIds,
                                                           Boolean hasChildren,
                                                           Boolean childrenStudyQuran,
                                                           List<ReadingLevel> readingLevels,
                                                           Boolean registrationCompleted,
                                                           Integer limit) {
        int normalizedLimit = normalizeLimit(limit);
        Set<Long> userIdFilter = userIds == null ? Set.of() : new HashSet<>(userIds);
        Set<ReadingLevel> readingLevelFilter = readingLevels == null
            ? Set.of()
            : new HashSet<>(readingLevels);

        return userRepository.findAll(Sort.by("userId")).stream()
            .filter(user -> userIdFilter.isEmpty() || userIdFilter.contains(user.getUserId()))
            .filter(user -> hasChildren == null || Objects.equals(hasChildren, resolveHasChildren(user)))
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
            entity.getTelegramUsername(),
            entity.getUserName(),
            entity.getAge(),
            resolveHasChildren(entity),
            entity.getChildrenStudyQuran(),
            entity.getPhone(),
            entity.getReadingLevel(),
            entity.isRegistrationCompleted(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private Boolean resolveHasChildren(UserEntity user) {
        if (user.getHasChildren() != null) {
            return user.getHasChildren();
        }
        Integer childrenCount = user.getChildrenCount();
        if (childrenCount == null) {
            return null;
        }
        return childrenCount > 0;
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
}
