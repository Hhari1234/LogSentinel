package com.logsentinel.analyzer;

import com.logsentinel.model.LogEntry;
import com.logsentinel.model.LogLevel;
import com.logsentinel.model.LogStatistics;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Counts log levels and produces a {@link LogStatistics} summary.
 */
public final class LogStatisticsAnalyzer {

    public LogStatistics analyze(List<LogEntry> entries, int invalidEntries) {
        if (entries == null) {
            entries = List.of();
        }

        Map<LogLevel, Integer> counts = new EnumMap<>(LogLevel.class);
        for (LogLevel level : LogLevel.values()) {
            counts.put(level, 0);
        }

        for (LogEntry entry : entries) {
            LogLevel level = entry.getLevel();
            counts.merge(level, 1, Integer::sum);
        }

        int total = entries.size();
        return new LogStatistics(total, counts, total, invalidEntries);
    }

    public LogStatistics analyze(List<LogEntry> entries) {
        return analyze(entries, 0);
    }

    public Map<LogLevel, Integer> countLevels(List<LogEntry> entries) {
        if (entries == null) {
            return Map.of();
        }
        return entries.stream()
                .collect(Collectors.groupingBy(
                        LogEntry::getLevel,
                        Collectors.reducing(0, e -> 1, Integer::sum)
                ));
    }
}
