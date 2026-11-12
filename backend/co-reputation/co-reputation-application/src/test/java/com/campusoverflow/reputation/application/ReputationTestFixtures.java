package com.campusoverflow.reputation.application;

import com.campusoverflow.identity.api.CourseSummary;
import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.qa.api.QaQueryApi;
import com.campusoverflow.qa.api.QuestionRef;
import com.campusoverflow.reputation.domain.BadgeAward;
import com.campusoverflow.reputation.domain.BadgeRepository;
import com.campusoverflow.reputation.domain.BadgeType;
import com.campusoverflow.reputation.domain.Bounty;
import com.campusoverflow.reputation.domain.BountyRepository;
import com.campusoverflow.reputation.domain.BountyStatus;
import com.campusoverflow.reputation.domain.LedgerEntry;
import com.campusoverflow.reputation.domain.LedgerRepository;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.reputation.domain.ReputationReason;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.event.IntegrationEvent;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.event.ProcessedEventStore;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 应用层测试替身集合：内存仓储、RecordingEvents、StubIdentity、StubQa、固定 Clock。
 * 与生产实现解耦，让事件处理器可在纯内存事务语义下被反复调用以验证幂等。
 */
public final class ReputationTestFixtures {

    public static final Instant NOW = Instant.parse("2026-11-10T02:00:00Z");

    public final InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    public final InMemoryLedgerRepository ledger = new InMemoryLedgerRepository();
    public final InMemoryBadgeRepository badges = new InMemoryBadgeRepository();
    public final InMemoryBountyRepository bounties = new InMemoryBountyRepository();
    public final InMemoryLeaderboardStore leaderboard = new InMemoryLeaderboardStore();
    public final InMemoryProcessedEventStore processed = new InMemoryProcessedEventStore();
    public final RecordingEvents events = new RecordingEvents();
    public final RecordingAudit audit = new RecordingAudit();
    public final StubIdentity identity = new StubIdentity();
    public final StubQa qa = new StubQa();
    public final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    public final BadgeService badgeService = new BadgeService(badges, events, clock);
    public final ReputationLedgerService ledgerService =
            new ReputationLedgerService(accounts, ledger, badgeService, events, clock);
    public final BountyService bountyService =
            new BountyService(bounties, accounts, ledgerService, qa, identity, events, audit, clock);
    public final ReputationEventHandlers handlers =
            new ReputationEventHandlers(accounts, ledger, ledgerService, badgeService, bountyService, clock);
    public final LeaderboardService leaderboardService =
            new LeaderboardService(leaderboard, accounts, identity, processed, clock);
    public final ReputationQueryService queryService =
            new ReputationQueryService(accounts, ledger, badges, identity);
    public final ReputationApiImpl api = new ReputationApiImpl(accounts);
    public final ReconciliationService reconciliation = new ReconciliationService(accounts, ledger);

    public ReputationAccount accountOf(long userId) {
        return accounts.findByUserId(userId).orElseThrow();
    }

    // ---------------------------------------------------------------- 内存仓储

    public static final class InMemoryAccountRepository implements ReputationAccountRepository {
        private final Map<Long, ReputationAccount> store = new LinkedHashMap<>();

        @Override
        public Optional<ReputationAccount> findByUserId(long userId) {
            return Optional.ofNullable(store.get(userId));
        }

        @Override
        public List<ReputationAccount> findAllByUserId(java.util.Collection<Long> userIds) {
            return userIds.stream().map(store::get).filter(java.util.Objects::nonNull).toList();
        }

        @Override
        public List<ReputationAccount> topByReputation(int limit) {
            return store.values().stream()
                    .sorted(Comparator.comparingInt(ReputationAccount::reputation).reversed())
                    .limit(limit).toList();
        }

        @Override
        public List<ReputationAccount> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public ReputationAccount save(ReputationAccount account) {
            store.put(account.userId(), account);
            return account;
        }
    }

    public static final class InMemoryLedgerRepository implements LedgerRepository {
        private final List<LedgerEntry> entries = new ArrayList<>();
        private final AtomicLong ids = new AtomicLong(1);

        @Override
        public boolean exists(String eventId, long userId, ReputationReason reason) {
            return entries.stream().anyMatch(e -> e.eventId().equals(eventId) && e.userId() == userId
                    && e.reason() == reason);
        }

        @Override
        public void append(LedgerEntry entry) {
            entries.add(new LedgerEntry(ids.getAndIncrement(), entry.userId(), entry.eventId(), entry.reason(),
                    entry.delta(), entry.refType(), entry.refId(), entry.createdAt()));
        }

        @Override
        public PageResult<LedgerEntry> findByUser(long userId, PageRequest page) {
            List<LedgerEntry> mine = entries.stream()
                    .filter(e -> e.userId() == userId)
                    .sorted(Comparator.comparing(LedgerEntry::createdAt).reversed())
                    .toList();
            int from = Math.min(page.offset(), mine.size());
            int to = Math.min(from + page.size(), mine.size());
            return new PageResult<>(mine.subList(from, to), mine.size(), page.page(), page.size());
        }

        @Override
        public long countByUserAndReason(long userId, ReputationReason reason) {
            return entries.stream().filter(e -> e.userId() == userId && e.reason() == reason).count();
        }

        @Override
        public int sumPositiveVoteDeltaSince(long userId, Instant since) {
            return entries.stream()
                    .filter(e -> e.userId() == userId && e.reason().isVote() && e.delta() > 0
                            && !e.createdAt().isBefore(since))
                    .mapToInt(LedgerEntry::delta).sum();
        }

        @Override
        public Map<Long, Long> sumByUser() {
            Map<Long, Long> sums = new HashMap<>();
            entries.forEach(e -> sums.merge(e.userId(), (long) e.delta(), Long::sum));
            return sums;
        }

