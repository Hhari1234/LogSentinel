package com.logsentinel.web;

import com.logsentinel.exception.LogFileException;
import com.logsentinel.model.AnalysisSession;
import com.logsentinel.report.CsvReportGenerator;
import com.logsentinel.report.ReportData;
import com.logsentinel.report.ReportGenerationException;
import com.logsentinel.report.TextReportGenerator;
import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Handles GET /api/reports/download?type=txt|csv.
 * Generates a report from the latest analysis session and returns it as a
 * downloadable file. Reuses the existing TextReportGenerator and
 * CsvReportGenerator.
 */
public class ReportDownloadHandler extends ApiHandler {

    private static final String REPORT_DIR = System.getProperty("java.io.tmpdir")
            + "/logsentinel-reports";

    private final DashboardStore store;

    public ReportDownloadHandler(DashboardStore store) {
        this.store = store;
        try {
            Files.createDirectories(Path.of(REPORT_DIR));
        } catch (IOException e) {
            throw new RuntimeException("Cannot create report directory", e);
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        if (!"/api/reports/download".equals(path)) {
            sendError(exchange, 404, "Not found");
            return;
        }

        String type = extractParam(exchange.getRequestURI().getQuery(), "type");
        if (type == null || (!type.equals("txt") && !type.equals("csv"))) {
            sendError(exchange, 400, "Invalid or missing 'type' parameter. Use 'txt' or 'csv'.");
            return;
        }

        AnalysisSession session = store.getSession();
        if (session == null) {
            sendError(exchange, 404, "No analysis session available. Upload a log file first.");
            return;
        }

        try {
            ReportData data = new ReportData(
                    session.getFileName(),
                    session.getAnalysisTimestamp(),
                    session.getProcessingResult().getTotalLines(),
                    session.getProcessingResult().getValidEntries(),
                    session.getProcessingResult().getInvalidEntries(),
                    session.getStatistics().getLevelCounts(),
                    session.getErrorFrequencies(),
                    session.getDailyStats(),
                    session.getIncidents(),
                    session.getProcessingResult().getProcessingTimeMillis()
            );

            String fileName;
            byte[] content;
            String contentType;

            if ("txt".equals(type)) {
                fileName = "logsentinel-report.txt";
                contentType = "text/plain; charset=utf-8";
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                new TextReportGenerator().generate(
                        data, Path.of(REPORT_DIR));
                // Read back the generated file
                Path generated = Path.of(REPORT_DIR, "summary_report.txt");
                content = Files.readAllBytes(generated);
            } else {
                fileName = "logsentinel-report.csv";
                contentType = "text/csv; charset=utf-8";
                new CsvReportGenerator().generate(
                        data, Path.of(REPORT_DIR));
                Path generated = Path.of(REPORT_DIR, "summary_report.csv");
                content = Files.readAllBytes(generated);
            }

            exchange.getResponseHeaders().set(
                    "Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(content);
            }
        } catch (ReportGenerationException e) {
            sendError(exchange, 500, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, "Report generation failed: " + e.getMessage());
        }
    }

    private String extractParam(String query, String name) {
        if (query == null) return null;
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) {
                return kv[1];
            }
        }
        return null;
    }
}
