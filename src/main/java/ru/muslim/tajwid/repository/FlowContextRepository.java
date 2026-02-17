package ru.muslim.tajwid.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.muslim.tajwid.domain.FlowContextEntity;

public interface FlowContextRepository extends JpaRepository<FlowContextEntity, Long> {

    Optional<FlowContextEntity> findByUserId(Long userId);

    Optional<FlowContextEntity> findByTelegramUsernameIgnoreCase(String telegramUsername);

    List<FlowContextEntity> findByReferralAnnouncementSentFalseAndReferralAnnouncementDueAtLessThanEqual(Instant dueAt);

    @Query("select distinct fc.userId from FlowContextEntity fc "
        + "where fc.telegramUsername is not null and lower(fc.telegramUsername) in :usernames")
    List<Long> findUserIdsByTelegramUsernameIn(@Param("usernames") Collection<String> usernames);

    void deleteByUserId(Long userId);
}
