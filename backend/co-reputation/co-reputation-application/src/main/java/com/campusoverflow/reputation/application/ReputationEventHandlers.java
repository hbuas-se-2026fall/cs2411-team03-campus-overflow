package com.campusoverflow.reputation.application;

import com.campusoverflow.identity.api.event.UserRegisteredEvent;
import com.campusoverflow.qa.api.event.AnswerAcceptedEvent;
import com.campusoverflow.qa.api.event.AnswerEndorsedEvent;
import com.campusoverflow.qa.api.event.AnswerSubmittedEvent;
import com.campusoverflow.qa.api.event.VoteCastEvent;
import com.campusoverflow.reputation.domain.BadgeType;
import com.campusoverflow.reputation.domain.LedgerRepository;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.reputation.domain.ReputationReason;
import com.campusoverflow.reputation.domain.ReputationRulebook;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReputationEventHandlers {

    private static final Logger log = LoggerFactory.getLogger(ReputationEventHandlers.class);

    private final ReputationAccountRepository accounts;
    private final LedgerRepository ledger;
    private final ReputationLedgerService ledgerService;
    private final BadgeService badges;
    private final Clock clock;

    public ReputationEventHandlers(ReputationAccountRepository accounts, LedgerRepository ledger,
                                   ReputationLedgerService ledgerService, BadgeService badges, Clock clock) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.ledgerService = ledgerService;
        this.badges = badges;
        this.clock = clock;
    }

    @EventListener
    @Transactional
    public void on(UserRegisteredEvent e) {
        accounts.findByUserId(e.userId()).orElseGet(() -> {
            ReputationAccount acc = ReputationAccount.open(e.userId(), ReputationRulebook.initialPoints(e.role()));
            return accounts.save(acc);
        });
    }

    @EventListener
    @Transactional
    public void on(AnswerAcceptedEvent e) {
        int delta = ReputationRulebook.acceptedDelta(e.answerAuthorId(), e.questionAuthorId());
        ledgerService.record(e.answerAuthorId(), e.eventId(), ReputationReason.ANSWER_ACCEPTED, delta,
                "ANSWER", e.answerId());
    }

    @EventListener
    @Transactional
    public void on(VoteCastEvent e) {
        int rawDelta = ReputationRulebook.voteDelta(e.oldValue(), e.newValue());
        if (rawDelta <= 0) {
            ledgerService.record(e.targetAuthorId(), e.eventId(), ReputationReason.ANSWER_VOTE, rawDelta,
                    e.targetType(), e.targetId());
            return;
        }
        Instant startOfDay = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        int gainedToday = ledger.sumPositiveVoteDeltaSince(e.targetAuthorId(), startOfDay);
        int capped = ReputationRulebook.applyDailyCap(rawDelta, gainedToday);
        if (capped != 0) {
            ledgerService.record(e.targetAuthorId(), e.eventId(), ReputationReason.ANSWER_VOTE, capped,
                    e.targetType(), e.targetId());
        }
    }

    @EventListener
    @Transactional
    public void on(AnswerSubmittedEvent e) {
        badges.grant(e.answerAuthorId(), BadgeType.FIRST_ANSWER);
    }

    @EventListener
    @Transactional
    public void on(AnswerEndorsedEvent e) {
        badges.grant(e.answerAuthorId(), BadgeType.TEACHER_ENDORSED);
    }
}