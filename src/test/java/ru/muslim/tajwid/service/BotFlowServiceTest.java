package ru.muslim.tajwid.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.muslim.tajwid.support.PostgresContainerTestBase;
import ru.muslim.tajwid.domain.FlowStep;
import ru.muslim.tajwid.domain.FlowType;
import ru.muslim.tajwid.domain.ReadingLevel;
import ru.muslim.tajwid.domain.ReferralStatus;
import ru.muslim.tajwid.domain.UserEntity;
import ru.muslim.tajwid.repository.FlowContextRepository;
import ru.muslim.tajwid.repository.ReferralLinkUsageRepository;
import ru.muslim.tajwid.repository.UserRepository;
import ru.muslim.tajwid.repository.UserTagRepository;
import ru.muslim.tajwid.web.dto.ButtonType;
import ru.muslim.tajwid.web.dto.BotContactPayload;
import ru.muslim.tajwid.web.dto.BotUpdateRequest;
import ru.muslim.tajwid.web.dto.BotUpdateResult;

@SpringBootTest
class BotFlowServiceTest extends PostgresContainerTestBase {

    private static final String FLOW1_INTRO_CONTINUE = "FLOW1_INTRO_CONTINUE";
    private static final String FLOW1_CONSENT_CONTINUE = "FLOW1_CONSENT_CONTINUE";
    private static final String FLOW1_COURSE_RECHECK = "FLOW1_COURSE_RECHECK";
    private static final String FLOW2_TERMS = "FLOW2_TERMS";
    private static final String FLOW2_SHOW_LINK = "FLOW2_SHOW_LINK";
    private static final String FLOW3_SCHOOL_RECHECK = "FLOW3_SCHOOL_RECHECK";
    private static final String FLOW3_CONSENT_CONTINUE = "FLOW3_CONSENT_CONTINUE";
    private static final String FLOW4_COURSE_CLICK_CONFIRMED = "FLOW4_COURSE_CLICK_CONFIRMED";

    @Autowired
    private BotFlowService botFlowService;

    @Autowired
    private ReferralBonusService referralBonusService;

    @Autowired
    private StubChannelSubscriptionService subscriptionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FlowContextRepository flowContextRepository;

    @Autowired
    private UserTagRepository userTagRepository;

    @Autowired
    private ReferralLinkUsageRepository referralLinkUsageRepository;

