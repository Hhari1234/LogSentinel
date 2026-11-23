package com.logsentinel.model;

/**
 * A single error message and its occurrence count.
 */
public final class ErrorFrequency implements Comparable<ErrorFrequency> {

    private final String message;
    private final String normalizedMessage;
    private final String service;
    private final int count;

    public ErrorFrequency(String message, String normalizedMessage,
                          String service, int count) {
        this.message = message;
        this.normalizedMessage = normalizedMessage;
        this.service = service;
        this.count = count;
    }

    public String getMessage() {
        return message;
    }

    public String getNormalizedMessage() {
        return normalizedMessage;
    }

    public String getService() {
        return service;
    }

    public int getCount() {
        return count;
    }

    @Override
    public int compareTo(ErrorFrequency other) {
        // Higher count first; ties broken by message for deterministic order
        int cmp = Integer.compare(other.count, this.count);
        if (cmp != 0) return cmp;
        return (this.message == null ? "" : this.message)
                .compareTo(other.message == null ? "" : other.message);
    }

    @Override
    public String toString() {
        String servicePart = service == null ? "unknown service" : service;
        return String.format("%s (%s): %d", message, servicePart, count);
    }
}
