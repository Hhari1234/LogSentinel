package com.logsentinel.web;

import com.logsentinel.model.AnalysisSession;
import com.logsentinel.model.ProcessingResult;
import com.logsentinel.model.LogEntry;
import com.logsentinel.model.LogLevel;
import com.logsentinel.model.LogStatistics;
import com.logsentinel.model.ErrorFrequency;
import com.logsentinel.model.DailyStat;
import com.logsentinel.incident.Incident;
import com.logsentinel.incident.IncidentSeverity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DashboardHandler API endpoints using an embedded HttpServer.
 */
class DashboardHandlerTest {

    private com.sun.net.httpserver.HttpServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        DashboardStore store = new DashboardStore();

        // Create a sample analysis session
        LogEntry entry1 = LogEntry.builder()
                .lineNumber(1)
                .rawLine("2026-11-23 10:30:01 ERROR [DatabaseService] Connection timeout")
                .timestamp(LocalDateTime.of(2026, Month.NOVEMBER, 23, 10, 30, 1))
                .level(LogLevel.ERROR)
                .service("DatabaseService")
                .message("Connection timeout")
                .build();
        LogEntry entry2 = LogEntry.builder()
                .lineNumber(2)
                .rawLine("2026-11-23 10:30:05 ERROR [DatabaseService] Connection timeout")
                .timestamp(LocalDateTime.of(2026, Month.NOVEMBER, 23, 10, 30, 5))
                .level(LogLevel.ERROR)
                .service("DatabaseService")
                .message("Connection timeout")
                .build();
        LogEntry entry3 = LogEntry.builder()
                .lineNumber(3)
                .rawLine("2026-11-23 10:31:00 INFO [AuthService] User login successful")
                .timestamp(LocalDateTime.of(2026, Month.NOVEMBER, 23, 10, 31, 0))
                .level(LogLevel.INFO)
                .service("AuthService")
                .message("User login successful")
                .build();

        ProcessingResult result = new ProcessingResult(
                "test.log", List.of(entry1, entry2, entry3),
                3, 3, 0, 100L);

        Map<LogLevel, Integer> levelCounts = Map.of(
                LogLevel.ERROR, 2,
                LogLevel.INFO, 1
        );

        List<ErrorFrequency> errorFreq = List.of(
                new ErrorFrequency("Connection timeout", "Connection timeout", "DatabaseService", 2)
        );

        List<DailyStat> dailyStats = List.of(
                new DailyStat(java.time.LocalDate.of(2026, 11, 23), 2, 0, 3)
        );

        Incident incident = new Incident.Builder()
                .id("INC-0001")
                .service("DatabaseService")
                .pattern("Connection timeout")
                .occurrences(2)
                .firstOccurrence(LocalDateTime.of(2026, Month.NOVEMBER, 23, 10, 30, 1))
                .lastOccurrence(LocalDateTime.of(2026, Month.NOVEMBER, 23, 10, 30, 5))
                .windowMinutes(1)
                .severity(IncidentSeverity.HIGH)
                .potential(true)
                .build();

        AnalysisSession session = new AnalysisSession(
                "test.log", result, List.of(entry1, entry2, entry3),
                new LogStatistics(3, levelCounts, 3, 0),
                errorFreq, dailyStats, List.of(incident),
                LocalDateTime.now()
        );

        store.setSession(session);

        server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(0), 0);
        DashboardHandler handler = new DashboardHandler(store);
        server.createContext("/api/health", handler);
        server.createContext("/api/dashboard", handler);
        server.createContext("/api/logs", handler);
        server.createContext("/api/errors", handler);
        server.createContext("/api/incidents", handler);
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private String get(String path) throws Exception {
        URL url = new URL("http://localhost:" + port + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        int code = conn.getResponseCode();
        String body;
        try (var is = conn.getInputStream()) {
            body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            try (var is = conn.getErrorStream()) {
                body = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        conn.disconnect();
        return body;
    }

    @Test
    void healthEndpointReturnsOk() throws Exception {
        String body = get("/api/health");
        assertTrue(body.contains("\"status\":\"ok\""));
        assertTrue(body.contains("\"hasSession\":true"));
    }

    @Test
    void dashboardEndpointReturnsData() throws Exception {
        String body = get("/api/dashboard");
        assertTrue(body.contains("\"hasSession\":true"));
        assertTrue(body.contains("\"totalLines\":3"));
        assertTrue(body.contains("\"validEntries\":3"));
        assertTrue(body.contains("\"invalidEntries\":0"));
        assertTrue(body.contains("\"ERROR\":2"));
        assertTrue(body.contains("\"INFO\":1"));
        assertTrue(body.contains("\"INC-0001\""));
        assertTrue(body.contains("\"Connection timeout\""));
        assertTrue(body.contains("\"HIGH\""));
        assertTrue(body.contains("\"potential\":true"));
    }

    @Test
    void logsEndpointReturnsEntries() throws Exception {
        String body = get("/api/logs");
        assertTrue(body.contains("\"total\":3"));
        assertTrue(body.contains("\"lineNumber\":1"));
        assertTrue(body.contains("\"ERROR\""));
        assertTrue(body.contains("\"INFO\""));
        assertTrue(body.contains("\"DatabaseService\""));
        assertTrue(body.contains("\"AuthService\""));
    }

    @Test
    void logsEndpointFiltersByLevel() throws Exception {
        String body = get("/api/logs?level=ERROR");
        assertTrue(body.contains("\"total\":2"));
        assertTrue(body.contains("\"DatabaseService\""));
        assertFalse(body.contains("\"AuthService\""));
    }

    @Test
    void errorsEndpointReturnsFrequentErrors() throws Exception {
        String body = get("/api/errors");
        assertTrue(body.contains("\"total\":1"));
        assertTrue(body.contains("\"Connection timeout\""));
        assertTrue(body.contains("\"DatabaseService\""));
    }

    @Test
    void incidentsEndpointReturnsIncidents() throws Exception {
        String body = get("/api/incidents");
        assertTrue(body.contains("\"total\":1"));
        assertTrue(body.contains("\"INC-0001\""));
        assertTrue(body.contains("\"DatabaseService\""));
        assertTrue(body.contains("\"HIGH\""));
    }

    @Test
    void unknownEndpointReturns404() throws Exception {
        URL url = new URL("http://localhost:" + port + "/api/unknown");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        assertEquals(404, conn.getResponseCode());
        conn.disconnect();
    }

    @Test
    void healthEndpointWithNoSession() throws Exception {
        DashboardStore emptyStore = new DashboardStore();
        com.sun.net.httpserver.HttpServer emptyServer =
                com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(0), 0);
        emptyServer.createContext("/api/health", new DashboardHandler(emptyStore));
        emptyServer.start();
        int emptyPort = emptyServer.getAddress().getPort();

        try {
            URL url = new URL("http://localhost:" + emptyPort + "/api/health");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            assertEquals(200, conn.getResponseCode());
            try (var is = conn.getInputStream()) {
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(body.contains("\"hasSession\":false"));
            }
            conn.disconnect();
        } finally {
            emptyServer.stop(0);
        }
    }
}
