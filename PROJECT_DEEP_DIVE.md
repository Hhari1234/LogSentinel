# LogSentinel – Project Deep Dive

This document explains the LogSentinel source code file by file. It covers each class, its responsibilities, key methods, data flow, and design decisions. Intended for code reviewers, interviewers, and anyone who wants to understand the implementation in depth.

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Module: model — Immutable Data Models](#module-model)
3. [Module: parser — Log Parsing](#module-parser)
4. [Module: processor — File Reading and Processing](#module-processor)
5. [Module: analyzer — Analysis Logic](#module-analyzer)
6. [Module: incident — Incident Detection](#module-incident)
7. [Module: report — Report Generation](#module-report)
8. [Module: service — Analysis Orchestration](#module-service)
9. [Module: web — Web Dashboard and REST API](#module-web)
10. [Module: benchmark — Performance Testing](#module-benchmark)
11. [Module: exception — Custom Exceptions](#module-exception)
12. [Data Flow — End to End](#data-flow)
13. [Design Decisions](#design-decisions)
14. [Thread Safety](#thread-safety)

---

## Project Overview

LogSentinel is a Java 17 application that analyzes `.log` and `.txt` log files. It has two interfaces:

1. **CLI** (`Main.java`) — Interactive menu-driven console application
2. **Web Dashboard** (`DashboardServer.java` + `Main.java --dashboard`) — Browser-based interface using Java's built-in `HttpServer`

Both interfaces share the same analysis engine (`LogAnalysisService`). The project uses only Core Java with JUnit 5 for testing and Maven for building. No Spring, no database, no external web framework.

---

## Module: model — Immutable Data Models

All model classes are `final` and immutable. Fields are `private final`, accessed via getters. No setters.

### `LogEntry` (`src/main/java/com/logsentinel/model/LogEntry.java`)

Represents a single parsed log line.

**Fields:**
- `lineNumber` (int) — 1-based line number in the source file
- `rawLine` (String) — Original unparsed line text
- `timestamp` (LocalDateTime) — Parsed timestamp, or null if missing/unparseable
- `level` (LogLevel) — Log level enum (TRACE, DEBUG, INFO, WARN, ERROR, FATAL, UNKNOWN)
- `service` (String) — Service name from `[ServiceName]` brackets, trimmed
- `thread` (String) — Thread name from brackets, trimmed
- `message` (String) — Log message text
- `exceptionName` (String) — Extracted exception class name (e.g., `NullPointerException`), or null
- `valid` (boolean) — Whether the line was successfully parsed

**Builder pattern:** Uses a nested `Builder` static class for construction. The builder normalizes service/thread (trims, converts blank to null) and defaults level to `UNKNOWN` if null.

**Supported input formats:**
```
2026-11-23 10:30:45 INFO [AuthService] [main] User login successful
2026-11-23 10:30:45 INFO [AuthService] User login successful
2026-11-23 10:30:45 INFO User login successful
INFO [AuthService] User login successful
INFO User login successful
```

### `LogLevel` (`src/main/java/com/logsentinel/model/LogLevel.java`)

Enum with seven values: TRACE, DEBUG, INFO, WARN, ERROR, FATAL, UNKNOWN.

**Key method:** `fromString(String)` — case-insensitive lookup; returns `UNKNOWN` for null/blank/unrecognized values instead of throwing.

### `LogStatistics` (`src/main/java/com/logsentinel/model/LogStatistics.java`)

Aggregate counts for a set of log entries.

**Fields:**
- `totalEntries` (int) — Total valid entries
- `levelCounts` (Map<LogLevel, Integer>) — Count per log level (defensive copy via `Map.copyOf`)
- `validEntries` (int) — Count of successfully parsed entries
- `invalidEntries` (int) — Count of unparseable lines

**Key methods:**
- `getCount(LogLevel)` — Returns count for a level, or 0 if absent (uses `getOrDefault`)
- `getErrorPercentage()` — ERROR count / total * 100, returns 0.0 if total is 0
- `getWarningPercentage()` — WARN count / total * 100, returns 0.0 if total is 0
- `getFatalPercentage()` — FATAL count / total * 100, returns 0.0 if total is 0

**Immutability:** Constructor creates a defensive copy of the input map. `getLevelCounts()` returns `Map.copyOf()`.

### `AnalysisSession` (`src/main/java/com/logsentinel/model/AnalysisSession.java`)

Complete result of analyzing one log file. Holds all analysis outputs together.

**Fields:**
- `fileName` (String)
- `processingResult` (ProcessingResult)
- `entries` (List<LogEntry>)
- `statistics` (LogStatistics)
- `errorFrequencies` (List<ErrorFrequency>)
- `dailyStats` (List<DailyStat>)
- `incidents` (List<Incident>)
- `analysisTimestamp` (LocalDateTime)

### `ProcessingResult` (`src/main/java/com/logsentinel/model/ProcessingResult.java`)

Result from a `LogProcessor.process()` call.

**Fields:** fileName, entries, totalLines, validEntries, invalidEntries, processingTimeMillis.

### `ErrorFrequency` (`src/main/java/com/logsentinel/model/ErrorFrequency.java`)

Represents a frequent error message with its count and associated service.

**Fields:** message, normalizedMessage, service, count. Implements `Comparable` — sorts by count descending (natural ordering used in `ErrorFrequencyAnalyzer`).

### `DailyStat` (`src/main/java/com/logsentinel/model/DailyStat.java`)

Per-day grouping: date, error count, warning count, total count.

### `Incident` (`src/main/java/com/logsentinel/model/Incident.java`)

Detected potential incident.

**Fields:** id (e.g., "INC-0001"), service, pattern, occurrences, firstOccurrence, lastOccurrence, windowMinutes, severity (IncidentSeverity), potential (boolean).

### `IncidentSeverity` (`src/main/java/com/logsentinel/model/IncidentSeverity.java`)

Enum: LOW, MEDIUM, HIGH, CRITICAL.

### `SearchResult` (`src/main/java/com/logsentinel/model/SearchResult.java`)

Wrapper for a search match: the matching `LogEntry` and the matched keyword.

### `FileProcessingResult` (`src/main/java/com/logsentinel/model/FileProcessingResult.java`)

Summary from file reading: fileName, totalLines, validEntries, invalidEntries, processingTimeMillis.

### `ReportData` (`src/main/java/com/logsentinel/model/ReportData.java`)

Payload passed to report generators. Contains file name, timestamp, counts, level counts, error frequencies, daily stats, incidents, and processing duration.

---

## Module: parser — Log Parsing

### `LogParser` (`src/main/java/com/logsentinel/parser/LogParser.java`)

Static utility class (private constructor). Parses log lines using three regex patterns tried in order:

1. **FULL_PATTERN:** `timestamp LEVEL [service] [thread] message`
2. **NO_TIMESTAMP_PATTERN:** `LEVEL [service] [thread] message`
3. **NO_BRACKETS_PATTERN:** `timestamp LEVEL message`

**Key method:** `parse(String line, int lineNumber)` returns `ParseResult`.

**Flow:**
1. Reject null or empty/blank lines with `ParseResult.failure()`
2. Try FULL_PATTERN, then NO_TIMESTAMP_PATTERN, then NO_BRACKETS_PATTERN
3. If none match, return `ParseResult.failure("no recognizable structure")`
4. Extract groups: timestamp, level, service, thread, message
5. Parse timestamp using `DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")`
6. Map level string via `LogLevel.fromString()`
7. Extract exception name from message using regex `\b([A-Z][A-Za-z0-9]*Exception)\b`
8. Return `ParseResult.success(LogEntry)`

**Graceful degradation:** Malformed lines return failure results rather than throwing exceptions. The caller counts invalid entries separately.

**Exception extraction:** Scans message for Java exception class names (capital letter followed by alphanumerics ending in "Exception").

### `ParseResult` (`src/main/java/com/logsentinel/parser/ParseResult.java`)

Result wrapper with `isSuccess()`, `getEntry()`, `getErrorMessage()`. Static factories: `success(LogEntry)` and `failure(String)`.

---

## Module: processor — File Reading and Processing

### `LogFileReader` (`src/main/java/com/logsentinel/processor/LogFileReader.java`)

Streams a file line-by-line using `BufferedReader` and `Files.newBufferedReader()`. Never loads the entire file into memory.

**Methods:**
- `read(String filePath)` — Returns `FileProcessingResult` (summary only)
- `readWithEntries(String filePath)` — Returns `ReadResult` (entries + summary)
- `validateFile(Path)` — Checks extension (.log/.txt), existence, regular file, readability

**Validation order:** extension check → exists → isRegularFile → isReadable.

**Throws:** `LogFileException` with descriptive messages for each failure case.

**Inner class `ReadResult`:** Holds `List<LogEntry>` and `FileProcessingResult`.

### `LogProcessor` (`src/main/java/com/logsentinel/processor/LogProcessor.java`)

Interface with one method: `ProcessingResult process(String filePath)`.

### `SequentialLogProcessor` (`src/main/java/com/logsentinel/processor/SequentialLogProcessor.java`)

Single-threaded implementation. Delegates to `LogFileReader.readWithEntries()`, wraps the result in a `ProcessingResult`. Simple, deterministic, no thread safety concerns.

### `ParallelLogProcessor` (`src/main/java/com/logsentinel/processor/ParallelLogProcessor.java`)

Multi-threaded implementation using `ExecutorService`.

**Architecture:**
1. Stream file line-by-line with `BufferedReader` (never loads whole file)
2. Group lines into bounded batches (default 1000 lines per batch)
3. Submit each batch as a `Callable<BatchResult>` to a fixed thread pool
4. `ExecutorService.shutdown()` + `awaitTermination(1, TimeUnit.HOURS)` for orderly completion
5. Aggregate results from all `Future<BatchResult>` objects
6. Combine entries, sum valid/invalid counts

**Thread safety:** Each `ParseBatchTask` owns its data (its own `ArrayList` copy of the batch). No shared mutable state during parsing. Results are combined after all workers finish — no race conditions.

**Line number preservation:** Each task receives its starting line number, so `LogEntry.lineNumber` is correct across batches.

**Error handling:** `shutdownNow()` on IOException during reading. `ExecutionException` propagated from worker failures. `InterruptedException` handled with re-interruption.

**BatchResult:** Private static inner class holding entries list, entry count, invalid count for one batch.

---

## Module: analyzer — Analysis Logic

### `LogStatisticsAnalyzer` (`src/main/java/com/logsentinel/analyzer/LogStatisticsAnalyzer.java`)

Counts log level occurrences.

**Key method:** `analyze(List<LogEntry>, int invalidEntries)`

**Flow:**
1. Initialize `EnumMap<LogLevel, Integer>` with all levels set to 0
2. Iterate entries, `counts.merge(level, 1, Integer::sum)` for each
3. Return `new LogStatistics(total, counts, total, invalidEntries)`

**Uses `EnumMap`** — efficient for enum keys, maintains enum declaration order.

**Alternative method:** `countLevels(List<LogEntry>)` uses Java Streams `groupingBy` + `reducing`.

### `ErrorFrequencyAnalyzer` (`src/main/java/com/logsentinel/analyzer/ErrorFrequencyAnalyzer.java`)

Finds the most frequent ERROR messages.

**Flow:**
1. Filter: only `LogLevel.ERROR` entries
2. Skip: null or blank messages
3. Normalize message: `message.trim().replaceAll("\\s+", " ")` (collapses whitespace)
4. Group key: `serviceName + "||" + normalizedMessage` — prevents merging same error from different services
5. Use mutable `BuilderHolder` to accumulate counts per bucket
6. Sort by count descending (`ErrorFrequency` implements `Comparable`)
7. Return top N (default 10)

**Why composite key:** "Connection timeout" from DatabaseService and "Connection timeout" from AuthService are different errors — they should not be merged.

**Inner class `BuilderHolder`:** Mutable holder for accumulating count while building immutable `ErrorFrequency`.

### `DailyStatisticsAnalyzer` (`src/main/java/com/logsentinel/analyzer/DailyStatisticsAnalyzer.java`)

Groups entries by date and counts errors/warnings/total per day.

**Key methods:**
- `analyze(List<LogEntry>)` — Returns `List<DailyStat>` grouped by `entry.getTimestamp().toLocalDate()`
- `dayWithMostErrors(List<LogEntry>)` — Returns the `LocalDate` with the highest error count

### `LogSearchService` (`src/main/java/com/logsentinel/analyzer/LogSearchService.java`)

Keyword search across entries.

**Method:** `byKeyword(List<LogEntry>, String keyword)` — Case-insensitive search in message and service fields. Returns `List<SearchResult>`.

---

## Module: incident — Incident Detection

### `IncidentDetector` (`src/main/java/com/logsentinel/incident/IncidentDetector.java`)

Detects potential incidents from repeated error patterns.

**Default threshold:** 5 errors within 1 minute.

**Flow:**
1. Filter: only ERROR level entries with valid timestamps
2. Group by composite key: `service + "||" + normalizedMessage`
3. For each group, sort timestamps ascending
4. Sliding window: find densest window where `Duration.between(first, last) <= windowMinutes`
5. If window count >= threshold, create an `Incident`:
   - ID: `INC-0001`, `INC-0002`, ... (sequential counter)
   - Severity: CRITICAL if count >= threshold*2, HIGH if count >= threshold
   - `potential = true` (not a confirmed root cause)
6. Remove attributed occurrences, repeat until no more qualifying windows
7. Sort incidents by severity (descending), then by count (descending)

**`findDensestWindow`:** Two-pointer sliding window O(n) after sorting. For each start index i, extend end index j while within the time window. Track the window with the most occurrences.

**Why not root cause:** Incidents are statistical findings. The tool flags suspicious repetition but cannot determine business logic root cause.

### `Incident` (`src/main/java/com/logsentinel/model/Incident.java`)

Immutable model with Builder pattern. Fields: id, service, pattern, occurrences, firstOccurrence, lastOccurrence, windowMinutes, severity, potential.

### `IncidentSeverity` (`src/main/java/com/logsentinel/model/IncidentSeverity.java`)

Enum: LOW, MEDIUM, HIGH, CRITICAL.

---

## Module: report — Report Generation

### `ReportGenerator` (`src/main/java/com/logsentinel/report/ReportGenerator.java`)

Interface with one method: `generate(ReportData, Path outputDir)`.

### `TextReportGenerator` (`src/main/java/com/logsentinel/report/TextReportGenerator.java`)

Generates human-readable `.txt` reports. Creates four files:
- `summary_report.txt` — file info, processing time, level counts, percentages
- `error_frequency_report.txt` — ranked frequent errors with service
- `daily_statistics_report.txt` — per-day errors/warnings/total, busiest day
- `incident_report.txt` — detected incidents with severity

**Key detail:** Percentage calculation uses `validEntries` as denominator, with zero-division guard (`if (total == 0) return 0.0`).

### `CsvReportGenerator` (`src/main/java/com/logsentinel/report/CsvReportGenerator.java`)

Generates RFC 4180-compliant `.csv` reports. Same four files as TXT.

**`escape(String)` method:**
- If value contains comma, double-quote, newline, or carriage return: wrap in double quotes and double any embedded quotes (`"` → `""`)
- Otherwise: return as-is
- Null → empty string

**CSV files:**
- `summary_report.csv` — Metric,Value pairs
- `error_frequency_report.csv` — Rank,Error Message,Service,Occurrences
- `daily_statistics_report.csv` — Date,Errors,Warnings,Total
- `incident_report.csv` — Incident ID,Service,Pattern,Occurrences,First,Last,Window,Severity,Type

### `ReportData` (`src/main/java/com/logsentinel/model/ReportData.java`)

Payload object passed to both report generators. Holds all analysis data in one object.

### `ReportGenerationException` (`src/main/java/com/logsentinel/report/ReportGenerationException.java`)

Custom exception for report failures (missing output directory, write failures).

---

## Module: service — Analysis Orchestration

### `LogAnalysisService` (`src/main/java/com/logsentinel/service/LogAnalysisService.java`)

Coordinates reading a log file and running all analyses. Separated from CLI so the same analysis can be reused by reports, tests, or other UIs.

**Constructors:**
- `LogAnalysisService(LogProcessor)` — injects processor (used by CLI for parallel/sequential choice)
- `LogAnalysisService()` — defaults to `SequentialLogProcessor`

**Method:** `analyze(String filePath, boolean parallel)`

**Flow:**
1. `processor.process(filePath)` → `ProcessingResult` with entries
2. `statisticsAnalyzer.analyze(entries, invalidEntries)` → `LogStatistics`
3. `errorFrequencyAnalyzer.analyze(entries)` → `List<ErrorFrequency>`
4. `dailyStatisticsAnalyzer.analyze(entries)` → `List<DailyStat>`
5. `incidentDetector.detect(entries)` → `List<Incident>`
6. Construct `AnalysisSession` with all results + `LocalDateTime.now()`

**Note:** The `parallel` parameter is accepted but the processor is injected at construction time. The CLI decides sequential vs parallel before creating the service.

**`searchByKeyword(List<LogEntry>, String)`:** Delegates to `LogSearchService.byKeyword()`, extracts entries from results.

---

## Module: web — Web Dashboard and REST API

### `DashboardServer` (`src/main/java/com/logsentinel/web/DashboardServer.java`)

Starts the HTTP server using `com.sun.net.httpserver.HttpServer`.

**Key features:**
- Binds to `0.0.0.0` on configured port (all network interfaces)
- Port from `System.getenv("PORT")` with fallback to 8080
- Supports `--port` command-line argument
- Registers all API contexts and static resource handler
- `server.setExecutor(null)` — uses default thread pool
- Shutdown hook: `server.stop(5)` on SIGTERM/CTRL+C
- `start()` prints formatted startup box with status, port, URLs
- `awaitShutdown()` blocks main thread via `CountDownLatch`
- `main()` creates server, calls `start()`, then `Thread.currentThread().join()` (keeps JVM alive)

**Routes:**
| Path | Handler |
|------|---------|
| `/api/health` | DashboardHandler |
| `/api/dashboard` | DashboardHandler |
| `/api/logs` | DashboardHandler |
| `/api/errors` | DashboardHandler |
| `/api/incidents` | DashboardHandler |
| `/api/upload` | FileUploadHandler |
| `/api/reports/download` | ReportDownloadHandler |
| `/` (and all others) | StaticResourceHandler |

### `DashboardStore` (`src/main/java/com/logsentinel/web/DashboardStore.java`)

Thread-safe in-memory holder for the latest `AnalysisSession`.

- `volatile AnalysisSession session` field
- `synchronized` block on a private lock for `setSession()`
- `getSession()` and `hasSession()` are simple volatile reads

**Limitation:** Single instance per JVM. Not shared across container restarts or scaled instances.

### `DashboardHandler` (`src/main/java/com/logsentinel/web/DashboardHandler.java`)

Handles all GET API endpoints. Extends `ApiHandler`.

**Endpoints:**
- `GET /api/health` — `{"status":"ok","service":"LogSentinel","hasSession":bool}`
- `GET /api/dashboard` — Full data: fileName, timestamp, totals, levelCounts, dailyStats, errorFrequencies, incidents
- `GET /api/logs` — Filtered entries (query params: `level`, `keyword`)
- `GET /api/errors` — Ranked error frequencies
- `GET /api/incidents` — Detected incidents with severity, timestamps

**Error handling:** All exceptions caught and return HTTP 500 with sanitized "Internal server error" message (no stack trace leakage).

**Empty session handling:** All endpoints return empty collections or `hasSession: false` when no session exists — no null pointer exceptions.

### `FileUploadHandler` (`src/main/java/com/logsentinel/web/FileUploadHandler.java`)

Handles `POST /api/upload` with multipart/form-data.

**Validation:**
- Method must be POST (405 otherwise)
- Path must be `/api/upload` (404 otherwise)
- Filename must not be null/blank (400)
- Extension must be `.log` or `.txt` (400)
- Content length must be <= 50 MB (413)
- Filename sanitized via `Paths.get(filename).getFileName()` (path traversal prevention)

**Processing:**
1. Parse multipart body manually (no external library)
2. Write temp file to `java.io.tmpdir/logsentinel-uploads/` with UUID prefix
3. Run `LogAnalysisService.analyze()` on the temp file
4. Store session in `DashboardStore`
5. Delete temp file in `finally` block
6. Return JSON with status, fileName, counts, processing time

**Multipart parsing:** Custom implementation that splits on boundary, extracts `filename` from `Content-Disposition` header, and returns file content as UTF-8 string.

### `ReportDownloadHandler` (`src/main/java/com/logsentinel/web/ReportDownloadHandler.java`)

Handles `GET /api/reports/download?type=txt|csv`.

**Flow:**
1. Validate method is GET, path is correct
2. Validate `type` parameter is `txt` or `csv` (400 otherwise)
3. Check session exists (404 if not)
4. Build `ReportData` from session
5. Create unique temp directory: `logsentinel-reports-<UUID>`
6. Generate report into temp directory
7. Read generated file bytes
8. Clean up temp directory in `finally` block (recursive delete)
9. Send file with `Content-Disposition: attachment` and appropriate `Content-Type`

**Security:** Unique per-request directory prevents race conditions. Cleanup ensures no files persist.

### `StaticResourceHandler` (`src/main/java/com/logsentinel/web/StaticResourceHandler.java`)

Serves frontend assets from classpath `/static/`.

**Flow:**
1. Map request path to resource path under `/static/`
2. `ClassLoader.getResourceAsStream()` to load the resource
3. If not found, fall back to `index.html` (SPA-style routing)
4. Set `Content-Type` based on file extension (css, js, html, json, png, svg)
5. Return 200 with file bytes, or 404 if nothing found

### `ApiHandler` (`src/main/java/com/logsentinel/web/ApiHandler.java`)

Abstract base class implementing `HttpHandler`.

**Helpers:**
- `sendJson(exchange, status, body)` — Serializes object via `JsonUtil.toJson()`, sets `Content-Type: application/json; charset=utf-8`
- `sendError(exchange, status, message)` — Sends `{"error": "STATUS MESSAGE"}` as JSON
- `sendText(exchange, status, contentType, bytes)` — Raw byte response
- `readBody(exchange)` — Reads all request body bytes into a UTF-8 string

**`readBody` fix:** Uses `ByteArrayOutputStream` with 8KB buffer, reading until -1. Does NOT use `InputStream.available()` (which returns 0 for socket streams).

### `JsonUtil` (`src/main/java/com/logsentinel/web/JsonUtil.java`)

Minimal JSON serializer with no external dependencies.

**Handles:** String, Number, Boolean, null, Map, Iterable, array, and other objects (via `String.valueOf`).

**String escaping:** Quotes, backslashes, forward slashes, backspace, formfeed, newline, carriage return, tab, and control characters (< 32) as `\uXXXX`.

**No Jackson/Gson dependency** — keeps the dependency footprint minimal.

---

## Module: benchmark — Performance Testing

### `PerformanceBenchmark` (`src/main/java/com/logsentinel/benchmark/PerformanceBenchmark.java`)

Generates deterministic sample files (1/10/50/100 MB) and measures sequential vs parallel throughput using `System.nanoTime()`. Results are actual measurements, not hardcoded.

### `LogGenerator` (`src/main/java/com/logsentinel/benchmark/LogGenerator.java`)

Generates sample log files with configurable size and content patterns.

---

## Module: exception — Custom Exceptions

### `LogFileException` (`src/main/java/com/logsentinel/exception/LogFileException.java`)

Thrown for file-related errors: missing file, bad extension, unreadable file, parse failures.

### `ReportGenerationException` (`src/main/java/com/logsentinel/report/ReportGenerationException.java`)

Thrown for report generation errors: missing output directory, write failures.

---

## Data Flow — End to End

### CLI Mode

```
User types file path in Main menu
    │
    ▼
Main.analyzeFile() → LogAnalysisService(SequentialLogProcessor or ParallelLogProcessor)
    │
    ▼
processor.process(filePath)
    │
    ├─ LogFileReader.readWithEntries()
    │   ├─ validateFile() (extension, exists, readable)
    │   ├─ BufferedReader line-by-line
    │   └─ LogParser.parse() per line → LogEntry list + FileProcessingResult
    │
    ▼
LogAnalysisService.analyze()
    │
    ├─ LogStatisticsAnalyzer → LogStatistics
    ├─ ErrorFrequencyAnalyzer → List<ErrorFrequency>
    ├─ DailyStatisticsAnalyzer → List<DailyStat>
    ├─ IncidentDetector → List<Incident>
    │
    ▼
new AnalysisSession(...)
    │
    ▼
Main.printAnalysisSummary() → Console output
```

### Dashboard Mode

```
Browser → GET /api/upload (multipart file)
    │
    ▼
FileUploadHandler.handle()
    │
    ├─ parseMultipart() → filename + content bytes
    │   ├─ validate extension (.log/.txt)
    │   ├─ validate size (<= 50 MB)
    │   ├─ sanitize filename (path traversal prevention)
    │   ├─ write temp file (UUID prefix)
    │   └─ delete temp file in finally
    │
    ├─ LogAnalysisService.analyze(tempFilePath)
    │   └─ (same analysis pipeline as CLI)
    │
    ├─ DashboardStore.setSession(session)
    │
    ▼
JSON response: {status, fileName, totalLines, validEntries, errorCount, ...}

Browser → GET /api/dashboard
    │
    ▼
DashboardHandler.handleDashboard()
    │
    ├─ DashboardStore.getSession()
    │   ├─ null → {"hasSession": false}
    │   └─ session → full data JSON
    │
    ▼
JavaScript fetch() → renderOverview() / renderAnalytics() / renderErrors() / renderIncidents() / renderLogs()
```

---

## Design Decisions

| Decision | Rationale |
|----------|-----------|
| **No Spring Boot** | Keeps dependency footprint minimal; demonstrates Core Java proficiency |
| **Built-in HttpServer** | No external web server needed; runs with just Java 17 JRE |
| **Immutable models** | Thread-safe by design; no accidental state mutation |
| **Builder pattern for LogEntry** | Clean construction with many optional fields |
| **Streaming file read** | Never loads entire file into memory; safe for large files |
| **Bounded batches in parallel mode** | Limits per-batch memory; no shared mutable state between workers |
| **Composite key for error grouping** | `(service || normalizedMessage)` prevents incorrect merging across services |
| **Custom JSON serializer** | No Jackson/Gson dependency; full control over output |
| **Sanitized error responses** | No stack traces or internal details leaked to API clients |
| **In-memory DashboardStore** | Simple, no database dependency; documented limitation |
| **JUnit 5 only in test scope** | Zero runtime dependencies beyond Java 17 |

---

## Thread Safety

| Component | Thread Safety | Mechanism |
|-----------|--------------|-----------|
| `LogEntry` | Safe | Immutable (all fields `final`) |
| `LogStatistics` | Safe | Immutable with defensive copies |
| `AnalysisSession` | Safe | Immutable (all fields `final`) |
| `DashboardStore` | Safe | `volatile` session + `synchronized` setter |
| `LogFileReader` | Safe | Stateless (no instance state) |
| `LogParser` | Safe | Stateless (static methods only) |
| `ParallelLogProcessor` | Safe per call | Each `process()` creates its own `ExecutorService`; workers own their data |
| `ErrorFrequencyAnalyzer` | Safe | Stateless (local `HashMap` only) |
| `IncidentDetector` | NOT thread-safe | `nextId` counter is instance-level mutable state |

**Note:** `IncidentDetector.nextId` is not thread-safe. If used concurrently, IDs could collide. In the current architecture, each analysis creates a new `IncidentDetector` instance, so this is not an issue in practice.

---

## Source File Inventory

**Main source (34 files):**

| Package | Files |
|---------|-------|
| `com.logsentinel` | Main.java |
| `com.logsentinel.model` | LogEntry, LogLevel, LogStatistics, AnalysisSession, ProcessingResult, ErrorFrequency, DailyStat, Incident, IncidentSeverity, SearchResult, FileProcessingResult, ReportData |
| `com.logsentinel.parser` | LogParser, ParseResult |
| `com.logsentinel.processor` | LogProcessor, LogFileReader, SequentialLogProcessor, ParallelLogProcessor |
| `com.logsentinel.analyzer` | LogStatisticsAnalyzer, ErrorFrequencyAnalyzer, DailyStatisticsAnalyzer, LogSearchService |
| `com.logsentinel.incident` | IncidentDetector |
| `com.logsentinel.report` | ReportGenerator, TextReportGenerator, CsvReportGenerator, ReportGenerationException |
| `com.logsentinel.service` | LogAnalysisService |
| `com.logsentinel.web` | DashboardServer, DashboardStore, DashboardHandler, FileUploadHandler, ReportDownloadHandler, StaticResourceHandler, ApiHandler, JsonUtil |
| `com.logsentinel.exception` | LogFileException, ReportGenerationException |
| `com.logsentinel.benchmark` | PerformanceBenchmark, LogGenerator |

**Test source (13 files):**

| Package | Files |
|---------|-------|
| `com.logsentinel.parser` | LogParserTest |
| `com.logsentinel.analyzer` | LogStatisticsAnalyzerTest, ErrorFrequencyAnalyzerTest, DailyStatisticsAnalyzerTest, LogSearchServiceTest |
| `com.logsentinel.processor` | LogFileReaderTest, ParallelLogProcessorTest |
| `com.logsentinel.incident` | IncidentDetectorTest |
| `com.logsentinel.report` | ReportGeneratorTest |
| `com.logsentinel.web` | DashboardHandlerTest, DashboardStoreTest, FileUploadHandlerTest, JsonUtilTest |

**Configuration:**
- `pom.xml` — Maven build (Java 17, JUnit 5.10.0, compiler/warn/surefire/jar plugins)
- `Dockerfile.vercel` — Multi-stage Docker build (Maven build → JRE runtime)
- `.dockerignore` / `.vercelignore` — Exclude build artifacts
