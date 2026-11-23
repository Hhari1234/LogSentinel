package com.logsentinel.parser;

import com.logsentinel.model.LogEntry;
import com.logsentinel.model.LogLevel;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses application log lines into {@link LogEntry} objects.
 *
 * <p>Supported line formats:</p>
 * <pre>
 * yyyy-MM-dd HH:mm:ss LEVEL [ServiceName] [thread-name] message
 * yyyy-MM-dd HH:mm:ss LEVEL [ServiceName] message
 * yyyy-MM-dd HH:mm:ss LEVEL message
 * LEVEL [ServiceName] message
 * LEVEL message
 * </pre>
 *
 * <p>The timestamp, service name, and thread name are optional. The level
 * token is matched as an uppercase word and then mapped to a {@link
 * LogLevel}; unrecognized tokens become {@link LogLevel#UNKNOWN} but the
 * line is still considered valid.</p>
 */
public final class LogParser {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // timestamp LEVEL [service] [thread] message
    private static final Pattern FULL_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})\\s+([A-Z]+)\\s+" +
                    "(?:\\[([^\\]]+)\\])?" +
                    "\\s*(?:\\[([^\\]]+)\\])?" +
                    "\\s?(.*)$"
    );

    // LEVEL [service] [thread] message  (no timestamp)
    private static final Pattern NO_TIMESTAMP_PATTERN = Pattern.compile(
            "([A-Z]+)\\s+" +
                    "(?:\\[([^\\]]+)\\])?" +
                    "\\s*(?:\\[([^\\]]+)\\])?" +
                    "\\s?(.*)$"
    );

    // timestamp LEVEL message  (no brackets)
    private static final Pattern NO_BRACKETS_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})\\s+([A-Z]+)\\s+(.*)$"
    );

    private static final Pattern EXCEPTION_PATTERN = Pattern.compile(
            "\\b([A-Z][A-Za-z0-9]*Exception)\\b"
    );

    private LogParser() {
    }

    public static ParseResult parse(String line, int lineNumber) {
        if (line == null) {
            return ParseResult.failure("null line");
        }

        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return ParseResult.failure("empty line");
        }

        Matcher matcher = FULL_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            matcher = NO_TIMESTAMP_PATTERN.matcher(trimmed);
            if (!matcher.matches()) {
                matcher = NO_BRACKETS_PATTERN.matcher(trimmed);
                if (!matcher.matches()) {
                    return ParseResult.failure("no recognizable structure");
                }
            }
        }

        LogEntry.Builder builder = LogEntry.builder()
                .lineNumber(lineNumber)
                .rawLine(line);

        boolean hasTimestamp = matcher.group(1) != null
                && matcher.group(1).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");

        if (hasTimestamp) {
            builder.timestamp(parseTimestamp(matcher.group(1)));
            builder.level(LogLevel.fromString(matcher.group(2)));
            builder.service(matcher.group(3));
            builder.thread(matcher.group(4));
            builder.message(matcher.group(5) == null ? "" : matcher.group(5).trim());
        } else {
            builder.timestamp(null);
            builder.level(LogLevel.fromString(matcher.group(1)));
            builder.service(matcher.group(2));
            builder.thread(matcher.group(3));
            builder.message(matcher.group(4) == null ? "" : matcher.group(4).trim());
        }

        String message = builder.build().getMessage();
        String exceptionName = extractExceptionName(message);
        if (exceptionName != null) {
            builder.exceptionName(exceptionName);
        }

        return ParseResult.success(builder.build());
    }

    private static LocalDateTime parseTimestamp(String value) {
        try {
            return LocalDateTime.parse(value, TIMESTAMP_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String extractExceptionName(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        Matcher matcher = EXCEPTION_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
