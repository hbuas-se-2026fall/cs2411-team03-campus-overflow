package com.campusoverflow.discovery.application;

import com.campusoverflow.discovery.domain.notification.Notification;
import com.campusoverflow.discovery.domain.notification.NotificationRepository;
import com.campusoverflow.identity.api.CourseSummary;
import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.qa.api.QaQueryApi;
import com.campusoverflow.qa.api.QuestionRef;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Discovery 应用层测试替身：内存通知仓储、Stub 身份上下文、Stub Q&A 查询。
 * 与生产实现解耦，让投影器可以在纯内存语义下被反复调用以验证幂等。
 */
public final class DiscoveryTestFixtures {

    public static final Instant NOW = Instant.parse("2026-11-12T02:00:00Z");

    public final InMemoryNotificationRepository notifications = new InMemoryNotificationRepository();
    public final StubIdentity identity = new StubIdentity();
    public final StubQa qa = new StubQa();
    public final NotificationProjector projector = new NotificationProjector(notifications, identity, qa);

    /** 造一个可用昵称解析的用户。 */
    public UserSummary user(long id, String displayName, Role role) {
        UserSummary summary = new UserSummary(id, displayName, role, true, "计算机学院");
        identity.byId.put(id, summary);
        identity.byName.put(displayName, summary);
        return summary;
    }

    public List<Notification> all() {
        return List.copyOf(notifications.store.values());
    }

    public List<Notification> of(long recipientId) {
        return notifications.store.values().stream()
                .filter(n -> n.recipientId() == recipientId)
                .toList();
    }

    // ---------------------------------------------------------------- 内存仓储

    public static final class InMemoryNotificationRepository implements NotificationRepository {
        private final Map<Long, Notification> store = new LinkedHashMap<>();
        private final AtomicLong ids = new AtomicLong(1);

        @Override
        public boolean exists(String sourceEventId, long recipientId) {
            return store.values().stream()
                    .anyMatch(n -> n.sourceEventId().equals(sourceEventId) && n.recipientId() == recipientId);
        }

        @Override
        public Notification save(Notification notification) {
            if (notification.id() == null) {
                notification.assignId(ids.getAndIncrement());
            }
            store.put(notification.id(), notification);
            return notification;
        }

        @Override
        public Optional<Notification> findById(long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public PageResult<Notification> findByRecipient(long recipientId, PageRequest page) {
            List<Notification> hits = store.values().stream()
                    .filter(n -> n.recipientId() == recipientId)
                    .sorted(Comparator.comparing(Notification::createdAt).reversed()
                            .thenComparing(Notification::id, Comparator.reverseOrder()))
                    .toList();
            int from = Math.min(page.offset(), hits.size());
            int to = Math.min(from + page.size(), hits.size());
            return new PageResult<>(hits.subList(from, to), hits.size(), page.page(), page.size());
        }

        @Override
        public long countUnread(long recipientId) {
            return store.values().stream()
                    .filter(n -> n.recipientId() == recipientId && !n.read())
                    .count();
        }

        @Override
        public int markAllRead(long recipientId) {
            int changed = 0;
            for (Notification n : store.values()) {
                if (n.recipientId() == recipientId && !n.read()) {
                    n.markRead(recipientId);
                    changed++;
                }
            }
            return changed;
        }
    }

    // ---------------------------------------------------------------- 身份上下文

    public static final class StubIdentity implements IdentityApi {
        public final Map<Long, UserSummary> byId = new LinkedHashMap<>();
        public final Map<String, UserSummary> byName = new LinkedHashMap<>();

        @Override
        public Optional<UserSummary> findUser(long userId) {
            return Optional.ofNullable(byId.get(userId));
        }

        @Override
        public Map<Long, UserSummary> findUsers(Collection<Long> userIds) {
            Map<Long, UserSummary> found = new LinkedHashMap<>();
            for (Long id : userIds) {
                Optional.ofNullable(byId.get(id)).ifPresent(u -> found.put(id, u));
            }
            return found;
        }

        @Override
        public Optional<UserSummary> findByDisplayName(String displayName) {
            return Optional.ofNullable(byName.get(displayName));
        }

        @Override
        public Optional<CourseSummary> findCourse(long courseId) {
            return Optional.empty();
        }

        @Override
        public boolean canManageCourse(long userId, Long courseId) {
            return true;
        }
    }

    // ---------------------------------------------------------------- Q&A 查询

    public static final class StubQa implements QaQueryApi {
        public final Map<Long, QuestionRef> questions = new LinkedHashMap<>();

        @Override
        public Optional<QuestionRef> findQuestion(long questionId) {
            return Optional.ofNullable(questions.get(questionId));
        }

        public void put(long id, String title) {
            questions.put(id, new QuestionRef(id, 1, null, title, null, false));
        }
    }
}
