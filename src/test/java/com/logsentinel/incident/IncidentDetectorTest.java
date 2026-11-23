package com.logsentinel.incident;

import com.logsentinel.model.LogEntry;
import com.logsentinel.model.LogLevel;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IncidentDetectorTest {

    @Test
    void detectsPotentialIncidentWhenThresholdExceeded() {
        List<LogEntry> entries = new java.util.ArrayList<>();
        LocalDateTime base = LocalDateTime.of(2026, Month.NOVEMBER, 23, 10, 0, 0);
        for (int i = 0; i < 7; i++) {
            entries.add(entry(base.plusSeconds(i * 10), "Connection timeout", "DatabaseService"));
        }

        List<Incident> incidents = new IncidentDetector(5, 1).detect(entries);

        assertEquals(1, incidents.size());
        Incident incident = incidents.get(0);
        assertEquals("DatabaseService", incident.getService());
        assertEquals("Connection timeout", incident.getPattern());
        assertEquals(7, incident.getOccurrences());
        assertTrue(incident.isPotential());
        assertEquals(IncidentSeverity.HIGH, incident.getSeverity());
        assertEquals("INC-0001", incident.getId());
    }

    @Test
    void noIncidentWhenBelowThreshold() {
        List<LogEntry> entries = new java.util.ArrayList<>();
        LocalDateTime base = LocalDateTime.of(2026, Month.NOVEMBER, 23, 10, 0, 0);
        for (int i = 0; i < 4; i++) {
            entries.add(entry(base.plusSeconds(i * 10), "Connection timeout", "DatabaseService"));
        }

        assertTrue(new IncidentDetector(5, 1).detect(entries).isEmpty());
    }

    @Test
    void errorsOutsideTimeWindowAreNotCounted() {
        List<LogEntry> entries = new java.util.ArrayList<>();
        LocalDateTime base = LocalDateTime.of(2026, Month.NOVEMBER, 23, 10, 0, 0);
        // 5 errors within 30 seconds
        for (int i = 0; i < 5; i++) {
            entries.add(entry(base.plusSeconds(i * 5), "Connection timeout", "DatabaseService"));
        }
        // 5 more errors two hours later (outside the 1-minute window)
        for (int i = 0; i < 5; i++) {
            entries.add(entry(base.plusHours(2).plusSeconds(i * 5), "Connection timeout", "DatabaseService"));
        }

        List<Incident> incidents = new IncidentDetector(5, 1).detect(entries);
        // Each cluster of 5 should be detected as its own incident
        assertEquals(2, incidents.size());
    }

    @Test
    void differentServicesAreGroupedSeparately() {
        List<LogEntry> entries = new java.util.ArrayList<>();
        LocalDateTime base = LocalDateTime.of(2026, Month.NOVEMBER, 23, 10, 0, 0);
        for (int i = 0; i < 5; i++) {
            entries.add(entry(base.plusSeconds(i * 5), "Connection timeout", "DatabaseService"));
        }
        for (int i = 0; i < 5; i++) {
            entries.add(entry(base.plusSeconds(i * 5), "Auth failed", "AuthService"));
        }

        List<Incident> incidents = new IncidentDetector(5, 1).detect(entries);
        assertEquals(2, incidents.size());
    }

    @Test
    void errorsWithoutTimestampsAreIgnoredForWindowing() {
        List<LogEntry> entries = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            entries.add(LogEntry.builder()
                    .lineNumber(i + 1)
                    .rawLine("test")
                    .level(LogLevel.ERROR)
                    .service("DatabaseService")
                    .message("Connection timeout")
                    .build());
        }

        assertTrue(new IncidentDetector(5, 1).detect(entries).isEmpty());
    }

    @Test
    void nonErrorLevelsAreIgnored() {
        List<LogEntry> entries = new java.util.ArrayList<>();
        LocalDateTime base = LocalDateTime.of(2026, Month.NOVEMBER, 23, 10, 0, 0);
        for (int i = 0; i < 10; i++) {
            entries.add(entry(base.plusSeconds(i * 5), "Connection timeout", "DatabaseService"));
        }
        entries.add(LogEntry.builder()
                .lineNumber(11)
                .rawLine("test")
                .level(LogLevel.INFO)
                .service("DatabaseService")
                .message("Connection timeout")
                .build());

        List<Incident> incidents = new IncidentDetector(5, 1).detect(entries);
        assertEquals(1, incidents.size());
        assertEquals(10, incidents.get(0).getOccurrences());
    }

    @Test
    void nullEntriesReturnsEmpty() {
        assertTrue(new IncidentDetector(5, 1).detect(null).isEmpty());
    }

    @Test
    void thresholdAndWindowAreConfigurable() {
        List<LogEntry> entries = new java.util.ArrayList<>();
        LocalDateTime base = LocalDateTime.of(2026, Month.NOVEMBER, 23, 10, 0, 0);
        // 3 errors within 1 minute — meets threshold 3 but not 5
        for (int i = 0; i < 3; i++) {
            entries.add(entry(base.plusSeconds(i * 10), "Connection timeout", "DatabaseService"));
        }

        assertTrue(new IncidentDetector(5, 1).detect(entries).isEmpty());
        assertEquals(1, new IncidentDetector(3, 1).detect(entries).size());
    }

    @Test
    void incidentsAreSortedBySeverityAndCount() {
        List<LogEntry> entries = new java.util.ArrayList<>();
        LocalDateTime base = LocalDateTime.of(2026, Month.NOVEMBER, 23, 10, 0, 0);
        for (int i = 0; i < 10; i++) {
            entries.add(entry(base.plusSeconds(i), "Critical error", "ServiceA"));
        }
        for (int i = 0; i < 5; i++) {
            entries.add(entry(base.plusSeconds(i), "High error", "ServiceB"));
        }

        List<Incident> incidents = new IncidentDetector(5, 1).detect(entries);
        assertEquals(2, incidents.size());
        assertEquals("Critical error", incidents.get(0).getPattern());
        assertEquals(IncidentSeverity.CRITICAL, incidents.get(0).getSeverity());
    }

    private static LogEntry entry(LocalDateTime timestamp, String message, String service) {
        return LogEntry.builder()
                .lineNumber(1)
                .rawLine("test")
                .timestamp(timestamp)
                .level(LogLevel.ERROR)
                .service(service)
                .message(message)
                .build();
    }
}
