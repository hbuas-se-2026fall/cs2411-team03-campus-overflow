package com.campusoverflow.reputation.domain;

import com.campusoverflow.shared.security.Role;

public final class ReputationRulebook {

    public static final int ACCEPTED = 15;
    public static final int UPVOTE = 10;
    public static final int DOWNVOTE = -2;
    public static final int DAILY_VOTE_CAP = 200;

    private ReputationRulebook() {
    }

    public static int voteEffect(int value) {
        if (value > 0) return UPVOTE;
        if (value < 0) return DOWNVOTE;
        return 0;
    }

    public static int voteDelta(int oldValue, int newValue) {
        return voteEffect(newValue) - voteEffect(oldValue);
    }

    public static int acceptedDelta(long answerAuthorId, long questionAuthorId) {
        return answerAuthorId == questionAuthorId ? 0 : ACCEPTED;
    }

    public static int applyDailyCap(int delta, int positiveVoteGainToday) {
        if (delta <= 0) return delta;
        int remaining = DAILY_VOTE_CAP - positiveVoteGainToday;
        if (remaining <= 0) return 0;
        return Math.min(delta, remaining);
    }

    public static int initialPoints(Role role) {
        return switch (role) {
            case TEACHER, ADMIN -> 1000;
            case TA -> 500;
            case STUDENT -> 0;
        };
    }
}