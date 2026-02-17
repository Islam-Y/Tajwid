package ru.muslim.tajwid.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.muslim.tajwid.config.TajwidBotProperties;
import ru.muslim.tajwid.domain.FlowContextEntity;
import ru.muslim.tajwid.domain.FlowStep;
import ru.muslim.tajwid.domain.FlowType;
import ru.muslim.tajwid.domain.ReadingLevel;
import ru.muslim.tajwid.domain.ReferralStatus;
import ru.muslim.tajwid.domain.UserEntity;
import ru.muslim.tajwid.repository.FlowContextRepository;
import ru.muslim.tajwid.repository.ReferralLinkUsageRepository;
import ru.muslim.tajwid.repository.UserRepository;
import ru.muslim.tajwid.repository.UserTagRepository;
import ru.muslim.tajwid.web.dto.BotContactPayload;
import ru.muslim.tajwid.web.dto.BotUpdateRequest;
import ru.muslim.tajwid.web.dto.BotUpdateResult;
import ru.muslim.tajwid.web.dto.ButtonType;
import ru.muslim.tajwid.support.PostgresContainerTestBase;

@SpringBootTest
class BotFlowServiceTest extends PostgresContainerTestBase {

    private static final String FLOW1_INTRO_CONTINUE = "FLOW1_INTRO_CONTINUE";
    private static final String FLOW1_CONSENT_CONTINUE = "FLOW1_CONSENT_CONTINUE";
    private static final String FLOW1_HAS_CHILDREN_NO = "FLOW1_HAS_CHILDREN:NO";
    private static final String FLOW1_HAS_CHILDREN_YES = "FLOW1_HAS_CHILDREN:YES";
    private static final String FLOW1_CHILDREN_STUDY_NO = "FLOW1_CHILDREN_STUDY:NO";
    private static final String FLOW2_TERMS = "FLOW2_TERMS";
    private static final String FLOW2_SHOW_LINK = "FLOW2_SHOW_LINK";
    private static final String FLOW3_SCHOOL_RECHECK = "FLOW3_SCHOOL_RECHECK";
    private static final String FLOW3_CONSENT_CONTINUE = "FLOW3_CONSENT_CONTINUE";
    private static final String FLOW4_HAS_CHILDREN_YES = "FLOW4_HAS_CHILDREN:YES";
    private static final String FLOW4_CHILDREN_STUDY_YES = "FLOW4_CHILDREN_STUDY:YES";

    @Autowired
    private BotFlowService botFlowService;

    @Autowired
    private ReferralBonusService referralBonusService;

    @Autowired
    private TajwidBotProperties properties;

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
        send(userId, "Ali", null, FLOW1_HAS_CHILDREN_NO, null);
        send(userId, "Ali", null, null, new BotContactPayload(userId, "+79990000000"));

        BotUpdateResult afterReadingLevel = send(userId, "Ali", null,
            "FLOW1_LEVEL:START_FROM_ZERO", null);
        assertThat(afterReadingLevel.messages()).hasSize(2);
        assertThat(afterReadingLevel.messages().getFirst().text())
            .contains("Вы успешно зарегистрированы на курс по таджвиду");
        assertThat(afterReadingLevel.messages().get(1).text())
            .contains("Вы записались — альхамдулиллях");

        send(userId, "Ali", null, FLOW2_TERMS, null);
        BotUpdateResult afterShowLink = send(userId, "Ali", null, FLOW2_SHOW_LINK, null);

        assertThat(afterShowLink.messages()).hasSize(2);
        assertThat(afterShowLink.messages().getFirst().text())
            .isEqualTo("https://t.me/tajwid_test_bot?start=1");

