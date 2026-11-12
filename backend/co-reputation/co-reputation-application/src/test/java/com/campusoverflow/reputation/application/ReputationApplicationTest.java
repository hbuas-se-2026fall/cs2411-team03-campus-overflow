package com.campusoverflow.reputation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.campusoverflow.identity.api.event.UserRegisteredEvent;
import com.campusoverflow.qa.api.QuestionRef;
import com.campusoverflow.qa.api.event.AnswerAcceptedEvent;
import com.campusoverflow.qa.api.event.VoteCastEvent;
import com.campusoverflow.reputation.api.event.ReputationChangedEvent;
import com.campusoverflow.reputation.domain.BadgeType;
import com.campusoverflow.reputation.domain.Bounty;
import com.campusoverflow.reputation.domain.BountyStatus;
import com.campusoverflow.reputation.domain.LedgerEntry;
import com.campusoverflow.reputation.domain.ReputationReason;
import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 应用层事件处理器、查询服务、榜单与对账的行为测试（含幂等，V3 6.4.3 的验收重点）。 */
class ReputationApplicationTest {

    private static final Instant NOW = ReputationTestFixtures.NOW;
    private ReputationTestFixtures fx;

    @BeforeEach
    void setUp() {
        fx = new ReputationTestFixtures();
    }

    private static Actor actor(long id, Role role) {
        return new Actor(id, "user-" + id, role);
    }

    private void registered(long userId, Role role) {
        fx.identity.register(userId, "user-" + userId, role);
        fx.handlers.on(new UserRegisteredEvent("reg-" + userId, NOW, userId, "user-" + userId, role));
    }

    private AnswerAcceptedEvent accepted(String eventId, long answerId, long questionId, long authorId,
                                         long questionAuthorId) {
        return new AnswerAcceptedEvent(eventId, NOW, answerId, questionId, authorId, questionAuthorId, questionAuthorId,
                "question-" + questionId);
    }

    private VoteCastEvent vote(String eventId, String targetType, long targetId, long authorId, int oldValue,
                               int newValue) {
        return new VoteCastEvent(eventId, NOW, targetType, targetId, authorId, 99L, 1L, oldValue, newValue);
    }

    // ------------------------------------------------------------ 开户

    @Test
    @DisplayName("注册开户：教师 1000、学生 0，重复注册不改变余额")
    void registerOpensAccountWithInitialPoints() {
        registered(1L, Role.TEACHER);
        registered(2L, Role.STUDENT);

        assertEquals(1000, fx.accountOf(1L).availablePoints());
        assertEquals(0, fx.accountOf(2L).availablePoints());
        assertEquals(0, fx.accountOf(2L).reputation());

        registered(1L, Role.TEACHER);
        assertEquals(1000, fx.accountOf(1L).availablePoints());
        assertEquals(2, fx.accounts.findAll().size());
    }

    // ------------------------------------------------------------ 采纳

    @Test
    @DisplayName("采纳：回答者 +15 且只记一次账，并授予「初露锋芒」")
    void acceptAwardsFifteenOnceAndGrantsBadge() {
        registered(2L, Role.STUDENT);
        AnswerAcceptedEvent e = accepted("evt-a1", 10L, 20L, 2L, 1L);

        fx.handlers.on(e);
        fx.handlers.on(e);

        assertEquals(15, fx.accountOf(2L).reputation());
        assertEquals(1, fx.ledger.all().size());
        assertTrue(fx.badges.has(2L, BadgeType.FIRST_ACCEPTED));
        assertEquals(1, fx.badges.findByUser(2L).stream()
                .filter(b -> b.type() == BadgeType.FIRST_ACCEPTED).count());
    }

    @Test
    @DisplayName("自问自答被采纳：声誉不变、不产生流水")
    void selfAcceptedKeepsReputation() {
        registered(1L, Role.TEACHER);

        fx.handlers.on(accepted("evt-self", 10L, 20L, 1L, 1L));

        assertEquals(0, fx.accountOf(1L).reputation());
        assertEquals(0, fx.ledger.all().size());
    }

