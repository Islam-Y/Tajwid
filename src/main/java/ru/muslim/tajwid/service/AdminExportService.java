package ru.muslim.tajwid.service;

import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import ru.muslim.tajwid.domain.FlowContextEntity;
import ru.muslim.tajwid.domain.ReferralLinkUsageEntity;
import ru.muslim.tajwid.domain.UserEntity;
import ru.muslim.tajwid.domain.UserTagEntity;
import ru.muslim.tajwid.repository.FlowContextRepository;
import ru.muslim.tajwid.repository.ReferralLinkUsageRepository;
import ru.muslim.tajwid.repository.UserRepository;
import ru.muslim.tajwid.repository.UserTagRepository;
import ru.muslim.tajwid.web.dto.AdminExportFlowContextRow;
import ru.muslim.tajwid.web.dto.AdminExportReferralLinkUsageRow;
import ru.muslim.tajwid.web.dto.AdminExportSnapshotResponse;
import ru.muslim.tajwid.web.dto.AdminExportUserRow;
import ru.muslim.tajwid.web.dto.AdminExportUserTagRow;

@Service
@RequiredArgsConstructor
public class AdminExportService {

    private final UserRepository userRepository;
    private final FlowContextRepository flowContextRepository;
    private final UserTagRepository userTagRepository;
    private final ReferralLinkUsageRepository referralLinkUsageRepository;
    private final AdminExportMapper exportMapper;

    public AdminExportSnapshotResponse exportSnapshot() {
        return new AdminExportSnapshotResponse(
            fetchUsers(),
            fetchFlowContexts(),
            fetchUserTags(),
            fetchReferralLinkUsages()
        );
    }

    public String exportUsersCsv() {
        List<? extends List<?>> rows = fetchUsers().stream()
            .map(row -> csvRow(
                row.userId(),
                row.telegramFirstName(),
                row.telegramUsername(),
                row.userName(),
                row.age(),
                row.hasChildren(),
                row.childrenCount(),
                row.childrenAges(),
                row.childrenStudyQuran(),
                row.phone(),
                row.readingLevel(),
                row.schoolChannelSubscribed(),
                row.courseChannelSubscribed(),
                row.consentGiven(),
                row.registrationCompleted(),
                row.registrationCompletedAt(),
                row.referrerUserId(),
                row.referralStatus(),
                row.referralCountedAt(),
                row.referralPoints(),
                row.referralLinkCp(),
                row.createdAt(),
                row.updatedAt()
            ))
            .toList();

        return buildCsv(
            List.of(
                "user_id",
                "telegram_first_name",
                "telegram_username",
                "user_name",
                "age",
                "has_children",
                "children_count",
                "children_ages",
                "children_study_quran",
                "phone",
                "reading_level",
                "is_school_channel_subscribed",
                "is_course_channel_subscribed",
                "consent_given",
                "registration_completed",
                "registration_completed_at",
                "referrer_user_id",
                "referral_status",
                "referral_counted_at",
                "referral_points",
                "referral_link_cp",
                "created_at",
                "updated_at"
            ),
            rows
        );
    }

    public String exportFlowContextsCsv() {
        List<? extends List<?>> rows = fetchFlowContexts().stream()
            .map(row -> csvRow(
                row.userId(),
                row.telegramFirstName(),
                row.telegramUsername(),
                row.currentStep(),
                row.flowType(),
                row.userName(),
                row.age(),
                row.hasChildren(),
                row.childrenCount(),
                row.childrenAges(),
                row.childrenStudyQuran(),
                row.childrenAgeIndex(),
                row.phone(),
                row.readingLevel(),
                row.consentGiven(),
                row.schoolChannelSubscribed(),
                row.referrerUserId(),
                row.referralEntrySource(),
                row.referralEntryAt(),
                row.tempTags(),
                row.referralAnnouncementDueAt(),
                row.referralAnnouncementSent(),
                row.createdAt(),
                row.updatedAt()
            ))
            .toList();

        return buildCsv(
            List.of(
                "user_id",
                "telegram_first_name",
                "telegram_username",
                "current_step",
                "flow_type",
                "user_name",
                "age",
                "has_children",
                "children_count",
                "children_ages",
                "children_study_quran",
                "children_age_index",
                "phone",
                "reading_level",
                "consent_given",
                "school_channel_subscribed",
                "referrer_user_id",
                "referral_entry_source",
                "referral_entry_at",
                "temp_tags",
                "referral_announcement_due_at",
                "referral_announcement_sent",
                "created_at",
                "updated_at"
            ),
            rows
        );
    }

    public String exportUserTagsCsv() {
        List<? extends List<?>> rows = fetchUserTags().stream()
            .map(row -> csvRow(
                row.userId(),
                row.tag(),
                row.createdAt()
            ))
            .toList();

        return buildCsv(
            List.of("user_id", "tag", "created_at"),
            rows
        );
    }

    public String exportReferralLinkUsagesCsv() {
        List<? extends List<?>> rows = fetchReferralLinkUsages().stream()
            .map(row -> csvRow(
                row.referralEventId(),
                row.referralLink(),
                row.referrerUserId(),
                row.inviteeUserId(),
                row.idempotencyKey(),
                row.alreadyCounted(),
                row.countedAt(),
                row.triggerSource(),
                row.triggeredAt(),
                row.createdAt()
            ))
            .toList();

        return buildCsv(
            List.of(
                "referral_event_id",
                "referral_link",
                "referrer_user_id",
                "invitee_user_id",
                "idempotency_key",
                "is_already_counted",
                "counted_at",
                "trigger_source",
                "triggered_at",
                "created_at"
            ),
            rows
        );
    }

    private List<AdminExportUserRow> fetchUsers() {
        List<UserEntity> entities = userRepository.findAll(Sort.by("userId"));
        return entities.stream()
            .map(exportMapper::toUserRow)
            .toList();
    }

    private List<AdminExportFlowContextRow> fetchFlowContexts() {
        List<FlowContextEntity> entities = flowContextRepository.findAll(Sort.by("userId"));
        return entities.stream()
            .map(exportMapper::toFlowContextRow)
            .toList();
    }

    private List<AdminExportUserTagRow> fetchUserTags() {
        List<UserTagEntity> entities = userTagRepository.findAll(
            Sort.by("userId").ascending().and(Sort.by("createdAt").ascending())
        );
        return entities.stream()
            .map(exportMapper::toUserTagRow)
            .toList();
    }

    private List<AdminExportReferralLinkUsageRow> fetchReferralLinkUsages() {
        List<ReferralLinkUsageEntity> entities = referralLinkUsageRepository.findAll(
            Sort.by("triggeredAt").ascending().and(Sort.by("createdAt").ascending())
        );
        return entities.stream()
            .map(exportMapper::toReferralLinkUsageRow)
            .toList();
    }

    private String buildCsv(List<String> header, List<? extends List<?>> rows) {
        StringBuilder csv = new StringBuilder();
        appendCsvRow(csv, header);
        for (List<?> row : rows) {
            appendCsvRow(csv, row);
        }
        return csv.toString();
    }

    private void appendCsvRow(StringBuilder csv, List<?> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(escapeCsvValue(values.get(i)));
        }
        csv.append('\n');
    }

    private String escapeCsvValue(Object value) {
        if (value == null) {
            return "";
        }

        String text = String.valueOf(value);
        boolean mustQuote = text.contains(",")
            || text.contains("\"")
            || text.contains("\n")
            || text.contains("\r");

        if (!mustQuote) {
            return text;
        }

        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private List<?> csvRow(Object... values) {
        return Arrays.asList(values);
    }
}