    @BeforeEach
    void cleanDatabase() {
        referralLinkUsageRepository.deleteAll();
        userTagRepository.deleteAll();
        flowContextRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void normalFlowRegistersUserAndProvidesReferralLink() {
        long userId = 1L;
        subscriptionService.setSubscriptionState(userId, true, true);

        send(userId, "Ali", "/start", null, null);
        send(userId, "Ali", null, FLOW1_INTRO_CONTINUE, null);
        send(userId, "Ali", null, FLOW1_CONSENT_CONTINUE, null);
        send(userId, "Ali", "Али", null, null);
        send(userId, "Ali", "22", null, null);
        send(userId, "Ali", null, null, new BotContactPayload(userId, "+79990000000"));

        BotUpdateResult afterReadingLevel = send(userId, "Ali", null,
            "FLOW1_LEVEL:START_FROM_ZERO", null);
        assertThat(afterReadingLevel.messages()).hasSize(1);
        assertThat(afterReadingLevel.messages().getFirst().text())
            .contains("Вы успешно зарегистрированы на курс по таджвиду");

        BotUpdateResult afterCourseCheck = send(userId, "Ali", null, FLOW1_COURSE_RECHECK, null);
        assertThat(afterCourseCheck.messages()).hasSize(1);
        assertThat(afterCourseCheck.messages().getFirst().text())
            .contains("Вы записались — альхамдулиллях");

        send(userId, "Ali", null, FLOW2_TERMS, null);
        BotUpdateResult afterShowLink = send(userId, "Ali", null, FLOW2_SHOW_LINK, null);

        assertThat(afterShowLink.messages()).hasSize(2);
        assertThat(afterShowLink.messages().getFirst().text())
            .isEqualTo("https://t.me/tajwid_test_bot?start=1");

        UserEntity user = userRepository.findByUserId(userId).orElseThrow();
        assertThat(user.isCourseChannelSubscribed()).isTrue();
        assertThat(user.getReferralStatus()).isEqualTo(ReferralStatus.NOT_APPLICABLE);
        assertThat(userTagRepository.existsByUserIdAndTag(userId, "Рефер получил ссылку")).isTrue();
    }

    @Test
    void referralFlowCompletesAndAwardsReferrer() {
        createReferrer(100L);
        subscriptionService.setSubscriptionState(200L, true, true);

        send(200L, "Invitee", "/start 100", null, null);
        send(200L, "Invitee", null, FLOW3_SCHOOL_RECHECK, null);
        send(200L, "Invitee", null, FLOW3_CONSENT_CONTINUE, null);
        send(200L, "Invitee", "Осман", null, null);
        send(200L, "Invitee", "19", null, null);
        send(200L, "Invitee", null, null, new BotContactPayload(200L, "+79991112233"));
        send(200L, "Invitee", null, "FLOW4_LEVEL:KNOW_BASICS", null);

        BotUpdateResult afterCourseClick = send(200L, "Invitee", null, FLOW4_COURSE_CLICK_CONFIRMED, null);

        assertThat(afterCourseClick.messages())
            .anyMatch(message -> message.recipientUserId().equals(100L)
                && message.text().contains("+1 к общей копилке"));
        assertThat(afterCourseClick.messages())
            .anyMatch(message -> message.recipientUserId().equals(200L)
                && message.text().contains("Вы записались — альхамдулиллях"));

        UserEntity referrer = userRepository.findByUserId(100L).orElseThrow();
        UserEntity invitee = userRepository.findByUserId(200L).orElseThrow();

        assertThat(referrer.getReferralPoints()).isEqualTo(1);
        assertThat(invitee.getReferralStatus()).isEqualTo(ReferralStatus.COUNTED);
        assertThat(invitee.isCourseChannelSubscribed()).isTrue();
        assertThat(flowContextRepository.findByUserId(200L).orElseThrow().getCurrentStep())
            .isEqualTo(FlowStep.FLOW2_WAIT_TERMS);
        assertThat(flowContextRepository.findByUserId(200L).orElseThrow().getFlowType())
            .isEqualTo(FlowType.REFERRAL);
    }

    @Test
    void referralFlowAutoAwardsReferrerAfterRealCourseSubscriptionEvent() {
        createReferrer(800L);
        subscriptionService.setSubscriptionState(801L, true, false);

        send(801L, "Invitee", "/start 800", null, null);
        send(801L, "Invitee", null, FLOW3_SCHOOL_RECHECK, null);
        send(801L, "Invitee", null, FLOW3_CONSENT_CONTINUE, null);
        send(801L, "Invitee", "Осман", null, null);
        send(801L, "Invitee", "19", null, null);
        send(801L, "Invitee", null, null, new BotContactPayload(801L, "+79991112233"));
        BotUpdateResult afterReadingLevel = send(801L, "Invitee", null, "FLOW4_LEVEL:KNOW_BASICS", null);

        assertThat(afterReadingLevel.messages()).hasSize(1);
        assertThat(afterReadingLevel.messages().getFirst().buttons())
            .hasSize(1)
            .allMatch(button -> button.type() == ButtonType.URL);

        BotUpdateResult afterAutoConfirmation = botFlowService.handleCourseChannelSubscriptionConfirmed(801L);

        assertThat(afterAutoConfirmation.messages())
            .anyMatch(message -> message.recipientUserId().equals(800L)
                && message.text().contains("+1 к общей копилке"));
        assertThat(afterAutoConfirmation.messages())
            .anyMatch(message -> message.recipientUserId().equals(801L)
                && message.text().contains("Вы записались — альхамдулиллях"));

        UserEntity referrer = userRepository.findByUserId(800L).orElseThrow();
        UserEntity invitee = userRepository.findByUserId(801L).orElseThrow();

        assertThat(referrer.getReferralPoints()).isEqualTo(1);
        assertThat(invitee.getReferralStatus()).isEqualTo(ReferralStatus.COUNTED);
        assertThat(invitee.isCourseChannelSubscribed()).isTrue();
        assertThat(flowContextRepository.findByUserId(801L).orElseThrow().getCurrentStep())
            .isEqualTo(FlowStep.FLOW2_WAIT_TERMS);
        assertThat(referralLinkUsageRepository.findByReferrerUserIdAndInviteeUserId(800L, 801L)).isPresent();
    }

    @Test
    void referralFlowDoesNotAwardReferrerWithoutCourseSubscription() {
        createReferrer(700L);
        subscriptionService.setSubscriptionState(701L, true, false);

        send(701L, "Invitee", "/start 700", null, null);
        send(701L, "Invitee", null, FLOW3_SCHOOL_RECHECK, null);
        send(701L, "Invitee", null, FLOW3_CONSENT_CONTINUE, null);
        send(701L, "Invitee", "Осман", null, null);
        send(701L, "Invitee", "19", null, null);
        send(701L, "Invitee", null, null, new BotContactPayload(701L, "+79991112233"));
        send(701L, "Invitee", null, "FLOW4_LEVEL:KNOW_BASICS", null);

        BotUpdateResult afterCourseCheck = send(701L, "Invitee", null, FLOW4_COURSE_CLICK_CONFIRMED, null);

        assertThat(afterCourseCheck.messages())
            .noneMatch(message -> message.recipientUserId().equals(700L)
                && message.text().contains("+1 к общей копилке"));
        assertThat(afterCourseCheck.messages())
            .anyMatch(message -> message.recipientUserId().equals(701L)
                && message.text().contains("подписка на канал курса ещё не активна"));

        UserEntity referrer = userRepository.findByUserId(700L).orElseThrow();
        UserEntity invitee = userRepository.findByUserId(701L).orElseThrow();

        assertThat(referrer.getReferralPoints()).isEqualTo(0);
        assertThat(invitee.getReferralStatus()).isEqualTo(ReferralStatus.PENDING);
        assertThat(invitee.isCourseChannelSubscribed()).isFalse();
        assertThat(referralLinkUsageRepository.findByReferrerUserIdAndInviteeUserId(700L, 701L)).isEmpty();
        assertThat(flowContextRepository.findByUserId(701L).orElseThrow().getCurrentStep())
            .isEqualTo(FlowStep.FLOW4_WAIT_COURSE_LINK_CONFIRM);
    }

    @Test
    void referralBonusIsIdempotent() {
        createReferrer(300L);
        createInvitee(301L, 300L);

        ReferralEventPayload payload = new ReferralEventPayload(
            UUID.randomUUID(),
            300L,
            301L,
            "ref:300:301",
            "flow4_course_link_click",
            Instant.now(),
            "https://t.me/tajwid_test_bot?start=300"
        );

        ReferralProcessResult first = referralBonusService.processReferralEvent(payload);
        ReferralProcessResult second = referralBonusService.processReferralEvent(new ReferralEventPayload(
            UUID.randomUUID(),
            300L,
            301L,
            "ref:300:301",
            "flow4_course_link_click",
            Instant.now(),
            "https://t.me/tajwid_test_bot?start=300"
        ));

        assertThat(first.alreadyCounted()).isTrue();
        assertThat(first.newlyCounted()).isTrue();
        assertThat(second.alreadyCounted()).isTrue();
        assertThat(second.newlyCounted()).isFalse();

        UserEntity referrer = userRepository.findByUserId(300L).orElseThrow();
        UserEntity invitee = userRepository.findByUserId(301L).orElseThrow();

        assertThat(referrer.getReferralPoints()).isEqualTo(1);
        assertThat(invitee.getReferralStatus()).isEqualTo(ReferralStatus.COUNTED);
    }

    @Test
    void referralStartIsBlockedForAlreadyProcessedInvitee() {
        createReferrer(500L);
        createInvitee(501L, 500L);

        BotUpdateResult result = send(501L, "Invitee", "/start 500", null, null);
        assertThat(result.messages()).hasSize(1);
        assertThat(result.messages().getFirst().text())
            .isEqualTo("Вы больше не можете использовать эту ссылку.");
    }

    private BotUpdateResult send(long userId,
                                 String firstName,
                                 String text,
                                 String callbackData,
                                 BotContactPayload contact) {
        return botFlowService.handleUpdate(new BotUpdateRequest(userId, firstName, text, callbackData, contact));
    }

    private void createReferrer(long userId) {
        UserEntity user = new UserEntity();
        user.setUserId(userId);
        user.setTelegramFirstName("Referrer");
        user.setUserName("Referrer");
        user.setAge(30);
        user.setPhone("+70000000000");
        user.setReadingLevel(ReadingLevel.KNOW_BASICS);
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

    private void createInvitee(long userId, long referrerUserId) {
        UserEntity user = new UserEntity();
        user.setUserId(userId);
        user.setTelegramFirstName("Invitee");
        user.setUserName("Invitee");
        user.setAge(20);
        user.setPhone("+71111111111");
        user.setReadingLevel(ReadingLevel.READ_BY_SYLLABLES);
        user.setSchoolChannelSubscribed(true);
        user.setCourseChannelSubscribed(false);
        user.setConsentGiven(true);
        user.setRegistrationCompleted(true);
        user.setRegistrationCompletedAt(Instant.now());
        user.setReferrerUserId(referrerUserId);
        user.setReferralStatus(ReferralStatus.PENDING);
        user.setReferralCountedAt(Instant.EPOCH);
        userRepository.save(user);
    }
}
