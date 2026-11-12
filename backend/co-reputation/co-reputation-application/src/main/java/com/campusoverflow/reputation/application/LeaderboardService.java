package com.campusoverflow.reputation.application;

import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.reputation.api.event.ReputationChangedEvent;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.shared.event.ProcessedEventStore;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 排行榜：周/月/学期榜来自 Redis ZSET，总榜直接读账户表。 */
@Service
public class LeaderboardService {

    private final LeaderboardStore store;
    private final ReputationAccountRepository accounts;
    private final IdentityApi identity;
    private final ProcessedEventStore processed;
    private final Clock clock;

    public LeaderboardService(LeaderboardStore store, ReputationAccountRepository accounts, IdentityApi identity,
                              ProcessedEventStore processed, Clock clock) {
        this.store = store;
        this.accounts = accounts;
        this.identity = identity;
        this.processed = processed;
        this.clock = clock;
    }

    @EventListener
    @Transactional
    public void on(ReputationChangedEvent e) {
        // 声誉发生变化时，把增量累加到「周/月/学期」三个 Redis ZSET；总榜直接读账户表。
        if (!processed.markProcessed(e.eventId(), "reputation.leaderboard")) {
            return;
        }
        store.increment(e.userId(), e.delta(), e.occurredAt());
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryView> top(LeaderboardPeriod period, int limit) {
        List<LeaderboardStore.Entry> raw;
        if (period == LeaderboardPeriod.ALL) {
            raw = accounts.topByReputation(limit).stream()
                    .map(a -> new LeaderboardStore.Entry(a.userId(), a.reputation()))
                    .toList();
        } else {
            raw = store.top(period, clock.instant(), limit);
        }
        if (raw.isEmpty()) {
            return List.of();
        }
        Map<Long, UserSummary> users =
                identity.findUsers(raw.stream().map(LeaderboardStore.Entry::userId).toList());
        List<LeaderboardEntryView> result = new ArrayList<>();
        int rank = 1;
        for (LeaderboardStore.Entry entry : raw) {
            UserSummary user = users.get(entry.userId());
            String displayName = user != null ? user.displayName() : "用户" + entry.userId();
            boolean verified = user != null && user.verified();
            result.add(new LeaderboardEntryView(rank++, entry.userId(), displayName, verified, entry.score()));
        }
        return result;
    }
}
