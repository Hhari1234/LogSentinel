package com.logsentinel.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Base handler for JSON API responses. Provides helpers for sending JSON,
 * error, and plain-text responses with appropriate HTTP status codes.
 */
public abstract class ApiHandler implements HttpHandler {

    protected void sendJson(HttpExchange exchange, int status, Object body)
            throws IOException {
        String json = JsonUtil.toJson(body);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type",
                "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    protected void sendJson(HttpExchange exchange, int status,
                            Map<String, Object> body) throws IOException {
        sendJson(exchange, status, (Object) body);
    }

    protected void sendError(HttpExchange exchange, int status, String message)
            throws IOException {
        sendJson(exchange, status,
                Map.of("error", status + " " + message));
    }

    protected void sendText(HttpExchange exchange, int status,
                            String contentType, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    protected String readBody(HttpExchange exchange) throws IOException {
        byte[] body = readAllBytes(exchange.getRequestBody());
        return new String(body, StandardCharsets.UTF_8);
    }

    private static byte[] readAllBytes(java.io.InputStream is) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int read;
        while ((read = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, read);
        }
        return buffer.toByteArray();
    }
}
