package com.logsentinel.processor;

import com.logsentinel.exception.LogFileException;
import com.logsentinel.model.FileProcessingResult;
import com.logsentinel.model.LogEntry;
import com.logsentinel.model.ProcessingResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Sequential implementation of {@link LogProcessor}. Reads the file line by
 * line using a {@link BufferedReader} and parses each line as it is read.
 */
public final class SequentialLogProcessor implements LogProcessor {

    private final LogFileReader reader = new LogFileReader();

    @Override
    public ProcessingResult process(String filePath) throws LogFileException {
        LogFileReader.ReadResult result = reader.readWithEntries(filePath);
        List<LogEntry> entries = result.getEntries();
        FileProcessingResult summary = result.getSummary();
        return new ProcessingResult(
                summary.getFileName(),
                entries,
                summary.getTotalLines(),
                summary.getValidEntries(),
                summary.getInvalidEntries(),
                summary.getProcessingTimeMillis()
        );
    }
}
