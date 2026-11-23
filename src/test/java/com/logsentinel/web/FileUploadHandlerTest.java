package com.logsentinel.web;

import com.logsentinel.model.AnalysisSession;
import com.logsentinel.model.ProcessingResult;
import com.logsentinel.model.LogEntry;
import com.logsentinel.model.LogLevel;
import com.logsentinel.model.LogStatistics;
import com.logsentinel.model.ErrorFrequency;
import com.logsentinel.model.DailyStat;
import com.logsentinel.incident.Incident;
import com.logsentinel.incident.IncidentSeverity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DashboardStore thread-safe session storage.
 */
class DashboardStoreTest {

    @Test
    void initiallyHasNoSession() {
        DashboardStore store = new DashboardStore();
        assertFalse(store.hasSession());
        assertNull(store.getSession());
    }

    @Test
    void setAndGetSession() {
        DashboardStore store = new DashboardStore();

        LogEntry entry = LogEntry.builder()
                .lineNumber(1)
                .rawLine("2026-11-23 10:30:01 ERROR [DatabaseService] Connection timeout")
                .timestamp(LocalDateTime.of(2026, Month.NOVEMBER, 23, 10, 30, 1))
                .level(LogLevel.ERROR)
                .service("DatabaseService")
                .message("Connection timeout")
                .build();

        ProcessingResult result = new ProcessingResult(
                "test.log", List.of(entry), 1, 1, 0, 50L);

        Map<LogLevel, Integer> levelCounts = Map.of(LogLevel.ERROR, 1);

        AnalysisSession session = new AnalysisSession(
                "test.log", result, List.of(entry),
                new LogStatistics(1, levelCounts, 1, 0),
                List.of(), List.of(), List.of(),
                LocalDateTime.now()
        );

        store.setSession(session);

        assertTrue(store.hasSession());
        assertEquals("test.log", store.getSession().getFileName());
        assertEquals(1, store.getSession().getEntries().size());
    }
}
