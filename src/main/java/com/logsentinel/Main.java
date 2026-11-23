package com.logsentinel;

import com.logsentinel.analyzer.LogSearchService;
import com.logsentinel.exception.LogFileException;
import com.logsentinel.model.AnalysisSession;
import com.logsentinel.model.DailyStat;
import com.logsentinel.model.ErrorFrequency;
import com.logsentinel.incident.Incident;
import com.logsentinel.model.LogEntry;
import com.logsentinel.model.SearchResult;
import com.logsentinel.processor.ParallelLogProcessor;
import com.logsentinel.processor.SequentialLogProcessor;
import com.logsentinel.report.CsvReportGenerator;
import com.logsentinel.report.ReportData;
import com.logsentinel.report.TextReportGenerator;
import com.logsentinel.service.LogAnalysisService;

import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;

/**
 * Command-line entry point for LogSentinel.
 */
public final class Main {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Scanner scanner = new Scanner(System.in);
    private AnalysisSession currentSession;

    public static void main(String[] args) {
        new Main().run();
    }

    private void run() {
        boolean running = true;
        while (running) {
            printMenu();
            String choice = readChoice();
            try {
                running = handleChoice(choice);
            } catch (LogFileException e) {
                System.out.println("Error: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Unexpected error: " + e.getMessage());
            }
        }
        System.out.println("Goodbye.");
    }

    private void printMenu() {
        System.out.println();
        System.out.println("========================================");
        System.out.println("             LOGSENTINEL");
        System.out.println("     Intelligent Log Analyzer");
        System.out.println("========================================");
        System.out.println("1. Analyze a log file");
        System.out.println("2. View log level statistics");
        System.out.println("3. Find most frequent errors");
        System.out.println("4. Search logs by keyword");
        System.out.println("5. View daily error statistics");
        System.out.println("6. Detect repeated errors");
        System.out.println("7. Generate analysis reports");
        System.out.println("8. Run performance benchmark");
        System.out.println("9. Exit");
        System.out.print("Enter your choice: ");
    }

    private String readChoice() {
        String line = scanner.nextLine();
        return line == null ? "" : line.trim();
    }

    private boolean handleChoice(String choice)
            throws LogFileException, com.logsentinel.report.ReportGenerationException {
        switch (choice) {
            case "1":
                return analyzeFile();
            case "2":
                showLevelStatistics();
                return true;
            case "3":
                showFrequentErrors();
                return true;
            case "4":
                searchLogs();
                return true;
            case "5":
                showDailyStatistics();
                return true;
            case "6":
                detectIncidents();
                return true;
            case "7":
                generateReports();
                return true;
            case "8":
                runBenchmark();
                return true;
            case "9":
                return false;
            default:
                System.out.println("Invalid option. Please enter a number between 1 and 9.");
                return true;
        }
    }

    private boolean analyzeFile() throws LogFileException {
        System.out.print("Enter log file path (.log or .txt): ");
        String path = readChoice();
        if (path.isEmpty()) {
            System.out.println("No path entered.");
            return true;
        }

        System.out.print("Use parallel processing? (y/n): ");
        String parallelInput = readChoice();
        boolean parallel = parallelInput.equalsIgnoreCase("y");

        LogAnalysisService service = new LogAnalysisService(
                parallel ? new ParallelLogProcessor(4) : new SequentialLogProcessor());
        currentSession = service.analyze(path);

        printAnalysisSummary(currentSession);
        return true;
    }

    private void showLevelStatistics() {
        if (currentSession == null) {
            System.out.println("No analysis loaded. Please analyze a log file first (option 1).");
            return;
        }
        System.out.println();
        System.out.println("LOG-LEVEL STATISTICS");
        System.out.println("--------------------");
        System.out.println(currentSession.getStatistics());
    }

    private void showFrequentErrors() {
        if (currentSession == null) {
            System.out.println("No analysis loaded. Please analyze a log file first (option 1).");
            return;
        }
        System.out.println();
        System.out.println("MOST FREQUENT ERRORS");
        System.out.println("--------------------");
        List<ErrorFrequency> errors = currentSession.getErrorFrequencies();
        if (errors.isEmpty()) {
            System.out.println("No errors found.");
            return;
        }
        int rank = 1;
        for (ErrorFrequency ef : errors) {
            System.out.printf("%d. %s – %d occurrences (service: %s)%n",
                    rank, ef.getMessage(), ef.getCount(),
                    ef.getService() == null ? "unknown" : ef.getService());
            rank++;
        }
    }

