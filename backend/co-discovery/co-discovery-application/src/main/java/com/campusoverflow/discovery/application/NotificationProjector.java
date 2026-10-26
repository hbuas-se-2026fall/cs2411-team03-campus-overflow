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
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通知中心：把上游事件转换为站内通知。幂等键 (sourceEventId, recipientId)。
 * <p>行走骨架（PB-18，指导书 6.3.4）：Sprint 1 暂为空实现（只打 DEBUG 日志），避免发件箱被阻塞；
 * 实现/移除见 tech-debt Issue #67（Sprint 3 按 T3-09 完成）。</p>
 */
@Component
public class NotificationProjector {

    private static final Logger log = LoggerFactory.getLogger(NotificationProjector.class);

    private final NotificationRepository notifications;
    private final IdentityApi identity;
    private final QaQueryApi qa;

    public NotificationProjector(NotificationRepository notifications, IdentityApi identity, QaQueryApi qa) {
        this.notifications = notifications;
        this.identity = identity;
        this.qa = qa;
    }

    @EventListener
    @Transactional
    public void on(AnswerSubmittedEvent e) {
        // 行走骨架：空实现，事件被标记为已投递。实现见 Issue #67。
        log.debug("[walking-skeleton] Discovery 通知尚未实现，忽略事件 {}", e.eventId()); // TODO(S3) 见 Issue #67
    }

    @EventListener
    @Transactional
    public void on(AnswerAcceptedEvent e) {
        // 行走骨架：空实现，事件被标记为已投递。实现见 Issue #67。
        log.debug("[walking-skeleton] Discovery 通知尚未实现，忽略事件 {}", e.eventId()); // TODO(S3) 见 Issue #67
    }

    @EventListener
    @Transactional
    public void on(AnswerEndorsedEvent e) {
        // 行走骨架：空实现，事件被标记为已投递。实现见 Issue #67。
        log.debug("[walking-skeleton] Discovery 通知尚未实现，忽略事件 {}", e.eventId()); // TODO(S3) 见 Issue #67
    }

    @EventListener
    @Transactional
    public void on(CommentPostedEvent e) {
        // 行走骨架：空实现，事件被标记为已投递。实现见 Issue #67。
        log.debug("[walking-skeleton] Discovery 通知尚未实现，忽略事件 {}", e.eventId()); // TODO(S3) 见 Issue #67
    }

    @EventListener
    @Transactional
    public void on(BountyAwardedEvent e) {
        // 行走骨架：空实现，事件被标记为已投递。实现见 Issue #67。
        log.debug("[walking-skeleton] Discovery 通知尚未实现，忽略事件 {}", e.eventId()); // TODO(S3) 见 Issue #67
    }

    @EventListener
    @Transactional
    public void on(BadgeAwardedEvent e) {
        // 行走骨架：空实现，事件被标记为已投递。实现见 Issue #67。
        log.debug("[walking-skeleton] Discovery 通知尚未实现，忽略事件 {}", e.eventId()); // TODO(S3) 见 Issue #67
    }

    private void notify(long recipientId, NotificationType type, String title, String link, String eventId,
                        java.time.Instant at) {
        // 行走骨架：监听器为空实现，本方法暂不可达；实现见 Issue #67。
        log.debug("[walking-skeleton] Discovery 通知尚未实现，忽略事件 {}", eventId); // TODO(S3) 见 Issue #67
    }

    private String name(long userId) {
        // 行走骨架：监听器为空实现，本方法暂不可达；实现见 Issue #67。
        log.debug("[walking-skeleton] Discovery 通知尚未实现"); // TODO(S3) 见 Issue #67
        return "";
    }
}
