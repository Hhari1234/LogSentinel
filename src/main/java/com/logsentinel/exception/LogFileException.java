package com.logsentinel.exception;

/**
 * Thrown when a log file cannot be read due to a missing file, a permission
 * problem, or another I/O failure.
 */
public class LogFileException extends Exception {

    public LogFileException(String message) {
        super(message);
    }

    public LogFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
