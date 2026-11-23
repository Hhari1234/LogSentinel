package com.logsentinel.web;

import com.logsentinel.analyzer.DailyStatisticsAnalyzer;
import com.logsentinel.model.AnalysisSession;
import com.logsentinel.model.DailyStat;
import com.logsentinel.model.ErrorFrequency;
import com.logsentinel.incident.Incident;
import com.logsentinel.model.LogEntry;
import com.logsentinel.model.LogLevel;
import com.logsentinel.model.LogStatistics;
import com.logsentinel.processor.LogProcessor;
import com.logsentinel.processor.SequentialLogProcessor;
import com.logsentinel.service.LogAnalysisService;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles dashboard API endpoints:
 * GET /api/health, GET /api/dashboard, GET /api/logs,
 * GET /api/errors, GET /api/incidents
 */
public class DashboardHandler extends ApiHandler {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DashboardStore store;
    private final LogAnalysisService service;

    public DashboardHandler(DashboardStore store) {
        this.store = store;
        this.service = new LogAnalysisService(new SequentialLogProcessor());
    }

    public DashboardStore getStore() {
        return store;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            if ("GET".equals(method) && "/api/health".equals(path)) {
                handleHealth(exchange);
            } else if ("GET".equals(method) && "/api/dashboard".equals(path)) {
                handleDashboard(exchange);
            } else if ("GET".equals(method) && "/api/logs".equals(path)) {
                handleLogs(exchange);
            } else if ("GET".equals(method) && "/api/errors".equals(path)) {
                handleErrors(exchange);
            } else if ("GET".equals(method) && "/api/incidents".equals(path)) {
                handleIncidents(exchange);
            } else {
                sendError(exchange, 404, "Not found");
            }
        } catch (Exception e) {
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, Map.of(
                "status", "ok",
                "service", "LogSentinel",
                "hasSession", store.hasSession()
        ));
    }

    private void handleDashboard(HttpExchange exchange) throws IOException {
        AnalysisSession session = store.getSession();
        if (session == null) {
            sendJson(exchange, 200, Map.of(
                "hasSession", false,
                "message", "No log file has been analyzed yet."
            ));
            return;
        }

        LogStatistics stats = session.getStatistics();
        Map<String, Object> levelCounts = new LinkedHashMap<>();
        for (LogLevel level : LogLevel.values()) {
            levelCounts.put(level.name(), stats.getCount(level));
        }

        List<Map<String, Object>> dailyList = new ArrayList<>();
        for (DailyStat stat : session.getDailyStats()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("date", stat.getDate().toString());
            entry.put("errors", stat.getErrorCount());
            entry.put("warnings", stat.getWarnCount());
            entry.put("total", stat.getTotalCount());
            dailyList.add(entry);
        }

        List<Map<String, Object>> errorList = new ArrayList<>();
        int rank = 1;
        for (ErrorFrequency ef : session.getErrorFrequencies()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("rank", rank++);
            entry.put("message", ef.getMessage());
            entry.put("service", ef.getService() == null ? "unknown" : ef.getService());
            entry.put("count", ef.getCount());
            errorList.add(entry);
        }

        List<Map<String, Object>> incidentList = new ArrayList<>();
        for (Incident inc : session.getIncidents()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", inc.getId());
            entry.put("service", inc.getService() == null ? "unknown" : inc.getService());
            entry.put("pattern", inc.getPattern());
            entry.put("occurrences", inc.getOccurrences());
            entry.put("firstOccurrence",
                    inc.getFirstOccurrence() == null ? "unknown"
                            : inc.getFirstOccurrence().format(TIMESTAMP_FORMAT));
            entry.put("lastOccurrence",
                    inc.getLastOccurrence() == null ? "unknown"
                            : inc.getLastOccurrence().format(TIMESTAMP_FORMAT));
            entry.put("windowMinutes", inc.getWindowMinutes());
            entry.put("severity", inc.getSeverity().name());
            entry.put("potential", inc.isPotential());
            incidentList.add(entry);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("hasSession", true);
        response.put("fileName", session.getFileName());
        response.put("analysisTimestamp",
                session.getAnalysisTimestamp().format(TIMESTAMP_FORMAT));
        response.put("processingMode", "sequential");
        response.put("totalLines", session.getProcessingResult().getTotalLines());
        response.put("validEntries", session.getProcessingResult().getValidEntries());
        response.put("invalidEntries", session.getProcessingResult().getInvalidEntries());
        response.put("processingTimeMillis",
                session.getProcessingResult().getProcessingTimeMillis());
        response.put("levelCounts", levelCounts);
        response.put("errorPercentage", stats.getErrorPercentage());
        response.put("warningPercentage", stats.getWarningPercentage());
        response.put("dailyStats", dailyList);
        response.put("errorFrequencies", errorList);
        response.put("incidents", incidentList);

        sendJson(exchange, 200, response);
    }

    private void handleLogs(HttpExchange exchange) throws IOException {
        AnalysisSession session = store.getSession();
        if (session == null) {
            sendJson(exchange, 200, Map.of("logs", new ArrayList<>(), "total", 0));
            return;
        }

        String levelFilter = exchange.getRequestURI().getQuery() == null
                ? null : extractParam(exchange.getRequestURI().getQuery(), "level");
        String keyword = exchange.getRequestURI().getQuery() == null
                ? null : extractParam(exchange.getRequestURI().getQuery(), "keyword");

        List<Map<String, Object>> result = new ArrayList<>();
        for (LogEntry entry : session.getEntries()) {
            if (levelFilter != null && !levelFilter.isEmpty()) {
                if (!entry.getLevel().name().equalsIgnoreCase(levelFilter)) {
                    continue;
                }
            }
            if (keyword != null && !keyword.isEmpty()) {
                String lower = keyword.toLowerCase();
                String msg = entry.getMessage() == null ? "" : entry.getMessage().toLowerCase();
                String svc = entry.getService() == null ? "" : entry.getService().toLowerCase();
                if (!msg.contains(lower) && !svc.contains(lower)) {
                    continue;
                }
            }
            Map<String, Object> entryMap = new LinkedHashMap<>();
            entryMap.put("lineNumber", entry.getLineNumber());
            entryMap.put("timestamp",
                    entry.getTimestamp() == null ? "unknown"
                            : entry.getTimestamp().format(TIMESTAMP_FORMAT));
            entryMap.put("level", entry.getLevel().name());
            entryMap.put("service", entry.getService() == null ? "-" : entry.getService());
            entryMap.put("thread", entry.getThread() == null ? "-" : entry.getThread());
            entryMap.put("message", entry.getMessage());
            entryMap.put("exceptionName",
                    entry.getExceptionName() == null ? "-" : entry.getExceptionName());
            result.add(entryMap);
        }

        sendJson(exchange, 200, Map.of(
                "logs", result,
                "total", result.size()
        ));
    }

    private void handleErrors(HttpExchange exchange) throws IOException {
        AnalysisSession session = store.getSession();
        if (session == null) {
            sendJson(exchange, 200, Map.of("errors", new ArrayList<>(), "total", 0));
            return;
        }

        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 1;
        for (ErrorFrequency ef : session.getErrorFrequencies()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("rank", rank++);
            entry.put("message", ef.getMessage());
            entry.put("service", ef.getService() == null ? "unknown" : ef.getService());
            entry.put("count", ef.getCount());
            result.add(entry);
        }

        sendJson(exchange, 200, Map.of(
                "errors", result,
                "total", result.size()
        ));
    }

    private void handleIncidents(HttpExchange exchange) throws IOException {
        AnalysisSession session = store.getSession();
        if (session == null) {
            sendJson(exchange, 200, Map.of("incidents", new ArrayList<>(), "total", 0));
            return;
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Incident inc : session.getIncidents()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", inc.getId());
            entry.put("service", inc.getService() == null ? "unknown" : inc.getService());
            entry.put("pattern", inc.getPattern());
            entry.put("occurrences", inc.getOccurrences());
            entry.put("firstOccurrence",
                    inc.getFirstOccurrence() == null ? "unknown"
                            : inc.getFirstOccurrence().format(TIMESTAMP_FORMAT));
            entry.put("lastOccurrence",
                    inc.getLastOccurrence() == null ? "unknown"
                            : inc.getLastOccurrence().format(TIMESTAMP_FORMAT));
            entry.put("windowMinutes", inc.getWindowMinutes());
            entry.put("severity", inc.getSeverity().name());
            entry.put("potential", inc.isPotential());
            result.add(entry);
        }

        sendJson(exchange, 200, Map.of(
                "incidents", result,
                "total", result.size()
        ));
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
