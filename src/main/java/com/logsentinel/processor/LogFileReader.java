package com.logsentinel.processor;

import com.logsentinel.exception.LogFileException;
import com.logsentinel.model.FileProcessingResult;
import com.logsentinel.model.LogEntry;
import com.logsentinel.parser.LogParser;
import com.logsentinel.parser.ParseResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a log file line by line using {@link BufferedReader} and parses each
 * line. The file is streamed, so the entire contents are never held in memory
 * at once; only the successfully parsed entries are returned.
 */
public final class LogFileReader {

    private static final String LOG_EXTENSION = ".log";
    private static final String TXT_EXTENSION = ".txt";

    public LogFileReader() {
    }

    /**
     * Reads and parses the given file.
     *
     * @param filePath path to the .log or .txt file
     * @return a result containing the parsed entries and summary counts
     * @throws LogFileException if the file is missing, has a bad extension, or
     *                          cannot be read
     */
    public FileProcessingResult read(String filePath) throws LogFileException {
        if (filePath == null || filePath.isBlank()) {
            throw new LogFileException("File path must not be null or blank");
        }

        Path path = Paths.get(filePath);
        validateFile(path);

        List<LogEntry> entries = new ArrayList<>();
        long totalLines = 0;
        int invalidEntries = 0;
        long start = System.currentTimeMillis();

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                totalLines++;
                ParseResult result = LogParser.parse(line, (int) totalLines);
                if (result.isSuccess()) {
                    entries.add(result.getEntry());
                } else {
                    invalidEntries++;
                }
            }
        } catch (IOException e) {
            throw new LogFileException("Failed to read file: " + filePath, e);
        }

        long processingTimeMillis = System.currentTimeMillis() - start;
        return new FileProcessingResult(
                path.getFileName().toString(),
                totalLines,
                entries.size(),
                invalidEntries,
                processingTimeMillis
        );
    }

    /**
     * Reads and parses the given file, returning both the parsed entries and
     * the summary result.
     */
    public ReadResult readWithEntries(String filePath) throws LogFileException {
        if (filePath == null || filePath.isBlank()) {
            throw new LogFileException("File path must not be null or blank");
        }

        Path path = Paths.get(filePath);
        validateFile(path);

        List<LogEntry> entries = new ArrayList<>();
        long totalLines = 0;
        int invalidEntries = 0;
        long start = System.currentTimeMillis();

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                totalLines++;
                ParseResult result = LogParser.parse(line, (int) totalLines);
                if (result.isSuccess()) {
                    entries.add(result.getEntry());
                } else {
                    invalidEntries++;
                }
            }
        } catch (IOException e) {
            throw new LogFileException("Failed to read file: " + filePath, e);
        }

        long processingTimeMillis = System.currentTimeMillis() - start;
        FileProcessingResult summary = new FileProcessingResult(
                path.getFileName().toString(),
                totalLines,
                entries.size(),
                invalidEntries,
                processingTimeMillis
        );
        return new ReadResult(entries, summary);
    }

    private void validateFile(Path path) throws LogFileException {
        String name = path.getFileName().toString();
        if (!name.endsWith(LOG_EXTENSION) && !name.endsWith(TXT_EXTENSION)) {
            throw new LogFileException("Unsupported file extension: " + name
                    + ". Only .log and .txt files are accepted.");
        }

        if (!Files.exists(path)) {
            throw new LogFileException("File not found: " + path.toAbsolutePath());
        }

        if (!Files.isRegularFile(path)) {
            throw new LogFileException("Not a regular file: " + path.toAbsolutePath());
        }

        if (!Files.isReadable(path)) {
            throw new LogFileException("File is not readable: " + path.toAbsolutePath());
        }
    }

    /**
     * Pair of parsed entries and the processing summary.
     */
    public static final class ReadResult {
        private final List<LogEntry> entries;
        private final FileProcessingResult summary;

        public ReadResult(List<LogEntry> entries, FileProcessingResult summary) {
            this.entries = entries;
            this.summary = summary;
        }

        public List<LogEntry> getEntries() {
            return entries;
        }

        public FileProcessingResult getSummary() {
            return summary;
        }
    }
}
