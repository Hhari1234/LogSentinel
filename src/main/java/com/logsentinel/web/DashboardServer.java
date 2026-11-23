package com.logsentinel.web;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Starts the LogSentinel web dashboard server using the built-in
 * {@link com.sun.net.httpserver.HttpServer}. No external web framework is used.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>GET /api/health — service health</li>
 *   <li>GET /api/dashboard — full dashboard data</li>
 *   <li>GET /api/logs — log entries with optional level/keyword filters</li>
 *   <li>GET /api/errors — frequent error list</li>
 *   <li>GET /api/incidents — detected incidents</li>
 *   <li>POST /api/upload — upload and analyze a log file</li>
 *   <li>GET /api/reports/download?type=txt|csv — download report</li>
 *   <li>GET / — serves the frontend dashboard</li>
 * </ul>
 */
public final class DashboardServer {

    private static final int DEFAULT_PORT = 8080;

    private final HttpServer server;
    private final int port;

    private DashboardServer(int port) throws IOException {
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        configureRoutes();
    }

    public static DashboardServer create(int port) throws IOException {
        return new DashboardServer(port);
    }

    public static void main(String[] args) throws Exception {
        int port = DEFAULT_PORT;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--port") && i + 1 < args.length) {
                port = Integer.parseInt(args[i + 1]);
            }
        }
        DashboardServer ds = new DashboardServer(port);
        ds.start();
    }

    private void configureRoutes() {
        DashboardStore store = new DashboardStore();

        server.createContext("/api/health", new DashboardHandler(store));
        server.createContext("/api/dashboard", new DashboardHandler(store));
        server.createContext("/api/logs", new DashboardHandler(store));
        server.createContext("/api/errors", new DashboardHandler(store));
        server.createContext("/api/incidents", new DashboardHandler(store));
        server.createContext("/api/upload", new FileUploadHandler(store));
        server.createContext("/api/reports/download", new ReportDownloadHandler(store));
        server.createContext("/", new StaticResourceHandler());

        server.setExecutor(null); // uses default executor
    }

    public void start() {
        server.start();
        System.out.println("========================================");
        System.out.println("       LOGSENTINEL WEB DASHBOARD");
        System.out.println("========================================");
        System.out.println("Dashboard URL: http://localhost:" + port);
        System.out.println("Press Ctrl+C to stop.");
        System.out.println("========================================");
    }

    public void stop() {
        server.stop(0);
        System.out.println("LogSentinel dashboard stopped.");
    }

    public int getPort() {
        return port;
    }
}
