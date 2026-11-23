package com.logsentinel.report;

import com.logsentinel.model.DailyStat;
import com.logsentinel.model.ErrorFrequency;
import com.logsentinel.model.LogLevel;
import com.logsentinel.incident.Incident;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Generates human-readable text reports.
 */
public final class TextReportGenerator implements ReportGenerator {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void generate(ReportData data, Path outputDir) throws ReportGenerationException {
        if (!Files.isDirectory(outputDir)) {
            throw new ReportGenerationException("Output directory does not exist: " + outputDir);
        }

        writeSummaryReport(data, outputDir);
        writeErrorFrequencyReport(data, outputDir);
        writeDailyStatisticsReport(data, outputDir);
        writeIncidentReport(data, outputDir);
    }

    private void writeSummaryReport(ReportData data, Path outputDir) throws ReportGenerationException {
        Path file = outputDir.resolve("summary_report.txt");
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write("========================================");
            writer.newLine();
            writer.write("          LOGSENTINEL SUMMARY REPORT");
            writer.newLine();
            writer.write("========================================");
            writer.newLine();
            writer.newLine();
            writer.write("Input file: " + data.getFileName());
            writer.newLine();
            writer.write("Processing date: " + format(data.getProcessingTimestamp()));
            writer.newLine();
            writer.write("Total lines: " + data.getTotalLines());
            writer.newLine();
            writer.write("Valid entries: " + data.getValidEntries());
            writer.newLine();
            writer.write("Invalid entries: " + data.getInvalidEntries());
            writer.newLine();
            writer.write("Processing duration (ms): " + data.getProcessingDurationMillis());
            writer.newLine();
            writer.newLine();
            writer.write("LOG-LEVEL COUNTS");
            writer.newLine();
            writer.write("----------------");
            writer.newLine();
            for (LogLevel level : LogLevel.values()) {
                Integer count = data.getLevelCounts().getOrDefault(level, 0);
                writer.write(String.format("%-8s: %d", level, count));
                writer.newLine();
            }
            writer.write(String.format("Error percentage: %.2f%%%n",
                    percentage(data.getLevelCounts().getOrDefault(LogLevel.ERROR, 0), data.getValidEntries())));
            writer.write(String.format("Warning percentage: %.2f%%%n",
                    percentage(data.getLevelCounts().getOrDefault(LogLevel.WARN, 0), data.getValidEntries())));
        } catch (IOException e) {
            throw new ReportGenerationException("Failed to write summary report", e);
        }
    }

    private void writeErrorFrequencyReport(ReportData data, Path outputDir) throws ReportGenerationException {
        Path file = outputDir.resolve("error_frequency_report.txt");
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write("========================================");
            writer.newLine();
            writer.write("       MOST FREQUENT ERRORS REPORT");
            writer.newLine();
            writer.write("========================================");
            writer.newLine();
            writer.newLine();
            List<ErrorFrequency> errors = data.getErrorFrequencies();
            if (errors.isEmpty()) {
                writer.write("No errors found.");
                writer.newLine();
            } else {
                int rank = 1;
                for (ErrorFrequency ef : errors) {
                    writer.write(String.format("%d. %s – %d occurrences",
                            rank, ef.getMessage(), ef.getCount()));
                    writer.newLine();
                    writer.write("   Service: " + (ef.getService() == null ? "unknown" : ef.getService()));
                    writer.newLine();
                    rank++;
                }
            }
        } catch (IOException e) {
            throw new ReportGenerationException("Failed to write error frequency report", e);
        }
    }

    private void writeDailyStatisticsReport(ReportData data, Path outputDir) throws ReportGenerationException {
        Path file = outputDir.resolve("daily_statistics_report.txt");
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write("========================================");
            writer.newLine();
            writer.write("        DAILY STATISTICS REPORT");
            writer.newLine();
            writer.write("========================================");
            writer.newLine();
            writer.newLine();
            writer.write(String.format("%-12s %-10s %-10s %-10s%n",
                    "DATE", "ERRORS", "WARNINGS", "TOTAL"));
            writer.write(String.format("%-12s %-10s %-10s %-10s%n",
                    "----", "------", "--------", "-----"));
            for (DailyStat stat : data.getDailyStats()) {
                writer.write(String.format("%-12s %-10d %-10d %-10d%n",
                        stat.getDate(), stat.getErrorCount(), stat.getWarnCount(), stat.getTotalCount()));
            }
            writer.newLine();
            DailyStat maxErrorDay = null;
            int maxErrors = 0;
            for (DailyStat stat : data.getDailyStats()) {
                if (stat.getErrorCount() > maxErrors) {
                    maxErrors = stat.getErrorCount();
                    maxErrorDay = stat;
                }
            }
            if (maxErrorDay != null) {
                writer.write("Day with highest error count: " + maxErrorDay.getDate()
                        + " (" + maxErrors + " errors)");
                writer.newLine();
            }
        } catch (IOException e) {
            throw new ReportGenerationException("Failed to write daily statistics report", e);
        }
    }

    private void writeIncidentReport(ReportData data, Path outputDir) throws ReportGenerationException {
        Path file = outputDir.resolve("incident_report.txt");
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write("========================================");
            writer.newLine();
            writer.write("       POTENTIAL INCIDENT REPORT");
            writer.newLine();
            writer.write("========================================");
            writer.newLine();
            writer.newLine();
            List<Incident> incidents = data.getIncidents();
            if (incidents.isEmpty()) {
                writer.write("No potential incidents detected.");
                writer.newLine();
            } else {
                for (Incident incident : incidents) {
                    writer.write(incident.toString());
                    writer.newLine();
                    writer.write("----------------------------------------");
                    writer.newLine();
                }
            }
            writer.newLine();
            writer.write("Note: These are pattern-based potential incidents,");
            writer.newLine();
            writer.write("not confirmed root causes.");
            writer.newLine();
        } catch (IOException e) {
            throw new ReportGenerationException("Failed to write incident report", e);
        }
    }

    private String format(java.time.LocalDateTime dateTime) {
        return dateTime == null ? "unknown" : dateTime.format(TIMESTAMP_FORMAT);
    }

    private double percentage(int part, long total) {
        if (total == 0) return 0.0;
        return (part * 100.0) / total;
    }

    private long totalEntries(Map<LogLevel, Integer> counts) {
        long total = 0;
        for (int count : counts.values()) {
            total += count;
        }
        return total;
    }
}