    @Test
    @DisplayName("第 10 次采纳授予「解惑达人」")
    void tenthAcceptanceGrantsHelperBadge() {
        registered(2L, Role.STUDENT);
        for (int i = 0; i < 10; i++) {
            fx.handlers.on(accepted("evt-h" + i, 100L + i, 200L + i, 2L, 1L));
        }

        assertEquals(150, fx.accountOf(2L).reputation());
        assertTrue(fx.badges.has(2L, BadgeType.FIRST_ACCEPTED));
        assertTrue(fx.badges.has(2L, BadgeType.HELPER_10));
    }

    // ------------------------------------------------------------ 投票

    @Test
    @DisplayName("点赞 +10、踩 -2、赞改踩 -12")
    void voteDeltasFollowRulebook() {
        registered(2L, Role.STUDENT);

        fx.handlers.on(vote("v-up", "ANSWER", 10L, 2L, 0, 1));
        fx.handlers.on(vote("v-down", "ANSWER", 11L, 2L, 0, -1));
        fx.handlers.on(vote("v-flip", "ANSWER", 12L, 2L, 1, -1));

        assertTrue(fx.ledger.all().stream().anyMatch(e -> e.delta() == 10));
        assertTrue(fx.ledger.all().stream().anyMatch(e -> e.delta() == -2));
        assertTrue(fx.ledger.all().stream().anyMatch(e -> e.delta() == -12));
        assertEquals(10 - 2 - 12, fx.accountOf(2L).reputation());
    }

    @Test
    @DisplayName("投票 reason 按 targetType 区分问题票与回答票")
    void voteReasonDependsOnTargetType() {
        registered(2L, Role.STUDENT);

        fx.handlers.on(vote("v-q", "QUESTION", 10L, 2L, 0, 1));
        fx.handlers.on(vote("v-a", "ANSWER", 11L, 2L, 0, 1));

        assertTrue(fx.ledger.all().stream().anyMatch(e -> e.reason() == ReputationReason.QUESTION_VOTE));
        assertTrue(fx.ledger.all().stream().anyMatch(e -> e.reason() == ReputationReason.ANSWER_VOTE));
    }

    @Test
    @DisplayName("每日正向投票上限 200：超出部分不计")
    void positiveVotesCappedAt200PerDay() {
        registered(2L, Role.STUDENT);

        for (int i = 0; i < 20; i++) {
            fx.handlers.on(vote("v-cap" + i, "ANSWER", 100L + i, 2L, 0, 1));
        }
        assertEquals(200, fx.accountOf(2L).reputation());

        fx.handlers.on(vote("v-cap-over", "ANSWER", 200L, 2L, 0, 1));
        assertEquals(200, fx.accountOf(2L).reputation());
        assertEquals(20, fx.ledger.all().size());
    }

    @Test
    @DisplayName("同一投票事件重放不重复记账")
    void repeatedVoteEventIsIdempotent() {
        registered(2L, Role.STUDENT);
        VoteCastEvent e = vote("v-dup", "ANSWER", 10L, 2L, 0, 1);

        fx.handlers.on(e);
        fx.handlers.on(e);

        assertEquals(10, fx.accountOf(2L).reputation());
        assertEquals(1, fx.ledger.all().size());
    }

    // ------------------------------------------------------------ 悬赏

    @Test
    @DisplayName("发起悬赏冻结积分，采纳他人回答后结算给获奖者")
    void bountyFreezesThenAwardsOnAccept() {
        registered(1L, Role.TEACHER);
        registered(2L, Role.STUDENT);
        fx.qa.put(new QuestionRef(20L, 1L, 5L, "问题", null, false));

        fx.bountyService.open(actor(1L, Role.TEACHER), 20L, 50, 7);

        assertEquals(950, fx.accountOf(1L).availablePoints());
        assertEquals(50, fx.accountOf(1L).frozenPoints());

        fx.handlers.on(accepted("evt-bounty", 10L, 20L, 2L, 1L));

        assertEquals(0, fx.accountOf(1L).frozenPoints());
        assertEquals(950, fx.accountOf(1L).availablePoints());
        assertEquals(65, fx.accountOf(2L).reputation());
        assertTrue(fx.ledger.all().stream()
                .anyMatch(e -> e.reason() == ReputationReason.BOUNTY_AWARDED && e.delta() == 50 && e.userId() == 2L));
        assertEquals(1, fx.events.count("BountyAwardedEvent"));
    }

