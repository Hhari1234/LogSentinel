package com.logsentinel.analyzer;

import com.logsentinel.model.ErrorFrequency;
import com.logsentinel.model.LogEntry;
import com.logsentinel.model.LogLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ErrorFrequencyAnalyzerTest {

    @Test
    void countsRepeatedErrorMessages() {
        List<LogEntry> entries = List.of(
                error("Connection timeout", "DatabaseService"),
                error("Connection timeout", "DatabaseService"),
                error("Connection timeout", "DatabaseService"),
                error("Authentication failed", "AuthService"),
                error("Authentication failed", "AuthService"),
                error("Payment gateway unavailable", "PaymentService")
        );

        List<ErrorFrequency> result = new ErrorFrequencyAnalyzer().analyze(entries);

        assertEquals(3, result.size());
        assertEquals("Connection timeout", result.get(0).getMessage());
        assertEquals(3, result.get(0).getCount());
        assertEquals("DatabaseService", result.get(0).getService());
        assertEquals("Authentication failed", result.get(1).getMessage());
        assertEquals(2, result.get(1).getCount());
    }

    @Test
    void ignoresNonErrorLevels() {
        List<LogEntry> entries = List.of(
                error("Connection timeout", "DatabaseService"),
                info("Connection timeout", "DatabaseService"),
                warn("Connection timeout", "DatabaseService")
        );

        List<ErrorFrequency> result = new ErrorFrequencyAnalyzer().analyze(entries);
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getCount());
    }

    @Test
    void returnsEmptyForNoErrors() {
        List<LogEntry> entries = List.of(
                info("All good", "Service"),
                warn("Slow", "Service")
        );

        assertTrue(new ErrorFrequencyAnalyzer().analyze(entries).isEmpty());
    }

    @Test
    void topNLimitsResults() {
        List<LogEntry> entries = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            entries.add(error("Error " + i, "Service"));
        }

        List<ErrorFrequency> result = new ErrorFrequencyAnalyzer().analyze(entries, 5);
        assertEquals(5, result.size());
    }

    @Test
    void handlesNullAndEmptyEntries() {
        assertTrue(new ErrorFrequencyAnalyzer().analyze(null).isEmpty());
        assertTrue(new ErrorFrequencyAnalyzer().analyze(List.of()).isEmpty());
    }

    @Test
    void normalizesWhitespaceButPreservesOriginal() {
        List<LogEntry> entries = List.of(
                error("Connection  timeout", "DatabaseService"),
                error("Connection timeout", "DatabaseService")
        );

        List<ErrorFrequency> result = new ErrorFrequencyAnalyzer().analyze(entries);
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getCount());
        // First occurrence's original message is preserved
        assertEquals("Connection  timeout", result.get(0).getMessage());
    }

    @Test
    void handlesNullEntries() {
        List<LogEntry> entries = List.of(
                LogEntry.builder().lineNumber(1).rawLine("x")
                        .level(LogLevel.ERROR).message(null).build(),
                error("Connection timeout", "DatabaseService")
        );

        List<ErrorFrequency> result = new ErrorFrequencyAnalyzer().analyze(entries);
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getCount());
    }

    @Test
    void groupsByNormalizedMessagePreservesFirstOriginal() {
        List<LogEntry> entries = List.of(
                error("Connection  timeout", "DatabaseService"),
                error("Connection timeout", "DatabaseService"),
                error("Connection\ttimeout", "DatabaseService")
        );

        List<ErrorFrequency> result = new ErrorFrequencyAnalyzer().analyze(entries);
        assertEquals(1, result.size());
        assertEquals(3, result.get(0).getCount());
        // First occurrence's original message is preserved
        assertEquals("Connection  timeout", result.get(0).getMessage());
    }

    @Test
    void differentServicesWithSameMessageAreSeparateBuckets() {
        List<LogEntry> entries = List.of(
                error("Connection timeout", "DatabaseService"),
                error("Connection timeout", "AuthService")
        );

        List<ErrorFrequency> result = new ErrorFrequencyAnalyzer().analyze(entries);
        assertEquals(2, result.size());
    }

    @Test
    void blankMessagesAreIgnored() {
        List<LogEntry> entries = List.of(
                error("", "Service"),
                error("   ", "Service"),
                error("Real error", "Service")
        );

        List<ErrorFrequency> result = new ErrorFrequencyAnalyzer().analyze(entries);
        assertEquals(1, result.size());
        assertEquals("Real error", result.get(0).getMessage());
    }

    private static LogEntry error(String message, String service) {
        return LogEntry.builder()
                .lineNumber(1)
                .rawLine("test")
                .level(LogLevel.ERROR)
                .service(service)
                .message(message)
                .build();
    }

    private static LogEntry info(String message, String service) {
        return LogEntry.builder()
                .lineNumber(1)
                .rawLine("test")
                .level(LogLevel.INFO)
                .service(service)
                .message(message)
                .build();
    }

    private static LogEntry warn(String message, String service) {
        return LogEntry.builder()
                .lineNumber(1)
                .rawLine("test")
                .level(LogLevel.WARN)
                .service(service)
                .message(message)
                .build();
    }
}
