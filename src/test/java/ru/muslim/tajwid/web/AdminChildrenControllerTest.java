package ru.muslim.tajwid.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import ru.muslim.tajwid.domain.ReadingLevel;
import ru.muslim.tajwid.domain.ReferralStatus;
import ru.muslim.tajwid.domain.UserEntity;
import ru.muslim.tajwid.repository.UserRepository;
import ru.muslim.tajwid.support.PostgresContainerTestBase;
import ru.muslim.tajwid.web.dto.AdminChildrenSearchRequest;
import ru.muslim.tajwid.web.dto.AdminChildrenUserResponse;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminChildrenControllerTest extends PostgresContainerTestBase {

    private static final String ADMIN_USERNAME = "test_admin";
    private static final String ADMIN_PASSWORD = "test_secret";

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        createUser(900L, "NoChildren", "no_children", false, null, ReadingLevel.START_FROM_ZERO);
        createUser(901L, "ChildrenYes", "children_yes", true, true, ReadingLevel.KNOW_BASICS);
        createUser(902L, "ChildrenNo", "children_no", true, false, ReadingLevel.READ_BY_SYLLABLES);
    }

    @Test
    void getByUserIdReturnsChildrenPayload() {
        AdminChildrenUserResponse response = adminRestClient()
            .get()
            .uri("/api/admin/children/users/{userId}", 901L)
            .retrieve()
            .body(AdminChildrenUserResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(901L);
        assertThat(response.telegramUsername()).isEqualTo("children_yes");
        assertThat(response.hasChildren()).isTrue();
        assertThat(response.childrenStudyQuran()).isTrue();
    }

    @Test
    void getUsersUsesChildrenFilters() {
        List<AdminChildrenUserResponse> defaultList = adminRestClient()
            .get()
            .uri("/api/admin/children/users")
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});

        assertThat(defaultList).isNotNull();
        assertThat(defaultList).extracting(AdminChildrenUserResponse::userId)
            .containsExactly(900L, 901L, 902L);

        List<AdminChildrenUserResponse> filtered = adminRestClient()
            .get()
            .uri("/api/admin/children/users?hasChildren=true&childrenStudyQuran=false")
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});

        assertThat(filtered).isNotNull();
        assertThat(filtered).extracting(AdminChildrenUserResponse::userId)
            .containsExactly(902L);
    }

    @Test
    void searchPostReturnsFilteredUsers() {
        AdminChildrenSearchRequest request = new AdminChildrenSearchRequest(
            null,
            true,
            true,
            List.of(ReadingLevel.KNOW_BASICS),
            true,
            50
        );

        List<AdminChildrenUserResponse> result = adminRestClient()
            .post()
            .uri("/api/admin/children/search")
            .body(request)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});

        assertThat(result).isNotNull();
        assertThat(result).extracting(AdminChildrenUserResponse::userId)
            .containsExactly(901L);
    }

    @Test
    void adminChildrenRequiresAuthentication() {
        HttpStatusCode status = anonymousRestClient()
            .get()
            .uri("/api/admin/children/users")
            .exchange((request, response) -> response.getStatusCode());

        assertThat(status.value()).isEqualTo(401);
    }

    private void createUser(Long userId,
                            String name,
                            String username,
                            Boolean hasChildren,
                            Boolean childrenStudyQuran,
                            ReadingLevel readingLevel) {
        UserEntity user = new UserEntity();
        user.setUserId(userId);
        user.setTelegramFirstName(name);
        user.setTelegramUsername(username);
        user.setUserName(name);
        user.setAge(30);
        user.setHasChildren(hasChildren);
        user.setChildrenCount(Boolean.TRUE.equals(hasChildren) ? 1 : 0);
        user.setChildrenAges(null);
        user.setChildrenStudyQuran(childrenStudyQuran);
        user.setPhone("+79990000000");
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
