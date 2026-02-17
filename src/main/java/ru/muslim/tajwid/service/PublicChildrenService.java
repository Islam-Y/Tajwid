package ru.muslim.tajwid.service;

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
            .filter(this::resolveHasChildren)
            .count();
        long usersWithoutChildren = totalUsers - usersWithChildren;

        long studyTrue = users.stream()
            .filter(this::resolveHasChildren)
            .filter(user -> Boolean.TRUE.equals(user.getChildrenStudyQuran()))
            .count();
        long studyFalse = users.stream()
            .filter(this::resolveHasChildren)
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
            resolveHasChildren(user),
            user.getChildrenStudyQuran(),
            user.getReadingLevel()
        );
    }

    private boolean resolveHasChildren(UserEntity user) {
        if (user.getHasChildren() != null) {
            return user.getHasChildren();
        }

        Integer childrenCount = user.getChildrenCount();
        return childrenCount != null && childrenCount > 0;
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
}
