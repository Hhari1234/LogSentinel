package com.logsentinel.processor;

import com.logsentinel.exception.LogFileException;
import com.logsentinel.model.LogEntry;
import com.logsentinel.model.ProcessingResult;
import com.logsentinel.parser.LogParser;
import com.logsentinel.parser.ParseResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Parallel implementation of {@link LogProcessor}.
 *
 * <p>The file is streamed line by line (never loaded entirely into memory).
 * Lines are grouped into bounded batches and each batch is parsed by a worker
 * task submitted to an {@link ExecutorService}. Results are aggregated after
 * all workers finish, so there is no shared mutable state during processing
 * and no race conditions.</p>
 *
 * <p>Line numbers are preserved by passing the starting line number of each
 * batch to its worker task.</p>
 */
public final class ParallelLogProcessor implements LogProcessor {

    private static final int DEFAULT_BATCH_SIZE = 1000;

    private final int threadCount;
    private final int batchSize;

    public ParallelLogProcessor(int threadCount) {
        this(threadCount, DEFAULT_BATCH_SIZE);
    }

    public ParallelLogProcessor(int threadCount, int batchSize) {
        if (threadCount < 1) {
            throw new IllegalArgumentException("Thread count must be at least 1");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("Batch size must be at least 1");
        }
        this.threadCount = threadCount;
        this.batchSize = batchSize;
    }

    public int getThreadCount() {
        return threadCount;
    }

    @Override
    public ProcessingResult process(String filePath) throws LogFileException {
        if (filePath == null || filePath.isBlank()) {
            throw new LogFileException("File path must not be null or blank");
        }

        Path path = Paths.get(filePath);
        validateFile(path);

        List<Future<BatchResult>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        long totalLines = 0;
        long start = System.nanoTime();

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            List<String> batch = new ArrayList<>(batchSize);
            int batchStartLine = 1;
            String line;
            while ((line = reader.readLine()) != null) {
                batch.add(line);
                if (batch.size() == batchSize) {
                    int startLine = batchStartLine;
                    List<String> batchCopy = new ArrayList<>(batch);
                    futures.add(executor.submit(new ParseBatchTask(batchCopy, startLine)));
                    batch.clear();
                    batchStartLine += batchSize;
                    totalLines += batchSize;
                }
            }
            // Submit the remaining partial batch
            if (!batch.isEmpty()) {
                int startLine = batchStartLine;
                List<String> batchCopy = new ArrayList<>(batch);
                futures.add(executor.submit(new ParseBatchTask(batchCopy, startLine)));
                totalLines += batch.size();
            }
        } catch (IOException e) {
            executor.shutdownNow();
            throw new LogFileException("Failed to read file: " + filePath, e);
        }

        // Shut down and aggregate results
        List<LogEntry> entries = new ArrayList<>();
        int validEntries = 0;
        int invalidEntries = 0;

        try {
            executor.shutdown();
            if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            throw new LogFileException("Processing was interrupted: " + filePath, e);
        }

        for (Future<BatchResult> future : futures) {
            try {
                BatchResult result = future.get();
                entries.addAll(result.entries);
                validEntries += result.entryCount;
                invalidEntries += result.invalidCount;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LogFileException("Processing was interrupted while aggregating results", e);
            } catch (ExecutionException e) {
                throw new LogFileException("A worker task failed: " + e.getCause().getMessage(), e.getCause());
            }
        }

        long processingTimeMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        return new ProcessingResult(
                path.getFileName().toString(),
                entries,
                totalLines,
                validEntries,
                invalidEntries,
                processingTimeMillis
        );
    }

    private void validateFile(Path path) throws LogFileException {
        String name = path.getFileName().toString();
        if (!name.endsWith(".log") && !name.endsWith(".txt")) {
            throw new LogFileException("Unsupported file extension: " + name);
        }
        if (!Files.exists(path)) {
            throw new LogFileException("File not found: " + path.toAbsolutePath());
        }
        if (!Files.isReadable(path)) {
            throw new LogFileException("File is not readable: " + path.toAbsolutePath());
        }
    }

    /**
     * Parses a bounded batch of lines. Each task owns its data, so no
     * synchronization is needed between workers.
     */
    private static final class ParseBatchTask implements Callable<BatchResult> {
        private final List<String> lines;
        private final int startLineNumber;

        ParseBatchTask(List<String> lines, int startLineNumber) {
            this.lines = lines;
            this.startLineNumber = startLineNumber;
        }

        @Override
        public BatchResult call() {
            List<LogEntry> entries = new ArrayList<>();
            int invalidCount = 0;
            int lineNumber = startLineNumber;
            for (String line : lines) {
                ParseResult result = LogParser.parse(line, lineNumber);
                if (result.isSuccess()) {
                    entries.add(result.getEntry());
                } else {
                    invalidCount++;
                }
                lineNumber++;
            }
            return new BatchResult(entries, entries.size(), invalidCount);
        }
    }

    /**
     * Result returned by a single batch-parsing task.
     */
    private static final class BatchResult {
        final List<LogEntry> entries;
        final int entryCount;
        final int invalidCount;

        BatchResult(List<LogEntry> entries, int entryCount, int invalidCount) {
            this.entries = entries;
            this.entryCount = entryCount;
            this.invalidCount = invalidCount;
        }
    }
}
