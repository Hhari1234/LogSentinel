package com.logsentinel.analyzer;

import com.logsentinel.model.LogEntry;
import com.logsentinel.model.LogLevel;
import com.logsentinel.model.SearchResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogSearchServiceTest {

    @Test
    void byKeywordFindsMatchesCaseInsensitive() {
        List<LogEntry> entries = List.of(
                entry(1, "Connection timeout", LogLevel.ERROR, "DatabaseService"),
                entry(2, "connection Timeout occurred", LogLevel.ERROR, "DatabaseService"),
                entry(3, "User login successful", LogLevel.INFO, "AuthService")
        );

        List<SearchResult> results = new LogSearchService().byKeyword(entries, "timeout");
        assertEquals(2, results.size());
        assertEquals(1, results.get(0).getLineNumber());
        assertEquals(2, results.get(1).getLineNumber());
    }

    @Test
    void byLevelReturnsMatchingEntries() {
        List<LogEntry> entries = List.of(
                entry(1, "msg", LogLevel.ERROR, "S"),
                entry(2, "msg", LogLevel.INFO, "S"),
                entry(3, "msg", LogLevel.ERROR, "S")
        );

        List<SearchResult> results = new LogSearchService().byLevel(entries, LogLevel.ERROR);
        assertEquals(2, results.size());
        assertEquals(1, results.get(0).getLineNumber());
        assertEquals(3, results.get(1).getLineNumber());
    }

    @Test
    void byServiceIsCaseInsensitive() {
        List<LogEntry> entries = List.of(
                entry(1, "msg", LogLevel.ERROR, "DataBaseService"),
                entry(2, "msg", LogLevel.ERROR, "AuthService"),
                entry(3, "msg", LogLevel.ERROR, "databaseService")
        );

        List<SearchResult> results = new LogSearchService().byService(entries, "DATABASESERVICE");
        assertEquals(2, results.size());
        assertEquals(1, results.get(0).getLineNumber());
        assertEquals(3, results.get(1).getLineNumber());
    }

    @Test
    void byDateMatchesDatePortion() {
        List<LogEntry> entries = List.of(
                entry(1, "msg", LogLevel.ERROR, LocalDateTime.of(2026, Month.NOVEMBER, 23, 10, 0, 0), "S"),
                entry(2, "msg", LogLevel.ERROR, LocalDateTime.of(2026, Month.NOVEMBER, 23, 15, 0, 0), "S"),
                entry(3, "msg", LogLevel.ERROR, LocalDateTime.of(2026, Month.NOVEMBER, 24, 10, 0, 0), "S")
        );

        List<SearchResult> results = new LogSearchService().byDate(
                entries, LocalDate.of(2026, 11, 23));
        assertEquals(2, results.size());
    }

    @Test
    void combinedSearchFiltersByLevelAndService() {
        List<LogEntry> entries = List.of(
                entry(1, "Connection timeout", LogLevel.ERROR, "DatabaseService"),
                entry(2, "Connection timeout", LogLevel.WARN, "DatabaseService"),
                entry(3, "Connection timeout", LogLevel.ERROR, "AuthService"),
                entry(4, "User login", LogLevel.ERROR, "DatabaseService")
        );

        List<SearchResult> results = new LogSearchService().search(
                entries, "timeout", LogLevel.ERROR, "DatabaseService");
        assertEquals(1, results.size());
        assertEquals(1, results.get(0).getLineNumber());
    }

    @Test
    void keywordOnlySearch() {
        List<LogEntry> entries = List.of(
                entry(1, "Connection timeout", LogLevel.ERROR, "S"),
                entry(2, "User login", LogLevel.INFO, "S")
        );

        List<SearchResult> results = new LogSearchService().search(entries, "login", null, null);
        assertEquals(1, results.size());
        assertEquals(2, results.get(0).getLineNumber());
    }

    @Test
    void nullAndEmptyInputsReturnEmpty() {
        LogSearchService service = new LogSearchService();
        assertTrue(service.byKeyword(null, "x").isEmpty());
        assertTrue(service.byKeyword(List.of(), "x").isEmpty());
        assertTrue(service.byKeyword(List.of(entry(1, "a", LogLevel.INFO, "S")), "").isEmpty());
    }

    private static LogEntry entry(int line, String message, LogLevel level, String service) {
        return LogEntry.builder()
                .lineNumber(line)
                .rawLine("test")
                .level(level)
                .service(service)
                .message(message)
                .build();
    }

    private static LogEntry entry(int line, String message, LogLevel level,
                                  LocalDateTime timestamp, String service) {
        return LogEntry.builder()
                .lineNumber(line)
                .rawLine("test")
                .timestamp(timestamp)
                .level(level)
                .service(service)
                .message(message)
                .build();
    }
}
