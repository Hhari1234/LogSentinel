package com.logsentinel.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Outcome of processing a log file: the parsed entries plus summary counts.
 */
public final class ProcessingResult {

    private final String fileName;
    private final List<LogEntry> entries;
    private final long totalLines;
    private final int validEntries;
    private final int invalidEntries;
    private final long processingTimeMillis;

    public ProcessingResult(String fileName, List<LogEntry> entries,
                            long totalLines, int validEntries, int invalidEntries,
                            long processingTimeMillis) {
        this.fileName = fileName;
        this.entries = entries == null ? Collections.emptyList() : entries;
        this.totalLines = totalLines;
        this.validEntries = validEntries;
        this.invalidEntries = invalidEntries;
        this.processingTimeMillis = processingTimeMillis;
    }

    public String getFileName() {
        return fileName;
    }

    public List<LogEntry> getEntries() {
        return entries;
    }

    public long getTotalLines() {
        return totalLines;
    }

    public int getValidEntries() {
        return validEntries;
    }

    public int getInvalidEntries() {
        return invalidEntries;
    }

    public long getProcessingTimeMillis() {
        return processingTimeMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProcessingResult that = (ProcessingResult) o;
        return totalLines == that.totalLines
                && validEntries == that.validEntries
                && invalidEntries == that.invalidEntries
                && processingTimeMillis == that.processingTimeMillis
                && Objects.equals(fileName, that.fileName)
                && Objects.equals(entries, that.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, entries, totalLines, validEntries, invalidEntries, processingTimeMillis);
    }
}
