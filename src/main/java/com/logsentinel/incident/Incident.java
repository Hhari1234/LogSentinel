package com.logsentinel.incident;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * A potential incident detected from repeated log patterns.
 *
 * <p>This is a pattern-based detection. It indicates a suspicious repetition
 * of errors and does not identify the actual root cause of the problem.</p>
 */
public final class Incident {

    private final String id;
    private final String service;
    private final String pattern;
    private final int occurrences;
    private final LocalDateTime firstOccurrence;
    private final LocalDateTime lastOccurrence;
    private final long windowMinutes;
    private final IncidentSeverity severity;
    private final boolean potential;

    private Incident(String id, String service, String pattern, int occurrences,
                     LocalDateTime firstOccurrence, LocalDateTime lastOccurrence,
                     long windowMinutes, IncidentSeverity severity, boolean potential) {
        this.id = id;
        this.service = service;
        this.pattern = pattern;
        this.occurrences = occurrences;
        this.firstOccurrence = firstOccurrence;
        this.lastOccurrence = lastOccurrence;
        this.windowMinutes = windowMinutes;
        this.severity = severity;
        this.potential = potential;
    }

    public String getId() {
        return id;
    }

    public String getService() {
        return service;
    }

    public String getPattern() {
        return pattern;
    }

    public int getOccurrences() {
        return occurrences;
    }

    public LocalDateTime getFirstOccurrence() {
        return firstOccurrence;
    }

    public LocalDateTime getLastOccurrence() {
        return lastOccurrence;
    }

    public long getWindowMinutes() {
        return windowMinutes;
    }

    public IncidentSeverity getSeverity() {
        return severity;
    }

    public boolean isPotential() {
        return potential;
    }

    @Override
    public String toString() {
        return String.format(
                "Incident ID: %s%nService: %s%nPattern: %s%nOccurrences: %d%n" +
                        "First occurrence: %s%nLast occurrence: %s%nWindow: %d minute(s)%n" +
                        "Severity: %s%nType: %s%n",
                id, service == null ? "unknown" : service, pattern, occurrences,
                firstOccurrence, lastOccurrence, windowMinutes,
                severity, potential ? "POTENTIAL INCIDENT" : "PATTERN");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Incident incident = (Incident) o;
        return occurrences == incident.occurrences
                && windowMinutes == incident.windowMinutes
                && Objects.equals(id, incident.id)
                && Objects.equals(service, incident.service)
                && Objects.equals(pattern, incident.pattern)
                && Objects.equals(firstOccurrence, incident.firstOccurrence)
                && Objects.equals(lastOccurrence, incident.lastOccurrence)
                && severity == incident.severity
                && potential == incident.potential;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, service, pattern, occurrences, firstOccurrence,
                lastOccurrence, windowMinutes, severity, potential);
    }

    /**
     * Builds incidents. The caller supplies the unique id.
     */
    public static final class Builder {
        private String id;
        private String service;
        private String pattern;
        private int occurrences;
        private LocalDateTime firstOccurrence;
        private LocalDateTime lastOccurrence;
        private long windowMinutes;
        private IncidentSeverity severity = IncidentSeverity.MEDIUM;
        private boolean potential = true;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder service(String service) {
            this.service = service;
            return this;
        }

        public Builder pattern(String pattern) {
            this.pattern = pattern;
            return this;
        }

        public Builder occurrences(int occurrences) {
            this.occurrences = occurrences;
            return this;
        }

        public Builder firstOccurrence(LocalDateTime firstOccurrence) {
            this.firstOccurrence = firstOccurrence;
            return this;
        }

        public Builder lastOccurrence(LocalDateTime lastOccurrence) {
            this.lastOccurrence = lastOccurrence;
            return this;
        }

        public Builder windowMinutes(long windowMinutes) {
            this.windowMinutes = windowMinutes;
            return this;
        }

        public Builder severity(IncidentSeverity severity) {
            this.severity = severity == null ? IncidentSeverity.MEDIUM : severity;
            return this;
        }

        public Builder potential(boolean potential) {
            this.potential = potential;
            return this;
        }

        public Incident build() {
            return new Incident(id, service, pattern, occurrences, firstOccurrence,
                    lastOccurrence, windowMinutes, severity, potential);
        }
    }
}
