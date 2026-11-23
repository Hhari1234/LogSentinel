package com.logsentinel.model;

/**
 * A single search match, including the original line number.
 */
public final class SearchResult {

    private final int lineNumber;
    private final LogEntry entry;

    public SearchResult(int lineNumber, LogEntry entry) {
        this.lineNumber = lineNumber;
        this.entry = entry;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public LogEntry getEntry() {
        return entry;
    }

    @Override
    public String toString() {
        return String.format("[line %d] %s [%s] %s",
                lineNumber, entry.getTimestamp(), entry.getLevel(), entry.getMessage());
    }
}
