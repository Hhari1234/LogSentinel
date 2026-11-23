package com.logsentinel.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * A single parsed log entry.
 *
 * <p>Supported format:</p>
 * <pre>
 * 2026-11-23 10:30:45 INFO [AuthService] User login successful
 * </pre>
 *
 * <p>The service name in brackets and the timestamp are optional in the
 * sense that the parser degrades gracefully when they are missing or
 * malformed. The timestamp, when present, must follow
 * {@code yyyy-MM-dd HH:mm:ss}.</p>
 */
public final class LogEntry {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final int lineNumber;
    private final String rawLine;
    private final LocalDateTime timestamp;
    private final LogLevel level;
    private final String service;
    private final String thread;
    private final String message;
    private final String exceptionName;
    private final boolean valid;

    private LogEntry(Builder builder) {
        this.lineNumber = builder.lineNumber;
        this.rawLine = builder.rawLine;
        this.timestamp = builder.timestamp;
        this.level = builder.level;
        this.service = builder.service;
        this.thread = builder.thread;
        this.message = builder.message;
        this.exceptionName = builder.exceptionName;
        this.valid = builder.valid;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getRawLine() {
        return rawLine;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public LogLevel getLevel() {
        return level;
    }

    public String getService() {
        return service;
    }

    public String getThread() {
        return thread;
    }

    public String getMessage() {
        return message;
    }

    public String getExceptionName() {
        return exceptionName;
    }

    public boolean isValid() {
        return valid;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int lineNumber;
        private String rawLine;
        private LocalDateTime timestamp;
        private LogLevel level = LogLevel.UNKNOWN;
        private String service;
        private String thread;
        private String message;
        private String exceptionName;
        private boolean valid = true;

        private Builder() {
        }

        public Builder lineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
            return this;
        }

        public Builder rawLine(String rawLine) {
            this.rawLine = rawLine;
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder level(LogLevel level) {
            this.level = level == null ? LogLevel.UNKNOWN : level;
            return this;
        }

        public Builder service(String service) {
            this.service = service == null || service.isBlank() ? null : service.trim();
            return this;
        }

        public Builder thread(String thread) {
            this.thread = thread == null || thread.isBlank() ? null : thread.trim();
            return this;
        }

        public Builder message(String message) {
            this.message = message == null ? "" : message;
            return this;
        }

        public Builder exceptionName(String exceptionName) {
            this.exceptionName = exceptionName;
            return this;
        }

        public Builder valid(boolean valid) {
            this.valid = valid;
            return this;
        }

        public LogEntry build() {
            return new LogEntry(this);
        }
    }
}
