package com.campusoverflow.reputation.application;

import com.campusoverflow.reputation.domain.LedgerRepository;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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