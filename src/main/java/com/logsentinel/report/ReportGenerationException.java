package com.logsentinel.report;

/**
 * Thrown when a report cannot be generated or written to disk.
 */
public class ReportGenerationException extends Exception {

    public ReportGenerationException(String message) {
        super(message);
    }

    public ReportGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
