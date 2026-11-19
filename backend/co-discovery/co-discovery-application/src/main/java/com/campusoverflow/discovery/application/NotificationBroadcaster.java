package com.campusoverflow.discovery.application;

/**
 * 通知实时推送端口（#49）。
 * <p>应用层只表达"给哪个用户推多少条未读"，具体传输方式（SSE / WebSocket / 空实现）由接口层决定，
 * 这样应用层不依赖任何 HTTP 概念，也便于测试替换。</p>
 */
@FunctionalInterface
public interface NotificationBroadcaster {

    /** 空实现：未部署实时通道（或单元测试场景）时使用，不产生任何副作用。 */
    NotificationBroadcaster NOOP = (recipientId, unread) -> 0;

    /**
     * 向某个用户的在线连接推送未读数。
     *
     * @return 实际推送成功的连接数；用户不在线时返回 0
     */
    int pushUnread(long recipientId, long unread);
}
