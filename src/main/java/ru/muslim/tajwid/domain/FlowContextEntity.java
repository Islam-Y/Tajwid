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
@Table(name = "flow_contexts")
@Getter
@Setter
@NoArgsConstructor
public class FlowContextEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "telegram_first_name")
    private String telegramFirstName;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false)
    private FlowStep currentStep = FlowStep.IDLE;

    @Enumerated(EnumType.STRING)
    @Column(name = "flow_type", nullable = false)
    private FlowType flowType = FlowType.NORMAL;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "age")
    private Integer age;

    @Column(name = "children_count")
    private Integer childrenCount;

    @Column(name = "children_ages")
    private String childrenAges;

    @Column(name = "children_study_quran")
    private Boolean childrenStudyQuran;

    @Column(name = "children_age_index")
    private Integer childrenAgeIndex;

    @Column(name = "phone")
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "reading_level")
    private ReadingLevel readingLevel;

    @Column(name = "consent_given", nullable = false)
    private boolean consentGiven;

    @Column(name = "school_channel_subscribed", nullable = false)
    private boolean schoolChannelSubscribed;

    @Column(name = "referrer_user_id", nullable = false)
    private long referrerUserId;

    @Column(name = "referral_entry_source")
    private String referralEntrySource;

    @Column(name = "referral_entry_at")
    private Instant referralEntryAt;

    @Column(name = "temp_tags")
    private String tempTags;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (currentStep == null) {
            currentStep = FlowStep.IDLE;
        }
        if (flowType == null) {
            flowType = FlowType.NORMAL;
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }
}
