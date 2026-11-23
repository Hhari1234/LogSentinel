package com.logsentinel.analyzer;

import com.logsentinel.model.ErrorFrequency;
import com.logsentinel.model.LogEntry;
import com.logsentinel.model.LogLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Counts how often each ERROR message occurs and identifies the most frequent
 * ones. Messages are normalized (whitespace collapsed) so that minor
 * formatting differences do not split the same error into separate buckets,
 * while the original message is preserved for display.
 */
public final class ErrorFrequencyAnalyzer {

    private static final int DEFAULT_TOP_N = 10;

    public ErrorFrequencyAnalyzer() {
    }

    /**
     * Analyzes the given entries and returns the most frequent error messages,
     * sorted by occurrence count in descending order.
     *
     * @param entries the parsed log entries
     * @return the full list of error frequencies, sorted
     */
    public List<ErrorFrequency> analyze(List<LogEntry> entries) {
        return analyze(entries, DEFAULT_TOP_N);
    }

    /**
     * Analyzes the given entries and returns the top N most frequent error
     * messages.
     *
     * @param entries the parsed log entries
     * @param topN    maximum number of results to return
     * @return the top N error frequencies, sorted by count descending
     */
    public List<ErrorFrequency> analyze(List<LogEntry> entries, int topN) {
        if (entries == null) {
            return List.of();
        }

        Map<String, BuilderHolder> buckets = new HashMap<>();

        for (LogEntry entry : entries) {
            if (entry.getLevel() != LogLevel.ERROR) {
                continue;
            }
            String message = entry.getMessage();
            if (message == null || message.isBlank()) {
                continue;
            }
            String normalized = normalize(message);
            String service = entry.getService();

            BuilderHolder holder = buckets.get(normalized);
            if (holder == null) {
                holder = new BuilderHolder(message, normalized, service);
                buckets.put(normalized, holder);
            }
            holder.increment();
            // Keep the first seen service for the bucket
        }

        return buckets.values().stream()
                .map(BuilderHolder::build)
                .sorted(Comparator.naturalOrder())
                .limit(topN)
                .collect(Collectors.toList());
    }

    private static String normalize(String message) {
        return message.trim().replaceAll("\\s+", " ");
    }

    /**
     * Mutable holder used while building frequency buckets.
     */
    private static final class BuilderHolder {
        private final String message;
        private final String normalizedMessage;
        private final String service;
        private int count;

        BuilderHolder(String message, String normalizedMessage, String service) {
            this.message = message;
            this.normalizedMessage = normalizedMessage;
            this.service = service;
            this.count = 0;
        }

        void increment() {
            count++;
        }

        ErrorFrequency build() {
            return new ErrorFrequency(message, normalizedMessage, service, count);
        }
    }
}
