package com.logsentinel.processor;

import com.logsentinel.exception.LogFileException;
import com.logsentinel.model.FileProcessingResult;
import com.logsentinel.model.LogEntry;
import com.logsentinel.model.LogLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogFileReaderTest {

    @TempDir
    Path tempDir;

    private Path writeFile(String name, String... lines) throws Exception {
        Path file = tempDir.resolve(name);
        Files.write(file, java.util.List.of(lines));
        return file;
    }

    @Test
    void readsValidLogFileAndCountsEntries() throws Exception {
        Path file = writeFile("app.log",
                "2026-11-23 10:30:45 INFO [AuthService] User login successful",
                "2026-11-23 10:31:12 WARN [PaymentService] Payment response delayed",
                "2026-11-23 10:32:01 ERROR [DatabaseService] Connection timeout");

        LogFileReader reader = new LogFileReader();
        LogFileReader.ReadResult result = reader.readWithEntries(file.toString());

        assertEquals(3, result.getSummary().getTotalLines());
        assertEquals(3, result.getSummary().getValidEntries());
        assertEquals(0, result.getSummary().getInvalidEntries());
        assertEquals(3, result.getEntries().size());
    }

    @Test
    void countsMalformedLinesAsInvalid() throws Exception {
        Path file = writeFile("app.log",
                "2026-11-23 10:30:45 INFO [AuthService] User login successful",
                "this is not a log line",
                "",
                "2026-11-23 10:32:01 ERROR [DatabaseService] Connection timeout");

        LogFileReader reader = new LogFileReader();
        LogFileReader.ReadResult result = reader.readWithEntries(file.toString());

        assertEquals(4, result.getSummary().getTotalLines());
        assertEquals(2, result.getSummary().getValidEntries());
        assertEquals(2, result.getSummary().getInvalidEntries());
    }

    @Test
    void throwsOnMissingFile() {
        LogFileReader reader = new LogFileReader();
        assertThrows(LogFileException.class, () ->
                reader.read("C:\\nonexistent\\app.log"));
    }

    @Test
    void rejectsUnsupportedExtension() throws Exception {
        Path file = writeFile("app.csv", "some data");
        LogFileReader reader = new LogFileReader();
        assertThrows(LogFileException.class, () -> reader.read(file.toString()));
    }

    @Test
    void acceptsTxtExtension() throws Exception {
        Path file = writeFile("app.txt",
                "2026-11-23 10:30:45 INFO [AuthService] User login successful");

        LogFileReader reader = new LogFileReader();
        FileProcessingResult result = reader.read(file.toString());
        assertEquals(1, result.getValidEntries());
    }

    @Test
    void emptyFileProducesZeroCounts() throws Exception {
        Path file = writeFile("empty.log");

        LogFileReader reader = new LogFileReader();
        FileProcessingResult result = reader.read(file.toString());
        assertEquals(0, result.getTotalLines());
        assertEquals(0, result.getValidEntries());
        assertEquals(0, result.getInvalidEntries());
    }

    @Test
    void preservesLineNumbersInEntries() throws Exception {
        Path file = writeFile("app.log",
                "2026-11-23 10:30:45 INFO [AuthService] first",
                "bad line",
                "2026-11-23 10:31:00 WARN [AuthService] second");

        LogFileReader reader = new LogFileReader();
        LogFileReader.ReadResult result = reader.readWithEntries(file.toString());
        List<LogEntry> entries = result.getEntries();

        assertEquals(2, entries.size());
        assertEquals(1, entries.get(0).getLineNumber());
        assertEquals(3, entries.get(1).getLineNumber());
    }

    @Test
    void rejectsNullPath() {
        LogFileReader reader = new LogFileReader();
        assertThrows(LogFileException.class, () -> reader.read(null));
    }

    @Test
    void sampleApplicationLogReadsSuccessfully() throws Exception {
        Path sample = java.nio.file.Paths.get("C:\\LogSentinel\\sample-logs\\application.log");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                java.nio.file.Files.exists(sample), "Sample log file not present");

        LogFileReader reader = new LogFileReader();
        FileProcessingResult result = reader.read(sample.toString());
        assertTrue(result.getTotalLines() > 0);
        assertTrue(result.getValidEntries() > 0);
        assertTrue(result.getInvalidEntries() > 0);
    }
}
