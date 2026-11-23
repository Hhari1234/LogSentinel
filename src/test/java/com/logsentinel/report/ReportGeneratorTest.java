package com.logsentinel.report;

import com.logsentinel.model.DailyStat;
import com.logsentinel.model.ErrorFrequency;
import com.logsentinel.model.LogEntry;
import com.logsentinel.model.LogLevel;
import com.logsentinel.incident.Incident;
import com.logsentinel.incident.IncidentSeverity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReportGeneratorTest {

    @TempDir
    Path outputDir;

    private ReportData sampleData() {
        Map<LogLevel, Integer> levelCounts = Map.of(
                LogLevel.INFO, 6000,
                LogLevel.DEBUG, 1500,
                LogLevel.WARN, 1200,
                LogLevel.ERROR, 1100,
                LogLevel.FATAL, 100,
                LogLevel.TRACE, 50,
                LogLevel.UNKNOWN, 50
        );
        List<ErrorFrequency> errors = List.of(
                new ErrorFrequency("Connection timeout", "Connection timeout", "DatabaseService", 150),
                new ErrorFrequency("Authentication failed", "Authentication failed", "AuthService", 95)
        );
        List<DailyStat> daily = List.of(
                new DailyStat(LocalDate.of(2026, 11, 23), 100, 50, 500),
                new DailyStat(LocalDate.of(2026, 11, 24), 200, 30, 600)
        );
        List<Incident> incidents = List.of(
                new Incident.Builder()
                        .id("INC-0001")
                        .service("DatabaseService")
                        .pattern("Connection timeout")
                        .occurrences(12)
                        .firstOccurrence(LocalDateTime.of(2026, 11, 23, 10, 30, 1))
                        .lastOccurrence(LocalDateTime.of(2026, 11, 23, 10, 30, 45))
                        .windowMinutes(1)
                        .severity(IncidentSeverity.HIGH)
                        .potential(true)
                        .build()
        );

        return new ReportData(
                "application.log",
                LocalDateTime.of(2026, 11, 23, 11, 0, 0),
                10000, 9950, 50,
                levelCounts,
                errors,
                daily,
                incidents,
                250
        );
    }

    @Test
    void textGeneratorCreatesAllReports() throws Exception {
        ReportGenerator generator = new TextReportGenerator();
        generator.generate(sampleData(), outputDir);

        assertTrue(Files.exists(outputDir.resolve("summary_report.txt")));
        assertTrue(Files.exists(outputDir.resolve("error_frequency_report.txt")));
        assertTrue(Files.exists(outputDir.resolve("daily_statistics_report.txt")));
        assertTrue(Files.exists(outputDir.resolve("incident_report.txt")));

        String summary = Files.readString(outputDir.resolve("summary_report.txt"));
        assertTrue(summary.contains("LOGSENTINEL SUMMARY REPORT"));
        assertTrue(summary.contains("application.log"));
        assertTrue(summary.contains("10000"));
        assertTrue(summary.contains("INFO"));
        assertTrue(summary.contains("6000"));
    }

    @Test
    void textIncidentReportLabelsAsPotential() throws Exception {
        new TextReportGenerator().generate(sampleData(), outputDir);
        String incident = Files.readString(outputDir.resolve("incident_report.txt"));
        assertTrue(incident.contains("POTENTIAL INCIDENT"));
        assertTrue(incident.contains("INC-0001"));
        assertTrue(incident.contains("Connection timeout"));
        assertTrue(incident.contains("not confirmed root causes"));
    }

    @Test
    void csvGeneratorCreatesAllReports() throws Exception {
        ReportGenerator generator = new CsvReportGenerator();
        generator.generate(sampleData(), outputDir);

        assertTrue(Files.exists(outputDir.resolve("summary_report.csv")));
        assertTrue(Files.exists(outputDir.resolve("error_frequency_report.csv")));
        assertTrue(Files.exists(outputDir.resolve("daily_statistics_report.csv")));
        assertTrue(Files.exists(outputDir.resolve("incident_report.csv")));

        String csv = Files.readString(outputDir.resolve("error_frequency_report.csv"));
        assertTrue(csv.contains("Connection timeout,DatabaseService,150"));
    }

    @Test
    void csvEscapesCommasAndQuotes() throws Exception {
        ReportData data = new ReportData(
                "file,with,commas.log",
                LocalDateTime.now(),
                10, 10, 0,
                Map.of(LogLevel.INFO, 10),
                List.of(new ErrorFrequency('"' + "msg with \"quote\"" + '"', "msg", "Svc", 1)),
                List.of(),
                List.of(),
                5
        );
        new CsvReportGenerator().generate(data, outputDir);
        String csv = Files.readString(outputDir.resolve("summary_report.csv"));
        assertTrue(csv.contains("\"file,with,commas.log\""));
    }

    @Test
    void rejectsMissingOutputDirectory() {
        ReportGenerator generator = new TextReportGenerator();
        assertThrows(ReportGenerationException.class,
                () -> generator.generate(sampleData(), outputDir.resolve("missing")));
    }

    @Test
    void csvEscapesUnicodeAndNewlines() throws Exception {
        ReportData data = new ReportData(
                "unicode-测试.log",
                LocalDateTime.now(),
                10, 10, 0,
                Map.of(LogLevel.INFO, 10),
                List.of(new ErrorFrequency("msg with newline\nand tab\there", "msg", "Svc", 1)),
                List.of(),
                List.of(),
                5
        );
        new CsvReportGenerator().generate(data, outputDir);
        String csv = Files.readString(outputDir.resolve("error_frequency_report.csv"));
        // Newlines and commas should be quoted
        assertTrue(csv.contains("newline"));
    }

    @Test
    void textReportWithEmptyIncidents() throws Exception {
        ReportData data = new ReportData(
                "clean.log",
                LocalDateTime.now(),
                100, 100, 0,
                Map.of(LogLevel.INFO, 100),
                List.of(),
                List.of(),
                List.of(),
                10
        );
        new TextReportGenerator().generate(data, outputDir);
        String incident = Files.readString(outputDir.resolve("incident_report.txt"));
        assertTrue(incident.contains("No potential incidents detected."));
    }

    @Test
    void textReportWithEmptyErrors() throws Exception {
        ReportData data = new ReportData(
                "clean.log",
                LocalDateTime.now(),
                100, 100, 0,
                Map.of(LogLevel.INFO, 100),
                List.of(),
                List.of(),
                List.of(),
                10
        );
        new TextReportGenerator().generate(data, outputDir);
        String errorFreq = Files.readString(outputDir.resolve("error_frequency_report.txt"));
        assertTrue(errorFreq.contains("No errors found."));
    }

    @Test
    void csvReportWithEmptyDailyStats() throws Exception {
        ReportData data = new ReportData(
                "empty.log",
                LocalDateTime.now(),
                0, 0, 0,
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                5
        );
        new CsvReportGenerator().generate(data, outputDir);
        String daily = Files.readString(outputDir.resolve("daily_statistics_report.csv"));
        assertTrue(daily.contains("Date,Errors,Warnings,Total"));
    }
}
