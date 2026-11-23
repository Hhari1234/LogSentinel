package com.logsentinel.benchmark;

import com.logsentinel.exception.LogFileException;
import com.logsentinel.model.LogEntry;
import com.logsentinel.model.ProcessingResult;
import com.logsentinel.processor.LogProcessor;
import com.logsentinel.processor.ParallelLogProcessor;
import com.logsentinel.processor.SequentialLogProcessor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Measures sequential and parallel log processing performance on files of
 * different sizes. Results are actual measured values; no numbers are
 * hardcoded or invented.
 */
public final class PerformanceBenchmark {

    private static final int[] SIZES_MB = {1, 10, 50, 100};
    private static final int ITERATIONS = 3;
    private static final int WARMUP = 1;
    private static final long SEED = 42;

    public static void main(String[] args) throws Exception {
        File benchmarkDir = new File("benchmarks");
        benchmarkDir.mkdirs();

        System.out.println("========================================");
        System.out.println("   LOGSENTINEL PERFORMANCE BENCHMARK");
        System.out.println("========================================");
        System.out.println();

        List<BenchmarkResult> results = new ArrayList<>();

        for (int sizeMB : SIZES_MB) {
            File file = new File(benchmarkDir, "sample_" + sizeMB + "mb.log");
            if (!file.exists()) {
                System.out.println("Generating " + sizeMB + " MB sample log file...");
                long lines = LogGenerator.generate(file, sizeMB, SEED);
                System.out.println("Generated " + lines + " lines (~" + sizeMB + " MB)");
            }

            BenchmarkResult result = benchmarkFile(file, sizeMB);
            results.add(result);
            System.out.println();
        }

        printSummary(results);
    }

    private static BenchmarkResult benchmarkFile(File file, int sizeMB) throws Exception {
        System.out.println("Benchmarking ~" + sizeMB + " MB file: " + file.getName());

        // Warm-up
        for (int i = 0; i < WARMUP; i++) {
            new SequentialLogProcessor().process(file.getAbsolutePath());
            new ParallelLogProcessor(4).process(file.getAbsolutePath());
        }

        // Sequential
        double seqTotalNanos = 0;
        ProcessingResult seqResult = null;
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            seqResult = new SequentialLogProcessor().process(file.getAbsolutePath());
            long end = System.nanoTime();
            seqTotalNanos += (end - start);
        }
        double seqAvgMillis = (seqTotalNanos / ITERATIONS) / 1_000_000.0;

        // Parallel
        double parTotalNanos = 0;
        ProcessingResult parResult = null;
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            parResult = new ParallelLogProcessor(4).process(file.getAbsolutePath());
            long end = System.nanoTime();
            parTotalNanos += (end - start);
        }
        double parAvgMillis = (parTotalNanos / ITERATIONS) / 1_000_000.0;

        // Verify results match
        boolean countsMatch = seqResult.getTotalLines() == parResult.getTotalLines()
                && seqResult.getValidEntries() == parResult.getValidEntries()
                && seqResult.getInvalidEntries() == parResult.getInvalidEntries();

        double seqLinesPerSec = seqResult.getTotalLines() / (seqAvgMillis / 1000.0);
        double parLinesPerSec = parResult.getTotalLines() / (parAvgMillis / 1000.0);

        System.out.printf("  Records: %,d  (valid=%,d invalid=%,d)%n",
                seqResult.getTotalLines(), seqResult.getValidEntries(), seqResult.getInvalidEntries());
        System.out.printf("  Sequential:   %.2f ms  (%,.0f lines/sec)%n", seqAvgMillis, seqLinesPerSec);
        System.out.printf("  Parallel (4t): %.2f ms  (%,.0f lines/sec)%n", parAvgMillis, parLinesPerSec);
        System.out.printf("  Results match: %s%n", countsMatch);
        System.out.printf("  Speedup: %.2fx%n", seqAvgMillis / parAvgMillis);

        return new BenchmarkResult(sizeMB, seqResult.getTotalLines(),
                seqAvgMillis, parAvgMillis, seqLinesPerSec, parLinesPerSec, countsMatch);
    }

    private static void printSummary(List<BenchmarkResult> results) {
        System.out.println("========================================");
        System.out.println("              SUMMARY");
        System.out.println("========================================");
        System.out.printf("%-10s %-12s %-14s %-14s %-14s %-10s%n",
                "Size (MB)", "Records", "Seq (ms)", "Par (ms)", "Speedup", "Match");
        for (BenchmarkResult r : results) {
            System.out.printf("%-10d %-12d %-14.2f %-14.2f %-14.2f %-10s%n",
                    r.sizeMB, r.recordCount, r.seqAvgMillis, r.parAvgMillis,
                    r.seqAvgMillis / Math.max(r.parAvgMillis, 0.001), r.countsMatch);
        }
        System.out.println();
        System.out.println("Note: Actual measured results. Throughput depends on CPU, disk,");
        System.out.println("memory, and operating system. Parallel is not guaranteed to be");
        System.out.println("faster for small files due to thread coordination overhead.");
    }

    /**
     * Result of benchmarking a single file.
     */
    public static final class BenchmarkResult {
        final int sizeMB;
        final long recordCount;
        final double seqAvgMillis;
        final double parAvgMillis;
        final double seqLinesPerSec;
        final double parLinesPerSec;
        final boolean countsMatch;

        BenchmarkResult(int sizeMB, long recordCount, double seqAvgMillis,
                        double parAvgMillis, double seqLinesPerSec,
                        double parLinesPerSec, boolean countsMatch) {
            this.sizeMB = sizeMB;
            this.recordCount = recordCount;
            this.seqAvgMillis = seqAvgMillis;
            this.parAvgMillis = parAvgMillis;
            this.seqLinesPerSec = seqLinesPerSec;
            this.parLinesPerSec = parLinesPerSec;
            this.countsMatch = countsMatch;
        }
    }
}
