package com.campusoverflow.discovery.interfaces;

import com.campusoverflow.discovery.application.NotificationService;
import com.campusoverflow.discovery.application.NotificationView;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "通知 Notification")
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notifications;
    private final SseNotificationBroadcaster broadcaster;

    public NotificationController(NotificationService notifications, SseNotificationBroadcaster broadcaster) {
        this.notifications = notifications;
        this.broadcaster = broadcaster;
    }

    @Operation(summary = "我的通知")
    @GetMapping
    public PageResult<NotificationView> list(@Parameter(hidden = true) Actor actor,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return notifications.list(actor, new PageRequest(page, size));
    }

    @Operation(summary = "未读数量")
    @GetMapping("/unread-count")
    public Map<String, Long> unread(@Parameter(hidden = true) Actor actor) {
        return Map.of("count", notifications.unreadCount(actor));
    }

    @Operation(summary = "标记已读")
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> read(@Parameter(hidden = true) Actor actor, @PathVariable long id) {
        notifications.markRead(actor, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "全部标记已读")
    @PostMapping("/read-all")
    public Map<String, Integer> readAll(@Parameter(hidden = true) Actor actor) {
        return Map.of("updated", notifications.markAllRead(actor));
    }

    @Operation(summary = "通知实时流（SSE）：有新通知时推送未读数")
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Parameter(hidden = true) Actor actor) {
        // 只订阅当前会话所属用户，未登录由安全过滤链返回 401
        return broadcaster.subscribe(actor.userId());
    }
}
