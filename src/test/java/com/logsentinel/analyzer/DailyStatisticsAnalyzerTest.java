package com.logsentinel.analyzer;

import com.logsentinel.model.DailyStat;
import com.logsentinel.model.LogEntry;
import com.logsentinel.model.LogLevel;
import com.logsentinel.model.SearchResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DailyStatisticsAnalyzerTest {

    @Test
    void groupsByDateAndCountsErrorsAndWarnings() {
        List<LogEntry> entries = List.of(
                entry(LocalDateTime.of(2026, Month.NOVEMBER, 23, 10, 0, 0), LogLevel.ERROR),
                entry(LocalDateTime.of(2026, Month.NOVEMBER, 23, 10, 5, 0), LogLevel.ERROR),
                entry(LocalDateTime.of(2026, Month.NOVEMBER, 23, 10, 10, 0), LogLevel.WARN),
                entry(LocalDateTime.of(2026, Month.NOVEMBER, 24, 11, 0, 0), LogLevel.ERROR),
                entry(LocalDateTime.of(2026, Month.NOVEMBER, 25, 12, 0, 0), LogLevel.INFO)
        );

        List<DailyStat> stats = new DailyStatisticsAnalyzer().analyze(entries);

        assertEquals(3, stats.size());
        DailyStat d23 = stats.get(0);
        assertEquals(LocalDate.of(2026, 11, 23), d23.getDate());
        assertEquals(2, d23.getErrorCount());
        assertEquals(1, d23.getWarnCount());
        assertEquals(3, d23.getTotalCount());

        DailyStat d24 = stats.get(1);
        assertEquals(1, d24.getErrorCount());
        assertEquals(0, d24.getWarnCount());

        DailyStat d25 = stats.get(2);
        assertEquals(0, d25.getErrorCount());
        assertEquals(1, d25.getTotalCount());
    }

    @Test
    void entriesWithoutTimestampAreSkipped() {
        List<LogEntry> entries = List.of(
                entry(null, LogLevel.ERROR),
                entry(LocalDateTime.of(2026, Month.NOVEMBER, 23, 10, 0, 0), LogLevel.ERROR)
        );

        List<DailyStat> stats = new DailyStatisticsAnalyzer().analyze(entries);
        assertEquals(1, stats.size());
    }

    @Test
    void dayWithMostErrors() {
        List<LogEntry> entries = List.of(
                entry(LocalDateTime.of(2026, Month.NOVEMBER, 23, 10, 0, 0), LogLevel.ERROR),
                entry(LocalDateTime.of(2026, Month.NOVEMBER, 24, 10, 0, 0), LogLevel.ERROR),
                entry(LocalDateTime.of(2026, Month.NOVEMBER, 24, 10, 5, 0), LogLevel.ERROR),
                entry(LocalDateTime.of(2026, Month.NOVEMBER, 24, 10, 10, 0), LogLevel.ERROR)
        );

        DailyStatisticsAnalyzer analyzer = new DailyStatisticsAnalyzer();
        LocalDate day = analyzer.dayWithMostErrors(entries);
        assertEquals(LocalDate.of(2026, 11, 24), day);
    }

    @Test
    void dayWithMostErrorsNullWhenNoErrors() {
        List<LogEntry> entries = List.of(
                entry(LocalDateTime.of(2026, Month.NOVEMBER, 23, 10, 0, 0), LogLevel.INFO)
        );
        assertNull(new DailyStatisticsAnalyzer().dayWithMostErrors(entries));
    }

    @Test
    void busiestErrorPeriod() {
        LocalDateTime base = LocalDateTime.of(2026, Month.NOVEMBER, 23, 10, 0, 0);
        List<LogEntry> entries = new java.util.ArrayList<>();
        // 5 errors within one hour starting at 10:00
        for (int i = 0; i < 5; i++) {
            entries.add(entry(base.plusMinutes(i), LogLevel.ERROR));
        }
        // 1 error far away
        entries.add(entry(base.plusHours(5), LogLevel.ERROR));

        DailyStatisticsAnalyzer.TimeWindow window =
                new DailyStatisticsAnalyzer().busiestErrorPeriod(entries);
        assertNotNull(window);
        assertEquals(5, window.getErrorCount());
        assertEquals(base, window.getStart());
    }

    @Test
    void busiestErrorPeriodNullWhenNoTimestampedErrors() {
        List<LogEntry> entries = List.of(
                entry(null, LogLevel.ERROR),
                entry(null, LogLevel.ERROR)
        );
        assertNull(new DailyStatisticsAnalyzer().busiestErrorPeriod(entries));
    }

    @Test
    void nullEntriesReturnsEmpty() {
        assertTrue(new DailyStatisticsAnalyzer().analyze(null).isEmpty());
    }

    private static LogEntry entry(LocalDateTime ts, LogLevel level) {
        return LogEntry.builder()
                .lineNumber(1)
                .rawLine("test")
                .timestamp(ts)
                .level(level)
                .message("msg")
                .build();
    }
}
