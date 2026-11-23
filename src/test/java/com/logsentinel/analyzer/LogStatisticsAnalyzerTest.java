package com.logsentinel.analyzer;

import com.logsentinel.model.LogEntry;
import com.logsentinel.model.LogLevel;
import com.logsentinel.model.LogStatistics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogStatisticsAnalyzerTest {

    @Test
    void countsAllLevels() {
        List<LogEntry> entries = List.of(
                entry(LogLevel.INFO), entry(LogLevel.INFO),
                entry(LogLevel.WARN), entry(LogLevel.ERROR),
                entry(LogLevel.ERROR), entry(LogLevel.FATAL),
                entry(LogLevel.DEBUG), entry(LogLevel.TRACE)
        );

        LogStatistics stats = new LogStatisticsAnalyzer().analyze(entries, 0);

        assertEquals(8, stats.getTotalEntries());
        assertEquals(2, stats.getCount(LogLevel.INFO));
        assertEquals(1, stats.getCount(LogLevel.WARN));
        assertEquals(2, stats.getCount(LogLevel.ERROR));
        assertEquals(1, stats.getCount(LogLevel.FATAL));
        assertEquals(1, stats.getCount(LogLevel.DEBUG));
        assertEquals(1, stats.getCount(LogLevel.TRACE));
        assertEquals(0, stats.getCount(LogLevel.UNKNOWN));
    }

    @Test
    void unknownLevelIsCounted() {
        List<LogEntry> entries = List.of(
                entry(LogLevel.UNKNOWN), entry(LogLevel.UNKNOWN),
                entry(LogLevel.INFO)
        );

        LogStatistics stats = new LogStatisticsAnalyzer().analyze(entries, 2);

        assertEquals(3, stats.getTotalEntries());
        assertEquals(2, stats.getCount(LogLevel.UNKNOWN));
        assertEquals(2, stats.getInvalidEntries());
    }

    @Test
    void emptyListReturnsZeros() {
        LogStatistics stats = new LogStatisticsAnalyzer().analyze(List.of(), 0);

        assertEquals(0, stats.getTotalEntries());
        assertEquals(0, stats.getCount(LogLevel.ERROR));
        assertEquals(0.0, stats.getErrorPercentage());
        assertEquals(0.0, stats.getWarningPercentage());
    }

    @Test
    void errorPercentageIsCalculated() {
        List<LogEntry> entries = List.of(
                entry(LogLevel.ERROR), entry(LogLevel.ERROR),
                entry(LogLevel.INFO), entry(LogLevel.INFO),
                entry(LogLevel.INFO)
        );

        LogStatistics stats = new LogStatisticsAnalyzer().analyze(entries, 0);
        assertEquals(40.0, stats.getErrorPercentage());
    }

    @Test
    void warningPercentageIsCalculated() {
        List<LogEntry> entries = List.of(
                entry(LogLevel.WARN), entry(LogLevel.INFO),
                entry(LogLevel.INFO)
        );

        LogStatistics stats = new LogStatisticsAnalyzer().analyze(entries, 0);
        assertEquals((1.0 / 3.0) * 100.0, stats.getWarningPercentage(), 0.001);
    }

    @Test
    void nullEntriesTreatedAsEmpty() {
        LogStatistics stats = new LogStatisticsAnalyzer().analyze(null, 0);
        assertEquals(0, stats.getTotalEntries());
    }

    private static LogEntry entry(LogLevel level) {
        return LogEntry.builder()
                .lineNumber(1)
                .rawLine("test")
                .level(level)
                .message("msg")
                .build();
    }
}
