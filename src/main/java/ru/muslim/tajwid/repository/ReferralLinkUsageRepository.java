package ru.muslim.tajwid.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.muslim.tajwid.domain.ReferralLinkUsageEntity;

public interface ReferralLinkUsageRepository extends JpaRepository<ReferralLinkUsageEntity, UUID> {

    boolean existsByInviteeUserId(Long inviteeUserId);

    Optional<ReferralLinkUsageEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<ReferralLinkUsageEntity> findByReferrerUserIdAndInviteeUserId(Long referrerUserId, Long inviteeUserId);
}
