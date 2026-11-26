package com.campusoverflow.discovery.domain.notification;

import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ForbiddenException;
import java.time.Instant;
import java.util.Objects;

/** 站内通知。(sourceEventId, recipientId) 唯一，事件重复投递不会产生重复通知。 */
public class Notification {

    /** 标题上限：超出部分截断，避免把整段问题描述塞进通知列表。 */
    private static final int MAX_TITLE_LENGTH = 200;

    private Long id;
    private final long recipientId;
    private final NotificationType type;
    private final String title;
    private final String link;
    private final String sourceEventId;
    private boolean read;
    private final Instant createdAt;

    private Notification(Long id, long recipientId, NotificationType type, String title, String link,
                         String sourceEventId, boolean read, Instant createdAt) {
        this.id = id;
        this.recipientId = recipientId;
        this.type = Objects.requireNonNull(type);
        this.title = title;
        this.link = link;
        this.sourceEventId = Objects.requireNonNull(sourceEventId);
        this.read = read;
        this.createdAt = createdAt;
    }

    /** 新通知一律为未读；标题不能为空，超过 200 个字符按码点截断。 */
    public static Notification create(long recipientId, NotificationType type, String title, String link,
                                      String sourceEventId, Instant now) {
        if (title == null || title.isBlank()) {
            throw new BusinessRuleException("DIS-1003", "通知标题不能为空");
        }
        String normalized = title.strip();
        if (normalized.codePointCount(0, normalized.length()) > MAX_TITLE_LENGTH) {
            normalized = normalized.substring(0, normalized.offsetByCodePoints(0, MAX_TITLE_LENGTH));
        }
        return new Notification(null, recipientId, type, normalized, link, sourceEventId, false,
                now == null ? Instant.now() : now);
    }

    public static Notification reconstitute(Long id, long recipientId, NotificationType type, String title,
                                            String link, String sourceEventId, boolean read, Instant createdAt) {
        return new Notification(id, recipientId, type, title, link, sourceEventId, read, createdAt);
    }

    /** 只有收件人本人可以标记已读（否则 403 DIS-3001）；重复标记幂等。 */
    public void markRead(long actorId) {
        if (actorId != recipientId) {
            throw new ForbiddenException("DIS-3001", "只能标记自己的通知为已读");
        }
        this.read = true;
    }

    public void assignId(long id) {
        this.id = id;
    }

    public Long id() { return id; }
    public long recipientId() { return recipientId; }
    public NotificationType type() { return type; }
    public String title() { return title; }
    public String link() { return link; }
    public String sourceEventId() { return sourceEventId; }
    public boolean read() { return read; }
    public Instant createdAt() { return createdAt; }
}
