package com.logsentinel.report;

import java.nio.file.Path;

/**
 * Generates analysis reports from a {@link ReportData} object.
 */
public interface ReportGenerator {

    /**
     * Generates all reports and writes them to the given output directory.
     *
     * @param data       the report data
     * @param outputDir  the directory to write reports into
     * @throws ReportGenerationException if a report cannot be written
     */
    void generate(ReportData data, Path outputDir) throws ReportGenerationException;
}
