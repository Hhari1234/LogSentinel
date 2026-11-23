package com.logsentinel.report;

import com.logsentinel.model.DailyStat;
import com.logsentinel.model.ErrorFrequency;
import com.logsentinel.incident.Incident;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * All the data needed to generate a complete set of analysis reports.
 */
public final class ReportData {

    private final String fileName;
    private final LocalDateTime processingTimestamp;
    private final long totalLines;
    private final int validEntries;
    private final int invalidEntries;
    private final Map<com.logsentinel.model.LogLevel, Integer> levelCounts;
    private final List<ErrorFrequency> errorFrequencies;
    private final List<DailyStat> dailyStats;
    private final List<Incident> incidents;
    private final long processingDurationMillis;

    public ReportData(String fileName, LocalDateTime processingTimestamp,
                      long totalLines, int validEntries, int invalidEntries,
                      Map<com.logsentinel.model.LogLevel, Integer> levelCounts,
                      List<ErrorFrequency> errorFrequencies,
                      List<DailyStat> dailyStats, List<Incident> incidents,
                      long processingDurationMillis) {
        this.fileName = fileName;
        this.processingTimestamp = processingTimestamp;
        this.totalLines = totalLines;
        this.validEntries = validEntries;
        this.invalidEntries = invalidEntries;
        this.levelCounts = levelCounts;
        this.errorFrequencies = errorFrequencies;
        this.dailyStats = dailyStats;
        this.incidents = incidents;
        this.processingDurationMillis = processingDurationMillis;
    }

    public String getFileName() {
        return fileName;
    }

    public LocalDateTime getProcessingTimestamp() {
        return processingTimestamp;
    }

    public long getTotalLines() {
        return totalLines;
    }

    public int getValidEntries() {
        return validEntries;
    }

    public int getInvalidEntries() {
        return invalidEntries;
    }

    public Map<com.logsentinel.model.LogLevel, Integer> getLevelCounts() {
        return levelCounts;
    }

    public List<ErrorFrequency> getErrorFrequencies() {
        return errorFrequencies;
    }

    public List<DailyStat> getDailyStats() {
        return dailyStats;
    }

    public List<Incident> getIncidents() {
        return incidents;
    }

    public long getProcessingDurationMillis() {
        return processingDurationMillis;
    }
}
