package com.logsentinel.web;

import com.logsentinel.exception.LogFileException;
import com.logsentinel.model.AnalysisSession;
import com.logsentinel.model.LogEntry;
import com.logsentinel.processor.LogFileReader;
import com.logsentinel.processor.SequentialLogProcessor;
import com.logsentinel.service.LogAnalysisService;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles POST /api/upload for log file upload and analysis.
 * Supports .log and .txt files with size limits and path traversal protection.
 */
public class FileUploadHandler extends ApiHandler {

    private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024; // 50 MB
    private static final String UPLOAD_DIR = System.getProperty("java.io.tmpdir")
            + "/logsentinel-uploads";

    private final DashboardStore store;

    public FileUploadHandler(DashboardStore store) {
        this.store = store;
        try {
            Files.createDirectories(Path.of(UPLOAD_DIR));
        } catch (IOException e) {
            throw new RuntimeException("Cannot create upload directory", e);
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        if (!"/api/upload".equals(path)) {
            sendError(exchange, 404, "Not found");
            return;
        }

        try {
            Map<String, String> parts = parseMultipart(exchange);
            String filename = parts.get("filename");
            byte[] content = parts.get("content").getBytes(StandardCharsets.UTF_8);

            if (filename == null || filename.isBlank()) {
                sendError(exchange, 400, "No file provided");
                return;
            }

            String lower = filename.toLowerCase();
            if (!lower.endsWith(".log") && !lower.endsWith(".txt")) {
                sendError(exchange, 400,
                        "Unsupported file extension. Only .log and .txt files are accepted.");
                return;
            }

            if (content.length > MAX_FILE_SIZE_BYTES) {
                sendError(exchange, 413, "File too large. Maximum size is 50 MB.");
                return;
            }

            // Sanitize filename to prevent path traversal
            String safeName = Paths.get(filename).getFileName().toString();
            Path tempFile = Path.of(UPLOAD_DIR, UUID.randomUUID() + "_" + safeName);
            Files.write(tempFile, content);

            try {
                LogAnalysisService service = new LogAnalysisService(
                        new SequentialLogProcessor());
                AnalysisSession session = service.analyze(tempFile.toString());
                store.setSession(session);

                List<LogEntry> entries = session.getEntries();
                sendJson(exchange, 200, Map.of(
                        "status", "success",
                        "fileName", session.getFileName(),
                        "totalLines", session.getProcessingResult().getTotalLines(),
                        "validEntries", session.getProcessingResult().getValidEntries(),
                        "invalidEntries", session.getProcessingResult().getInvalidEntries(),
                        "processingTimeMillis",
                                session.getProcessingResult().getProcessingTimeMillis(),
                        "errorCount", session.getStatistics().getCount(
                                com.logsentinel.model.LogLevel.ERROR),
                        "warningCount", session.getStatistics().getCount(
                                com.logsentinel.model.LogLevel.WARN),
                        "incidentCount", session.getIncidents().size(),
                        "entryCount", entries.size()
                ));
            } finally {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        } catch (LogFileException e) {
            sendError(exchange, 500, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, "Upload failed: " + e.getMessage());
        }
    }

    /**
     * Parses multipart/form-data request body. Simple implementation that
     * extracts the file field named "file".
     */
    private Map<String, String> parseMultipart(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.startsWith("multipart/form-data")) {
            throw new IOException("Expected multipart/form-data request");
        }

        String boundary = extractBoundary(contentType);
        if (boundary == null) {
            throw new IOException("Missing multipart boundary");
        }

        byte[] body = readAllBytes(exchange.getRequestBody());
        String bodyStr = new String(body, StandardCharsets.UTF_8);

        String resultFilename = null;
        String resultContent = null;

        String[] parts = bodyStr.split("--" + boundary);
        for (String part : parts) {
            if (part.trim().isEmpty() || part.trim().equals("--")) {
                continue;
            }
            int headerEnd = part.indexOf("\r\n\r\n");
            if (headerEnd < 0) {
                continue;
            }
            String headers = part.substring(0, headerEnd);
            String content = part.substring(headerEnd + 4);
            // Trim trailing \r\n
            if (content.endsWith("\r\n")) {
                content = content.substring(0, content.length() - 2);
            }

            String filename = extractFilename(headers);
            if (filename != null) {
                resultFilename = filename;
                resultContent = content;
            }
        }

        return Map.of("filename", resultFilename, "content", resultContent);
    }

    private String extractBoundary(String contentType) {
        for (String part : contentType.split(";")) {
            part = part.trim();
            if (part.startsWith("boundary=")) {
                return part.substring("boundary=".length()).trim();
            }
        }
        return null;
    }

    private String extractFilename(String headers) {
        for (String line : headers.split("\r\n")) {
            line = line.trim();
            if (line.toLowerCase().startsWith("content-disposition")) {
                for (String token : line.split(";")) {
                    token = token.trim();
                    if (token.toLowerCase().startsWith("filename=")) {
                        String value = token.substring("filename=".length()).trim();
                        if (value.startsWith("\"") && value.endsWith("\"")) {
                            value = value.substring(1, value.length() - 1);
                        }
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private byte[] readAllBytes(java.io.InputStream is) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int read;
        while ((read = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, read);
        }
        return buffer.toByteArray();
    }
}
