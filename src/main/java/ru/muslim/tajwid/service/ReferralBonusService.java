package ru.muslim.tajwid.service;

import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.muslim.tajwid.domain.ReferralLinkUsageEntity;
import ru.muslim.tajwid.domain.ReferralStatus;
import ru.muslim.tajwid.domain.UserEntity;
import ru.muslim.tajwid.repository.ReferralLinkUsageRepository;
import ru.muslim.tajwid.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class ReferralBonusService {

    private static final Logger log = LoggerFactory.getLogger(ReferralBonusService.class);

    private final UserRepository userRepository;
    private final ReferralLinkUsageRepository referralLinkUsageRepository;

    @Transactional
    public ReferralProcessResult processReferralEvent(ReferralEventPayload payload) {
        if (!isPayloadValid(payload)) {
            log.warn("Invalid referral payload: {}", payload);
            return ReferralProcessResult.invalid();
        }

        if (payload.referrerUserId().equals(payload.inviteeUserId())) {
            log.warn("Self-referral attempt: {}", payload);
            return ReferralProcessResult.invalid();
        }

        Optional<UserEntity> maybeReferrerLocked = userRepository.findByUserIdForUpdate(payload.referrerUserId());
        Optional<UserEntity> maybeInvitee = userRepository.findByUserId(payload.inviteeUserId());

        if (maybeReferrerLocked.isEmpty() || maybeInvitee.isEmpty()) {
            log.warn("Referrer or invitee not found for payload: {}", payload);
            return ReferralProcessResult.invalid();
        }

        UserEntity referrer = maybeReferrerLocked.get();
        UserEntity invitee = maybeInvitee.get();

        Optional<ReferralLinkUsageEntity> existing = referralLinkUsageRepository
            .findByIdempotencyKey(payload.idempotencyKey())
            .or(() -> referralLinkUsageRepository
                .findByReferrerUserIdAndInviteeUserId(payload.referrerUserId(), payload.inviteeUserId()));

        if (existing.isPresent()) {
            ReferralLinkUsageEntity existingUsage = existing.get();
            if (existingUsage.isAlreadyCounted()) {
                ensureInviteeMarkedAsCounted(invitee, existingUsage.getCountedAt());
                return new ReferralProcessResult(true, true, false, referrer.getReferralPoints(), false);
            }
            log.warn("Found referral usage with not-counted status for payload: {}", payload);
            return new ReferralProcessResult(true, false, false, referrer.getReferralPoints(), true);
        }

        ReferralLinkUsageEntity usage = new ReferralLinkUsageEntity();
        Instant now = Instant.now();
        usage.setReferralEventId(payload.referralEventId());
        usage.setReferralLink(payload.referralLink());
        usage.setReferrerUserId(payload.referrerUserId());
        usage.setInviteeUserId(payload.inviteeUserId());
        usage.setIdempotencyKey(payload.idempotencyKey());
        usage.setAlreadyCounted(true);
        usage.setCountedAt(now);
        usage.setTriggerSource(payload.triggerSource());
        usage.setTriggeredAt(payload.triggeredAt());
        referralLinkUsageRepository.save(usage);

        referrer.incrementReferralPoints();
        userRepository.save(referrer);

        invitee.setReferralStatus(ReferralStatus.COUNTED);
        invitee.setReferralCountedAt(now);
        userRepository.save(invitee);

        return new ReferralProcessResult(true, true, true, referrer.getReferralPoints(), false);
    }

    private void ensureInviteeMarkedAsCounted(UserEntity invitee, Instant countedAt) {
        if (invitee.getReferralStatus() == ReferralStatus.COUNTED) {
            return;
        }
        invitee.setReferralStatus(ReferralStatus.COUNTED);
        invitee.setReferralCountedAt(countedAt == null ? Instant.now() : countedAt);
        userRepository.save(invitee);
    }

    private boolean isPayloadValid(ReferralEventPayload payload) {
        return payload != null
            && payload.referralEventId() != null
            && payload.referrerUserId() != null
            && payload.inviteeUserId() != null
            && payload.idempotencyKey() != null
            && !payload.idempotencyKey().isBlank()
            && payload.triggerSource() != null
            && !payload.triggerSource().isBlank()
            && payload.triggeredAt() != null;
    }
}
