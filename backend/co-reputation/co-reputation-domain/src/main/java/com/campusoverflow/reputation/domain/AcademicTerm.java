package com.campusoverflow.reputation.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;

public final class AcademicTerm {

    public static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");

    private AcademicTerm() {
    }

    public static String termKey(Instant at) {
        LocalDate d = LocalDate.ofInstant(at, CAMPUS_ZONE);
        int m = d.getMonthValue();
        int year = d.getYear();
        if (m >= 2 && m <= 5) {
            return year + "-SPRING";
        } else if (m >= 6 && m <= 12) {
            return year + "-FALL";
        } else {
            return (year - 1) + "-FALL";
        }
    }

    public static String weekKey(Instant at) {
        LocalDate d = LocalDate.ofInstant(at, CAMPUS_ZONE);
        return d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + String.format("%02d", d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    public static String monthKey(Instant at) {
        LocalDate d = LocalDate.ofInstant(at, CAMPUS_ZONE);
        return d.getYear() + "-" + String.format("%02d", d.getMonthValue());
    }
}