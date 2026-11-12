package com.campusoverflow.reputation.application;

import com.campusoverflow.reputation.domain.LedgerRepository;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.security.Actor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 对账（FF-8）：账户声誉值必须等于账本流水之和。 */
@Service
public class ReconciliationService {

    public record Mismatch(long userId, int accountReputation, long ledgerSum) {
    }

    private final ReputationAccountRepository accounts;
    private final LedgerRepository ledger;

    public ReconciliationService(ReputationAccountRepository accounts, LedgerRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    /** 管理员入口：非管理员 403 REP-3002。 */
    @Transactional(readOnly = true)
    public List<Mismatch> reconcile(Actor actor) {
        if (!actor.isAdmin()) {
            throw new ForbiddenException("REP-3002", "仅管理员可查看对账结果");
        }
        return reconcile();
    }

    @Transactional(readOnly = true)
    public List<Mismatch> reconcile() {
        Map<Long, Long> sums = ledger.sumByUser();
        List<Mismatch> result = new ArrayList<>();
        for (ReputationAccount acc : accounts.findAll()) {
            long sum = sums.getOrDefault(acc.userId(), 0L);
            if (acc.reputation() != sum) {
                result.add(new Mismatch(acc.userId(), acc.reputation(), sum));
            }
        }
        return result;
    }
}