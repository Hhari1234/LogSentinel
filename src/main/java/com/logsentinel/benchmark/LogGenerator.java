package com.logsentinel.benchmark;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Random;

/**
 * Generates deterministic sample log files for performance benchmarking.
 *
 * <p>Uses a fixed seed so the same input is produced every time, which makes
 * sequential and parallel benchmark results comparable.</p>
 */
public final class LogGenerator {

    private static final String[] SERVICES = {
            "AuthService", "PaymentService", "DatabaseService", "OrderService",
            "InventoryService", "NotificationService", "UserService", "CartService"
    };

    private static final String[] MESSAGES = {
            "User login successful",
            "Payment response delayed",
            "Connection timeout",
            "Order created",
            "Payment processed",
            "Database query slow",
            "Cache miss",
            "Token expired",
            "Null pointer in handler",
            "File not found",
            "Network unreachable",
            "Invalid request payload",
            "Session expired",
            "Email sent successfully",
            "Report generated",
            "Disk usage high",
            "Memory usage high",
            "Connection refused",
            "Permission denied",
            "Data synchronization completed"
    };

    private LogGenerator() {
    }

    /**
     * Generates a log file of approximately the given size in MB.
     *
     * @param file        the file to create
     * @param sizeMB      target size in megabytes
     * @param seed        fixed seed for deterministic output
     * @return the number of lines written
     * @throws IOException if the file cannot be written
     */
    public static long generate(File file, int sizeMB, long seed) throws IOException {
        file.getParentFile().mkdirs();
        Random random = new Random(seed);
        long targetBytes = (long) sizeMB * 1024 * 1024;
        long bytesWritten = 0;
        long lineCount = 0;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            while (bytesWritten < targetBytes) {
                long ts = 1763884800000L + random.nextInt(28800000); // 2026-11-23 08:00 + up to 8h
                String timestamp = formatTimestamp(ts);
                String level = pickLevel(random);
                String service = SERVICES[random.nextInt(SERVICES.length)];
                String message = MESSAGES[random.nextInt(MESSAGES.length)];

                String line;
                if ("ERROR".equals(level)) {
                    line = timestamp + " ERROR [" + service + "] " + message;
                } else if ("WARN".equals(level)) {
                    line = timestamp + " WARN [" + service + "] " + message;
                } else {
                    line = timestamp + " " + level + " [" + service + "] " + message;
                }

                writer.write(line);
                writer.newLine();
                bytesWritten += line.getBytes().length + 2; // line + newline
                lineCount++;
            }
        }
        return lineCount;
    }

    private static String pickLevel(Random random) {
        int r = random.nextInt(100);
        if (r < 60) return "INFO";
        if (r < 75) return "WARN";
        if (r < 90) return "ERROR";
        if (r < 95) return "DEBUG";
        if (r < 98) return "TRACE";
        return "FATAL";
    }

    private static String formatTimestamp(long millis) {
        // Approximate: 2026-11-23 HH:mm:ss
        long totalSeconds = millis / 1000;
        long hour = 8 + (totalSeconds % 28800) / 3600;
        long minute = (totalSeconds % 3600) / 60;
        long second = totalSeconds % 60;
        return String.format("2026-11-23 %02d:%02d:%02d", hour, minute, second);
    }

    public static long sizeInMB(File file) {
        return file.length() / (1024 * 1024);
    }
}
