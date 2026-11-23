package com.logsentinel.analyzer;

import com.logsentinel.model.LogEntry;
import com.logsentinel.model.LogLevel;
import com.logsentinel.model.SearchResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Searches parsed entries by keyword, log level, service, or date.
 * Searches are case-insensitive where applicable.
 */
public final class LogSearchService {

    public LogSearchService() {
    }

    /**
     * Searches entries whose message contains the given keyword.
     */
    public List<SearchResult> byKeyword(List<LogEntry> entries, String keyword) {
        if (entries == null || keyword == null || keyword.isBlank()) {
            return List.of();
        }
        String lower = keyword.toLowerCase();
        List<SearchResult> matches = new ArrayList<>();
        for (LogEntry entry : entries) {
            if (containsIgnoreCase(entry.getMessage(), lower)) {
                matches.add(new SearchResult(entry.getLineNumber(), entry));
            }
        }
        return matches;
    }

    /**
     * Searches entries by log level.
     */
    public List<SearchResult> byLevel(List<LogEntry> entries, LogLevel level) {
        if (entries == null || level == null) {
            return List.of();
        }
        List<SearchResult> matches = new ArrayList<>();
        for (LogEntry entry : entries) {
            if (entry.getLevel() == level) {
                matches.add(new SearchResult(entry.getLineNumber(), entry));
            }
        }
        return matches;
    }

    /**
     * Searches entries by service name (case-insensitive).
     */
    public List<SearchResult> byService(List<LogEntry> entries, String service) {
        if (entries == null || service == null || service.isBlank()) {
            return List.of();
        }
        String lower = service.toLowerCase();
        List<SearchResult> matches = new ArrayList<>();
        for (LogEntry entry : entries) {
            String entryService = entry.getService();
            if (entryService != null && entryService.toLowerCase().equals(lower)) {
                matches.add(new SearchResult(entry.getLineNumber(), entry));
            }
        }
        return matches;
    }

    /**
     * Searches entries by date (matches the date portion of the timestamp).
     */
    public List<SearchResult> byDate(List<LogEntry> entries, LocalDate date) {
        if (entries == null || date == null) {
            return List.of();
        }
        List<SearchResult> matches = new ArrayList<>();
        for (LogEntry entry : entries) {
            LocalDateTime ts = entry.getTimestamp();
            if (ts != null && ts.toLocalDate().equals(date)) {
                matches.add(new SearchResult(entry.getLineNumber(), entry));
            }
        }
        return matches;
    }

    /**
     * Combined search: entries matching the keyword (in message), optionally
     * filtered by level and/or service.
     */
    public List<SearchResult> search(List<LogEntry> entries, String keyword,
                                     LogLevel level, String service) {
        if (entries == null) {
            return List.of();
        }
        String lower = keyword == null || keyword.isBlank() ? null : keyword.toLowerCase();
        String svcLower = service == null || service.isBlank() ? null : service.toLowerCase();
        List<SearchResult> matches = new ArrayList<>();
        for (LogEntry entry : entries) {
            if (level != null && entry.getLevel() != level) {
                continue;
            }
            if (svcLower != null) {
                String entryService = entry.getService();
                if (entryService == null || !entryService.toLowerCase().equals(svcLower)) {
                    continue;
                }
            }
            if (lower != null && !containsIgnoreCase(entry.getMessage(), lower)) {
                continue;
            }
            matches.add(new SearchResult(entry.getLineNumber(), entry));
        }
        return matches;
    }

    private static boolean containsIgnoreCase(String value, String lower) {
        return value != null && value.toLowerCase().contains(lower);
    }
}
