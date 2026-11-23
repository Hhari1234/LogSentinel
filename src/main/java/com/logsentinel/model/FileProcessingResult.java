package com.logsentinel.model;

import java.util.Objects;

/**
 * Outcome of reading and parsing a single log file.
 */
public final class FileProcessingResult {

    private final String fileName;
    private final long totalLines;
    private final int validEntries;
    private final int invalidEntries;
    private final long processingTimeMillis;

    public FileProcessingResult(String fileName, long totalLines,
                                int validEntries, int invalidEntries,
                                long processingTimeMillis) {
        this.fileName = fileName;
        this.totalLines = totalLines;
        this.validEntries = validEntries;
        this.invalidEntries = invalidEntries;
        this.processingTimeMillis = processingTimeMillis;
    }

    public String getFileName() {
        return fileName;
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
        FileProcessingResult that = (FileProcessingResult) o;
        return totalLines == that.totalLines
                && validEntries == that.validEntries
                && invalidEntries == that.invalidEntries
                && processingTimeMillis == that.processingTimeMillis
                && Objects.equals(fileName, that.fileName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, totalLines, validEntries, invalidEntries, processingTimeMillis);
    }

    @Override
    public String toString() {
        return "FileProcessingResult{"
                + "fileName='" + fileName + '\''
                + ", totalLines=" + totalLines
                + ", validEntries=" + validEntries
                + ", invalidEntries=" + invalidEntries
                + ", processingTimeMillis=" + processingTimeMillis
                + '}';
    }
}
