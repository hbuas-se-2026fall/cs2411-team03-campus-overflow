package com.campusoverflow.discovery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.discovery.domain.notification.Notification;
import com.campusoverflow.discovery.domain.notification.NotificationType;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T3-07：通知中心用例（PB-41/PB-42 前端的数据来源）。
 * 覆盖：只看得到自己的通知、未读数、单条标已读、全部已读条数、404 与 403 异常码。
 */
class NotificationServiceTest {

    private final DiscoveryTestFixtures fx = new DiscoveryTestFixtures();
    private final NotificationService service = new NotificationService(fx.notifications);
    private final Actor alice = new Actor(1, "小明", Role.STUDENT);
    private final Actor bob = new Actor(2, "小红", Role.STUDENT);

    private final Instant at = DiscoveryTestFixtures.NOW;

    @BeforeEach
    void seed() {
        // 1 号（alice）两条：一条未读、一条已读；2 号（bob）一条未读
        Notification unread = fx.notifications.save(Notification.create(
                1, NotificationType.ANSWER_RECEIVED, "你的问题收到一条新回答", "/questions/1", "evt-a1", at));
        Notification read = Notification.create(
                1, NotificationType.ANSWER_ACCEPTED, "你的回答被采纳", "/questions/2", "evt-a2", at.plusSeconds(30));
        read.markRead(1);
        fx.notifications.save(read);
        fx.notifications.save(Notification.create(
                2, NotificationType.MENTIONED, "有人在评论中提到了你", "/questions/3", "evt-b1", at.plusSeconds(60)));
        assertThat(unread.read()).isFalse();
    }

    @Test
    void listReturnsOnlyOwnNotifications() {
        PageResult<NotificationView> page = service.list(alice, new PageRequest(1, 20));

        assertThat(page.items()).hasSize(2);
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).allSatisfy(v -> assertThat(v.title()).doesNotContain("提到了你"));

        PageResult<NotificationView> bobPage = service.list(bob, new PageRequest(1, 20));
        assertThat(bobPage.items()).singleElement()
                .satisfies(v -> assertThat(v.type()).isEqualTo("MENTIONED"));
    }

    @Test
    void toViewMapsDomainFieldsIncludingReadFlag() {
        PageResult<NotificationView> page = service.list(alice, new PageRequest(1, 20));

        NotificationView newest = page.items().get(0);
        assertThat(newest.id()).isPositive();
        assertThat(newest.type()).isEqualTo("ANSWER_ACCEPTED");
        assertThat(newest.link()).isEqualTo("/questions/2");
        assertThat(newest.read()).isTrue();
        assertThat(newest.createdAt()).isEqualTo(at.plusSeconds(30));
    }

    @Test
    void unreadCountOnlyCountsOwnUnread() {
        assertThat(service.unreadCount(alice)).isEqualTo(1);
        assertThat(service.unreadCount(bob)).isEqualTo(1);
    }

    @Test
    void markReadUpdatesOwnNotification() {
        long id = service.list(alice, new PageRequest(1, 20)).items().stream()
                .filter(v -> !v.read()).findFirst().orElseThrow().id();

        service.markRead(alice, id);

        assertThat(service.unreadCount(alice)).isZero();
        assertThat(service.list(alice, new PageRequest(1, 20)).items())
                .allSatisfy(v -> assertThat(v.read()).isTrue());
    }

    @Test
    void markReadMissingNotificationThrows404() {
        assertThatThrownBy(() -> service.markRead(alice, 9999))
                .isInstanceOf(NotFoundException.class)
                .satisfies(e -> {
                    NotFoundException ex = (NotFoundException) e;
                    assertThat(ex.code()).isEqualTo("DIS-4001");
                    assertThat(ex.suggestedStatus()).isEqualTo(404);
                });
    }

    @Test
    void markReadOthersNotificationThrows403() {
        long bobId = service.list(bob, new PageRequest(1, 20)).items().get(0).id();

        assertThatThrownBy(() -> service.markRead(alice, bobId))
                .isInstanceOf(ForbiddenException.class)
                .satisfies(e -> {
                    ForbiddenException ex = (ForbiddenException) e;
                    assertThat(ex.code()).isEqualTo("DIS-3001");
                    assertThat(ex.suggestedStatus()).isEqualTo(403);
                });
        // 越权失败后对方通知仍未读
        assertThat(service.unreadCount(bob)).isEqualTo(1);
    }

    @Test
    void markAllReadReturnsUpdatedCountAndIsScopedToActor() {
        assertThat(service.markAllRead(alice)).isEqualTo(1);
        assertThat(service.unreadCount(alice)).isZero();
        assertThat(service.unreadCount(bob)).isEqualTo(1);

        // 再调一次没有可更新的，返回 0
        assertThat(service.markAllRead(alice)).isZero();
    }
}
