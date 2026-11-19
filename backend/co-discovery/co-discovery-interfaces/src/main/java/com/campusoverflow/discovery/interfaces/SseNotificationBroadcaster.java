package com.campusoverflow.discovery.interfaces;

import com.campusoverflow.discovery.application.NotificationBroadcaster;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 版实时通知推送（#49）。
 * <p>维护 userId → 该用户所有在线连接的映射；连接结束/超时/出错时把自己摘掉，避免连接泄漏。
 * 每 30 秒发一次注释心跳（comment），防止中间代理把空闲连接判定为超时。
 * 推送负载只含未读数，前端收到后再按需回查，不推通知全文。</p>
 */
@Component
public class SseNotificationBroadcaster implements NotificationBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(SseNotificationBroadcaster.class);

    /** 心跳间隔（秒），需小于 nginx 的 proxy_read_timeout。 */
    private static final long HEARTBEAT_SECONDS = 30;

    private final Map<Long, Set<SseEmitter>> connections = new ConcurrentHashMap<>();

    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "notification-sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public SseNotificationBroadcaster() {
        heartbeat.scheduleAtFixedRate(this::pingAll, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    /** 建立一条 SSE 连接并登记到该用户的连接集合。 */
    public SseEmitter subscribe(long userId) {
        // 0L：不设服务端超时，连接由客户端断开或心跳写失败触发清理
        SseEmitter emitter = new SseEmitter(0L);
        connections.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(emitter);

        emitter.onCompletion(() -> unregister(userId, emitter));
        emitter.onTimeout(() -> {
            unregister(userId, emitter);
            safeComplete(emitter);
        });
        emitter.onError(error -> unregister(userId, emitter));

        try {
            // 立即发一条注释，让客户端与代理尽快建立连接
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException | RuntimeException ex) {
            unregister(userId, emitter);
            safeComplete(emitter);
        }
        log.debug("SSE 连接已建立 user={} 在线连接数={}", userId, connectionCount(userId));
        return emitter;
    }

    @Override
    public int pushUnread(long recipientId, long unread) {
        Set<SseEmitter> online = connections.get(recipientId);
        if (online == null || online.isEmpty()) {
            return 0;
        }
        String payload = "{\"unread\":" + unread + "}";
        int sent = 0;
        for (SseEmitter emitter : online) {
            try {
                emitter.send(SseEmitter.event().data(payload));
                sent++;
            } catch (IOException | RuntimeException ex) {
                // 写失败说明连接已断，立即摘除，避免泄漏
                unregister(recipientId, emitter);
                safeComplete(emitter);
            }
        }
        return sent;
    }

    /** 心跳：向所有连接发注释，同时顺带清掉已失效的连接。 */
    private void pingAll() {
        connections.forEach((userId, online) -> {
            for (SseEmitter emitter : online) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | RuntimeException ex) {
                    unregister(userId, emitter);
                    safeComplete(emitter);
                }
            }
        });
    }

    /** 从登记表移除某条连接；该用户没有连接后整条记录删除，表不会持续增长。 */
    void unregister(long userId, SseEmitter emitter) {
        connections.computeIfPresent(userId, (key, online) -> {
            online.remove(emitter);
            return online.isEmpty() ? null : online;
        });
    }

    /** 某个用户当前的在线连接数（用于验证清理逻辑）。 */
    public int connectionCount(long userId) {
        Set<SseEmitter> online = connections.get(userId);
        return online == null ? 0 : online.size();
    }

    /** 全站在线连接总数（用于监控与验证）。 */
    public int totalConnectionCount() {
        return connections.values().stream().mapToInt(Set::size).sum();
    }

    @PreDestroy
    void shutdown() {
        heartbeat.shutdownNow();
        connections.values().forEach(online -> online.forEach(this::safeComplete));
        connections.clear();
    }

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            // 连接已经结束，完成动作失败无需处理
        }
    }
}
