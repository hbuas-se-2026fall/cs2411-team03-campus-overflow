package com.campusoverflow.reputation.application;

import com.campusoverflow.identity.api.event.UserRegisteredEvent;
import com.campusoverflow.qa.api.event.AnswerAcceptedEvent;
import com.campusoverflow.qa.api.event.AnswerEndorsedEvent;
import com.campusoverflow.qa.api.event.AnswerSubmittedEvent;
import com.campusoverflow.qa.api.event.VoteCastEvent;
import com.campusoverflow.reputation.domain.BadgePolicy;
import com.campusoverflow.reputation.domain.BadgeType;
import com.campusoverflow.reputation.domain.LedgerRepository;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.reputation.domain.ReputationReason;
import com.campusoverflow.reputation.domain.ReputationRulebook;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订阅上游上下文的集成事件（由 OutboxRelay 在事务提交后投递，至少一次语义）。
 * 每个处理器运行在自己的事务中，并通过账本唯一键 / 徽章存在性检查实现幂等。
 * <p>行走骨架（PB-18，指导书 6.3.4）：Sprint 1 暂为空实现（只打 DEBUG 日志），避免发件箱被阻塞；
 * 实现/移除见 tech-debt Issue #67（Sprint 2 按 T2-12 完成）。</p>
 */
@Component
public class ReputationEventHandlers {

    private static final Logger log = LoggerFactory.getLogger(ReputationEventHandlers.class);

    private final ReputationAccountRepository accounts;
    private final LedgerRepository ledger;
    private final ReputationLedgerService ledgerService;
    private final BadgeService badges;
    private final BountyService bounties;
    private final Clock clock;

    public ReputationEventHandlers(ReputationAccountRepository accounts, LedgerRepository ledger,
                                   ReputationLedgerService ledgerService, BadgeService badges,
                                   BountyService bounties, Clock clock) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.ledgerService = ledgerService;
        this.badges = badges;
        this.bounties = bounties;
        this.clock = clock;
    }

    @EventListener
    @Transactional
    public void on(UserRegisteredEvent e) {
        // 行走骨架：空实现，事件被标记为已投递。实现见 Issue #67。
        log.debug("[walking-skeleton] Reputation 尚未实现，忽略事件 {}", e.eventId()); // TODO(S3) 见 Issue #67
    }

    /** 场景 R-2 的异步后半段：采纳 → 回答者 +15 → 徽章 → 悬赏结算。 */
    @EventListener
    @Transactional
    public void on(AnswerAcceptedEvent e) {
        // 行走骨架：空实现，事件被标记为已投递。实现见 Issue #67。
        log.debug("[walking-skeleton] Reputation 尚未实现，忽略事件 {}", e.eventId()); // TODO(S3) 见 Issue #67
    }

    @EventListener
    @Transactional
    public void on(VoteCastEvent e) {
        // 行走骨架：空实现，事件被标记为已投递。实现见 Issue #67。
        log.debug("[walking-skeleton] Reputation 尚未实现，忽略事件 {}", e.eventId()); // TODO(S3) 见 Issue #67
    }

    @EventListener
    @Transactional
    public void on(AnswerSubmittedEvent e) {
        // 行走骨架：空实现，事件被标记为已投递。实现见 Issue #67。
        log.debug("[walking-skeleton] Reputation 尚未实现，忽略事件 {}", e.eventId()); // TODO(S3) 见 Issue #67
    }

    @EventListener
    @Transactional
    public void on(AnswerEndorsedEvent e) {
        // 行走骨架：空实现，事件被标记为已投递。实现见 Issue #67。
        log.debug("[walking-skeleton] Reputation 尚未实现，忽略事件 {}", e.eventId()); // TODO(S3) 见 Issue #67
    }
}