    private void searchLogs() {
        if (currentSession == null) {
            System.out.println("No analysis loaded. Please analyze a log file first (option 1).");
            return;
        }
        System.out.print("Enter keyword to search: ");
        String keyword = readChoice();
        if (keyword.isEmpty()) {
            System.out.println("No keyword entered.");
            return;
        }
        LogSearchService search = new LogSearchService();
        List<SearchResult> results = search.byKeyword(
                currentSession.getEntries(), keyword);
        System.out.println();
        System.out.println("SEARCH RESULTS FOR: \"" + keyword + "\" (" + results.size() + " matches)");
        System.out.println("----------------------------------------------");
        for (SearchResult r : results) {
            System.out.println(r);
        }
    }

    private void showDailyStatistics() {
        if (currentSession == null) {
            System.out.println("No analysis loaded. Please analyze a log file first (option 1).");
            return;
        }
        System.out.println();
        System.out.println("DAILY ERROR STATISTICS");
        System.out.println("----------------------");
        System.out.printf("%-12s %-10s %-10s %-10s%n",
                "DATE", "ERRORS", "WARNINGS", "TOTAL");
        System.out.printf("%-12s %-10s %-10s %-10s%n",
                "----", "------", "--------", "-----");
        for (DailyStat stat : currentSession.getDailyStats()) {
            System.out.printf("%-12s %-10d %-10d %-10d%n",
                    stat.getDate(), stat.getErrorCount(), stat.getWarnCount(), stat.getTotalCount());
        }
        com.logsentinel.analyzer.DailyStatisticsAnalyzer analyzer =
                new com.logsentinel.analyzer.DailyStatisticsAnalyzer();
        java.time.LocalDate maxDay = analyzer.dayWithMostErrors(currentSession.getEntries());
        if (maxDay != null) {
            System.out.println();
            System.out.println("Day with highest error count: " + maxDay);
        }
    }

    private void detectIncidents() {
        if (currentSession == null) {
            System.out.println("No analysis loaded. Please analyze a log file first (option 1).");
            return;
        }
        System.out.println();
        System.out.println("POTENTIAL INCIDENTS (REPEATED ERROR DETECTION)");
        System.out.println("-----------------------------------------------");
        List<Incident> incidents = currentSession.getIncidents();
        if (incidents.isEmpty()) {
            System.out.println("No potential incidents detected.");
            System.out.println("Note: These are pattern-based findings, not confirmed root causes.");
            return;
        }
        for (Incident incident : incidents) {
            System.out.println(incident);
            System.out.println("-----------------------------------------------");
        }
        System.out.println("Note: Default threshold = 5 errors within 1 minute.");
        System.out.println("These are pattern-based potential incidents, not confirmed root causes.");
    }

    private void generateReports() throws LogFileException, com.logsentinel.report.ReportGenerationException {
        if (currentSession == null) {
            System.out.println("No analysis loaded. Please analyze a log file first (option 1).");
            return;
        }
        System.out.print("Enter reports output directory [reports/]: ");
        String dir = readChoice();
        if (dir.isEmpty()) {
            dir = "reports";
        }

        ReportData data = new ReportData(
                currentSession.getFileName(),
                currentSession.getAnalysisTimestamp(),
                currentSession.getProcessingResult().getTotalLines(),
                currentSession.getProcessingResult().getValidEntries(),
                currentSession.getProcessingResult().getInvalidEntries(),
                currentSession.getStatistics().getLevelCounts(),
                currentSession.getErrorFrequencies(),
                currentSession.getDailyStats(),
                currentSession.getIncidents(),
                currentSession.getProcessingResult().getProcessingTimeMillis()
        );

        new TextReportGenerator().generate(data, Paths.get(dir));
        new CsvReportGenerator().generate(data, Paths.get(dir));
        System.out.println("Reports generated in: " + dir);
    }

    private void runBenchmark() {
        System.out.println("Running performance benchmark...");
        System.out.println("This may take a minute for larger files.");
        try {
            com.logsentinel.benchmark.PerformanceBenchmark.main(new String[0]);
        } catch (Exception e) {
            System.out.println("Benchmark error: " + e.getMessage());
        }
    }

    private void printAnalysisSummary(AnalysisSession session) {
        System.out.println();
        System.out.println("ANALYSIS COMPLETE");
        System.out.println("-----------------");
        System.out.println("File: " + session.getFileName());
        System.out.println("Total lines: " + session.getProcessingResult().getTotalLines());
        System.out.println("Valid entries: " + session.getProcessingResult().getValidEntries());
        System.out.println("Invalid entries: " + session.getProcessingResult().getInvalidEntries());
        System.out.println("Processing time (ms): " + session.getProcessingResult().getProcessingTimeMillis());
        System.out.println();
        System.out.println("Error count: " + session.getStatistics().getCount(com.logsentinel.model.LogLevel.ERROR));
        System.out.println("Warning count: " + session.getStatistics().getCount(com.logsentinel.model.LogLevel.WARN));
        System.out.println("Detected potential incidents: " + session.getIncidents().size());
        System.out.println("Most frequent errors: " + session.getErrorFrequencies().size());
    }
}
