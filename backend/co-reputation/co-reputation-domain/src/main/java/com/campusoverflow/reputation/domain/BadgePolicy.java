package com.campusoverflow.reputation.domain;

import java.util.EnumSet;
import java.util.Set;

/** 徽章授予规则（纯函数，易于测试与扩展）。 */
public final class BadgePolicy {

    private BadgePolicy() {
    }

    /**
     * 按累计「被采纳回答数」判定应授予的徽章：
     * <ul>
     *   <li>≥1：初露锋芒 FIRST_ACCEPTED（第一个回答被采纳）</li>
     *   <li>≥10：解惑达人 HELPER_10（累计 10 个回答被采纳）</li>
     * </ul>
     */
    public static Set<BadgeType> forAcceptedCount(long acceptedCount) {
        Set<BadgeType> badges = EnumSet.noneOf(BadgeType.class);
        if (acceptedCount >= 1) {
            badges.add(BadgeType.FIRST_ACCEPTED);
        }
        if (acceptedCount >= 10) {
            badges.add(BadgeType.HELPER_10);
        }
        return badges;
    }

    /**
     * 按当前声誉值判定应授予的徽章：
     * <ul>
     *   <li>≥100：小有名气 REPUTATION_100</li>
     *   <li>≥1000：德高望重 REPUTATION_1000</li>
     * </ul>
     */
    public static Set<BadgeType> forReputation(int reputation) {
        Set<BadgeType> badges = EnumSet.noneOf(BadgeType.class);
        if (reputation >= 100) {
            badges.add(BadgeType.REPUTATION_100);
        }
        if (reputation >= 1000) {
            badges.add(BadgeType.REPUTATION_1000);
        }
        return badges;
    }
}
