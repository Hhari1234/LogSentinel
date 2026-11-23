package com.logsentinel.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregated log-level statistics for a set of parsed entries.
 */
public final class LogStatistics {

    private final int totalEntries;
    private final Map<LogLevel, Integer> levelCounts;
    private final int validEntries;
    private final int invalidEntries;

    public LogStatistics(int totalEntries, Map<LogLevel, Integer> levelCounts,
                         int validEntries, int invalidEntries) {
        this.totalEntries = totalEntries;
        this.levelCounts = levelCounts == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(levelCounts);
        this.validEntries = validEntries;
        this.invalidEntries = invalidEntries;
    }

    public int getTotalEntries() {
        return totalEntries;
    }

    public Map<LogLevel, Integer> getLevelCounts() {
        return Map.copyOf(levelCounts);
    }

    public int getCount(LogLevel level) {
        return levelCounts.getOrDefault(level, 0);
    }

    public int getValidEntries() {
        return validEntries;
    }

    public int getInvalidEntries() {
        return invalidEntries;
    }

    public double getErrorPercentage() {
        if (totalEntries == 0) return 0.0;
        return (getCount(LogLevel.ERROR) * 100.0) / totalEntries;
    }

    public double getWarningPercentage() {
        if (totalEntries == 0) return 0.0;
        return (getCount(LogLevel.WARN) * 100.0) / totalEntries;
    }

    public double getFatalPercentage() {
        if (totalEntries == 0) return 0.0;
        return (getCount(LogLevel.FATAL) * 100.0) / totalEntries;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Total entries: ").append(totalEntries).append('\n');
        sb.append("Valid: ").append(validEntries).append(", Invalid: ").append(invalidEntries).append('\n');
        for (Map.Entry<LogLevel, Integer> entry : levelCounts.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }
        sb.append(String.format("Error percentage: %.2f%%\n", getErrorPercentage()));
        sb.append(String.format("Warning percentage: %.2f%%\n", getWarningPercentage()));
        return sb.toString();
    }
}
