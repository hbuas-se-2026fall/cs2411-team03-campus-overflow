package com.campusoverflow.discovery.application;

import com.campusoverflow.discovery.domain.notification.MentionParser;
import com.campusoverflow.discovery.domain.notification.Notification;
import com.campusoverflow.discovery.domain.notification.NotificationRepository;
import com.campusoverflow.discovery.domain.notification.NotificationType;
import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.qa.api.QaQueryApi;
import com.campusoverflow.qa.api.QuestionRef;
import com.campusoverflow.qa.api.event.AnswerAcceptedEvent;
import com.campusoverflow.qa.api.event.AnswerEndorsedEvent;
import com.campusoverflow.qa.api.event.AnswerSubmittedEvent;
import com.campusoverflow.qa.api.event.CommentPostedEvent;
import com.campusoverflow.reputation.api.event.BadgeAwardedEvent;
import com.campusoverflow.reputation.api.event.BountyAwardedEvent;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 通知中心：把上游事件转换为站内通知。幂等键 (sourceEventId, recipientId)，
 * 同一事件重复投递不会给同一个人产生第二条通知。
 * <p>规则（指导书 6.5.2）：自己触发的事件不通知自己；评论中的 @昵称按身份上下文解析，
 * 解析不到的用户静默忽略；昵称缺失时以“某同学”代称，通知本身仍然投递。</p>
 */
@Component
public class NotificationProjector {

    private static final Logger log = LoggerFactory.getLogger(NotificationProjector.class);

    /** 问题标题在通知文案中的展示上限。 */
    private static final int MAX_TITLE_IN_MESSAGE = 30;

    private final NotificationRepository notifications;
    private final IdentityApi identity;
    private final QaQueryApi qa;
    private final NotificationBroadcaster broadcaster;

    /** 兼容既有测试与不装配推送端口的场景：等价于不推送。 */
    public NotificationProjector(NotificationRepository notifications, IdentityApi identity, QaQueryApi qa) {
        this(notifications, identity, qa, NotificationBroadcaster.NOOP);
    }

    @Autowired
    public NotificationProjector(NotificationRepository notifications, IdentityApi identity, QaQueryApi qa,
                                 NotificationBroadcaster broadcaster) {
        this.notifications = notifications;
        this.identity = identity;
        this.qa = qa;
        this.broadcaster = broadcaster;
    }

    /** 新回答：通知提问者；自己回答自己的问题不通知。 */
    @EventListener
    @Transactional
    public void on(AnswerSubmittedEvent e) {
        if (e.answerAuthorId() == e.questionAuthorId()) {
            return;
        }
        notify(e.questionAuthorId(), NotificationType.ANSWER_RECEIVED,
                "你的问题「" + shorten(e.questionTitle()) + "」收到一条新回答",
                "/questions/" + e.questionId(), e.eventId(), e.occurredAt());
    }

    /** 采纳：通知被采纳回答的作者；操作者本人即作者时不通知。 */
    @EventListener
    @Transactional
    public void on(AnswerAcceptedEvent e) {
        if (e.acceptedById() == e.answerAuthorId()) {
            return;
        }
        notify(e.answerAuthorId(), NotificationType.ANSWER_ACCEPTED,
                "你的回答被采纳为「" + shorten(e.questionTitle()) + "」的最佳答案",
                "/questions/" + e.questionId(), e.eventId(), e.occurredAt());
    }

    /** 教师认证：通知回答作者；自己认证自己的回答时不通知。 */
    @EventListener
    @Transactional
    public void on(AnswerEndorsedEvent e) {
        if (e.endorserId() == e.answerAuthorId()) {
            return;
        }
        notify(e.answerAuthorId(), NotificationType.ANSWER_ENDORSED,
                "你的回答在「" + shorten(e.questionTitle()) + "」获得教师认证",
                "/questions/" + e.questionId(), e.eventId(), e.occurredAt());
    }

    /** 评论中的 @提及：仅通知被点名且不是评论作者本人的人。 */
    @EventListener
    @Transactional
    public void on(CommentPostedEvent e) {
        for (String displayName : MentionParser.extract(e.body())) {
            identity.findByDisplayName(displayName)
                    .filter(user -> user.id() != e.authorId())
                    .ifPresent(user -> notify(user.id(), NotificationType.MENTIONED,
                            name(e.authorId()) + " 在评论中提到了你",
                            "/questions/" + e.questionId(), e.eventId(), e.occurredAt()));
        }
    }

    /** 悬赏结算：通知获奖者，标题取问题标题。 */
    @EventListener
    @Transactional
    public void on(BountyAwardedEvent e) {
        notify(e.winnerId(), NotificationType.BOUNTY_AWARDED,
                "你获得了「" + shorten(questionTitle(e.questionId())) + "」的悬赏 " + e.points() + " 分",
                "/questions/" + e.questionId(), e.eventId(), e.occurredAt());
    }

    /** 徽章授予：通知获得者。 */
    @EventListener
    @Transactional
    public void on(BadgeAwardedEvent e) {
        notify(e.userId(), NotificationType.BADGE_AWARDED,
                "你获得了「" + e.badgeName() + "」徽章",
                "/me/badges", e.eventId(), e.occurredAt());
    }

    /** 幂等写入：同一事件对同一收件人只落一条；落库后按收件人推送未读数。 */
    private void notify(long recipientId, NotificationType type, String title, String link, String eventId,
                        Instant at) {
        if (notifications.exists(eventId, recipientId)) {
            return;
        }
        notifications.save(Notification.create(recipientId, type, title, link, eventId, at));
        broadcast(recipientId);
        log.debug("通知已投递 recipient={} type={} event={}", recipientId, type, eventId);
    }

    /**
     * 实时推送：事务内注册 afterCommit 回调，避免"推送到达时通知尚未提交、前端回查未读数拿到 0"的竞态。
     * 无事务上下文（单元测试、非事务调用）时立即推送。
     */
    private void broadcast(long recipientId) {
        Runnable push = () -> {
            try {
                broadcaster.pushUnread(recipientId, notifications.countUnread(recipientId));
            } catch (RuntimeException ex) {
                // 推送失败不影响通知落库，只记日志
                log.warn("通知实时推送失败 recipient={}", recipientId, ex);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    push.run();
                }
            });
        } else {
            push.run();
        }
    }

    /** 操作者显示名；查不到时用“某同学”，不因此丢弃通知。 */
    private String name(long userId) {
        return identity.findUser(userId).map(UserSummary::displayName).orElse("某同学");
    }

    private String questionTitle(long questionId) {
        return qa.findQuestion(questionId).map(QuestionRef::title).orElse("");
    }

    private static String shorten(String title) {
        if (title == null || title.isBlank()) {
            return "问题";
        }
        return title.length() > MAX_TITLE_IN_MESSAGE
                ? title.substring(0, MAX_TITLE_IN_MESSAGE) + "…"
                : title;
    }
}
