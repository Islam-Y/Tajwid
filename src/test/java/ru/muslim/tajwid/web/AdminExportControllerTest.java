package ru.muslim.tajwid.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import ru.muslim.tajwid.domain.FlowContextEntity;
import ru.muslim.tajwid.domain.FlowStep;
import ru.muslim.tajwid.domain.FlowType;
import ru.muslim.tajwid.domain.ReadingLevel;
import ru.muslim.tajwid.domain.ReferralLinkUsageEntity;
import ru.muslim.tajwid.domain.ReferralStatus;
import ru.muslim.tajwid.domain.UserEntity;
import ru.muslim.tajwid.domain.UserTagEntity;
import ru.muslim.tajwid.repository.FlowContextRepository;
import ru.muslim.tajwid.repository.ReferralLinkUsageRepository;
import ru.muslim.tajwid.repository.UserRepository;
import ru.muslim.tajwid.repository.UserTagRepository;
import ru.muslim.tajwid.support.PostgresContainerTestBase;
import ru.muslim.tajwid.web.dto.AdminExportSnapshotResponse;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminExportControllerTest extends PostgresContainerTestBase {

    private static final String ADMIN_USERNAME = "test_admin";
    private static final String ADMIN_PASSWORD = "test_secret";

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FlowContextRepository flowContextRepository;

    @Autowired
    private UserTagRepository userTagRepository;

    @Autowired
    private ReferralLinkUsageRepository referralLinkUsageRepository;

    @BeforeEach
    void setUp() {
        referralLinkUsageRepository.deleteAll();
        userTagRepository.deleteAll();
        flowContextRepository.deleteAll();
        userRepository.deleteAll();

        createSampleData();
    }

    @Test
    void snapshotExportReturnsAllCollections() throws Exception {
        AdminExportSnapshotResponse snapshot = adminRestClient()
            .get()
            .uri("/api/admin/export/snapshot")
            .retrieve()
            .body(AdminExportSnapshotResponse.class);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.users()).hasSize(2);
        assertThat(snapshot.users()).anyMatch(user -> user.userId().equals(501L));
        assertThat(snapshot.flowContexts()).hasSize(1);
        assertThat(snapshot.flowContexts().getFirst().currentStep()).isEqualTo(FlowStep.FLOW2_WAIT_TERMS);
        assertThat(snapshot.userTags()).hasSize(1);
        assertThat(snapshot.userTags().getFirst().tag()).isEqualTo("Подписка с бота");
        assertThat(snapshot.referralLinkUsages()).hasSize(1);
        assertThat(snapshot.referralLinkUsages().getFirst().idempotencyKey()).isEqualTo("ref:500:501");
    }

    @Test
    void usersCsvExportContainsHeaderAndData() throws Exception {
        ResponseEntity<String> response = adminRestClient()
            .get()
            .uri("/api/admin/export/users.csv")
            .retrieve()
            .toEntity(String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString()).contains("text/csv");
        assertThat(response.getHeaders().getFirst("Content-Disposition"))
            .contains("tajwid-users.csv");

        String csv = response.getBody();
        assertThat(csv).isNotNull();
        assertThat(csv).contains("user_id,telegram_first_name,user_name,age");
        assertThat(csv).contains("501");
        assertThat(csv).contains("NOT_APPLICABLE");
        assertThat(csv).contains("https://t.me/tajwid_test_bot?start=501");
    }

    @Test
    void adminExportRequiresAuthentication() {
        HttpStatusCode status = anonymousRestClient()
            .get()
            .uri("/api/admin/export/users.csv")
            .exchange((request, response) -> response.getStatusCode());

        assertThat(status.value()).isEqualTo(401);
    }

    private void createSampleData() {
        Instant now = Instant.now();

        UserEntity referrer = new UserEntity();
        referrer.setUserId(500L);
        referrer.setTelegramFirstName("Referrer");
        referrer.setUserName("Referrer");
        referrer.setAge(30);
        referrer.setPhone("+79990000000");
        referrer.setReadingLevel(ReadingLevel.KNOW_BASICS);
        referrer.setSchoolChannelSubscribed(true);
        referrer.setCourseChannelSubscribed(true);
        referrer.setConsentGiven(true);
        referrer.setRegistrationCompleted(true);
        referrer.setRegistrationCompletedAt(now);
        referrer.setReferrerUserId(0);
        referrer.setReferralStatus(ReferralStatus.NOT_APPLICABLE);
        referrer.setReferralCountedAt(Instant.EPOCH);
        referrer.setReferralPoints(0);
        referrer.setReferralLinkCp("https://t.me/tajwid_test_bot?start=500");
        userRepository.save(referrer);

        UserEntity user = new UserEntity();
        user.setUserId(501L);
        user.setTelegramFirstName("Amin");
        user.setUserName("Амин");
        user.setAge(23);
        user.setPhone("+79991234567");
        user.setReadingLevel(ReadingLevel.KNOW_BASICS);
        user.setSchoolChannelSubscribed(true);
        user.setCourseChannelSubscribed(true);
        user.setConsentGiven(true);
        user.setRegistrationCompleted(true);
        user.setRegistrationCompletedAt(now);
        user.setReferrerUserId(0);
        user.setReferralStatus(ReferralStatus.NOT_APPLICABLE);
        user.setReferralCountedAt(Instant.EPOCH);
        user.setReferralPoints(2);
        user.setReferralLinkCp("https://t.me/tajwid_test_bot?start=501");
        userRepository.save(user);

        FlowContextEntity flowContext = new FlowContextEntity();
        flowContext.setUserId(501L);
        flowContext.setTelegramFirstName("Amin");
        flowContext.setCurrentStep(FlowStep.FLOW2_WAIT_TERMS);
        flowContext.setFlowType(FlowType.NORMAL);
        flowContext.setUserName("Амин");
        flowContext.setAge(23);
        flowContext.setPhone("+79991234567");
        flowContext.setReadingLevel(ReadingLevel.KNOW_BASICS);
        flowContext.setConsentGiven(true);
        flowContext.setSchoolChannelSubscribed(true);
        flowContext.setReferrerUserId(0);
        flowContext.setTempTags("Подписка с бота");
        flowContextRepository.save(flowContext);

        UserTagEntity userTag = new UserTagEntity();
        userTag.setUserId(501L);
        userTag.setTag("Подписка с бота");
        userTagRepository.save(userTag);

        ReferralLinkUsageEntity usage = new ReferralLinkUsageEntity();
        usage.setReferralEventId(UUID.randomUUID());
        usage.setReferralLink("https://t.me/tajwid_test_bot?start=500");
        usage.setReferrerUserId(500L);
        usage.setInviteeUserId(501L);
        usage.setIdempotencyKey("ref:500:501");
        usage.setAlreadyCounted(true);
        usage.setCountedAt(now);
        usage.setTriggerSource("course_channel_membership_update");
        usage.setTriggeredAt(now);
        referralLinkUsageRepository.save(usage);
    }

    private RestClient adminRestClient() {
        return RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultHeaders(headers -> headers.setBasicAuth(ADMIN_USERNAME, ADMIN_PASSWORD))
            .build();
    }

    private RestClient anonymousRestClient() {
        return RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .build();
    }
}
