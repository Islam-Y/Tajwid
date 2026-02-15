package ru.muslim.tajwid.repository;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.muslim.tajwid.domain.ReferralStatus;
import ru.muslim.tajwid.domain.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    boolean existsByUserIdAndReferralStatusIn(Long userId, Collection<ReferralStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.userId = :userId")
    Optional<UserEntity> findByUserIdForUpdate(@Param("userId") Long userId);
}
