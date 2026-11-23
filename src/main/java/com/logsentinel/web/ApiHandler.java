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
        int length = exchange.getRequestBody().available();
        if (length <= 0) {
            return "";
        }
        byte[] buffer = new byte[length];
        int read = exchange.getRequestBody().read(buffer);
        return new String(buffer, 0, read, StandardCharsets.UTF_8);
    }
}
