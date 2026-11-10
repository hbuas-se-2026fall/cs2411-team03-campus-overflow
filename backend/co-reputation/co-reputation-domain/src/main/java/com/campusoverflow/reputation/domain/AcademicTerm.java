package com.campusoverflow.reputation.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;

/**
 * 排行榜周期键。学期划分（可按学校校历调整）：2–7 月为春季学期，8–12 月与次年 1 月为秋季学期。
 */
public final class AcademicTerm {

    public static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");

    private AcademicTerm() {
    }

    /**
     * 学期键，格式 {@code 2026-FALL} / {@code 2027-SPRING}。
     * 春季学期为 2–7 月；秋季学期为 8 月至次年 1 月（1 月归入前一年秋季）。
     */
    public static String termKey(Instant at) {
        LocalDate date = at.atZone(CAMPUS_ZONE).toLocalDate();
        int month = date.getMonthValue();
        int year = date.getYear();
        if (month >= 2 && month <= 7) {
            return year + "-SPRING";
        }
        if (month >= 8) {
            return year + "-FALL";
        }
        // 1 月属于上一年度的秋季学期
        return (year - 1) + "-FALL";
    }

    /** 月份键，格式 {@code 2026-10}。 */
    public static String monthKey(Instant at) {
        LocalDate date = at.atZone(CAMPUS_ZONE).toLocalDate();
        return String.format("%d-%02d", date.getYear(), date.getMonthValue());
    }

    /** ISO 周键，格式 {@code 2026-W41}。 */
    public static String weekKey(Instant at) {
        LocalDate date = at.atZone(CAMPUS_ZONE).toLocalDate();
        int weekYear = date.get(IsoFields.WEEK_BASED_YEAR);
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("%d-W%02d", weekYear, week);
    }
}
