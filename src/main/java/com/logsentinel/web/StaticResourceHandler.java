package com.logsentinel.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serves static frontend assets (HTML, CSS, JS) from the classpath resources
 * directory under /static/.
 */
public class StaticResourceHandler implements HttpHandler {

    private static final String RESOURCE_ROOT = "/static/";
    private static final String INDEX_FILE = "index.html";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        if (path == null || path.equals("/") || path.isEmpty()) {
            path = "/" + INDEX_FILE;
        }

        String resourcePath = RESOURCE_ROOT + path.replaceFirst("^/", "");

        // Try the requested resource first, then fall back to index.html for SPA routing
        java.io.InputStream is = StaticResourceHandler.class.getResourceAsStream(resourcePath);
        if (is == null) {
            is = StaticResourceHandler.class.getResourceAsStream(RESOURCE_ROOT + INDEX_FILE);
            if (is == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        } else {
            exchange.getResponseHeaders().set("Content-Type", probeContentType(resourcePath));
        }

        byte[] bytes = is.readAllBytes();
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String probeContentType(String path) {
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }
}