    @Test
    @DisplayName("采纳答案恰是发起人自己的：悬赏关闭并退款")
    void selfWinningBountyRefundsSponsor() {
        registered(1L, Role.TEACHER);
        fx.qa.put(new QuestionRef(20L, 1L, 5L, "问题", null, false));

        fx.bountyService.open(actor(1L, Role.TEACHER), 20L, 50, 7);
        assertEquals(50, fx.accountOf(1L).frozenPoints());

        fx.handlers.on(accepted("evt-self-win", 10L, 20L, 1L, 1L));

        assertEquals(0, fx.accountOf(1L).frozenPoints());
        assertEquals(1000, fx.accountOf(1L).availablePoints());
        assertEquals(BountyStatus.EXPIRED, fx.bounties.all().get(0).status());
        assertFalse(fx.ledger.all().stream().anyMatch(e -> e.reason() == ReputationReason.BOUNTY_AWARDED));
    }

    @Test
    @DisplayName("到期悬赏自动关闭并退还冻结积分")
    void expireDueRefundsSponsor() {
        registered(1L, Role.TEACHER);
        fx.accountOf(1L).freezePoints(50);
        fx.accounts.save(fx.accountOf(1L));
        fx.bounties.save(Bounty.reconstitute(null, 20L, 1L, 50, BountyStatus.OPEN, NOW.minusSeconds(7 * 86400L),
                NOW.minusSeconds(1), null, null, null));

        assertEquals(1, fx.bountyService.expireDue());

        assertEquals(0, fx.accountOf(1L).frozenPoints());
        assertEquals(1000, fx.accountOf(1L).availablePoints());
        assertEquals(BountyStatus.EXPIRED, fx.bounties.all().get(0).status());
    }

    @Test
    @DisplayName("非课程管理者发起悬赏 403 REP-3001")
    void bountyRequiresManagement() {
        registered(2L, Role.STUDENT);
        fx.qa.put(new QuestionRef(20L, 1L, 5L, "问题", null, false));

        assertThrows(ForbiddenException.class,
                () -> fx.bountyService.open(actor(2L, Role.STUDENT), 20L, 50, 7));
    }

    @Test
    @DisplayName("已有进行中的悬赏再发起 409 REP-2001")
    void secondOpenBountyConflicts() {
        registered(1L, Role.TEACHER);
        fx.qa.put(new QuestionRef(20L, 1L, 5L, "问题", null, false));

        fx.bountyService.open(actor(1L, Role.TEACHER), 20L, 50, 7);
        assertThrows(ConflictException.class,
                () -> fx.bountyService.open(actor(1L, Role.TEACHER), 20L, 30, 3));
    }

    // ------------------------------------------------------------ 排行榜

    @Test
    @DisplayName("排行榜消费同一事件两次只累加一次")
    void leaderboardIsIdempotent() {
        ReputationChangedEvent e = new ReputationChangedEvent("evt-lb", NOW, 2L, 15, 15, "ANSWER_ACCEPTED");

        fx.leaderboardService.on(e);
        fx.leaderboardService.on(e);

        assertEquals(1, fx.leaderboard.incrementCount());
        assertEquals(15, fx.leaderboard.top(LeaderboardPeriod.WEEK, NOW, 5).get(0).score());
    }

    // ------------------------------------------------------------ 查询

    @Test
    @DisplayName("他人查看档案时积分字段为 null，本人可见")
    void profileHidesPointsFromOthers() {
        registered(1L, Role.TEACHER);

        ReputationProfileView others = fx.queryService.profile(1L, actor(2L, Role.STUDENT));
        ReputationProfileView self = fx.queryService.profile(1L, actor(1L, Role.TEACHER));

        assertNull(others.availablePoints());
        assertNull(others.frozenPoints());
        assertEquals(0, others.reputation());
        assertEquals("user-1", others.displayName());
        assertEquals(1000, self.availablePoints());
        assertEquals(0, self.frozenPoints());
    }

