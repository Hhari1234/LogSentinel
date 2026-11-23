package com.logsentinel.web;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
    private volatile boolean running = false;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    private DashboardServer(int port) throws IOException {
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        configureRoutes();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Received shutdown signal, stopping LogSentinel dashboard...");
            stop();
            shutdownLatch.countDown();
        }));
    }

    public static DashboardServer create(int port) throws IOException {
        return new DashboardServer(port);
    }

    public static void main(String[] args) throws Exception {
        int port = DEFAULT_PORT;
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            try {
                port = Integer.parseInt(envPort.trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid PORT env var: " + envPort + ", using default " + DEFAULT_PORT);
            }
        }
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--port") && i + 1 < args.length) {
                try {
                    port = Integer.parseInt(args[i + 1]);
                    i++;
                } catch (NumberFormatException e) {
                    System.err.println("Invalid port: " + args[i + 1]);
                    System.exit(1);
                }
            }
        }
        DashboardServer ds = new DashboardServer(port);
        ds.start();
        ds.awaitShutdown();
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
        running = true;
        System.out.println("========================================");
        System.out.println("       LOGSENTINEL DASHBOARD");
        System.out.println("========================================");
        System.out.println("Server status: RUNNING");
        System.out.println("Bind address: 0.0.0.0");
        System.out.println("Port: " + port);
        System.out.println("Dashboard: http://0.0.0.0:" + port);
        System.out.println("API Health: http://0.0.0.0:" + port + "/api/health");
        System.out.println();
        System.out.println("Press Ctrl+C to stop the server.");
        System.out.println("========================================");
    }

    public void stop() {
        if (running) {
            server.stop(5);
            running = false;
            System.out.println("LogSentinel dashboard stopped.");
        }
    }

    public void awaitShutdown() throws InterruptedException {
        shutdownLatch.await();
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }
}
