package com.campusoverflow.reputation.domain;

import com.campusoverflow.shared.domain.BusinessRuleException;

public class ReputationAccount {

    private static final String ERR_INSUFFICIENT_AVAILABLE = "REP-1002";
    private static final String ERR_INSUFFICIENT_FROZEN = "REP-1006";

    private final long userId;
    private int reputation;
    private int availablePoints;
    private int frozenPoints;
    private final long version;

    private ReputationAccount(long userId, int reputation, int availablePoints, int frozenPoints, long version) {
        this.userId = userId;
        this.reputation = reputation;
        this.availablePoints = availablePoints;
        this.frozenPoints = frozenPoints;
        this.version = version;
    }

    public static ReputationAccount open(long userId, int initialPoints) {
        if (initialPoints < 0) {
            throw new BusinessRuleException(ERR_INSUFFICIENT_FROZEN, "initialPoints cannot be negative");
        }
        return new ReputationAccount(userId, 0, initialPoints, 0, 0L);
    }

    public static ReputationAccount reconstitute(long userId, int reputation, int availablePoints, int frozenPoints,
                                                 long version) {
        return new ReputationAccount(userId, reputation, availablePoints, frozenPoints, version);
    }

    public void applyReputation(int delta) {
        this.reputation += delta;
    }

    public void freezePoints(int points) {
        requirePositive(points);
        if (points > availablePoints) {
            throw new BusinessRuleException(ERR_INSUFFICIENT_AVAILABLE, "not enough available points");
        }
        availablePoints -= points;
        frozenPoints += points;
    }

    public void spendFrozen(int points) {
        requirePositive(points);
        ensureFrozen(points);
        frozenPoints -= points;
    }

    public void refundFrozen(int points) {
        requirePositive(points);
        ensureFrozen(points);
        frozenPoints -= points;
        availablePoints += points;
    }

    private void ensureFrozen(int points) {
        if (points > frozenPoints) {
            throw new BusinessRuleException(ERR_INSUFFICIENT_FROZEN, "not enough frozen points");
        }
    }

    private static void requirePositive(int points) {
        if (points <= 0) {
            throw new BusinessRuleException(ERR_INSUFFICIENT_FROZEN, "points must be positive");
        }
    }

    public long userId() { return userId; }
    public int reputation() { return reputation; }
    public int availablePoints() { return availablePoints; }
    public int frozenPoints() { return frozenPoints; }
    public long version() { return version; }
}