        UserEntity user = userRepository.findByUserId(userId).orElseThrow();
        assertThat(user.getHasChildren()).isFalse();
        assertThat(user.getChildrenCount()).isEqualTo(0);
        assertThat(user.getChildrenAges()).isNull();
        assertThat(user.getChildrenStudyQuran()).isNull();
        assertThat(user.isCourseChannelSubscribed()).isFalse();
        assertThat(user.getReferralStatus()).isEqualTo(ReferralStatus.NOT_APPLICABLE);
        assertThat(userTagRepository.existsByUserIdAndTag(userId, "Рефер получил ссылку")).isTrue();
    }

    @Test
    void childrenQuestionUsesYesNoButtons() {
        long userId = 11L;
        subscriptionService.setSubscriptionState(userId, true, true);

        send(userId, "Ali", "/start", null, null);
        send(userId, "Ali", null, FLOW1_INTRO_CONTINUE, null);
        send(userId, "Ali", null, FLOW1_CONSENT_CONTINUE, null);
        send(userId, "Ali", "Али", null, null);

        BotUpdateResult afterAge = send(userId, "Ali", "22", null, null);

        assertThat(afterAge.messages()).hasSize(2);
        assertThat(afterAge.messages().get(1).text()).contains("Есть ли у вас дети?");
        assertThat(afterAge.messages().get(1).buttons())
            .extracting(button -> button.value())
            .containsExactly(FLOW1_HAS_CHILDREN_YES, FLOW1_HAS_CHILDREN_NO);
    }

    @Test
    void hasChildrenNoSkipsQuranQuestionAndRequestsPhone() {
        long userId = 13L;
        subscriptionService.setSubscriptionState(userId, true, true);

        send(userId, "Ali", "/start", null, null);
        send(userId, "Ali", null, FLOW1_INTRO_CONTINUE, null);
        send(userId, "Ali", null, FLOW1_CONSENT_CONTINUE, null);
        send(userId, "Ali", "Али", null, null);
        send(userId, "Ali", "22", null, null);

        BotUpdateResult afterHasChildren = send(userId, "Ali", null, FLOW1_HAS_CHILDREN_NO, null);
        assertThat(afterHasChildren.messages()).hasSize(1);
        assertThat(afterHasChildren.messages().getFirst().text())
            .contains("Отправьте номер телефона через кнопку ниже")
            .doesNotContain("Изучают ли они Коран");
        assertThat(afterHasChildren.messages().getFirst().buttons())
            .hasSize(1)
            .anyMatch(button -> button.type() == ButtonType.REQUEST_CONTACT);

        FlowContextEntity context = flowContextRepository.findByUserId(userId).orElseThrow();
        assertThat(context.getCurrentStep()).isEqualTo(FlowStep.FLOW1_WAIT_PHONE);
        assertThat(context.getHasChildren()).isFalse();
        assertThat(context.getChildrenStudyQuran()).isNull();
    }

    @Test
    void hasChildrenYesAsksQuranQuestion() {
        long userId = 14L;
        subscriptionService.setSubscriptionState(userId, true, true);

        send(userId, "Ali", "/start", null, null);
        send(userId, "Ali", null, FLOW1_INTRO_CONTINUE, null);
        send(userId, "Ali", null, FLOW1_CONSENT_CONTINUE, null);
        send(userId, "Ali", "Али", null, null);
        send(userId, "Ali", "22", null, null);

        BotUpdateResult afterHasChildren = send(userId, "Ali", null, FLOW1_HAS_CHILDREN_YES, null);
        assertThat(afterHasChildren.messages()).hasSize(1);
        assertThat(afterHasChildren.messages().getFirst().text()).contains("Изучают ли они Коран");

        BotUpdateResult afterStudy = send(userId, "Ali", null, FLOW1_CHILDREN_STUDY_NO, null);
        assertThat(afterStudy.messages()).hasSize(1);
        assertThat(afterStudy.messages().getFirst().text()).contains("Отправьте номер телефона");

        FlowContextEntity context = flowContextRepository.findByUserId(userId).orElseThrow();
        assertThat(context.getHasChildren()).isTrue();
        assertThat(context.getChildrenStudyQuran()).isFalse();
    }

    @Test
    void staleConsentCallbackDoesNotInterruptFlow() {
        long userId = 15L;
        subscriptionService.setSubscriptionState(userId, true, true);

        send(userId, "Ali", "/start", null, null);
        send(userId, "Ali", null, FLOW1_INTRO_CONTINUE, null);
        send(userId, "Ali", null, FLOW1_CONSENT_CONTINUE, null);
        send(userId, "Ali", "Али", null, null);

        BotUpdateResult staleCallback = send(userId, "Ali", null, FLOW1_CONSENT_CONTINUE, null);
        assertThat(staleCallback.messages()).isEmpty();

        BotUpdateResult afterAge = send(userId, "Ali", "22", null, null);
        assertThat(afterAge.messages()).anyMatch(message -> message.text().contains("Есть ли у вас дети"));
    }

    @Test
    void flow2ShowLinkReturnsRetryButtonWhenReferralLinkSaveFails() {
        long userId = 12L;
        createReferrer(userId);

        FlowContextEntity context = new FlowContextEntity();
        context.setUserId(userId);
        context.setFlowType(FlowType.NORMAL);
        context.setCurrentStep(FlowStep.FLOW2_WAIT_LINK_REQUEST);
        flowContextRepository.save(context);

        String initialBotUsername = properties.getBotUsername();
        properties.setBotUsername("x".repeat(600));
        try {
            BotUpdateResult result = send(userId, "Ali", null, FLOW2_SHOW_LINK, null);

            assertThat(result.messages()).hasSize(1);
            assertThat(result.messages().getFirst().text())
                .contains("Временная ошибка при генерации ссылки");
            assertThat(result.messages().getFirst().buttons())
                .hasSize(1)
                .anyMatch(button -> button.type() == ButtonType.CALLBACK
                    && button.text().equals("Попробовать снова")
                    && button.value().equals(FLOW2_SHOW_LINK));

            UserEntity user = userRepository.findByUserId(userId).orElseThrow();
            assertThat(user.getReferralLinkCp()).isNull();
            assertThat(userTagRepository.existsByUserIdAndTag(userId, "Рефер получил ссылку")).isFalse();
            assertThat(flowContextRepository.findByUserId(userId).orElseThrow().getCurrentStep())
                .isEqualTo(FlowStep.FLOW2_WAIT_LINK_REQUEST);
        } finally {
            properties.setBotUsername(initialBotUsername);
        }
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
        send(200L, "Invitee", null, FLOW4_HAS_CHILDREN_YES, null);
        send(200L, "Invitee", null, FLOW4_CHILDREN_STUDY_YES, null);
        send(200L, "Invitee", null, null, new BotContactPayload(200L, "+79991112233"));
        send(200L, "Invitee", null, "FLOW4_LEVEL:KNOW_BASICS", null);

        BotUpdateResult afterCourseMembershipUpdate = botFlowService.handleCourseChannelSubscriptionConfirmed(200L);

        assertThat(afterCourseMembershipUpdate.messages())
            .anyMatch(message -> message.recipientUserId().equals(100L)
                && message.text().contains("+1 к общей копилке"));

        UserEntity referrer = userRepository.findByUserId(100L).orElseThrow();
        UserEntity invitee = userRepository.findByUserId(200L).orElseThrow();

        assertThat(referrer.getReferralPoints()).isEqualTo(1);
        assertThat(invitee.getHasChildren()).isTrue();
        assertThat(invitee.getChildrenStudyQuran()).isTrue();
        assertThat(invitee.getReferralStatus()).isEqualTo(ReferralStatus.COUNTED);
        assertThat(invitee.isCourseChannelSubscribed()).isTrue();
        assertThat(flowContextRepository.findByUserId(200L).orElseThrow().getCurrentStep())
            .isEqualTo(FlowStep.FLOW2_WAIT_TERMS);
        assertThat(flowContextRepository.findByUserId(200L).orElseThrow().getFlowType())
            .isEqualTo(FlowType.REFERRAL);
    }

    @Test
    void membershipTriggerDoesNotAwardWhenCourseSubscriptionIsNotConfirmed() {
        createReferrer(900L);
        createInvitee(901L, 900L);
        subscriptionService.setSubscriptionState(901L, true, false);

        BotUpdateResult result = botFlowService.handleCourseChannelSubscriptionConfirmed(901L);

        assertThat(result.messages())
            .noneMatch(message -> message.recipientUserId().equals(900L)
                && message.text().contains("+1 к общей копилке"));

        UserEntity referrer = userRepository.findByUserId(900L).orElseThrow();
        UserEntity invitee = userRepository.findByUserId(901L).orElseThrow();
        assertThat(referrer.getReferralPoints()).isEqualTo(0);
        assertThat(invitee.getReferralStatus()).isEqualTo(ReferralStatus.PENDING);
        assertThat(invitee.isCourseChannelSubscribed()).isFalse();
    }

    @Test
    void scheduledAutomationCanRecoverMissedReferralAward() {
        createReferrer(800L);
        subscriptionService.setSubscriptionState(801L, true, true);

        send(801L, "Invitee", "/start 800", null, null);
        send(801L, "Invitee", null, FLOW3_SCHOOL_RECHECK, null);
        send(801L, "Invitee", null, FLOW3_CONSENT_CONTINUE, null);
        send(801L, "Invitee", "Осман", null, null);
        send(801L, "Invitee", "19", null, null);
        send(801L, "Invitee", null, FLOW4_HAS_CHILDREN_YES, null);
        send(801L, "Invitee", null, FLOW4_CHILDREN_STUDY_YES, null);
        send(801L, "Invitee", null, null, new BotContactPayload(801L, "+79991112233"));
        send(801L, "Invitee", null, "FLOW4_LEVEL:KNOW_BASICS", null);

        BotUpdateResult automationResult = botFlowService.processScheduledAutomations();

        assertThat(automationResult.messages())
            .anyMatch(message -> message.recipientUserId().equals(800L)
                && message.text().contains("+1 к общей копилке"));

        UserEntity referrer = userRepository.findByUserId(800L).orElseThrow();
        UserEntity invitee = userRepository.findByUserId(801L).orElseThrow();

        assertThat(referrer.getReferralPoints()).isEqualTo(1);
        assertThat(invitee.getReferralStatus()).isEqualTo(ReferralStatus.COUNTED);
        assertThat(invitee.isCourseChannelSubscribed()).isTrue();
        assertThat(referralLinkUsageRepository.findByReferrerUserIdAndInviteeUserId(800L, 801L)).isPresent();
    }

    @Test
    void flow2WaitsForCourseSubscriptionEvenAfterDelay() {
        long userId = 21L;
        subscriptionService.setSubscriptionState(userId, true, false);

        Duration originalDelay = properties.getFlow1ToFlow2Delay();
        try {
            properties.setFlow1ToFlow2Delay(Duration.ofMinutes(3));

            send(userId, "Ali", "/start", null, null);
            send(userId, "Ali", null, FLOW1_INTRO_CONTINUE, null);
            send(userId, "Ali", null, FLOW1_CONSENT_CONTINUE, null);
            send(userId, "Ali", "Али", null, null);
            send(userId, "Ali", "22", null, null);
            send(userId, "Ali", null, FLOW1_HAS_CHILDREN_NO, null);
            send(userId, "Ali", null, null, new BotContactPayload(userId, "+79990000000"));

            BotUpdateResult afterReadingLevel = send(userId, "Ali", null,
                "FLOW1_LEVEL:START_FROM_ZERO", null);
            assertThat(afterReadingLevel.messages()).hasSize(1);

            FlowContextEntity waitingContext = flowContextRepository.findByUserId(userId).orElseThrow();
            assertThat(waitingContext.getCurrentStep()).isEqualTo(FlowStep.FLOW1_WAIT_REFERRAL_PROGRAM_ANNOUNCEMENT);
            assertThat(waitingContext.isReferralAnnouncementSent()).isFalse();
            assertThat(waitingContext.getReferralAnnouncementDueAt()).isNotNull();

            waitingContext.setReferralAnnouncementDueAt(Instant.now().minusSeconds(1));
            flowContextRepository.save(waitingContext);
            BotUpdateResult withoutCourseSubscription = botFlowService.processScheduledAutomations();
            assertThat(withoutCourseSubscription.messages())
                .noneMatch(message -> message.recipientUserId().equals(userId)
                    && message.text().contains("Вы записались — альхамдулиллях"));

            FlowContextEntity stillWaitingContext = flowContextRepository.findByUserId(userId).orElseThrow();
            assertThat(stillWaitingContext.getCurrentStep()).isEqualTo(FlowStep.FLOW1_WAIT_REFERRAL_PROGRAM_ANNOUNCEMENT);
            assertThat(stillWaitingContext.isReferralAnnouncementSent()).isFalse();

            subscriptionService.setSubscriptionState(userId, true, true);
            stillWaitingContext.setReferralAnnouncementDueAt(Instant.now().minusSeconds(1));
            flowContextRepository.save(stillWaitingContext);

            BotUpdateResult afterSubscription = botFlowService.processScheduledAutomations();
            assertThat(afterSubscription.messages())
                .anyMatch(message -> message.recipientUserId().equals(userId)
                    && message.text().contains("Вы записались — альхамдулиллях"));

            UserEntity user = userRepository.findByUserId(userId).orElseThrow();
            assertThat(user.isCourseChannelSubscribed()).isTrue();
        } finally {
            properties.setFlow1ToFlow2Delay(originalDelay);
        }
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
        send(701L, "Invitee", null, FLOW4_HAS_CHILDREN_YES, null);
        send(701L, "Invitee", null, FLOW4_CHILDREN_STUDY_YES, null);
        send(701L, "Invitee", null, null, new BotContactPayload(701L, "+79991112233"));
        send(701L, "Invitee", null, "FLOW4_LEVEL:KNOW_BASICS", null);

        BotUpdateResult automationResult = botFlowService.processScheduledAutomations();

        assertThat(automationResult.messages())
            .noneMatch(message -> message.recipientUserId().equals(700L)
                && message.text().contains("+1 к общей копилке"));

        UserEntity referrer = userRepository.findByUserId(700L).orElseThrow();
        UserEntity invitee = userRepository.findByUserId(701L).orElseThrow();

        assertThat(referrer.getReferralPoints()).isEqualTo(0);
        assertThat(invitee.getReferralStatus()).isEqualTo(ReferralStatus.PENDING);
        assertThat(invitee.isCourseChannelSubscribed()).isFalse();
        assertThat(referralLinkUsageRepository.findByReferrerUserIdAndInviteeUserId(700L, 701L)).isEmpty();
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
        user.setTelegramUsername("referrer");
        user.setUserName("Referrer");
        user.setAge(30);
        user.setPhone("+70000000000");
        user.setReadingLevel(ReadingLevel.KNOW_BASICS);
        user.setHasChildren(false);
        user.setChildrenCount(0);
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
        user.setTelegramUsername("invitee");
        user.setUserName("Invitee");
        user.setAge(20);
        user.setPhone("+71111111111");
        user.setReadingLevel(ReadingLevel.READ_BY_SYLLABLES);
        user.setHasChildren(false);
        user.setChildrenCount(0);
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
