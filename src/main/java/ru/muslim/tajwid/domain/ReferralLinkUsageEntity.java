package ru.muslim.tajwid.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "referral_link_usage")
@Getter
@Setter
@NoArgsConstructor
public class ReferralLinkUsageEntity {

    @Id
    @Column(name = "referral_event_id", nullable = false)
    private UUID referralEventId;

    @Column(name = "referral_link")
    private String referralLink;

    @Column(name = "referrer_user_id", nullable = false)
    private Long referrerUserId;

    @Column(name = "invitee_user_id", nullable = false)
    private Long inviteeUserId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "is_already_counted", nullable = false)
    private boolean alreadyCounted;

    @Column(name = "counted_at", nullable = false)
    private Instant countedAt = Instant.EPOCH;

    @Column(name = "trigger_source", nullable = false)
    private String triggerSource;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
        if (countedAt == null) {
            countedAt = Instant.EPOCH;
        }
    }
}
