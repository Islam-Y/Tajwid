package ru.muslim.tajwid.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.muslim.tajwid.config.TajwidBotProperties;
import ru.muslim.tajwid.domain.FlowContextEntity;
import ru.muslim.tajwid.domain.FlowStep;
import ru.muslim.tajwid.domain.FlowType;
import ru.muslim.tajwid.domain.ReadingLevel;
import ru.muslim.tajwid.domain.RegistrationNotificationAdminEntity;
import ru.muslim.tajwid.domain.ReferralStatus;
import ru.muslim.tajwid.domain.UserEntity;
import ru.muslim.tajwid.domain.UserTagEntity;
import ru.muslim.tajwid.repository.FlowContextRepository;
import ru.muslim.tajwid.repository.RegistrationNotificationAdminRepository;
import ru.muslim.tajwid.repository.ReferralLinkUsageRepository;
import ru.muslim.tajwid.repository.UserRepository;
import ru.muslim.tajwid.repository.UserTagRepository;
import ru.muslim.tajwid.web.dto.BotButtonResponse;
import ru.muslim.tajwid.web.dto.BotContactPayload;
import ru.muslim.tajwid.web.dto.BotMessageResponse;
import ru.muslim.tajwid.web.dto.BotUpdateRequest;
import ru.muslim.tajwid.web.dto.BotUpdateResult;
import ru.muslim.tajwid.web.dto.ButtonType;

@Service
@RequiredArgsConstructor
public class BotFlowService {

