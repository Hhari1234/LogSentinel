package com.logsentinel.processor;

import com.logsentinel.exception.LogFileException;
import com.logsentinel.model.ProcessingResult;

/**
 * Processes a log file and returns the parsed entries with a summary.
 */
public interface LogProcessor {

    /**
     * Processes the given log file.
     *
     * @param filePath path to the .log or .txt file
     * @return the processing result
     * @throws LogFileException if the file cannot be read
     */
    ProcessingResult process(String filePath) throws LogFileException;
}
