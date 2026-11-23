package com.logsentinel.model;

import com.logsentinel.analyzer.DailyStatisticsAnalyzer;
import com.logsentinel.incident.Incident;
import com.logsentinel.incident.IncidentDetector;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Holds the complete result of analyzing one log file.
 */
public final class AnalysisSession {

    private final String fileName;
    private final ProcessingResult processingResult;
    private final List<LogEntry> entries;
    private final LogStatistics statistics;
    private final List<ErrorFrequency> errorFrequencies;
    private final List<DailyStat> dailyStats;
    private final List<Incident> incidents;
    private final LocalDateTime analysisTimestamp;

    public AnalysisSession(String fileName, ProcessingResult processingResult,
                           List<LogEntry> entries, LogStatistics statistics,
                           List<ErrorFrequency> errorFrequencies,
                           List<DailyStat> dailyStats, List<Incident> incidents,
                           LocalDateTime analysisTimestamp) {
        this.fileName = fileName;
        this.processingResult = processingResult;
        this.entries = entries;
        this.statistics = statistics;
        this.errorFrequencies = errorFrequencies;
        this.dailyStats = dailyStats;
        this.incidents = incidents;
        this.analysisTimestamp = analysisTimestamp;
    }

    public String getFileName() {
        return fileName;
    }

    public ProcessingResult getProcessingResult() {
        return processingResult;
    }

    public List<LogEntry> getEntries() {
        return entries;
    }

    public LogStatistics getStatistics() {
        return statistics;
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

    public LocalDateTime getAnalysisTimestamp() {
        return analysisTimestamp;
    }
}