        public List<LedgerEntry> all() {
            return List.copyOf(entries);
        }
    }

    public static final class InMemoryBadgeRepository implements BadgeRepository {
        private final Map<Long, List<BadgeAward>> store = new HashMap<>();

        @Override
        public boolean has(long userId, BadgeType type) {
            return store.getOrDefault(userId, List.of()).stream().anyMatch(a -> a.type() == type);
        }

        @Override
        public void award(BadgeAward award) {
            store.computeIfAbsent(award.userId(), k -> new ArrayList<>()).add(award);
        }

        @Override
        public List<BadgeAward> findByUser(long userId) {
            return List.copyOf(store.getOrDefault(userId, List.of()));
        }
    }

    public static final class InMemoryBountyRepository implements BountyRepository {
        private final List<Bounty> store = new ArrayList<>();
        private final AtomicLong ids = new AtomicLong(1);

        @Override
        public Optional<Bounty> findOpenByQuestion(long questionId) {
            return store.stream().filter(b -> b.questionId() == questionId && b.status() == BountyStatus.OPEN)
                    .findFirst();
        }

        @Override
        public Optional<Bounty> findLatestByQuestion(long questionId) {
            return store.stream().filter(b -> b.questionId() == questionId)
                    .max(Comparator.comparing(Bounty::createdAt));
        }

        @Override
        public List<Bounty> findDue(Instant now, int limit) {
            return store.stream().filter(b -> b.isDue(now)).limit(limit).toList();
        }

        @Override
        public Bounty save(Bounty bounty) {
            if (bounty.id() == null) {
                bounty.assignId(ids.getAndIncrement());
                store.add(bounty);
                return bounty;
            }
            boolean exists = store.stream().anyMatch(b -> b.id().equals(bounty.id()));
            if (exists) {
                store.replaceAll(b -> b.id().equals(bounty.id()) ? bounty : b);
            } else {
                store.add(bounty);
            }
            return bounty;
        }

        public List<Bounty> all() {
            return List.copyOf(store);
        }
    }

    public static final class InMemoryLeaderboardStore implements LeaderboardStore {
        private final List<Entry> increments = new ArrayList<>();

        private record Entry(long userId, int delta, Instant at) {
        }

        @Override
        public void increment(long userId, int delta, Instant at) {
            increments.add(new Entry(userId, delta, at));
        }

        @Override
        public List<LeaderboardStore.Entry> top(LeaderboardPeriod period, Instant at, int limit) {
            Map<Long, Long> sums = new LinkedHashMap<>();
            increments.forEach(e -> sums.merge(e.userId(), (long) e.delta(), Long::sum));
            return sums.entrySet().stream()
                    .filter(e -> e.getValue() != 0)
                    .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                    .limit(limit)
                    .map(e -> new LeaderboardStore.Entry(e.getKey(), e.getValue()))
                    .toList();
        }

        public int incrementCount() {
            return increments.size();
        }
    }

    public static final class InMemoryProcessedEventStore implements ProcessedEventStore {
        private final Set<String> keys = new HashSet<>();

        @Override
        public boolean markProcessed(String eventId, String consumer) {
            return keys.add(consumer + ":" + eventId);
        }
    }

    public static final class RecordingEvents implements IntegrationEventPublisher {
        private final List<IntegrationEvent> published = new ArrayList<>();

        @Override
        public void publish(IntegrationEvent event) {
            published.add(event);
        }

        public List<IntegrationEvent> all() {
            return List.copyOf(published);
        }

        public long count(String simpleTypeName) {
            return published.stream().filter(e -> e.getClass().getSimpleName().equals(simpleTypeName)).count();
        }
    }

    public static final class RecordingAudit implements AuditTrail {
        private final List<String> actions = new ArrayList<>();

        @Override
        public void record(String action, Long actorId, String targetType, String targetId, String detail) {
            actions.add(action + "|" + actorId + "|" + targetType + "|" + targetId);
        }

        public List<String> all() {
            return List.copyOf(actions);
        }
    }

    public static final class StubIdentity implements IdentityApi {
        private final Map<Long, UserSummary> users = new HashMap<>();

        public void register(long userId, String displayName, Role role) {
            users.put(userId, new UserSummary(userId, displayName, role, role == Role.TEACHER, "计算机学院"));
        }

        @Override
        public Optional<UserSummary> findUser(long userId) {
            return Optional.ofNullable(users.get(userId));
        }

        @Override
        public Map<Long, UserSummary> findUsers(java.util.Collection<Long> userIds) {
            Map<Long, UserSummary> result = new HashMap<>();
            userIds.forEach(id -> {
                UserSummary u = users.get(id);
                if (u != null) {
                    result.put(id, u);
                }
            });
            return result;
        }

        @Override
        public Optional<UserSummary> findByDisplayName(String displayName) {
            return users.values().stream().filter(u -> u.displayName().equals(displayName)).findFirst();
        }

        @Override
        public Optional<CourseSummary> findCourse(long courseId) {
            return Optional.empty();
        }

        @Override
        public boolean canManageCourse(long userId, Long courseId) {
            UserSummary u = users.get(userId);
            return u != null && (u.role() == Role.TEACHER || u.role() == Role.TA || u.role() == Role.ADMIN);
        }
    }

    public static final class StubQa implements QaQueryApi {
        private final Map<Long, QuestionRef> questions = new HashMap<>();

        public void put(QuestionRef ref) {
            questions.put(ref.id(), ref);
        }

        @Override
        public Optional<QuestionRef> findQuestion(long questionId) {
            return Optional.ofNullable(questions.get(questionId));
        }
    }
}