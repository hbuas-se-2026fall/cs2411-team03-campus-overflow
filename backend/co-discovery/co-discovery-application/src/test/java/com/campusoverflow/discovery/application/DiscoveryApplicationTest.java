package com.campusoverflow.discovery.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusoverflow.discovery.domain.notification.Notification;
import com.campusoverflow.discovery.domain.notification.NotificationType;
import com.campusoverflow.qa.api.event.AnswerAcceptedEvent;
import com.campusoverflow.qa.api.event.AnswerEndorsedEvent;
import com.campusoverflow.qa.api.event.AnswerSubmittedEvent;
import com.campusoverflow.qa.api.event.CommentPostedEvent;
import com.campusoverflow.reputation.api.event.BadgeAwardedEvent;
import com.campusoverflow.reputation.api.event.BountyAwardedEvent;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * T3-08：通知投影与应用层幂等（PB-39）。
 * 覆盖验收标准「回答/采纳/认证/悬赏获奖/徽章/被 @ 都产生通知，未读数正确，只能标记自己的通知」。
 */
class DiscoveryApplicationTest {

    private final DiscoveryTestFixtures fx = new DiscoveryTestFixtures();
    private final Instant at = DiscoveryTestFixtures.NOW;

    @Test
    void answerNotifiesQuestionAuthor() {
        fx.user(1, "提问者", Role.STUDENT);
        fx.user(2, "回答者", Role.STUDENT);

        fx.projector.on(new AnswerSubmittedEvent("evt-answer-1", at, 11, 1, 2, 1, "红黑树的旋转怎么理解"));

        List<Notification> inbox = fx.of(1);
        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).type()).isEqualTo(NotificationType.ANSWER_RECEIVED);
        assertThat(inbox.get(0).title()).contains("红黑树的旋转怎么理解");
        assertThat(inbox.get(0).link()).isEqualTo("/questions/1");
        assertThat(inbox.get(0).read()).isFalse();
        assertThat(fx.of(2)).isEmpty();
    }

    @Test
    void selfAnswerDoesNotNotifyAuthor() {
        fx.user(1, "提问者", Role.STUDENT);
        fx.projector.on(new AnswerSubmittedEvent("evt-answer-self", at, 11, 1, 1, 1, "自己的问题"));

        assertThat(fx.all()).isEmpty();
    }

    @Test
    void duplicateEventDeliveryProducesSingleNotification() {
        fx.user(1, "提问者", Role.STUDENT);
        AnswerSubmittedEvent event = new AnswerSubmittedEvent("evt-answer-dup", at, 11, 1, 2, 1, "重复投递");

        fx.projector.on(event);
        fx.projector.on(event);
        fx.projector.on(event);

        assertThat(fx.of(1)).hasSize(1);
    }

    @Test
    void acceptedAnswerNotifiesAnswerAuthorNotQuestionAuthor() {
        fx.user(1, "提问者", Role.STUDENT);
        fx.user(2, "回答者", Role.STUDENT);

        fx.projector.on(new AnswerAcceptedEvent("evt-accepted-1", at, 11, 1, 2, 1, 1, "红黑树的旋转怎么理解"));

        List<Notification> inbox = fx.of(2);
        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).type()).isEqualTo(NotificationType.ANSWER_ACCEPTED);
        assertThat(inbox.get(0).title()).contains("最佳答案");
        assertThat(fx.of(1)).isEmpty();
    }

    @Test
    void endorsementNotifiesAnswerAuthor() {
        fx.user(2, "回答者", Role.STUDENT);
        fx.user(9, "王老师", Role.TEACHER);

        fx.projector.on(new AnswerEndorsedEvent("evt-endorse-1", at, 11, 1, 2, 9, "红黑树"));

        assertThat(fx.of(2)).singleElement()
                .satisfies(n -> assertThat(n.type()).isEqualTo(NotificationType.ANSWER_ENDORSED));
        assertThat(fx.of(9)).isEmpty();
    }

    @Test
    void mentionNotifiesOnlyOtherUsers() {
        fx.user(5, "王老师", Role.TEACHER);
        fx.user(6, "小明", Role.STUDENT);

        // 评论作者是 6 号小明，正文里同时 @ 了自己和老师
        fx.projector.on(new CommentPostedEvent("evt-comment-1", at, 21, 1, 6, "感谢 @王老师，另外 @小明 也看下"));

        List<Notification> forTeacher = fx.of(5);
        assertThat(forTeacher).hasSize(1);
        assertThat(forTeacher.get(0).type()).isEqualTo(NotificationType.MENTIONED);
        assertThat(forTeacher.get(0).title()).contains("小明");
        assertThat(fx.of(6)).isEmpty();
    }

    @Test
    void unknownMentionIsIgnored() {
        fx.user(6, "小明", Role.STUDENT);

        fx.projector.on(new CommentPostedEvent("evt-comment-unknown", at, 21, 1, 6, "请问 @不存在的昵称 这个问题"));

        assertThat(fx.all()).isEmpty();
    }

    @Test
    void bountyAwardNotifiesWinnerWithPointsAndTitle() {
        fx.user(7, "获奖者", Role.STUDENT);
        fx.qa.put(1, "动态规划的状态转移怎么推");

        fx.projector.on(new BountyAwardedEvent("evt-bounty-1", at, 31, 1, 8, 7, 50));

        assertThat(fx.of(7)).singleElement().satisfies(n -> {
            assertThat(n.type()).isEqualTo(NotificationType.BOUNTY_AWARDED);
            assertThat(n.title()).contains("50").contains("动态规划的状态转移怎么推");
        });
    }

    @Test
    void badgeAwardNotifiesOwner() {
        fx.user(7, "获奖者", Role.STUDENT);

        fx.projector.on(new BadgeAwardedEvent("evt-badge-1", at, 7, "HELPER_10", "热心助人十次"));

        assertThat(fx.of(7)).singleElement()
                .satisfies(n -> assertThat(n.title()).contains("热心助人十次"));
    }

    @Test
    void unreadCountAndMarkAllReadOnlyAffectRecipient() {
        fx.user(1, "提问者", Role.STUDENT);
        fx.projector.on(new AnswerSubmittedEvent("evt-a", at, 11, 1, 2, 1, "问题一"));
        fx.projector.on(new AnswerSubmittedEvent("evt-b", at, 12, 2, 3, 1, "问题二"));

        assertThat(fx.notifications.countUnread(1)).isEqualTo(2);
        assertThat(fx.notifications.markAllRead(1)).isEqualTo(2);
        assertThat(fx.notifications.countUnread(1)).isZero();
        assertThat(fx.of(1)).allSatisfy(n -> assertThat(n.read()).isTrue());
    }
}
