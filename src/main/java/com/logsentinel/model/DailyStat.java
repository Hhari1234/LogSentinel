package com.logsentinel.model;

import java.time.LocalDate;

/**
 * Statistics for a single day.
 */
public final class DailyStat {

    private final LocalDate date;
    private final int errorCount;
    private final int warnCount;
    private final int totalCount;

    public DailyStat(LocalDate date, int errorCount, int warnCount, int totalCount) {
        this.date = date;
        this.errorCount = errorCount;
        this.warnCount = warnCount;
        this.totalCount = totalCount;
    }

    public LocalDate getDate() {
        return date;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public int getWarnCount() {
        return warnCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    @Override
    public String toString() {
        return String.format("%s | errors=%d | warnings=%d | total=%d",
                date, errorCount, warnCount, totalCount);
    }
}
