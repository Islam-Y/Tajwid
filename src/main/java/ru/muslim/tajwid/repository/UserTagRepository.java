package ru.muslim.tajwid.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.muslim.tajwid.domain.UserTagEntity;

public interface UserTagRepository extends JpaRepository<UserTagEntity, Long> {

    boolean existsByUserIdAndTag(Long userId, String tag);

    List<UserTagEntity> findAllByUserId(Long userId);
}
