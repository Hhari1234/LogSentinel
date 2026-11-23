package com.logsentinel.incident;

/**
 * Severity assigned to a detected incident pattern.
 * Severity is based on the number of occurrences and how tightly they are
 * clustered in time. This is an automated assessment, not a confirmed root
 * cause.
 */
public enum IncidentSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
