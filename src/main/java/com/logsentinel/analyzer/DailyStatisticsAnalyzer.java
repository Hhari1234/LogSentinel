package com.logsentinel.analyzer;

import com.logsentinel.model.DailyStat;
import com.logsentinel.model.LogEntry;
import com.logsentinel.model.LogLevel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Groups parsed entries by date and computes daily error/warning counts.
 */
public final class DailyStatisticsAnalyzer {

    public List<DailyStat> analyze(List<LogEntry> entries) {
        if (entries == null) {
            return List.of();
        }

        // date -> [error count, warn count, total count]
        Map<LocalDate, int[]> buckets = new TreeMap<>();

        for (LogEntry entry : entries) {
            LocalDate date = dateOf(entry);
            if (date == null) {
                continue;
            }
            int[] stats = buckets.computeIfAbsent(date, d -> new int[3]);
            stats[2]++; // total
            if (entry.getLevel() == LogLevel.ERROR) {
                stats[0]++; // errors
            } else if (entry.getLevel() == LogLevel.WARN) {
                stats[1]++; // warnings
            }
        }

        List<DailyStat> result = new ArrayList<>();
        for (Map.Entry<LocalDate, int[]> entry : buckets.entrySet()) {
            int[] stats = entry.getValue();
            result.add(new DailyStat(entry.getKey(), stats[0], stats[1], stats[2]));
        }
        return result;
    }

    /**
     * Returns the day with the highest error count, or null when there are no
     * errors.
     */
    public LocalDate dayWithMostErrors(List<LogEntry> entries) {
        return analyze(entries).stream()
                .filter(d -> d.getErrorCount() > 0)
                .max(Comparator.comparingInt(DailyStat::getErrorCount))
                .map(DailyStat::getDate)
                .orElse(null);
    }

    /**
     * Returns the busiest one-hour error window as a {@code LocalDateTime}
     * range. Returns null when there are no timestamped errors.
     */
    public TimeWindow busiestErrorPeriod(List<LogEntry> entries) {
        if (entries == null) {
            return null;
        }

        List<LocalDateTime> errorTimes = entries.stream()
                .filter(e -> e.getLevel() == LogLevel.ERROR && e.getTimestamp() != null)
                .map(LogEntry::getTimestamp)
                .sorted()
                .collect(Collectors.toList());

        if (errorTimes.isEmpty()) {
            return null;
        }

        int bestStart = 0;
        int bestCount = 0;
        int j = 0;
        for (int i = 0; i < errorTimes.size(); i++) {
            while (j < errorTimes.size()
                    && errorTimes.get(i).until(errorTimes.get(j), ChronoUnit.HOURS) < 1) {
                j++;
            }
            int count = j - i;
            if (count > bestCount) {
                bestCount = count;
                bestStart = i;
            }
        }

        LocalDateTime start = errorTimes.get(bestStart);
        // End of window is one hour after the start of the best window
        LocalDateTime end = start.plusHours(1);
        return new TimeWindow(start, end, bestCount);
    }

    private static LocalDate dateOf(LogEntry entry) {
        LocalDateTime ts = entry.getTimestamp();
        return ts == null ? null : ts.toLocalDate();
    }

    /**
     * A one-hour time window with an error count.
     */
    public static final class TimeWindow {
        private final LocalDateTime start;
        private final LocalDateTime end;
        private final int errorCount;

        public TimeWindow(LocalDateTime start, LocalDateTime end, int errorCount) {
            this.start = start;
            this.end = end;
            this.errorCount = errorCount;
        }

        public LocalDateTime getStart() {
            return start;
        }

        public LocalDateTime getEnd() {
            return end;
        }

        public int getErrorCount() {
            return errorCount;
        }
    }
}