    private static final Logger log = LoggerFactory.getLogger(BotFlowService.class);

    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\p{L}][\\p{L}\\s-]{1,49}$");

    private static final String FLOW1_INTRO_CONTINUE = "FLOW1_INTRO_CONTINUE";
    private static final String FLOW1_SCHOOL_RECHECK = "FLOW1_SCHOOL_RECHECK";
    private static final String FLOW1_CONSENT_CONTINUE = "FLOW1_CONSENT_CONTINUE";
    private static final String FLOW1_COURSE_RECHECK = "FLOW1_COURSE_RECHECK";
    private static final String FLOW1_LEVEL_PREFIX = "FLOW1_LEVEL:";
    private static final String FLOW1_HAS_CHILDREN_YES = "FLOW1_HAS_CHILDREN:YES";
    private static final String FLOW1_HAS_CHILDREN_NO = "FLOW1_HAS_CHILDREN:NO";
    private static final String FLOW1_CHILDREN_STUDY_YES = "FLOW1_CHILDREN_STUDY:YES";
    private static final String FLOW1_CHILDREN_STUDY_NO = "FLOW1_CHILDREN_STUDY:NO";

    private static final String FLOW2_TERMS = "FLOW2_TERMS";
    private static final String FLOW2_SHOW_LINK = "FLOW2_SHOW_LINK";

    private static final String FLOW3_SCHOOL_RECHECK = "FLOW3_SCHOOL_RECHECK";
    private static final String FLOW3_CONSENT_CONTINUE = "FLOW3_CONSENT_CONTINUE";

    private static final String FLOW4_LEVEL_PREFIX = "FLOW4_LEVEL:";
    private static final String FLOW4_COURSE_CLICK_CONFIRMED = "FLOW4_COURSE_CLICK_CONFIRMED";
    private static final String FLOW4_HAS_CHILDREN_YES = "FLOW4_HAS_CHILDREN:YES";
    private static final String FLOW4_HAS_CHILDREN_NO = "FLOW4_HAS_CHILDREN:NO";
    private static final String FLOW4_CHILDREN_STUDY_YES = "FLOW4_CHILDREN_STUDY:YES";
    private static final String FLOW4_CHILDREN_STUDY_NO = "FLOW4_CHILDREN_STUDY:NO";

    private static final String TAG_CLICKED_SUBSCRIBED_BUTTON = "Нажал на кнопку подписался";
    private static final String TAG_SUBSCRIBED_VIA_BOT = "Подписка с бота";
    private static final String TAG_REFERRER_GOT_LINK = "Рефер получил ссылку";
    private static final String TAG_COURSE_CHANNEL_CONFIRMED = "Подписка на канал курса подтверждена";
    private static final String DEFAULT_ADMIN_USERNAME = "Arslangaray";

    private static final EnumSet<ReferralStatus> BLOCKING_REFERRAL_STATUSES =
        EnumSet.of(ReferralStatus.PENDING, ReferralStatus.COUNTED);
    private static final int REFERRAL_LINK_MAX_LENGTH = 512;

    private final FlowContextRepository flowContextRepository;
    private final UserRepository userRepository;
    private final UserTagRepository userTagRepository;
    private final RegistrationNotificationAdminRepository registrationNotificationAdminRepository;
    private final ReferralLinkUsageRepository referralLinkUsageRepository;
    private final ChannelSubscriptionService channelSubscriptionService;
    private final ReferralBonusService referralBonusService;
    private final TajwidBotProperties properties;

    @Transactional
    public BotUpdateResult handleUpdate(BotUpdateRequest request) {
        FlowContextEntity context = flowContextRepository.findByUserId(request.userId())
            .orElseGet(() -> createDefaultContext(request.userId(), request.firstName()));
        context.setTelegramFirstName(request.firstName());
        context.setTelegramUsername(normalizeUsername(request.username()));

        List<BotMessageResponse> responses = new ArrayList<>();

        String text = normalize(request.text());
        String callback = normalize(request.callbackData());

        if (text != null && text.startsWith("/start")) {
            handleStartCommand(request, context, text, responses);
            flowContextRepository.save(context);
            return new BotUpdateResult(responses);
        }

        if (callback != null) {
            handleCallback(request, context, callback, responses);
            flowContextRepository.save(context);
            return new BotUpdateResult(responses);
        }

        if (request.contact() != null) {
            handleContact(request, context, request.contact(), responses);
            flowContextRepository.save(context);
            return new BotUpdateResult(responses);
        }

        if (text != null) {
            handleText(request, context, text, responses);
            flowContextRepository.save(context);
            return new BotUpdateResult(responses);
        }

        addMessage(responses, request.userId(), "Нажмите /start для начала регистрации.");
        flowContextRepository.save(context);
        return new BotUpdateResult(responses);
    }

    @Transactional
    public BotUpdateResult handleCourseChannelSubscriptionConfirmed(long userId) {
        List<BotMessageResponse> responses = new ArrayList<>();

        SubscriptionCheckResult result = channelSubscriptionService.checkCourseChannelSubscription(userId);
        if (result != SubscriptionCheckResult.SUBSCRIBED) {
            log.info("Skip course membership confirmation for user {}: {}", userId, result);
            return new BotUpdateResult(responses);
        }

        FlowContextEntity context = flowContextRepository.findByUserId(userId).orElse(null);

        confirmCourseSubscriptionAndProcessReferral(
            userId,
            context,
            responses,
            "course_channel_membership_update"
        );

        return new BotUpdateResult(responses);
    }

    @Transactional
    public BotUpdateResult processScheduledAutomations() {
        List<BotMessageResponse> responses = new ArrayList<>();

        processDueReferralAnnouncements(Instant.now(), responses);
        processPendingReferralAwards(responses);

        return new BotUpdateResult(responses);
    }

    private void handleStartCommand(BotUpdateRequest request,
                                    FlowContextEntity context,
                                    String commandText,
                                    List<BotMessageResponse> responses) {
        String[] parts = commandText.trim().split("\\s+");
        if (parts.length == 1) {
            startNormalFlow(request, context, responses);
            return;
        }

        Long referrerId;
        try {
            referrerId = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            setIdleContext(context);
            addMessage(responses, request.userId(),
                "Невозможно использовать текущую реферальную ссылку.");
            return;
        }

        startReferralFlow(request, context, referrerId, responses);
    }

    private void startNormalFlow(BotUpdateRequest request,
                                 FlowContextEntity context,
                                 List<BotMessageResponse> responses) {
        resetContext(context, request.userId(), request.firstName(), FlowType.NORMAL);

        SubscriptionCheckResult subscription =
            channelSubscriptionService.checkSchoolChannelSubscription(request.userId());

        if (subscription == SubscriptionCheckResult.SUBSCRIBED) {
            addMessage(responses, request.userId(),
                "Ассаламу алейкум ва рахматуЛлахи ва баракатух! 🌙\n"
                    + "Вы записываетесь на бесплатный курс по таджвиду в месяц Рамадан.",
                callbackButton("Продолжить", FLOW1_INTRO_CONTINUE));
            context.setCurrentStep(FlowStep.FLOW1_WAIT_INTRO_CONTINUE);
            return;
        }

        addMessage(responses, request.userId(),
            "Ассаламу алейкум ва рахматуЛлахи ва баракатух! 🌙\n"
                + "Вы записываетесь на бесплатный курс по таджвиду в месяц Рамадан.\n"
                + "Для участия необходимо сначала подписаться на наш Telegram-канал.\n"
                + "Пожалуйста, подпишитесь на канал и затем нажмите кнопку ниже для продолжения регистрации.",
            urlButton("Telegram-канал школы", properties.getSchoolChannelUrl()),
            callbackButton("Подписался", FLOW1_SCHOOL_RECHECK));
        context.setCurrentStep(FlowStep.FLOW1_WAIT_SCHOOL_SUBSCRIPTION_RECHECK);
    }

    private void startReferralFlow(BotUpdateRequest request,
                                   FlowContextEntity context,
                                   Long referrerUserId,
                                   List<BotMessageResponse> responses) {
        resetContext(context, request.userId(), request.firstName(), FlowType.REFERRAL);

        if (referrerUserId.equals(request.userId()) || !userRepository.existsByUserId(referrerUserId)) {
            setIdleContext(context);
            addMessage(responses, request.userId(),
                "Невозможно использовать текущую реферальную ссылку.");
            return;
        }

        boolean alreadyHandled = userRepository
            .existsByUserIdAndReferralStatusIn(request.userId(), BLOCKING_REFERRAL_STATUSES)
            || referralLinkUsageRepository.existsByInviteeUserId(request.userId());

        if (alreadyHandled) {
            setIdleContext(context);
            addMessage(responses, request.userId(), "Вы больше не можете использовать эту ссылку.");
            return;
        }

        context.setReferrerUserId(referrerUserId);
        context.setReferralEntrySource("referral_link");
        context.setReferralEntryAt(Instant.now());
        context.setCurrentStep(FlowStep.FLOW3_WAIT_SCHOOL_SUBSCRIPTION_RECHECK);

        addMessage(responses, request.userId(),
            "Ассаламу алейкум ва рахматуЛлахи ва баракатух! 🌙\n"
                + "Ваш друг позаботился о вас и пригласил на наш курс по таджвиду.\n"
                + "Для участия необходимо сначала подписаться на наш Telegram-канал.\n"
                + "Пожалуйста, подпишитесь на канал и затем нажмите кнопку ниже для продолжения регистрации.",
            callbackButton("Подписался", FLOW3_SCHOOL_RECHECK),
            urlButton("Telegram канал", properties.getSchoolChannelUrl()));
    }

    private void handleCallback(BotUpdateRequest request,
                                FlowContextEntity context,
                                String callback,
                                List<BotMessageResponse> responses) {
        if (callback.startsWith(FLOW1_LEVEL_PREFIX)) {
            handleFlow1ReadingLevel(request, context, callback, responses);
            return;
        }

        if (callback.startsWith(FLOW4_LEVEL_PREFIX)) {
            handleFlow4ReadingLevel(request, context, callback, responses);
            return;
        }

        switch (callback) {
            case FLOW1_INTRO_CONTINUE -> onFlow1IntroContinue(request, context, responses);
            case FLOW1_SCHOOL_RECHECK -> onFlow1SchoolRecheck(request, context, responses);
            case FLOW1_CONSENT_CONTINUE -> onFlow1ConsentContinue(request, context, responses);
            case FLOW1_COURSE_RECHECK -> onFlow1CourseRecheck(request, context, responses);
            case FLOW1_HAS_CHILDREN_YES -> onFlow1HasChildren(request, context, responses, true);
            case FLOW1_HAS_CHILDREN_NO -> onFlow1HasChildren(request, context, responses, false);
            case FLOW1_CHILDREN_STUDY_YES -> onFlow1ChildrenStudy(request, context, responses, true);
            case FLOW1_CHILDREN_STUDY_NO -> onFlow1ChildrenStudy(request, context, responses, false);
            case FLOW2_TERMS -> onFlow2Terms(request, context, responses);
            case FLOW2_SHOW_LINK -> onFlow2ShowLink(request, context, responses);
            case FLOW3_SCHOOL_RECHECK -> onFlow3SchoolRecheck(request, context, responses);
            case FLOW3_CONSENT_CONTINUE -> onFlow3ConsentContinue(request, context, responses);
            case FLOW4_HAS_CHILDREN_YES -> onFlow4HasChildren(request, context, responses, true);
            case FLOW4_HAS_CHILDREN_NO -> onFlow4HasChildren(request, context, responses, false);
            case FLOW4_CHILDREN_STUDY_YES -> onFlow4ChildrenStudy(request, context, responses, true);
            case FLOW4_CHILDREN_STUDY_NO -> onFlow4ChildrenStudy(request, context, responses, false);
            case FLOW4_COURSE_CLICK_CONFIRMED -> onFlow4CourseClickConfirmed(request, context, responses);
            default -> addMessage(responses, request.userId(),
                "Кнопка неактуальна. Нажмите /start для начала сценария заново.");
        }
    }

    private void onFlow1IntroContinue(BotUpdateRequest request,
                                      FlowContextEntity context,
                                      List<BotMessageResponse> responses) {
        if (context.getCurrentStep() != FlowStep.FLOW1_WAIT_INTRO_CONTINUE) {
            return;
        }

        sendConsentMessage(request.userId(), responses, FLOW1_CONSENT_CONTINUE);
        context.setCurrentStep(FlowStep.FLOW1_WAIT_CONSENT_ACCEPT);
    }

    private void onFlow1SchoolRecheck(BotUpdateRequest request,
                                      FlowContextEntity context,
                                      List<BotMessageResponse> responses) {
        addTempTag(context, TAG_CLICKED_SUBSCRIBED_BUTTON);

        SubscriptionCheckResult result =
            channelSubscriptionService.checkSchoolChannelSubscription(request.userId());

        if (result == SubscriptionCheckResult.SUBSCRIBED) {
            addTempTag(context, TAG_SUBSCRIBED_VIA_BOT);
            context.setSchoolChannelSubscribed(true);
            sendConsentMessage(request.userId(), responses, FLOW1_CONSENT_CONTINUE);
            context.setCurrentStep(FlowStep.FLOW1_WAIT_CONSENT_ACCEPT);
            return;
        }

        if (result == SubscriptionCheckResult.ERROR) {
            addMessage(responses, request.userId(),
                "Временная ошибка при проверке подписки. Пожалуйста, попробуйте снова.",
                callbackButton("Проверить снова", FLOW1_SCHOOL_RECHECK));
            context.setCurrentStep(FlowStep.FLOW1_WAIT_SCHOOL_SUBSCRIPTION_RECHECK);
            return;
        }

        addMessage(responses, request.userId(),
            "Возможно, подписка ещё не оформилась.\n"
                + "Пожалуйста, подпишитесь на Telegram-канал и затем нажмите кнопку снова.",
            urlButton("Telegram-канал школы", properties.getSchoolChannelUrl()),
            callbackButton("Проверить снова", FLOW1_SCHOOL_RECHECK));
        context.setCurrentStep(FlowStep.FLOW1_WAIT_SCHOOL_SUBSCRIPTION_RECHECK);
    }

    private void onFlow1ConsentContinue(BotUpdateRequest request,
                                        FlowContextEntity context,
                                        List<BotMessageResponse> responses) {
        if (context.getCurrentStep() != FlowStep.FLOW1_WAIT_CONSENT_ACCEPT) {
            return;
        }

        context.setConsentGiven(true);
        context.setTelegramFirstName(request.firstName());
        addMessage(responses, request.userId(),
            "Отлично, продолжаем регистрацию.\nУкажите ваше имя, пожалуйста.");
        context.setCurrentStep(FlowStep.FLOW1_WAIT_NAME);
    }

    private void onFlow1CourseRecheck(BotUpdateRequest request,
                                      FlowContextEntity context,
                                      List<BotMessageResponse> responses) {
        if (context.getCurrentStep() != FlowStep.FLOW1_WAIT_COURSE_SUBSCRIPTION_RECHECK) {
            return;
        }

        SubscriptionCheckResult result =
            channelSubscriptionService.checkCourseChannelSubscription(request.userId());

        if (result == SubscriptionCheckResult.SUBSCRIBED) {
            userRepository.findByUserId(request.userId()).ifPresent(user -> {
                user.setCourseChannelSubscribed(true);
                userRepository.save(user);
            });
            assignTag(request.userId(), TAG_COURSE_CHANNEL_CONFIRMED);
            startFlow2(request.userId(), context, responses);
            return;
        }

        if (result == SubscriptionCheckResult.ERROR) {
            addMessage(responses, request.userId(),
                "Временная ошибка при проверке подписки. Нажмите кнопку и попробуйте снова.",
                callbackButton("Проверить подписку", FLOW1_COURSE_RECHECK));
            return;
        }

        addMessage(responses, request.userId(),
            "Похоже, подписка на канал курса ещё не активна.\n"
                + "Подпишитесь на канал курса и нажмите кнопку проверки снова.",
            urlButton("Канал по курсу", properties.getCourseChannelUrl()),
            callbackButton("Проверить подписку", FLOW1_COURSE_RECHECK));
    }

    private void onFlow2Terms(BotUpdateRequest request,
                              FlowContextEntity context,
                              List<BotMessageResponse> responses) {
        if (context.getCurrentStep() != FlowStep.FLOW2_WAIT_TERMS) {
            addMessage(responses, request.userId(),
                "Нажмите /start, чтобы начать сценарий заново.");
            return;
        }

        addMessage(responses, request.userId(),
            "📌 Условия участия:\n"
                + "1) У вас персональная ссылка приглашения.\n"
                + "2) Приглашение засчитывается после того, как друг перейдет по ссылке, "
                + "завершит регистрацию и перейдет в канал курса.\n"
                + "3) Запрещено использовать фейковые аккаунты и ботов.\n"
                + "4) При нарушениях участие аннулируется.",
            callbackButton("А вот и ваша ссылка", FLOW2_SHOW_LINK));
        context.setCurrentStep(FlowStep.FLOW2_WAIT_LINK_REQUEST);
    }

    private void onFlow2ShowLink(BotUpdateRequest request,
                                 FlowContextEntity context,
                                 List<BotMessageResponse> responses) {
        Optional<UserEntity> maybeUser = userRepository.findByUserId(request.userId());
        if (maybeUser.isEmpty()) {
            addMessage(responses, request.userId(),
                "Сначала завершите регистрацию. Нажмите /start.");
            return;
        }

        UserEntity user = maybeUser.get();
        boolean hasTag = userTagRepository.existsByUserIdAndTag(request.userId(), TAG_REFERRER_GOT_LINK);
        String link = user.getReferralLinkCp();

        if (!hasTag || link == null || link.isBlank()) {
            try {
                link = buildReferralLink(request.userId());
                if (link.length() > REFERRAL_LINK_MAX_LENGTH) {
                    throw new IllegalStateException("Referral link length exceeds storage limit");
                }
                user.setReferralLinkCp(link);
                userRepository.save(user);
                assignTag(request.userId(), TAG_REFERRER_GOT_LINK);
            } catch (RuntimeException ex) {
                log.warn("Failed to generate or save referral link for user {}", request.userId(), ex);
                addMessage(responses, request.userId(),
                    "Временная ошибка при генерации ссылки. Нажмите кнопку и попробуйте снова.",
                    callbackButton("Попробовать снова", FLOW2_SHOW_LINK));
                context.setCurrentStep(FlowStep.FLOW2_WAIT_LINK_REQUEST);
                return;
            }
        }

        addMessage(responses, request.userId(), link);
        addMessage(responses, request.userId(),
            "Выше ваша пригласительная ссылка.\n"
                + "Начинайте прямо сейчас и приводите братьев к благу 🤲");
        context.setCurrentStep(FlowStep.IDLE);
    }

    private void onFlow3SchoolRecheck(BotUpdateRequest request,
                                      FlowContextEntity context,
                                      List<BotMessageResponse> responses) {
        if (context.getFlowType() != FlowType.REFERRAL || context.getReferrerUserId() <= 0) {
            addMessage(responses, request.userId(),
                "Технический контекст не найден. Запустите сценарий по реферальной ссылке снова.");
            return;
        }

        addTempTag(context, TAG_CLICKED_SUBSCRIBED_BUTTON);

        SubscriptionCheckResult result =
            channelSubscriptionService.checkSchoolChannelSubscription(request.userId());

        if (result == SubscriptionCheckResult.SUBSCRIBED) {
            addTempTag(context, TAG_SUBSCRIBED_VIA_BOT);
            context.setSchoolChannelSubscribed(true);
            sendConsentMessage(request.userId(), responses, FLOW3_CONSENT_CONTINUE);
            context.setCurrentStep(FlowStep.FLOW3_WAIT_CONSENT_ACCEPT);
            return;
        }

        if (result == SubscriptionCheckResult.ERROR) {
            addMessage(responses, request.userId(),
                "Временная ошибка при проверке подписки. Нажмите кнопку ниже для повтора.",
                callbackButton("Готово", FLOW3_SCHOOL_RECHECK));
            context.setCurrentStep(FlowStep.FLOW3_WAIT_SCHOOL_SUBSCRIPTION_RECHECK);
            return;
        }

        addMessage(responses, request.userId(),
            "Возможно, подписка ещё не оформилась.\n"
                + "Пожалуйста, подпишитесь на Telegram-канал и затем нажмите кнопку снова.",
            urlButton("Telegram канал", properties.getSchoolChannelUrl()),
            callbackButton("Готово", FLOW3_SCHOOL_RECHECK));
        context.setCurrentStep(FlowStep.FLOW3_WAIT_SCHOOL_SUBSCRIPTION_RECHECK);
    }

    private void onFlow3ConsentContinue(BotUpdateRequest request,
                                        FlowContextEntity context,
                                        List<BotMessageResponse> responses) {
        if (context.getCurrentStep() != FlowStep.FLOW3_WAIT_CONSENT_ACCEPT) {
            return;
        }

        context.setConsentGiven(true);
        context.setCurrentStep(FlowStep.FLOW4_WAIT_NAME);
        addMessage(responses, request.userId(),
            "Отлично, продолжаем регистрацию.\nУкажите ваше имя, пожалуйста.");
    }

    private void onFlow4CourseClickConfirmed(BotUpdateRequest request,
                                             FlowContextEntity context,
                                             List<BotMessageResponse> responses) {
        if (context.getCurrentStep() != FlowStep.FLOW4_WAIT_COURSE_LINK_CONFIRM) {
            return;
        }

        SubscriptionCheckResult subscriptionResult =
            channelSubscriptionService.checkCourseChannelSubscription(request.userId());

        if (subscriptionResult == SubscriptionCheckResult.ERROR) {
            addMessage(responses, request.userId(),
                "Временная ошибка при проверке подписки. Нажмите кнопку и попробуйте снова.",
                callbackButton("Проверить подписку", FLOW4_COURSE_CLICK_CONFIRMED));
            return;
        }

        if (subscriptionResult == SubscriptionCheckResult.NOT_SUBSCRIBED) {
            addMessage(responses, request.userId(),
                "Похоже, подписка на канал курса ещё не активна.\n"
                    + "Подпишитесь на канал курса и нажмите кнопку проверки снова.",
                urlButton("Канал по курсу", properties.getCourseChannelUrl()),
                callbackButton("Проверить подписку", FLOW4_COURSE_CLICK_CONFIRMED));
            return;
        }

        confirmCourseSubscriptionAndProcessReferral(
            request.userId(),
            context,
            responses,
            "flow4_course_link_click"
        );
    }

    private void handleText(BotUpdateRequest request,
                            FlowContextEntity context,
                            String text,
                            List<BotMessageResponse> responses) {
        switch (context.getCurrentStep()) {
            case FLOW1_WAIT_NAME -> handleNameInput(request.userId(), context, text, responses,
                FlowStep.FLOW1_WAIT_AGE, false);
            case FLOW4_WAIT_NAME -> handleNameInput(request.userId(), context, text, responses,
                FlowStep.FLOW4_WAIT_AGE, true);
            case FLOW1_WAIT_AGE -> handleAgeInput(request.userId(), context, text, responses,
                FlowStep.FLOW1_WAIT_HAS_CHILDREN, false);
            case FLOW4_WAIT_AGE -> handleAgeInput(request.userId(), context, text, responses,
                FlowStep.FLOW4_WAIT_HAS_CHILDREN, true);
            case FLOW1_WAIT_HAS_CHILDREN, FLOW1_WAIT_CHILDREN_COUNT -> handleHasChildrenInput(
                request.userId(), context, text, responses,
                FlowStep.FLOW1_WAIT_CHILDREN_QURAN_STUDY,
                FlowStep.FLOW1_WAIT_PHONE);
            case FLOW4_WAIT_HAS_CHILDREN, FLOW4_WAIT_CHILDREN_COUNT -> handleHasChildrenInput(
                request.userId(), context, text, responses,
                FlowStep.FLOW4_WAIT_CHILDREN_QURAN_STUDY,
                FlowStep.FLOW4_WAIT_PHONE);
            case FLOW1_WAIT_CHILD_AGE -> handleLegacyChildAgeInput(request.userId(), context, responses,
                FlowStep.FLOW1_WAIT_CHILDREN_QURAN_STUDY,
                FLOW1_CHILDREN_STUDY_YES,
                FLOW1_CHILDREN_STUDY_NO);
            case FLOW4_WAIT_CHILD_AGE -> handleLegacyChildAgeInput(request.userId(), context, responses,
                FlowStep.FLOW4_WAIT_CHILDREN_QURAN_STUDY,
                FLOW4_CHILDREN_STUDY_YES,
                FLOW4_CHILDREN_STUDY_NO);
            case FLOW1_WAIT_CHILDREN_QURAN_STUDY -> handleChildrenStudyTextInput(request.userId(), context, text,
                responses, FlowStep.FLOW1_WAIT_PHONE, FLOW1_CHILDREN_STUDY_YES, FLOW1_CHILDREN_STUDY_NO);
            case FLOW4_WAIT_CHILDREN_QURAN_STUDY -> handleChildrenStudyTextInput(request.userId(), context, text,
                responses, FlowStep.FLOW4_WAIT_PHONE, FLOW4_CHILDREN_STUDY_YES, FLOW4_CHILDREN_STUDY_NO);
            case FLOW1_WAIT_REFERRAL_PROGRAM_ANNOUNCEMENT, FLOW4_WAIT_REFERRAL_PROGRAM_ANNOUNCEMENT ->
                addMessage(responses, request.userId(),
                    "Почти готово 🙌 Реферальную программу пришлю автоматически через пару минут.");
            case FLOW1_WAIT_PHONE, FLOW4_WAIT_PHONE -> addMessage(responses, request.userId(),
                "Пожалуйста, отправьте ваш номер через кнопку в боте.",
                requestContactButton("Отправить номер"));
            default -> addMessage(responses, request.userId(),
                "Нажмите /start для начала или продолжайте сценарий кнопками.");
        }
    }

    private void handleContact(BotUpdateRequest request,
                               FlowContextEntity context,
                               BotContactPayload contact,
                               List<BotMessageResponse> responses) {
        if (context.getCurrentStep() != FlowStep.FLOW1_WAIT_PHONE
            && context.getCurrentStep() != FlowStep.FLOW4_WAIT_PHONE) {
            addMessage(responses, request.userId(),
                "Сейчас контакт не запрошен. Нажмите /start для начала сценария.");
            return;
        }

        if (!request.userId().equals(contact.userId())) {
            addMessage(responses, request.userId(),
                "Пожалуйста, отправьте ваш собственный номер через кнопку в боте.",
                requestContactButton("Отправить номер"));
            return;
        }

        context.setPhone(contact.phoneNumber());
        if (context.getCurrentStep() == FlowStep.FLOW1_WAIT_PHONE) {
            askReadingLevel(request.userId(), responses, FLOW1_LEVEL_PREFIX);
            context.setCurrentStep(FlowStep.FLOW1_WAIT_READING_LEVEL);
        } else {
            askReadingLevel(request.userId(), responses, FLOW4_LEVEL_PREFIX);
            context.setCurrentStep(FlowStep.FLOW4_WAIT_READING_LEVEL);
        }
    }

    private void handleFlow1ReadingLevel(BotUpdateRequest request,
                                         FlowContextEntity context,
                                         String callback,
                                         List<BotMessageResponse> responses) {
        if (context.getCurrentStep() != FlowStep.FLOW1_WAIT_READING_LEVEL) {
            addMessage(responses, request.userId(),
                "Сначала завершите предыдущие шаги регистрации.");
            return;
        }

        ReadingLevel level = parseReadingLevel(callback, FLOW1_LEVEL_PREFIX);
        if (level == null) {
            addMessage(responses, request.userId(),
                "Выберите один из предложенных вариантов уровня чтения.");
            return;
        }

        context.setReadingLevel(level);
        addTempTag(context, level.label());
        UserEntity user = upsertNormalUser(request.userId(), context);
        flushTempTagsToUser(request.userId(), context);
        assignTag(request.userId(), level.label());
        enqueueAdminRegistrationNotifications(user, responses);

        addMessage(responses, request.userId(), registrationCompletedMessage(),
            urlButton("Канал по курсу", properties.getCourseChannelUrl()));

        scheduleReferralProgramAnnouncement(request.userId(), context, responses,
            FlowStep.FLOW1_WAIT_REFERRAL_PROGRAM_ANNOUNCEMENT);
    }

    private void handleFlow4ReadingLevel(BotUpdateRequest request,
                                         FlowContextEntity context,
                                         String callback,
                                         List<BotMessageResponse> responses) {
        if (context.getCurrentStep() != FlowStep.FLOW4_WAIT_READING_LEVEL) {
            addMessage(responses, request.userId(),
                "Сначала завершите предыдущие шаги регистрации.");
            return;
        }

        ReadingLevel level = parseReadingLevel(callback, FLOW4_LEVEL_PREFIX);
        if (level == null) {
            addMessage(responses, request.userId(),
                "Выберите один из предложенных вариантов уровня чтения.");
            return;
        }

        context.setReadingLevel(level);
        addTempTag(context, level.label());
        UserEntity user = upsertReferralUser(request.userId(), context);
        flushTempTagsToUser(request.userId(), context);
        assignTag(request.userId(), level.label());
        enqueueAdminRegistrationNotifications(user, responses);

        addMessage(responses, request.userId(), registrationCompletedMessage(),
            urlButton("Канал по курсу", properties.getCourseChannelUrl()));

        scheduleReferralProgramAnnouncement(request.userId(), context, responses,
            FlowStep.FLOW4_WAIT_REFERRAL_PROGRAM_ANNOUNCEMENT);
    }

    private void confirmCourseSubscriptionAndProcessReferral(long userId,
                                                             FlowContextEntity context,
                                                             List<BotMessageResponse> responses,
                                                             String triggerSource) {
        Optional<UserEntity> maybeUser = userRepository.findByUserId(userId);
        if (maybeUser.isEmpty()) {
            log.warn("Course subscription confirmation ignored: user {} not found", userId);
            return;
        }

        UserEntity user = maybeUser.get();
        if (!user.isCourseChannelSubscribed()) {
            user.setCourseChannelSubscribed(true);
            userRepository.save(user);
        }
        assignTag(userId, TAG_COURSE_CHANNEL_CONFIRMED);

        long referrerUserId = user.getReferrerUserId();
        if (user.getReferralStatus() == ReferralStatus.PENDING && referrerUserId > 0) {
            ReferralEventPayload payload = new ReferralEventPayload(
                UUID.randomUUID(),
                referrerUserId,
                userId,
                buildIdempotencyKey(referrerUserId, userId),
                triggerSource,
                Instant.now(),
                buildReferralLink(referrerUserId)
            );

            ReferralProcessResult result = referralBonusService.processReferralEvent(payload);
            if (result.payloadValid() && result.newlyCounted()) {
                addMessage(responses, referrerUserId,
                    "+1 к общей копилке 🔥\n"
                        + "Общее кол-во приглашенных друзей: " + result.referralPoints() + ".");
            }

            if (!result.alreadyCounted()) {
                log.warn("Referral bonus was not counted for payload {}", payload);
            }
        }

        if (context == null) {
            return;
        }

        if (context.getCurrentStep() != FlowStep.FLOW1_WAIT_COURSE_SUBSCRIPTION_RECHECK
            && context.getCurrentStep() != FlowStep.FLOW4_WAIT_COURSE_LINK_CONFIRM) {
            return;
        }

        startFlow2(userId, context, responses);
        flowContextRepository.save(context);
    }

    private void handleNameInput(long userId,
                                 FlowContextEntity context,
                                 String text,
                                 List<BotMessageResponse> responses,
                                 FlowStep nextStep,
                                 boolean referralVariant) {
        String normalizedName = text == null ? "" : text.trim();
        if (!isValidName(normalizedName)) {
            addMessage(responses, userId,
                "Пожалуйста, укажите корректное имя (2-50 символов, только буквы, пробел, дефис).");
            return;
        }

        context.setUserName(normalizedName);
        context.setCurrentStep(nextStep);
        addMessage(responses, userId, "Укажите ваш возраст, пожалуйста.");

        if (referralVariant) {
            context.setFlowType(FlowType.REFERRAL);
        }
    }

    private void handleAgeInput(long userId,
                                FlowContextEntity context,
                                String text,
                                List<BotMessageResponse> responses,
                                FlowStep nextStep,
                                boolean flow4Variant) {
        Integer age = parseAge(text);
        if (age == null) {
            addMessage(responses, userId,
                "Пожалуйста, укажите корректный возраст числом от 7 до 100.");
            return;
        }

        context.setAge(age);
        context.setCurrentStep(nextStep);

        if (flow4Variant) {
            addMessage(responses, userId,
                "Рад знакомству, " + context.getUserName() + "! Идём дальше 😊");
        } else {
            addMessage(responses, userId,
                "Рад знакомству, " + context.getUserName() + "! Идём дальше 😊");
        }

        addMessage(responses, userId,
            "Есть ли у вас дети? 👶",
            callbackButton("Да", flow4Variant ? FLOW4_HAS_CHILDREN_YES : FLOW1_HAS_CHILDREN_YES),
            callbackButton("Нет", flow4Variant ? FLOW4_HAS_CHILDREN_NO : FLOW1_HAS_CHILDREN_NO));
    }

    private void handleHasChildrenInput(long userId,
                                        FlowContextEntity context,
                                        String text,
                                        List<BotMessageResponse> responses,
                                        FlowStep childrenStudyStep,
                                        FlowStep phoneStep) {
        Boolean hasChildren = parseYesNo(text);
        if (hasChildren == null) {
            addMessage(responses, userId,
                "Пожалуйста, выберите один из вариантов ниже 👇",
                callbackButton("Да", childrenStudyStep == FlowStep.FLOW1_WAIT_CHILDREN_QURAN_STUDY
                    ? FLOW1_HAS_CHILDREN_YES
                    : FLOW4_HAS_CHILDREN_YES),
                callbackButton("Нет", childrenStudyStep == FlowStep.FLOW1_WAIT_CHILDREN_QURAN_STUDY
                    ? FLOW1_HAS_CHILDREN_NO
                    : FLOW4_HAS_CHILDREN_NO));
            return;
        }

        finalizeHasChildrenInput(userId, context, responses, hasChildren, childrenStudyStep, phoneStep,
            childrenStudyStep == FlowStep.FLOW1_WAIT_CHILDREN_QURAN_STUDY
                ? FLOW1_CHILDREN_STUDY_YES
                : FLOW4_CHILDREN_STUDY_YES,
            childrenStudyStep == FlowStep.FLOW1_WAIT_CHILDREN_QURAN_STUDY
                ? FLOW1_CHILDREN_STUDY_NO
                : FLOW4_CHILDREN_STUDY_NO);
    }

    private void handleLegacyChildAgeInput(long userId,
                                           FlowContextEntity context,
                                           List<BotMessageResponse> responses,
                                           FlowStep childrenStudyStep,
                                           String yesCallback,
                                           String noCallback) {
        context.setHasChildren(true);
        context.setChildrenCount(null);
        context.setChildrenAges(null);
        context.setChildrenAgeIndex(null);
        context.setCurrentStep(childrenStudyStep);
        askChildrenStudyQuestion(userId, responses, yesCallback, noCallback);
    }

    private void onFlow1HasChildren(BotUpdateRequest request,
                                    FlowContextEntity context,
                                    List<BotMessageResponse> responses,
                                    boolean hasChildren) {
        if (context.getCurrentStep() != FlowStep.FLOW1_WAIT_HAS_CHILDREN
            && context.getCurrentStep() != FlowStep.FLOW1_WAIT_CHILDREN_COUNT) {
            return;
        }
        finalizeHasChildrenInput(
            request.userId(),
            context,
            responses,
            hasChildren,
            FlowStep.FLOW1_WAIT_CHILDREN_QURAN_STUDY,
            FlowStep.FLOW1_WAIT_PHONE,
            FLOW1_CHILDREN_STUDY_YES,
            FLOW1_CHILDREN_STUDY_NO
        );
    }

    private void onFlow4HasChildren(BotUpdateRequest request,
                                    FlowContextEntity context,
                                    List<BotMessageResponse> responses,
                                    boolean hasChildren) {
        if (context.getCurrentStep() != FlowStep.FLOW4_WAIT_HAS_CHILDREN
            && context.getCurrentStep() != FlowStep.FLOW4_WAIT_CHILDREN_COUNT) {
            return;
        }
        finalizeHasChildrenInput(
            request.userId(),
            context,
            responses,
            hasChildren,
            FlowStep.FLOW4_WAIT_CHILDREN_QURAN_STUDY,
            FlowStep.FLOW4_WAIT_PHONE,
            FLOW4_CHILDREN_STUDY_YES,
            FLOW4_CHILDREN_STUDY_NO
        );
    }

    private void finalizeHasChildrenInput(long userId,
                                          FlowContextEntity context,
                                          List<BotMessageResponse> responses,
                                          boolean hasChildren,
                                          FlowStep childrenStudyStep,
                                          FlowStep phoneStep,
                                          String yesCallback,
                                          String noCallback) {
        context.setHasChildren(hasChildren);
        context.setChildrenAges(null);
        context.setChildrenAgeIndex(null);
        context.setChildrenCount(hasChildren ? null : 0);

        if (!hasChildren) {
            context.setChildrenStudyQuran(null);
            context.setCurrentStep(phoneStep);
            requestPhoneNumber(userId, responses);
            return;
        }

        context.setCurrentStep(childrenStudyStep);
        askChildrenStudyQuestion(userId, responses, yesCallback, noCallback);
    }

    private void onFlow1ChildrenStudy(BotUpdateRequest request,
                                      FlowContextEntity context,
                                      List<BotMessageResponse> responses,
                                      boolean childrenStudyQuran) {
        if (context.getCurrentStep() != FlowStep.FLOW1_WAIT_CHILDREN_QURAN_STUDY) {
            return;
        }

        finalizeChildrenStudyInput(request.userId(), context, responses, childrenStudyQuran, FlowStep.FLOW1_WAIT_PHONE);
    }

    private void onFlow4ChildrenStudy(BotUpdateRequest request,
                                      FlowContextEntity context,
                                      List<BotMessageResponse> responses,
                                      boolean childrenStudyQuran) {
        if (context.getCurrentStep() != FlowStep.FLOW4_WAIT_CHILDREN_QURAN_STUDY) {
            return;
        }

        finalizeChildrenStudyInput(request.userId(), context, responses, childrenStudyQuran, FlowStep.FLOW4_WAIT_PHONE);
    }

    private void handleChildrenStudyTextInput(long userId,
                                              FlowContextEntity context,
                                              String text,
                                              List<BotMessageResponse> responses,
                                              FlowStep nextStep,
                                              String yesCallback,
                                              String noCallback) {
        Boolean childrenStudyQuran = parseYesNo(text);
        if (childrenStudyQuran == null) {
            addMessage(responses, userId,
                "Пожалуйста, выберите один из вариантов ниже.",
                callbackButton("Да", yesCallback),
                callbackButton("Нет", noCallback));
            return;
        }

        finalizeChildrenStudyInput(userId, context, responses, childrenStudyQuran, nextStep);
    }

    private void finalizeChildrenStudyInput(long userId,
                                            FlowContextEntity context,
                                            List<BotMessageResponse> responses,
                                            boolean childrenStudyQuran,
                                            FlowStep nextStep) {
        context.setHasChildren(true);
        context.setChildrenCount(null);
        context.setChildrenAges(null);
        context.setChildrenAgeIndex(null);
        context.setChildrenStudyQuran(childrenStudyQuran);
        context.setCurrentStep(nextStep);

        requestPhoneNumber(userId, responses);
    }

    private void askChildrenStudyQuestion(long userId,
                                          List<BotMessageResponse> responses,
                                          String yesCallback,
                                          String noCallback) {
        addMessage(responses, userId, "Изучают ли они Коран? 📖",
            callbackButton("Да", yesCallback),
            callbackButton("Нет", noCallback));
    }

    private void requestPhoneNumber(long userId, List<BotMessageResponse> responses) {
        addMessage(responses, userId,
            "Отправьте номер телефона через кнопку ниже 📱",
            requestContactButton("Отправить номер"));
    }

    private void askReadingLevel(long userId,
                                 List<BotMessageResponse> responses,
                                 String callbackPrefix) {
        List<BotButtonResponse> buttons = new ArrayList<>();
        for (ReadingLevel level : ReadingLevel.values()) {
            buttons.add(callbackButton(level.label(), callbackPrefix + level.name()));
        }

        addMessage(responses, userId,
            "Какой у вас текущий уровень чтения Корана?",
            buttons.toArray(new BotButtonResponse[0]));
    }

    private void sendConsentMessage(long userId,
                                    List<BotMessageResponse> responses,
                                    String callback) {
        addMessage(responses, userId,
            "Пусть Аллах облегчит вам обучение 🤍\n"
                + "Продолжая работу с ботом, вы даете согласие на обработку персональных данных.",
            callbackButton("Продолжить", callback));
    }

    private void startFlow2(long userId,
                            FlowContextEntity context,
                            List<BotMessageResponse> responses) {
        context.setCurrentStep(FlowStep.FLOW2_WAIT_TERMS);
        addMessage(responses, userId,
            "Вы записались — альхамдулиллях 🤍\n"
                + "Теперь приглашайте друзей и получайте награду за каждого! 🔥\n"
                + "Нажмите кнопку ниже, чтобы узнать условия и получить свою ссылку 👇",
            callbackButton("Условия", FLOW2_TERMS));
    }

    private String registrationCompletedMessage() {
        return "✅ Вы успешно зарегистрированы на курс по таджвиду!\n"
            + "Вся информация о занятиях, ссылки и материалы будут публиковаться в Telegram-канале.\n"
            + "Подпишитесь на канал курса.";
    }

    private void scheduleReferralProgramAnnouncement(long userId,
                                                     FlowContextEntity context,
                                                     List<BotMessageResponse> responses,
                                                     FlowStep waitingStep) {
        Duration delay = properties.getFlow1ToFlow2Delay();
        if (delay == null || delay.isNegative()) {
            delay = Duration.ZERO;
        }

        if (delay.isZero()) {
            if (confirmCourseSubscriptionIfPresent(
                userId,
                responses,
                "course_channel_check_before_flow2_zero_delay")) {
                context.setReferralAnnouncementDueAt(null);
                context.setReferralAnnouncementSent(true);
                startFlow2(userId, context, responses);
                return;
            }
            context.setCurrentStep(waitingStep);
            context.setReferralAnnouncementDueAt(Instant.now().plus(resolveAutomationRetryDelay()));
            context.setReferralAnnouncementSent(false);
            return;
        }

        context.setCurrentStep(waitingStep);
        context.setReferralAnnouncementDueAt(Instant.now().plus(delay));
        context.setReferralAnnouncementSent(false);
    }

    private void processDueReferralAnnouncements(Instant now, List<BotMessageResponse> responses) {
        List<FlowContextEntity> dueContexts =
            flowContextRepository.findByReferralAnnouncementSentFalseAndReferralAnnouncementDueAtLessThanEqual(now);
        for (FlowContextEntity context : dueContexts) {
            if (!isWaitingForReferralProgramAnnouncement(context.getCurrentStep())) {
                context.setReferralAnnouncementDueAt(null);
                context.setReferralAnnouncementSent(true);
                flowContextRepository.save(context);
                continue;
            }

            if (!confirmCourseSubscriptionIfPresent(
                context.getUserId(),
                responses,
                "course_channel_check_before_flow2_scheduled")) {
                context.setReferralAnnouncementDueAt(now.plus(resolveAutomationRetryDelay()));
                context.setReferralAnnouncementSent(false);
                flowContextRepository.save(context);
                continue;
            }

            startFlow2(context.getUserId(), context, responses);
            context.setReferralAnnouncementDueAt(null);
            context.setReferralAnnouncementSent(true);
            flowContextRepository.save(context);
        }
    }

    private boolean confirmCourseSubscriptionIfPresent(long userId,
                                                       List<BotMessageResponse> responses,
                                                       String triggerSource) {
        SubscriptionCheckResult result = channelSubscriptionService.checkCourseChannelSubscription(userId);
        if (result != SubscriptionCheckResult.SUBSCRIBED) {
            return false;
        }
        confirmCourseSubscriptionAndProcessReferral(userId, null, responses, triggerSource);
        return true;
    }

    private Duration resolveAutomationRetryDelay() {
        Duration retryDelay = properties.getAutomationTickDelay();
        if (retryDelay == null || retryDelay.isNegative() || retryDelay.isZero()) {
            return Duration.ofSeconds(30);
        }
        return retryDelay;
    }

    private boolean isWaitingForReferralProgramAnnouncement(FlowStep step) {
        return step == FlowStep.FLOW1_WAIT_REFERRAL_PROGRAM_ANNOUNCEMENT
            || step == FlowStep.FLOW4_WAIT_REFERRAL_PROGRAM_ANNOUNCEMENT;
    }

    private void processPendingReferralAwards(List<BotMessageResponse> responses) {
        List<UserEntity> pendingInvitees =
            userRepository.findAllByReferralStatusAndRegistrationCompletedTrue(ReferralStatus.PENDING);

        for (UserEntity invitee : pendingInvitees) {
            if (invitee.getReferrerUserId() <= 0) {
                continue;
            }

            boolean subscribed = invitee.isCourseChannelSubscribed();
            if (!subscribed) {
                SubscriptionCheckResult result =
                    channelSubscriptionService.checkCourseChannelSubscription(invitee.getUserId());
                if (result != SubscriptionCheckResult.SUBSCRIBED) {
                    continue;
                }
            }

            FlowContextEntity context = flowContextRepository.findByUserId(invitee.getUserId()).orElse(null);
            confirmCourseSubscriptionAndProcessReferral(
                invitee.getUserId(),
                context,
                responses,
                "course_channel_periodic_recheck"
            );
        }
    }

    private void enqueueAdminRegistrationNotifications(UserEntity user, List<BotMessageResponse> responses) {
        TajwidBotProperties.RegistrationNotificationsProperties notifications = properties.getRegistrationNotifications();
        if (notifications == null || !notifications.isEnabled() || user == null) {
            return;
        }

        Set<Long> recipientIds = resolveNotificationRecipients(notifications);
        if (recipientIds.isEmpty()) {
            return;
        }

        String inviterLabel = buildInviterLabel(user.getReferrerUserId());
        String levelLabel = user.getReadingLevel() == null ? "Не указан" : user.getReadingLevel().label();
        String hasChildrenLabel = toYesNo(resolveHasChildren(user));
        String telegramUsernameLabel = formatTelegramUsername(user.getTelegramUsername());

        String message = "🆕 Новая регистрация\n\n"
            + "👤 Имя: " + fallback(user.getUserName(), user.getTelegramFirstName(), "Не указано") + "\n"
            + "🎂 Возраст: " + fallback(user.getAge()) + "\n"
            + "📨 Telegram: " + telegramUsernameLabel + "\n"
            + "📱 Телефон: " + fallback(user.getPhone()) + "\n"
            + "👨‍👩‍👧 Дети: " + hasChildrenLabel + "\n"
            + "📖 Уровень: " + levelLabel + "\n"
            + "🤝 Кто пригласил: " + inviterLabel;

        for (Long recipientId : recipientIds) {
            addMessage(responses, recipientId, message);
        }
    }

    private Set<Long> resolveNotificationRecipients(TajwidBotProperties.RegistrationNotificationsProperties notifications) {
        Set<Long> recipients = new HashSet<>();

        for (String rawId : defaultIfNull(notifications.getAdminUserIds())) {
            if (rawId == null || rawId.isBlank()) {
                continue;
            }
            try {
                recipients.add(Long.parseLong(rawId.trim()));
            } catch (NumberFormatException ignored) {
                log.warn("Invalid TAJWID_REGISTRATION_NOTIFICATIONS_ADMIN_USER_IDS entry: '{}'", rawId);
            }
        }

        Set<String> usernames = normalizeUsernames(defaultIfNull(notifications.getAdminUsernames()));
        List<RegistrationNotificationAdminEntity> dbAdmins = registrationNotificationAdminRepository.findByActiveTrue();
        for (RegistrationNotificationAdminEntity admin : dbAdmins) {
            if (admin.getTelegramUserId() != null && admin.getTelegramUserId() > 0) {
                recipients.add(admin.getTelegramUserId());
            }
            String username = normalizeUsername(admin.getTelegramUsername());
            if (username != null) {
                usernames.add(username.toLowerCase(Locale.ROOT));
            }
        }

        if (usernames.isEmpty()) {
            usernames.add(DEFAULT_ADMIN_USERNAME.toLowerCase(Locale.ROOT));
        }
        if (!usernames.isEmpty()) {
            recipients.addAll(userRepository.findUserIdsByTelegramUsernameIn(usernames));
            recipients.addAll(flowContextRepository.findUserIdsByTelegramUsernameIn(usernames));
        }

        recipients.addAll(bindAdminUserIdsFromUsername(dbAdmins));
        return recipients;
    }

    private Set<Long> bindAdminUserIdsFromUsername(List<RegistrationNotificationAdminEntity> dbAdmins) {
        Set<Long> resolvedIds = new HashSet<>();
        for (RegistrationNotificationAdminEntity admin : dbAdmins) {
            if (admin.getTelegramUserId() != null && admin.getTelegramUserId() > 0) {
                resolvedIds.add(admin.getTelegramUserId());
                continue;
            }

            String username = normalizeUsername(admin.getTelegramUsername());
            if (username == null) {
                continue;
            }

            Long resolvedUserId = resolveUserIdByUsername(username);
            if (resolvedUserId == null || resolvedUserId <= 0) {
                continue;
            }

            admin.setTelegramUserId(resolvedUserId);
            registrationNotificationAdminRepository.save(admin);
            resolvedIds.add(resolvedUserId);
        }
        return resolvedIds;
    }

    private Long resolveUserIdByUsername(String username) {
        Optional<UserEntity> user = userRepository.findByTelegramUsernameIgnoreCase(username);
        if (user.isPresent() && user.get().getUserId() != null) {
            return user.get().getUserId();
        }
        return flowContextRepository.findByTelegramUsernameIgnoreCase(username)
            .map(FlowContextEntity::getUserId)
            .orElse(null);
    }

    private Set<String> normalizeUsernames(Collection<String> rawUsernames) {
        Set<String> result = new HashSet<>();
        if (rawUsernames == null) {
            return result;
        }
        for (String raw : rawUsernames) {
            String normalized = normalizeUsername(raw);
            if (normalized != null) {
                result.add(normalized.toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    private String buildInviterLabel(long referrerUserId) {
        if (referrerUserId <= 0) {
            return "Нет";
        }

        Optional<UserEntity> maybeReferrer = userRepository.findByUserId(referrerUserId);
        if (maybeReferrer.isEmpty()) {
            return String.valueOf(referrerUserId);
        }

        UserEntity referrer = maybeReferrer.get();
        if (referrer.getTelegramUsername() != null && !referrer.getTelegramUsername().isBlank()) {
            return "@" + referrer.getTelegramUsername() + " (" + referrerUserId + ")";
        }

        return fallback(referrer.getUserName(), referrer.getTelegramFirstName(), String.valueOf(referrerUserId))
            + " (" + referrerUserId + ")";
    }

    private Boolean resolveHasChildren(UserEntity user) {
        if (user.getHasChildren() != null) {
            return user.getHasChildren();
        }
        Integer childrenCount = user.getChildrenCount();
        if (childrenCount == null) {
            return null;
        }
        return childrenCount > 0;
    }

    private String toYesNo(Boolean value) {
        if (Boolean.TRUE.equals(value)) {
            return "Да";
        }
        if (Boolean.FALSE.equals(value)) {
            return "Нет";
        }
        return "Не указано";
    }

    private String formatTelegramUsername(String telegramUsername) {
        String normalized = normalizeUsername(telegramUsername);
        if (normalized == null) {
            return "Не указан";
        }
        return "@" + normalized;
    }

    private List<String> defaultIfNull(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String fallback(Object value) {
        return value == null ? "Не указано" : String.valueOf(value);
    }

    private String fallback(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
    }

    private UserEntity upsertNormalUser(long userId, FlowContextEntity context) {
        UserEntity user = userRepository.findByUserId(userId).orElseGet(UserEntity::new);
        if (user.getUserId() == null) {
            user.setUserId(userId);
        }

        user.setTelegramFirstName(context.getTelegramFirstName());
        user.setTelegramUsername(context.getTelegramUsername());
        user.setUserName(context.getUserName());
        user.setAge(context.getAge());
        user.setHasChildren(context.getHasChildren());
        user.setChildrenCount(context.getChildrenCount());
        user.setChildrenAges(context.getChildrenAges());
        user.setChildrenStudyQuran(context.getChildrenStudyQuran());
        user.setPhone(context.getPhone());
        user.setReadingLevel(context.getReadingLevel());
        user.setConsentGiven(true);
        user.setSchoolChannelSubscribed(true);
        user.setCourseChannelSubscribed(false);
        user.setRegistrationCompleted(true);
        user.setRegistrationCompletedAt(Instant.now());
        user.setReferrerUserId(0);
        user.setReferralStatus(ReferralStatus.NOT_APPLICABLE);
        user.setReferralCountedAt(Instant.EPOCH);

        return userRepository.save(user);
    }

    private UserEntity upsertReferralUser(long userId, FlowContextEntity context) {
        UserEntity user = userRepository.findByUserId(userId).orElseGet(UserEntity::new);
        if (user.getUserId() == null) {
            user.setUserId(userId);
        }

        user.setTelegramFirstName(context.getTelegramFirstName());
        user.setTelegramUsername(context.getTelegramUsername());
        user.setUserName(context.getUserName());
        user.setAge(context.getAge());
        user.setHasChildren(context.getHasChildren());
        user.setChildrenCount(context.getChildrenCount());
        user.setChildrenAges(context.getChildrenAges());
        user.setChildrenStudyQuran(context.getChildrenStudyQuran());
        user.setPhone(context.getPhone());
        user.setReadingLevel(context.getReadingLevel());
        user.setConsentGiven(true);
        user.setSchoolChannelSubscribed(true);
        user.setCourseChannelSubscribed(false);
        user.setRegistrationCompleted(true);
        user.setRegistrationCompletedAt(Instant.now());
        user.setReferrerUserId(context.getReferrerUserId());
        user.setReferralStatus(ReferralStatus.PENDING);
        user.setReferralCountedAt(Instant.EPOCH);

        return userRepository.save(user);
    }

    private void addTempTag(FlowContextEntity context, String tag) {
        Set<String> tags = parseTags(context.getTempTags());
        tags.add(tag);
        context.setTempTags(String.join(",", tags));
    }

    private void flushTempTagsToUser(long userId, FlowContextEntity context) {
        for (String tag : parseTags(context.getTempTags())) {
            assignTag(userId, tag);
        }
    }

    private void assignTag(long userId, String tag) {
        if (userTagRepository.existsByUserIdAndTag(userId, tag)) {
            return;
        }

        UserTagEntity entity = new UserTagEntity();
        entity.setUserId(userId);
        entity.setTag(tag);
        userTagRepository.save(entity);
    }

    private Set<String> parseTags(String rawTags) {
        Set<String> tags = new LinkedHashSet<>();
        if (rawTags == null || rawTags.isBlank()) {
            return tags;
        }

        String[] parts = rawTags.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                tags.add(trimmed);
            }
        }
        return tags;
    }

    private ReadingLevel parseReadingLevel(String callbackValue, String prefix) {
        String raw = callbackValue.substring(prefix.length());
        try {
            return ReadingLevel.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean isValidName(String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim();
        return trimmed.length() >= 2
            && trimmed.length() <= 50
            && NAME_PATTERN.matcher(trimmed).matches();
    }

    private Integer parseAge(String text) {
        try {
            int age = Integer.parseInt(text.trim());
            if (age < 7 || age > 100) {
                return null;
            }
            return age;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Boolean parseYesNo(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "да", "yes", "y" -> true;
            case "нет", "no", "n" -> false;
            default -> null;
        };
    }

    private String buildReferralLink(long referrerUserId) {
        String username = properties.getBotUsername();
        if (username == null || username.isBlank()) {
            username = "tajwid_bot";
        }
        if (username.startsWith("@")) {
            username = username.substring(1);
        }
        return "https://t.me/" + username + "?start=" + referrerUserId;
    }

    private String buildIdempotencyKey(long referrerUserId, long inviteeUserId) {
        return "ref:" + referrerUserId + ":" + inviteeUserId;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    private String normalizeUsername(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.startsWith("@")) {
            normalized = normalized.substring(1);
        }
        return normalized.isBlank() ? null : normalized;
    }

    private void resetContext(FlowContextEntity context,
                              long userId,
                              String firstName,
                              FlowType type) {
        context.setUserId(userId);
        context.setTelegramFirstName(firstName);
        context.setFlowType(type);
        context.setCurrentStep(FlowStep.IDLE);
        context.setUserName(null);
        context.setAge(null);
        context.setHasChildren(null);
        context.setChildrenCount(null);
        context.setChildrenAges(null);
        context.setChildrenStudyQuran(null);
        context.setChildrenAgeIndex(null);
        context.setPhone(null);
        context.setReadingLevel(null);
        context.setConsentGiven(false);
        context.setSchoolChannelSubscribed(false);
        context.setReferrerUserId(0);
        context.setReferralEntrySource(null);
        context.setReferralEntryAt(null);
        context.setTempTags(null);
        context.setReferralAnnouncementDueAt(null);
        context.setReferralAnnouncementSent(false);
    }

    private void setIdleContext(FlowContextEntity context) {
        context.setCurrentStep(FlowStep.IDLE);
        context.setFlowType(FlowType.NORMAL);
        context.setReferrerUserId(0);
        context.setReferralAnnouncementDueAt(null);
        context.setReferralAnnouncementSent(false);
    }

    private FlowContextEntity createDefaultContext(long userId, String firstName) {
        FlowContextEntity context = new FlowContextEntity();
        context.setUserId(userId);
        context.setTelegramFirstName(firstName);
        context.setFlowType(FlowType.NORMAL);
        context.setCurrentStep(FlowStep.IDLE);
        context.setReferrerUserId(0);
        context.setReferralAnnouncementSent(false);
        return context;
    }

    private void addMessage(List<BotMessageResponse> responses,
                            Long recipientUserId,
                            String text,
                            BotButtonResponse... buttons) {
        responses.add(new BotMessageResponse(recipientUserId, text, List.of(buttons)));
    }

    private BotButtonResponse callbackButton(String text, String callback) {
        return new BotButtonResponse(text, ButtonType.CALLBACK, callback);
    }

    private BotButtonResponse urlButton(String text, String url) {
        return new BotButtonResponse(text, ButtonType.URL, url);
    }

    private BotButtonResponse requestContactButton(String text) {
        return new BotButtonResponse(text, ButtonType.REQUEST_CONTACT, "request_contact");
    }
}
