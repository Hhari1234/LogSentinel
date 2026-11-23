package com.logsentinel.model;

public enum LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL,
    UNKNOWN;

    public static LogLevel fromString(String level) {
        if (level == null || level.isBlank()) {
            return UNKNOWN;
        }
        try {
            return valueOf(level.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