    @Test
    @DisplayName("档案展示徽章墙")
    void profileListsBadges() {
        registered(2L, Role.STUDENT);
        fx.handlers.on(accepted("evt-badge", 10L, 20L, 2L, 1L));

        ReputationProfileView view = fx.queryService.profile(2L, actor(2L, Role.STUDENT));

        assertEquals(1, view.badges().size());
        assertEquals("FIRST_ACCEPTED", view.badges().get(0).code());
        assertEquals("初露锋芒", view.badges().get(0).name());
    }

    @Test
    @DisplayName("非本人查看流水 403 REP-3002，本人与管理员可读")
    void ledgerForbiddenForOthers() {
        registered(2L, Role.STUDENT);
        fx.handlers.on(accepted("evt-ledger", 10L, 20L, 2L, 1L));

        assertThrows(ForbiddenException.class,
                () -> fx.queryService.ledger(2L, actor(3L, Role.STUDENT), new PageRequest(1, 20)));
        assertEquals(1, fx.queryService.ledger(2L, actor(2L, Role.STUDENT), new PageRequest(1, 20)).total());
        assertEquals(1, fx.queryService.ledger(2L, actor(3L, Role.ADMIN), new PageRequest(1, 20)).total());
    }

    @Test
    @DisplayName("流水条目带 reason 与中文标签")
    void ledgerEntryCarriesLabel() {
        registered(2L, Role.STUDENT);
        fx.handlers.on(accepted("evt-label", 10L, 20L, 2L, 1L));

        LedgerEntryView view = fx.queryService.ledger(2L, actor(2L, Role.STUDENT), new PageRequest(1, 20))
                .items().get(0);

        assertEquals("ANSWER_ACCEPTED", view.reason());
        assertEquals("回答被采纳", view.reasonLabel());
        assertEquals(15, view.delta());
        assertEquals("ANSWER", view.refType());
    }

    // ------------------------------------------------------------ 对账与 API

    @Test
    @DisplayName("对账：一致时为空，账实不符时给出差异；非管理员 403")
    void reconciliationDetectsMismatch() {
        registered(2L, Role.STUDENT);
        fx.handlers.on(accepted("evt-rec", 10L, 20L, 2L, 1L));

        assertTrue(fx.reconciliation.reconcile(actor(9L, Role.ADMIN)).isEmpty());

        fx.accountOf(2L).applyReputation(5);
        fx.accounts.save(fx.accountOf(2L));

        assertEquals(1, fx.reconciliation.reconcile(actor(9L, Role.ADMIN)).size());
        assertThrows(ForbiddenException.class, () -> fx.reconciliation.reconcile(actor(2L, Role.STUDENT)));
    }

    @Test
    @DisplayName("声誉 API：summary 与批量 reputations")
    void apiExposesReputation() {
        registered(2L, Role.STUDENT);
        fx.handlers.on(accepted("evt-api", 10L, 20L, 2L, 1L));

        assertEquals(15, fx.api.summary(2L).reputation());
        assertEquals(0, fx.api.summary(3L).reputation());
        assertEquals(15, fx.api.reputations(java.util.List.of(2L, 3L)).get(2L));
        assertEquals(1, fx.api.reputations(java.util.List.of(2L, 3L)).size());
    }

    @Test
    @DisplayName("悬赏参数越界 422：积分不在 10–500、期限不在 1–14")
    void bountyRejectsOutOfRangeInput() {
        registered(1L, Role.TEACHER);
        fx.qa.put(new QuestionRef(20L, 1L, 5L, "问题", null, false));

        assertThrows(BusinessRuleException.class,
                () -> fx.bountyService.open(actor(1L, Role.TEACHER), 20L, 5, 7));
        assertThrows(BusinessRuleException.class,
                () -> fx.bountyService.open(actor(1L, Role.TEACHER), 20L, 50, 20));
    }
}