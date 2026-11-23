package com.logsentinel.service;

import com.logsentinel.analyzer.DailyStatisticsAnalyzer;
import com.logsentinel.analyzer.ErrorFrequencyAnalyzer;
import com.logsentinel.analyzer.LogStatisticsAnalyzer;
import com.logsentinel.exception.LogFileException;
import com.logsentinel.incident.Incident;
import com.logsentinel.incident.IncidentDetector;
import com.logsentinel.model.AnalysisSession;
import com.logsentinel.model.DailyStat;
import com.logsentinel.model.ErrorFrequency;
import com.logsentinel.model.LogEntry;
import com.logsentinel.model.LogStatistics;
import com.logsentinel.model.ProcessingResult;
import com.logsentinel.processor.LogProcessor;
import com.logsentinel.processor.ParallelLogProcessor;
import com.logsentinel.processor.SequentialLogProcessor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Coordinates reading a log file and running all analyses. Kept separate from
 * the CLI so the same analysis can be reused by reports, tests, or other UIs.
 */
public final class LogAnalysisService {

    private final LogProcessor processor;
    private final LogStatisticsAnalyzer statisticsAnalyzer = new LogStatisticsAnalyzer();
    private final ErrorFrequencyAnalyzer errorFrequencyAnalyzer = new ErrorFrequencyAnalyzer();
    private final DailyStatisticsAnalyzer dailyStatisticsAnalyzer = new DailyStatisticsAnalyzer();
    private final IncidentDetector incidentDetector = new IncidentDetector();

    public LogAnalysisService(LogProcessor processor) {
        this.processor = processor;
    }

    public LogAnalysisService() {
        this(new SequentialLogProcessor());
    }

    /**
     * Analyzes the given log file sequentially.
     */
    public AnalysisSession analyze(String filePath) throws LogFileException {
        return analyze(filePath, false);
    }

    /**
     * Analyzes the given log file, optionally using parallel processing.
     */
    public AnalysisSession analyze(String filePath, boolean parallel) throws LogFileException {
        ProcessingResult result = processor.process(filePath);
        List<LogEntry> entries = result.getEntries();

        LogStatistics statistics = statisticsAnalyzer.analyze(
                entries, result.getInvalidEntries());
        List<ErrorFrequency> errorFrequencies = errorFrequencyAnalyzer.analyze(entries);
        List<DailyStat> dailyStats = new DailyStatisticsAnalyzer().analyze(entries);
        List<Incident> incidents = incidentDetector.detect(entries);

        return new AnalysisSession(
                result.getFileName(),
                result,
                entries,
                statistics,
                errorFrequencies,
                dailyStats,
                incidents,
                LocalDateTime.now()
        );
    }

    public List<LogEntry> searchByKeyword(List<LogEntry> entries, String keyword) {
        com.logsentinel.analyzer.LogSearchService search = new com.logsentinel.analyzer.LogSearchService();
        return search.byKeyword(entries, keyword).stream()
                .map(com.logsentinel.model.SearchResult::getEntry)
                .toList();
    }
}
