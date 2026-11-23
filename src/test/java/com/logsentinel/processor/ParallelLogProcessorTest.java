package com.logsentinel.processor;

import com.logsentinel.exception.LogFileException;
import com.logsentinel.model.LogEntry;
import com.logsentinel.model.LogLevel;
import com.logsentinel.model.ProcessingResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParallelLogProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    void sequentialAndParallelProduceSameCounts() throws Exception {
        Path file = writeSampleFile();

        ProcessingResult sequential = new SequentialLogProcessor().process(file.toString());
        ProcessingResult parallel = new ParallelLogProcessor(4).process(file.toString());

        assertEquals(sequential.getTotalLines(), parallel.getTotalLines());
        assertEquals(sequential.getValidEntries(), parallel.getValidEntries());
        assertEquals(sequential.getInvalidEntries(), parallel.getInvalidEntries());
    }

    @Test
    void sequentialAndParallelProduceSameEntries() throws Exception {
        Path file = writeSampleFile();

        ProcessingResult sequential = new SequentialLogProcessor().process(file.toString());
        ProcessingResult parallel = new ParallelLogProcessor(4).process(file.toString());

        assertEquals(sequential.getEntries().size(), parallel.getEntries().size());
        for (int i = 0; i < sequential.getEntries().size(); i++) {
            LogEntry a = sequential.getEntries().get(i);
            LogEntry b = parallel.getEntries().get(i);
            assertEquals(a.getLineNumber(), b.getLineNumber());
            assertEquals(a.getLevel(), b.getLevel());
            assertEquals(a.getMessage(), b.getMessage());
            assertEquals(a.getService(), b.getService());
        }
    }

    @Test
    void parallelWorksWithSingleThread() throws Exception {
        Path file = writeSampleFile();
        ProcessingResult result = new ParallelLogProcessor(1).process(file.toString());
        assertTrue(result.getValidEntries() > 0);
    }

    @Test
    void parallelWorksWithMultipleThreads() throws Exception {
        Path file = writeSampleFile();
        ProcessingResult result = new ParallelLogProcessor(8).process(file.toString());
        assertTrue(result.getValidEntries() > 0);
    }

    @Test
    void rejectsMissingFile() {
        ParallelLogProcessor processor = new ParallelLogProcessor(4);
        assertThrows(LogFileException.class,
                () -> processor.process("C:\\nonexistent\\app.log"));
    }

    @Test
    void rejectsNullPath() {
        ParallelLogProcessor processor = new ParallelLogProcessor(4);
        assertThrows(LogFileException.class, () -> processor.process(null));
    }

    @Test
    void rejectsInvalidThreadCount() {
        assertThrows(IllegalArgumentException.class, () -> new ParallelLogProcessor(0));
        assertThrows(IllegalArgumentException.class, () -> new ParallelLogProcessor(-1));
    }

    @Test
    void entryLineNumbersArePreservedInParallel() throws Exception {
        Path file = Files.write(tempDir.resolve("ordered.log"), List.of(
                "2026-11-23 10:30:45 INFO [AuthService] first",
                "bad line",
                "2026-11-23 10:31:00 WARN [AuthService] second",
                "another bad line",
                "2026-11-23 10:32:00 ERROR [DatabaseService] third"
        )).toAbsolutePath();

        ProcessingResult result = new ParallelLogProcessor(4, 2).process(file.toString());
        List<LogEntry> entries = result.getEntries();
        assertEquals(3, entries.size());
        assertEquals(1, entries.get(0).getLineNumber());
        assertEquals(3, entries.get(1).getLineNumber());
        assertEquals(5, entries.get(2).getLineNumber());
    }

    private Path writeSampleFile() throws Exception {
        List<String> lines = new java.util.ArrayList<>();
        for (int i = 0; i < 500; i++) {
            lines.add("2026-11-23 10:" + String.format("%02d", i % 60) + ":00 INFO [AuthService] User login");
        }
        for (int i = 0; i < 100; i++) {
            lines.add("2026-11-23 10:" + String.format("%02d", i % 60) + ":01 ERROR [DatabaseService] Connection timeout");
        }
        lines.add("this is malformed");
        lines.add("");
        return Files.write(tempDir.resolve("app.log"), lines);
    }
}
