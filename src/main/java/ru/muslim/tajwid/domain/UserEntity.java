package ru.muslim.tajwid.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "telegram_first_name")
    private String telegramFirstName;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "age")
    private Integer age;

    @Column(name = "phone")
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "reading_level")
    private ReadingLevel readingLevel;

    @Column(name = "is_school_channel_subscribed", nullable = false)
    private boolean schoolChannelSubscribed;

    @Column(name = "is_course_channel_subscribed", nullable = false)
    private boolean courseChannelSubscribed;

    @Column(name = "consent_given", nullable = false)
    private boolean consentGiven;

    @Column(name = "registration_completed", nullable = false)
    private boolean registrationCompleted;

    @Column(name = "registration_completed_at", nullable = false)
    private Instant registrationCompletedAt = Instant.EPOCH;

    @Column(name = "referrer_user_id", nullable = false)
    private long referrerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "referral_status", nullable = false)
    private ReferralStatus referralStatus = ReferralStatus.NOT_APPLICABLE;

    @Column(name = "referral_counted_at", nullable = false)
    private Instant referralCountedAt = Instant.EPOCH;

    @Column(name = "referral_points", nullable = false)
    private int referralPoints;

    @Column(name = "referral_link_cp")
    private String referralLinkCp;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (registrationCompletedAt == null) {
            registrationCompletedAt = Instant.EPOCH;
        }
        if (referralCountedAt == null) {
            referralCountedAt = Instant.EPOCH;
        }
        if (referralStatus == null) {
            referralStatus = ReferralStatus.NOT_APPLICABLE;
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    public void incrementReferralPoints() {
        referralPoints += 1;
    }
}
