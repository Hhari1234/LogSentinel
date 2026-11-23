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
 * Generates comma-separated values (CSV) reports.
 *
 * <p>Field values containing commas, double quotes, or line breaks are
 * escaped by wrapping the field in double quotes and doubling any embedded
 * double quotes.</p>
 */
public final class CsvReportGenerator implements ReportGenerator {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void generate(ReportData data, Path outputDir) throws ReportGenerationException {
        if (!Files.isDirectory(outputDir)) {
            throw new ReportGenerationException("Output directory does not exist: " + outputDir);
        }

        writeSummaryCsv(data, outputDir);
        writeErrorFrequencyCsv(data, outputDir);
        writeDailyStatisticsCsv(data, outputDir);
        writeIncidentCsv(data, outputDir);
    }

    private void writeSummaryCsv(ReportData data, Path outputDir) throws ReportGenerationException {
        Path file = outputDir.resolve("summary_report.csv");
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write("Metric,Value");
            writer.newLine();
            writer.write(escape("Input file") + "," + escape(data.getFileName()));
            writer.newLine();
            writer.write(escape("Processing date") + "," + escape(format(data.getProcessingTimestamp())));
            writer.newLine();
            writer.write(escape("Total lines") + "," + data.getTotalLines());
            writer.newLine();
            writer.write(escape("Valid entries") + "," + data.getValidEntries());
            writer.newLine();
            writer.write(escape("Invalid entries") + "," + data.getInvalidEntries());
            writer.newLine();
            writer.write(escape("Processing duration (ms)") + "," + data.getProcessingDurationMillis());
            writer.newLine();
            writer.newLine();
            writer.write("Log Level,Count");
            writer.newLine();
            long total = data.getValidEntries();
            for (LogLevel level : LogLevel.values()) {
                Integer count = data.getLevelCounts().getOrDefault(level, 0);
                writer.write(escape(level.name()) + "," + count);
                writer.newLine();
            }
            writer.write(escape("Error percentage") + ","
                    + String.format("%.2f", percentage(data.getLevelCounts().getOrDefault(LogLevel.ERROR, 0), total)));
            writer.newLine();
            writer.write(escape("Warning percentage") + ","
                    + String.format("%.2f", percentage(data.getLevelCounts().getOrDefault(LogLevel.WARN, 0), total)));
            writer.newLine();
        } catch (IOException e) {
            throw new ReportGenerationException("Failed to write summary CSV report", e);
        }
    }

    private void writeErrorFrequencyCsv(ReportData data, Path outputDir) throws ReportGenerationException {
        Path file = outputDir.resolve("error_frequency_report.csv");
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write("Rank,Error Message,Service,Occurrences");
            writer.newLine();
            List<ErrorFrequency> errors = data.getErrorFrequencies();
            int rank = 1;
            for (ErrorFrequency ef : errors) {
                writer.write(rank + "," + escape(ef.getMessage()) + ","
                        + escape(ef.getService() == null ? "unknown" : ef.getService()) + "," + ef.getCount());
                writer.newLine();
                rank++;
            }
        } catch (IOException e) {
            throw new ReportGenerationException("Failed to write error frequency CSV report", e);
        }
    }

    private void writeDailyStatisticsCsv(ReportData data, Path outputDir) throws ReportGenerationException {
        Path file = outputDir.resolve("daily_statistics_report.csv");
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write("Date,Errors,Warnings,Total");
            writer.newLine();
            for (DailyStat stat : data.getDailyStats()) {
                writer.write(escape(stat.getDate().toString()) + "," + stat.getErrorCount() + ","
                        + stat.getWarnCount() + "," + stat.getTotalCount());
                writer.newLine();
            }
            writer.newLine();
            writer.write("Metric,Value");
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
                writer.write(escape("Day with highest error count") + ","
                        + escape(maxErrorDay.getDate() + " (" + maxErrors + " errors)"));
                writer.newLine();
            }
        } catch (IOException e) {
            throw new ReportGenerationException("Failed to write daily statistics CSV report", e);
        }
    }

    private void writeIncidentCsv(ReportData data, Path outputDir) throws ReportGenerationException {
        Path file = outputDir.resolve("incident_report.csv");
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write("Incident ID,Service,Pattern,Occurrences,First Occurrence,Last Occurrence,Window (minutes),Severity,Type");
            writer.newLine();
            List<Incident> incidents = data.getIncidents();
            for (Incident incident : incidents) {
                writer.write(escape(incident.getId()) + ","
                        + escape(incident.getService() == null ? "unknown" : incident.getService()) + ","
                        + escape(incident.getPattern()) + "," + incident.getOccurrences() + ","
                        + escape(format(incident.getFirstOccurrence())) + ","
                        + escape(format(incident.getLastOccurrence())) + ","
                        + incident.getWindowMinutes() + ","
                        + escape(incident.getSeverity().name()) + ","
                        + escape(incident.isPotential() ? "POTENTIAL INCIDENT" : "PATTERN"));
                writer.newLine();
            }
            writer.newLine();
            writer.write("Note,These are pattern-based potential incidents, not confirmed root causes.");
            writer.newLine();
        } catch (IOException e) {
            throw new ReportGenerationException("Failed to write incident CSV report", e);
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n")
                || value.contains("\r");
        if (!needsQuotes) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String format(java.time.LocalDateTime dateTime) {
        return dateTime == null ? "unknown" : dateTime.format(TIMESTAMP_FORMAT);
    }

    private double percentage(int part, long total) {
        if (total == 0) return 0.0;
        return (part * 100.0) / total;
    }
}
