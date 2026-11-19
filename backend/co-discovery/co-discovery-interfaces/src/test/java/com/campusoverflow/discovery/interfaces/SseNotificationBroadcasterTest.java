package com.campusoverflow.discovery.interfaces;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * #49：SSE 连接登记表——按用户隔离、断开即摘除、无连接时推送返回 0。
 */
class SseNotificationBroadcasterTest {

    private final SseNotificationBroadcaster hub = new SseNotificationBroadcaster();

    @AfterEach
    void tearDown() {
        hub.shutdown();
    }

    @Test
    void registersPerUserAndRemovesOnClose() {
        SseEmitter first = hub.subscribe(1);
        SseEmitter second = hub.subscribe(1);
        SseEmitter other = hub.subscribe(2);

        assertThat(hub.connectionCount(1)).isEqualTo(2);
        assertThat(hub.connectionCount(2)).isEqualTo(1);
        assertThat(hub.totalConnectionCount()).isEqualTo(3);

        hub.unregister(1, first);
        assertThat(hub.connectionCount(1)).isEqualTo(1);

        hub.unregister(1, second);
        assertThat(hub.connectionCount(1)).isZero();

        hub.unregister(2, other);
        assertThat(hub.totalConnectionCount()).isZero();
    }

    @Test
    void pushOnlyReachesTargetUserConnections() {
        hub.subscribe(1);
        hub.subscribe(1);
        hub.subscribe(2);

        assertThat(hub.pushUnread(1, 3)).isEqualTo(2);
        assertThat(hub.pushUnread(2, 1)).isEqualTo(1);
        // 不在线的用户不产生任何推送
        assertThat(hub.pushUnread(99, 5)).isZero();
    }

    @Test
    void pushAfterAllConnectionsClosedReturnsZero() {
        SseEmitter emitter = hub.subscribe(3);
        hub.unregister(3, emitter);

        assertThat(hub.pushUnread(3, 1)).isZero();
        assertThat(hub.connectionCount(3)).isZero();
    }
}
