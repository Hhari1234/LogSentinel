package com.logsentinel.incident;

import com.logsentinel.model.LogEntry;
import com.logsentinel.model.LogLevel;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects potential incidents from repeated error patterns.
 *
 * <p>A potential incident is flagged when the same error message (for the
 * same service) occurs at least {@code threshold} times within a configurable
 * time window. This is purely pattern-based: the tool identifies suspicious
 * repetition, not the actual root cause.</p>
 */
public final class IncidentDetector {

    private final int threshold;
    private final long windowMinutes;
    private int nextId = 1;

    public IncidentDetector() {
        this(5, 1);
    }

    public IncidentDetector(int threshold, long windowMinutes) {
        if (threshold < 1) {
            throw new IllegalArgumentException("Threshold must be at least 1");
        }
        if (windowMinutes < 1) {
            throw new IllegalArgumentException("Window minutes must be at least 1");
        }
        this.threshold = threshold;
        this.windowMinutes = windowMinutes;
    }

    public int getThreshold() {
        return threshold;
    }

    public long getWindowMinutes() {
        return windowMinutes;
    }

    /**
     * Detects potential incidents in the given entries.
     *
     * @param entries the parsed log entries
     * @return a list of detected potential incidents
     */
    public List<Incident> detect(List<LogEntry> entries) {
        if (entries == null) {
            return List.of();
        }

        // Group timestamped errors by (service, normalized message)
        Map<String, List<LocalDateTime>> groups = new HashMap<>();
        for (LogEntry entry : entries) {
            if (entry.getLevel() != LogLevel.ERROR) {
                continue;
            }
            LocalDateTime ts = entry.getTimestamp();
            if (ts == null) {
                // Cannot time-window errors without a timestamp
                continue;
            }
            String key = groupKey(entry);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(ts);
        }

        List<Incident> incidents = new ArrayList<>();
        for (Map.Entry<String, List<LocalDateTime>> group : groups.entrySet()) {
            List<LocalDateTime> times = new ArrayList<>(group.getValue());
            times.sort(null);

            String[] parts = group.getKey().split("\\|\\|", 2);
            String service = parts[0].equals("unknown") ? null : parts[0];
            String pattern = parts.length > 1 ? parts[1] : "";

            // Repeatedly extract the densest qualifying window until none remain
            while (times.size() >= threshold) {
                int[] window = findDensestWindow(times);
                if (window == null) {
                    break;
                }
                int start = window[0];
                int end = window[1];
                int count = window[2];

                LocalDateTime first = times.get(start);
                LocalDateTime last = times.get(end);
                long windowMinutes = Duration.between(first, last).toMinutes();

                String id = String.format("INC-%04d", nextId++);
                incidents.add(new Incident.Builder()
                        .id(id)
                        .service(service)
                        .pattern(pattern)
                        .occurrences(count)
                        .firstOccurrence(first)
                        .lastOccurrence(last)
                        .windowMinutes(windowMinutes > 0 ? windowMinutes : 1)
                        .severity(severityFor(count))
                        .potential(true)
                        .build());

                // Remove the occurrences that were just attributed to an incident
                List<LocalDateTime> remaining = new ArrayList<>();
                for (int i = 0; i < times.size(); i++) {
                    if (i < start || i > end) {
                        remaining.add(times.get(i));
                    }
                }
                times = remaining;
            }
        }

        incidents.sort((a, b) -> {
            int sev = Integer.compare(b.getSeverity().ordinal(), a.getSeverity().ordinal());
            if (sev != 0) return sev;
            return Integer.compare(b.getOccurrences(), a.getOccurrences());
        });

        return incidents;
    }

    /**
     * Finds the densest time window that meets the threshold. Returns null
     * when no window contains enough occurrences.
     *
     * @return an int array {startIndex, endIndex, count} or null
     */
    private int[] findDensestWindow(List<LocalDateTime> times) {
        int bestStart = 0;
        int bestEnd = 0;
        int bestCount = 0;
        int j = 0;
        for (int i = 0; i < times.size(); i++) {
            while (j < times.size()
                    && Duration.between(times.get(i), times.get(j)).toMinutes() <= windowMinutes) {
                j++;
            }
            int count = j - i;
            if (count > bestCount) {
                bestCount = count;
                bestStart = i;
                bestEnd = j - 1;
            }
        }

        if (bestCount < threshold) {
            return null;
        }
        return new int[]{bestStart, bestEnd, bestCount};
    }

    private String groupKey(LogEntry entry) {
        String service = entry.getService() == null ? "unknown" : entry.getService();
        String message = entry.getMessage() == null ? "" : entry.getMessage();
        String normalized = message.trim().replaceAll("\\s+", " ");
        return service + "||" + normalized;
    }

    private IncidentSeverity severityFor(int count) {
        if (count >= threshold * 2) {
            return IncidentSeverity.CRITICAL;
        }
        if (count >= threshold) {
            return IncidentSeverity.HIGH;
        }
        return IncidentSeverity.MEDIUM;
    }
}
