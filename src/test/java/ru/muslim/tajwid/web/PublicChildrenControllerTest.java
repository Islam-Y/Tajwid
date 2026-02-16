package ru.muslim.tajwid.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import ru.muslim.tajwid.domain.ReadingLevel;
import ru.muslim.tajwid.domain.ReferralStatus;
import ru.muslim.tajwid.domain.UserEntity;
import ru.muslim.tajwid.repository.ReferralLinkUsageRepository;
import ru.muslim.tajwid.repository.UserRepository;
import ru.muslim.tajwid.support.PostgresContainerTestBase;
import ru.muslim.tajwid.web.dto.PublicChildrenLookupRequest;
import ru.muslim.tajwid.web.dto.PublicChildrenSelfResponse;
import ru.muslim.tajwid.web.dto.PublicChildrenStatsResponse;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublicChildrenControllerTest extends PostgresContainerTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReferralLinkUsageRepository referralLinkUsageRepository;

    @BeforeEach
    void setUp() {
        referralLinkUsageRepository.deleteAll();
        userRepository.deleteAll();
        createUser(910L, "A", 0, null, null, ReadingLevel.START_FROM_ZERO, "8(917)111-22-33");
        createUser(911L, "B", 2, "6,9", true, ReadingLevel.KNOW_BASICS, "+7 917 222 33 44");
        createUser(912L, "C", 1, "4", false, ReadingLevel.READ_BY_SYLLABLES, "79173334455");
    }

    @Test
    void statsIsPublicAndReturnsAggregates() {
        PublicChildrenStatsResponse response = restClient()
            .get()
            .uri("/api/public/children/stats?userId=911&phone=79172223344")
            .retrieve()
            .body(PublicChildrenStatsResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.totalUsers()).isEqualTo(3);
        assertThat(response.usersWithChildren()).isEqualTo(2);
        assertThat(response.usersWithoutChildren()).isEqualTo(1);
        assertThat(response.usersWithChildrenStudyQuranTrue()).isEqualTo(1);
        assertThat(response.usersWithChildrenStudyQuranFalse()).isEqualTo(1);
        assertThat(response.usersWithChildrenStudyQuranUnknown()).isEqualTo(0);
    }

    @Test
    void statsReturnsNotFoundWhenPhoneDoesNotMatch() {
        HttpStatusCode status = restClient()
            .get()
            .uri("/api/public/children/stats?userId=911&phone=79990000000")
            .exchange((request, response) -> response.getStatusCode());

        assertThat(status.value()).isEqualTo(404);
    }

    @Test
    void selfReturnsDataWhenPhoneMatches() {
        PublicChildrenSelfResponse response = restClient()
            .post()
            .uri("/api/public/children/self")
            .body(new PublicChildrenLookupRequest(911L, "79172223344"))
            .retrieve()
            .body(PublicChildrenSelfResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(911L);
        assertThat(response.childrenCount()).isEqualTo(2);
        assertThat(response.childrenAges()).containsExactly(6, 9);
        assertThat(response.childrenStudyQuran()).isTrue();
    }

    @Test
    void selfReturnsNotFoundWhenPhoneDoesNotMatch() {
        HttpStatusCode status = restClient()
            .post()
            .uri("/api/public/children/self")
            .body(new PublicChildrenLookupRequest(911L, "79990000000"))
            .exchange((request, response) -> response.getStatusCode());

        assertThat(status.value()).isEqualTo(404);
    }

    private RestClient restClient() {
        return RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .build();
    }

    private void createUser(Long userId,
                            String name,
                            Integer childrenCount,
                            String childrenAges,
                            Boolean childrenStudyQuran,
                            ReadingLevel readingLevel,
                            String phone) {
        UserEntity user = new UserEntity();
        user.setUserId(userId);
        user.setTelegramFirstName(name);
        user.setUserName(name);
        user.setAge(30);
        user.setChildrenCount(childrenCount);
        user.setChildrenAges(childrenAges);
        user.setChildrenStudyQuran(childrenStudyQuran);
        user.setPhone(phone);
        user.setReadingLevel(readingLevel);
        user.setSchoolChannelSubscribed(true);
        user.setCourseChannelSubscribed(true);
        user.setConsentGiven(true);
        user.setRegistrationCompleted(true);
        user.setRegistrationCompletedAt(Instant.now());
        user.setReferrerUserId(0);
        user.setReferralStatus(ReferralStatus.NOT_APPLICABLE);
        user.setReferralCountedAt(Instant.EPOCH);
        user.setReferralPoints(0);
        userRepository.save(user);
    }
}
