package com.logsentinel.parser;

import com.logsentinel.model.LogEntry;
import com.logsentinel.model.LogLevel;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;

import static org.junit.jupiter.api.Assertions.*;

class LogParserTest {

    @Test
    void parsesFullEntryWithTimestampServiceAndThread() {
        ParseResult result = LogParser.parse(
                "2026-11-23 10:30:45 INFO [AuthService] User login successful", 1);

        assertTrue(result.isSuccess());
        LogEntry entry = result.getEntry();
        assertEquals(LocalDateTime.of(2026, Month.NOVEMBER, 23, 10, 30, 45), entry.getTimestamp());
        assertEquals(LogLevel.INFO, entry.getLevel());
        assertEquals("AuthService", entry.getService());
        assertEquals("User login successful", entry.getMessage());
        assertEquals(1, entry.getLineNumber());
    }

    @Test
    void parsesEntryWithThreadName() {
        ParseResult result = LogParser.parse(
                "2026-11-23 10:31:12 WARN [PaymentService] [main-thread] Payment response delayed", 2);

        assertTrue(result.isSuccess());
        LogEntry entry = result.getEntry();
        assertEquals(LogLevel.WARN, entry.getLevel());
        assertEquals("PaymentService", entry.getService());
        assertEquals("main-thread", entry.getThread());
        assertEquals("Payment response delayed", entry.getMessage());
    }

    @Test
    void parsesErrorEntryWithoutService() {
        ParseResult result = LogParser.parse(
                "2026-11-23 10:32:01 ERROR Connection timeout", 3);

        assertTrue(result.isSuccess());
        LogEntry entry = result.getEntry();
        assertEquals(LogLevel.ERROR, entry.getLevel());
        assertNull(entry.getService());
        assertEquals("Connection timeout", entry.getMessage());
    }

    @Test
    void detectsExceptionNameInMessage() {
        ParseResult result = LogParser.parse(
                "2026-11-23 10:32:05 ERROR [DatabaseService] ConnectionTimeoutException: timed out", 4);

        assertTrue(result.isSuccess());
        assertEquals("ConnectionTimeoutException", result.getEntry().getExceptionName());
    }

    @Test
    void parsesLineWithoutTimestamp() {
        ParseResult result = LogParser.parse("INFO User login successful", 5);

        assertTrue(result.isSuccess());
        LogEntry entry = result.getEntry();
        assertNull(entry.getTimestamp());
        assertEquals(LogLevel.INFO, entry.getLevel());
        assertEquals("User login successful", entry.getMessage());
    }

    @Test
    void parsesUnknownLogLevelAsUnknown() {
        ParseResult result = LogParser.parse("2026-11-23 10:33:20 VERBOSE Order created", 6);

        assertTrue(result.isSuccess());
        assertEquals(LogLevel.UNKNOWN, result.getEntry().getLevel());
    }

    @Test
    void rejectsEmptyLine() {
        ParseResult result = LogParser.parse("", 7);
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void rejectsWhitespaceOnlyLine() {
        ParseResult result = LogParser.parse("   ", 8);
        assertFalse(result.isSuccess());
    }

    @Test
    void rejectsNullLine() {
        ParseResult result = LogParser.parse(null, 9);
        assertFalse(result.isSuccess());
    }

    @Test
    void rejectsLineWithoutRecognizableLevel() {
        ParseResult result = LogParser.parse("2026-11-23 10:33:20 some random text", 10);
        assertFalse(result.isSuccess());
    }

    @Test
    void preservesOriginalLine() {
        String line = "2026-11-23 10:30:45 INFO [AuthService] User login successful";
        ParseResult result = LogParser.parse(line, 1);
        assertEquals(line, result.getEntry().getRawLine());
    }

    @Test
    void immutableEntryCannotBeChangedAfterBuild() {
        ParseResult result = LogParser.parse("2026-11-23 10:30:45 INFO [AuthService] Hello", 1);
        LogEntry entry = result.getEntry();
        assertEquals(LogLevel.INFO, entry.getLevel());
    }
}
