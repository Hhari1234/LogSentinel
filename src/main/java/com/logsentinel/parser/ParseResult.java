package com.logsentinel.parser;

import com.logsentinel.model.LogEntry;

import java.util.List;

/**
 * Result of parsing a single line or a batch of lines.
 */
public final class ParseResult {

    private final LogEntry entry;
    private final boolean success;
    private final String errorMessage;

    private ParseResult(LogEntry entry, boolean success, String errorMessage) {
        this.entry = entry;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public static ParseResult success(LogEntry entry) {
        return new ParseResult(entry, true, null);
    }

    public static ParseResult failure(String errorMessage) {
        return new ParseResult(null, false, errorMessage);
    }

    public LogEntry getEntry() {
        return entry;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
