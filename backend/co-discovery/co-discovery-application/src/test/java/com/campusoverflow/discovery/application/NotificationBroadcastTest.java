package com.campusoverflow.discovery.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusoverflow.discovery.domain.notification.NotificationType;
import com.campusoverflow.qa.api.event.AnswerSubmittedEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * #49：通知落库后向收件人推送未读数。
 * 覆盖：推送收件人正确、重复事件只推一次、自己回答自己的问题不推。
 */
class NotificationBroadcastTest {

    private final DiscoveryTestFixtures fx = new DiscoveryTestFixtures();
    private final RecordingBroadcaster recorder = new RecordingBroadcaster();
    private final NotificationProjector projector =
            new NotificationProjector(fx.notifications, fx.identity, fx.qa, recorder);
    private final Instant at = DiscoveryTestFixtures.NOW;

    @Test
    void pushesUnreadCountToRecipientAfterSaving() {
        projector.on(new AnswerSubmittedEvent("evt-push-1", at, 11, 1, 2, 1, "红黑树的旋转"));

        assertThat(fx.of(1)).singleElement()
                .satisfies(n -> assertThat(n.type()).isEqualTo(NotificationType.ANSWER_RECEIVED));
        assertThat(recorder.calls).containsExactly(new Call(1, 1));
    }

    @Test
    void selfAnswerDoesNotPush() {
        projector.on(new AnswerSubmittedEvent("evt-push-self", at, 11, 1, 1, 1, "自己的问题"));

        assertThat(fx.all()).isEmpty();
        assertThat(recorder.calls).isEmpty();
    }

    @Test
    void duplicateDeliveryPushesOnlyOnce() {
        AnswerSubmittedEvent event = new AnswerSubmittedEvent("evt-push-dup", at, 11, 1, 2, 1, "重复投递");

        projector.on(event);
        projector.on(event);
        projector.on(event);

        assertThat(recorder.calls).containsExactly(new Call(1, 1));
    }

    @Test
    void mentionPushesToMentionedUserOnly() {
        fx.user(5, "王老师", com.campusoverflow.shared.security.Role.TEACHER);
        fx.user(6, "小明", com.campusoverflow.shared.security.Role.STUDENT);

        projector.on(new com.campusoverflow.qa.api.event.CommentPostedEvent("evt-push-mention", at, 21, 1, 6,
                "感谢 @王老师 解答"));

        assertThat(recorder.calls).containsExactly(new Call(5, 1));
    }

    record Call(long recipientId, long unread) {
    }

    /** 记录每次推送目标的测试替身。 */
    static final class RecordingBroadcaster implements NotificationBroadcaster {
        private final List<Call> calls = new ArrayList<>();

        @Override
        public int pushUnread(long recipientId, long unread) {
            calls.add(new Call(recipientId, unread));
            return 1;
        }
    }
}
