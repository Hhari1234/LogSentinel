# LogSentinel – Interview Preparation Guide

This document explains the LogSentinel project in simple, technically accurate English for software engineering interviews. It covers the project explanation, architecture, key technical concepts, design decisions, limitations, and possible improvements.

---

## 1. Project Explanation (30-Second Elevator Pitch)

LogSentinel is a Java 17 command-line and web application that analyzes application log files. It reads `.log` and `.txt` files, parses log entries, computes statistics (error counts, daily activity, frequent errors), detects potential incidents from repeated error patterns, and provides a browser-based dashboard plus TXT/CSV report downloads. It uses only Core Java — no frameworks, no databases.

---

## 2. Project Explanation (2-Minute Version)

LogSentinel helps developers and support engineers analyze application log files without setting up an observability stack like ELK or Splunk.

**What it does:**
- Reads `.log` and `.txt` files line-by-line (streaming, not loading the whole file into memory)
- Parses each line into structured data: timestamp, log level (TRACE, DEBUG, INFO, WARN, ERROR, FATAL, UNKNOWN), service name, thread name, message, and exception name
- Computes statistics: log-level counts with percentages, daily error/warning totals, most frequent errors ranked by count
- Detects potential incidents: repeated errors within a configurable time window (default: 5+ similar errors within 1 minute)
- Provides a command-line interactive menu and a browser-based dashboard at `http://localhost:8080`
- Supports file upload via the dashboard, log search/filtering, and TXT/CSV report downloads
- Optional parallel processing using `ExecutorService` for large files
- Generates deterministic performance benchmarks

**Tech stack:** Java 17, Maven, JUnit 5, built-in `HttpServer`, HTML/CSS/vanilla JavaScript frontend. No Spring, no database, no external dependencies beyond JUnit.

---

## 3. Architecture and Request Flow

### High-Level Flow

```
User (CLI or Browser)
    │
    ▼
Main.java (entry point)
    │
    ├─ CLI mode → interactive menu → LogAnalysisService
    └─ Dashboard mode → HttpServer → REST API → LogAnalysisService
    │
    ▼
LogAnalysisService (orchestrator)
    │
    ├─ LogFileReader (streams file line by line)
    ├─ LogParser (regex-based parsing → LogEntry objects)
    ├─ Analyzers (statistics, error frequency, daily stats, incidents, search)
    ├─ LogProcessor (sequential or parallel via ExecutorService)
    └─ ReportGenerator (TXT/CSV output)
    │
    ▼
AnalysisSession (in-memory result holder)
    │
    ├─ CLI output
    ├─ Dashboard JSON responses
    └─ Report files
```

### Dashboard Request Flow

```
Browser → GET /api/health → DashboardHandler → DashboardStore → JSON response
Browser → POST /api/upload (multipart) → FileUploadHandler → LogAnalysisService → DashboardStore → JSON response
Browser → GET /api/dashboard → DashboardHandler → DashboardStore → JSON response
Browser → GET /api/logs?level=ERROR&keyword=timeout → DashboardHandler → DashboardStore → JSON response
Browser → GET /api/reports/download?type=csv → ReportDownloadHandler → ReportGenerator → File response
```

---

## 4. Java HTTP Server

LogSentinel uses Java's built-in `com.sun.net.httpserver.HttpServer` — no external web framework (no Spring Boot, no Jetty, no Tomcat).

**Key characteristics:**
- `HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0)` binds to all network interfaces on the configured port
- `server.createContext(path, handler)` registers `HttpHandler` instances for each API endpoint
- `server.setExecutor(null)` uses the default thread pool executor
- `server.start()` begins listening; `server.stop(delay)` performs graceful shutdown with a delay
- A shutdown hook (`Runtime.getRuntime().addShutdownHook`) ensures `server.stop(5)` is called on SIGTERM/CTRL+C
- The port is read from the `PORT` environment variable (for cloud platforms like Vercel) with fallback to `8080`

---

## 5. OOP Concepts Used in the Project

| Concept | Where It's Used |
|---------|----------------|
| **Encapsulation** | All model classes (`LogEntry`, `LogStatistics`, `ErrorFrequency`, etc.) have private fields with public getters |
| **Immutability** | Model classes are immutable — fields are `final`, no setters, objects are created via constructors or builders |
| **Abstraction** | `LogProcessor` interface with `SequentialLogProcessor` and `ParallelLogProcessor` implementations; `ReportGenerator` abstract class |
| **Inheritance** | `DashboardHandler`, `FileUploadHandler`, `ReportDownloadHandler` extend `ApiHandler` which implements `HttpHandler` |
| **Polymorphism** | `LogProcessor` reference can point to either sequential or parallel implementation; `ApiHandler` subclasses are interchangeable |
| **Composition** | `LogAnalysisService` composes `LogFileReader`, `LogParser`, and analyzers; `DashboardHandler` composes `DashboardStore` and `LogAnalysisService` |
| **Single Responsibility** | Each class has one job: `LogParser` only parses, `ErrorFrequencyAnalyzer` only counts errors, `FileUploadHandler` only handles uploads |

---

## 6. Collections and HashMap Usage

| Collection | Usage |
|------------|-------|
| `HashMap<String, BuilderHolder>` | `ErrorFrequencyAnalyzer` groups errors by normalized message key; stores mutable `BuilderHolder` objects to accumulate counts |
| `ArrayList<LogEntry>` | Stores parsed log entries in `AnalysisSession`; returned by `session.getEntries()` |
| `ArrayList<ErrorFrequency>` | Returned sorted list of top-N frequent errors |
| `ArrayList<DailyStat>` | Daily statistics grouped by date |
| `ArrayList<Incident>` | Detected incidents |
| `HashMap<LogLevel, Integer>` | `LogStatistics` stores counts per log level using `EnumMap`-like semantics |
| `LinkedHashMap<String, Object>` | API JSON response building in `DashboardHandler` — preserves insertion order for predictable JSON output |
| `HashSet` | Used in parsers for deduplication where needed |

**Why HashMap for error frequency:** O(1) average-case lookup to accumulate counts. The key is the normalized message (whitespace collapsed) combined with service name, so the same error from different services is not incorrectly merged.

---

## 7. Regex-Based Log Parsing

`LogParser.parse(line, lineNumber)` uses regular expressions to extract structured data from unstructured log lines.

**Supported format:**
```
2026-11-23 10:30:45 INFO [AuthService] User login successful
```

**Regex components:**
- Timestamp: `\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}`
- Log level: `TRACE\|DEBUG\|INFO\|WARN\|ERROR\|FATAL`
- Service (optional): `\[([^\]]+)\]`
- Thread (optional): `\[([^\]]+)\]` after service
- Message: everything after the service/thread brackets

**Graceful degradation:** If a line doesn't match the expected pattern, parsing returns a failure result rather than throwing an exception. The caller can count invalid entries separately. This prevents one malformed line from crashing the entire analysis.

**Example ParseResult:**
```java
ParseResult result = LogParser.parse("2026-11-23 10:30:45 ERROR [DatabaseService] Connection timeout", 1);
// result.isSuccess() == true
// result.getEntry().getLevel() == LogLevel.ERROR
// result.getEntry().getService() == "DatabaseService"
// result.getEntry().getMessage() == "Connection timeout"
```

---

## 8. Streaming Large Log Files

**How streaming works:**
- `LogFileReader.readLines(Path)` uses `BufferedReader` with `Files.newBufferedReader(path, UTF_8)`
- It reads one line at a time in a `while ((line = reader.readLine()) != null)` loop
- Each line is parsed immediately and added to the results list
- The entire file is never loaded into memory at once

**Memory implications:**
- Each parsed `LogEntry` object is stored, so memory usage grows with the number of valid entries
- For very large files (50 MB+), this could use significant heap memory
- Parallel processing uses bounded batches to limit per-batch memory

**Why not load the whole file:** `Files.readAllBytes()` would load the entire file into a byte array, risking `OutOfMemoryError` for large files. Streaming is safer and scales better.

---

## 9. ExecutorService and Multithreading

**Sequential mode (`SequentialLogProcessor`):**
- Processes lines one at a time in a single thread
- Simple, deterministic, no thread safety concerns

**Parallel mode (`ParallelLogProcessor`):**
- Uses `ExecutorService` with a fixed thread pool (default 4 threads)
- Splits work into bounded batches of lines
- Each batch is processed by a worker thread
- Results are merged after all workers complete
- No shared mutable state between workers (each worker produces its own result list)

**Thread safety:**
- Workers don't share state; results are combined after all tasks finish
- `ExecutorService.shutdown()` + `awaitTermination()` ensures orderly completion
- Verified by tests: sequential and parallel modes produce identical results

**Trade-off:** Parallel mode has overhead from thread management and batch splitting. For small files, sequential is faster. For large files (100 MB+), parallel can be significantly faster.

---

## 10. Error Frequency Analysis

`ErrorFrequencyAnalyzer.analyze(List<LogEntry>)`:

**Algorithm:**
1. Filter entries: only `LogLevel.ERROR` entries are considered
2. Skip blank/null messages
3. Normalize each message: trim whitespace, collapse multiple spaces into one (`message.trim().replaceAll("\\s+", " ")`)
4. Group by composite key: `serviceName + "||" + normalizedMessage`
   - This prevents merging the same error from different services
5. Use a mutable `BuilderHolder` to accumulate occurrence counts per bucket
6. Sort results by count descending (using `Comparator.naturalOrder()` because `ErrorFrequency` implements `Comparable`)
7. Return top N results (default 10)

**Normalization purpose:** "Connection  timeout" (double space) and "Connection timeout" (single space) are treated as the same error. Whitespace differences from different loggers don't split one error into multiple buckets.

**Preserving original message:** The first-seen original message is kept for display, while the normalized form is used for grouping.

---

## 11. Incident Detection Algorithm

`IncidentDetector.detect(List<LogEntry>)`:

**What is an incident?**
A potential incident is a repeated error pattern — the same error message occurring multiple times within a configurable time window. It is flagged as "potential" because it indicates suspicious repetition, not a confirmed root cause.

**Algorithm:**
1. Filter entries: only ERROR and FATAL levels are considered
2. Skip entries without valid timestamps (can't determine time window)
3. Sort entries by timestamp
4. Use a sliding window approach: for each error, check if the same message appeared `threshold` times within `windowMinutes`
5. When threshold is met, create an `Incident` with:
   - Unique ID
   - Service name
   - Pattern (the error message)
   - Occurrence count
   - First and last occurrence timestamps
   - Window minutes
   - Severity (HIGH, CRITICAL based on count relative to threshold)
   - `potential = true`
6. Sort incidents by severity and count

**Default threshold:** 5 errors within 1 minute.

**Severity levels:**
- `HIGH`: count >= threshold but < threshold * 2
- `CRITICAL`: count >= threshold * 2

**Why not root cause:** Incidents are statistical findings. The tool doesn't know business logic, so it can't confirm what caused the errors. It only flags "this happened suspiciously often."

---

## 12. REST APIs

All endpoints are handled by `DashboardHandler` extending `ApiHandler`.

| Endpoint | Method | Query Params | Response |
|----------|--------|--------------|----------|
| `GET /api/health` | GET | none | `{"status":"ok","service":"LogSentinel","hasSession":bool}` |
| `GET /api/dashboard` | GET | none | Full data: levels, daily stats, errors, incidents, file info |
| `GET /api/logs` | GET | `level`, `keyword` | Filtered log entries list |
| `GET /api/errors` | GET | none | Ranked error frequencies |
| `GET /api/incidents` | GET | none | Detected incidents list |
| `POST /api/upload` | POST | none (multipart body) | Upload result JSON |
| `GET /api/reports/download?type=txt\|csv` | GET | `type` | File download |

**Response format:** All API responses are JSON with `Content-Type: application/json; charset=utf-8`.

**Error handling:** Invalid requests return appropriate HTTP status codes (400, 404, 405, 413, 500) with JSON error bodies. Exception messages are sanitized — internal details are not leaked.

---

## 13. HTTP Status Codes

| Code | When It's Used |
|------|----------------|
| 200 | Successful GET requests (health, dashboard, logs, errors, incidents, reports) |
| 200 | Successful POST upload |
| 400 | Invalid request: missing file, bad extension, missing `type` parameter |
| 404 | Unknown endpoint or no session for reports |
| 405 | Wrong HTTP method (e.g., GET on upload endpoint) |
| 413 | File too large (exceeds 50 MB) |
| 500 | Internal server error (sanitized message, no stack trace leaked) |

---

## 14. CSV Generation and Escaping

`CsvReportGenerator` generates machine-readable CSV reports.

**CSV escaping rules (RFC 4180 compliant):**
- Fields containing commas, double quotes, or newlines are wrapped in double quotes
- Double quotes inside fields are escaped by doubling them (`"` → `""`)
- UTF-8 encoding for Unicode characters
- Standard headers: `Metric,Value`, `Log Level,Count`, `Date,Errors,Warnings,Total`, `Rank,Message,Service,Count,Frequency`

**Example:**
```
Log Level,Count
ERROR,4
WARN,1
```

**Why proper escaping matters:** Log messages often contain commas ("Connection timeout, retrying") or quotes ("user \"admin\" logged in"). Without escaping, these would break CSV parsing in spreadsheets.

---

## 15. Temporary File Handling

**File upload flow:**
1. Multipart request body is parsed in `FileUploadHandler.parseMultipart()`
2. File content is written to a temp file in `java.io.tmpdir` with a UUID-prefixed filename
3. `LogAnalysisService.analyze(tempFilePath)` processes the file
4. The temp file is deleted in a `finally` block using `Files.deleteIfExists()`

**Report generation flow:**
1. `ReportDownloadHandler` creates a unique per-request temp directory (`logsentinel-reports-<UUID>`)
2. Report generators write files into that directory
3. Content is read back and sent as HTTP response
4. The temp directory is cleaned up in a `finally` block using `Files.walk().sorted(Comparator.reverseOrder())`

**Security:** UUID prefixes prevent filename collisions and path traversal. Files are deleted after processing, so nothing persists on disk.

---

## 16. Exception Handling

**Custom exceptions:**
- `LogFileException`: file not found, unreadable file, invalid log format
- `ReportGenerationException`: report directory missing, report write failure

**Handler-level handling:**
- `DashboardHandler.handle()` catches all exceptions and returns HTTP 500 with sanitized message ("Internal server error")
- `FileUploadHandler.handle()` catches `LogFileException` (returns exception message) and generic `Exception` (returns "Upload failed: ...")
- `ReportDownloadHandler.handle()` catches `ReportGenerationException` and generic `Exception`

**CLI-level handling:**
- `Main.run()` catches `LogFileException` and generic `Exception` in the menu loop, prints the message, and continues running

**Design principle:** Never print stack traces to users. Log them internally if needed, but return sanitized messages to prevent information disclosure.

---

## 17. Security and File Upload Validation

| Security Measure | Implementation |
|-----------------|----------------|
| Extension validation | Only `.log` and `.txt` accepted; checked on both client (accept attribute) and server (filename.endsWith) |
| Size limit | 50 MB max; checked server-side before processing |
| Path traversal | `Paths.get(filename).getFileName()` strips directory components; UUID prefix prevents collisions |
| No file execution | Uploaded files are only read as text; never executed |
| No arbitrary filesystem access | API endpoints only access upload temp dir and report temp dir |
| Sanitized errors | Internal exception details not exposed in API responses |
| Port binding | Binds to `0.0.0.0` (required for cloud platforms) but port comes from env var |

**Limitations:** No authentication, no rate limiting, no virus scanning. Single-user dashboard — all users share the same session.

---

## 18. JUnit 5 Testing

**Test count:** 104 tests, 0 failures.

**Test organization (by package):**
- `com.logsentinel.parser.LogParserTest` — valid/invalid parsing, special characters, Unicode, long messages
- `com.logsentinel.analyzer.LogStatisticsAnalyzerTest` — level counting, percentages
- `com.logsentinel.analyzer.ErrorFrequencyAnalyzerTest` — grouping, normalization, blank messages, service separation
- `com.logsentinel.analyzer.DailyStatisticsAnalyzerTest` — daily grouping, busiest day
- `com.logsentinel.analyzer.LogSearchServiceTest` — keyword search
- `com.logsentinel.processor.LogFileReaderTest` — missing files, empty files, permission errors
- `com.logsentinel.processor.ParallelLogProcessorTest` — sequential vs parallel consistency
- `com.logsentinel.incident.IncidentDetectorTest` — threshold, time windows, severity, false positives
- `com.logsentinel.report.ReportGeneratorTest` — TXT/CSV generation, CSV escaping, empty datasets
- `com.logsentinel.web.DashboardHandlerTest` — API endpoints, empty session, 404
- `com.logsentinel.web.DashboardStoreTest` — thread-safe session access
- `com.logsentinel.web.FileUploadHandlerTest` — upload validation
- `com.logsentinel.web.JsonUtilTest` — JSON serialization

**Key test patterns:**
- Arrange-Act-Assert (AAA)
- `assertThrows` for exception testing
- Temporary files created in test temp directory
- Empty dataset handling verified

**How to run:** `mvn clean test`

---

## 19. Maven Build Lifecycle

**pom.xml configuration:**
- Java 17: `maven.compiler.source/target/release = 17`
- UTF-8 encoding
- JUnit 5.10.0 (test scope only)
- `maven-compiler-plugin 3.11.0` with `showWarnings=true`
- `maven-surefire-plugin 3.2.5` with `**/*Test.java` include pattern
- `maven-jar-plugin 3.3.0` with `mainClass = com.logsentinel.Main` for executable JAR

**Commands:**
- `mvn clean test` — clean, compile, run all tests
- `mvn clean package` — clean, compile, test, package into `target/logsentinel-1.0.0.jar`
- `java -cp target/classes com.logsentinel.Main` — run CLI
- `java -cp target/classes com.logsentinel.Main --dashboard` — run dashboard
- `java -jar target/logsentinel-1.0.0.jar --dashboard` — run executable JAR

**No external runtime dependencies:** The JAR runs with just Java 17 JRE. JUnit is test-only scope.

---

## 20. Dockerfile Explanation

**File:** `Dockerfile.vercel`

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/logsentinel-1.0.0.jar app.jar
EXPOSE 8080
ENV PORT=8080
ENTRYPOINT ["java", "-jar", "app.jar", "--dashboard"]
```

**Multi-stage build rationale:**
- **Build stage:** Needs Maven + JDK to compile and package. Uses `maven:3.9-eclipse-temurin-17` which includes both. Runs `mvn clean package -DskipTests` (tests run in CI separately).
- **Runtime stage:** Needs only JRE to run the JAR. Uses `eclipse-temurin:17-jre` — smaller image, no build tools.
- **Artifact transfer:** `COPY --from=build` copies only the JAR, not source code or build output.
- **PORT:** `ENV PORT=8080` provides a default; cloud platforms override at runtime.
- **ENTRYPOINT:** `java -jar app.jar --dashboard` starts the web dashboard automatically.

**Status:** Dockerfile is syntactically correct but container build was not tested locally (Docker daemon unavailable on build machine).

---

## 21. Vercel Deployment Architecture

**Platform:** Vercel Container Deployment (Docker/OCI)

**Components:**
- `Dockerfile.vercel` — multi-stage Docker build (Maven build → JRE runtime)
- `.dockerignore` — excludes `target/`, `.git/`, IDE files, build artifacts
- `.vercelignore` — excludes same items from Vercel upload
- `Dockerfile.vercel` is auto-detected by Vercel when Container Deployment is enabled

**Runtime behavior:**
- Vercel provides `PORT` environment variable at runtime
- Server reads `PORT`, binds to `0.0.0.0`
- `DashboardStore` holds session in memory — cleared on container restart
- Not shared across scaled instances

**Production URL:** https://logsentinel-three.vercel.app

**Required steps for public access:**
1. Enable Container Deployment in Vercel dashboard (Settings → General)
2. Disable SSO/password protection: `vercel project protection disable --sso --yes`

**Status:** Deployed and verified. Health endpoint returns HTTP 200. Root page loads. File upload and report download verified.

---

## 22. Current Limitations

| Limitation | Impact | Mitigation |
|------------|--------|------------|
| In-memory session storage | Data lost on container restart; not shared across instances | Documented; acceptable for single-user/demo use |
| No authentication | Anyone can access the dashboard | Acceptable for portfolio project |
| No rate limiting | Potential abuse via repeated uploads | Acceptable for portfolio project |
| 50 MB file size limit | Larger files rejected | Configurable constant; can be increased |
| `.log`/`.txt` only | Other formats not supported | Matches project scope |
| Docker not tested locally | Container runtime unverified | Dockerfile follows best practices |
| No persistent database | No history across restarts | Intentional design choice (no external dependencies) |

---

## 23. Possible Future Improvements

1. **Persistent storage:** Add a database (PostgreSQL, MongoDB) or file-based history for session persistence across restarts
2. **Multi-user support:** Session isolation per user with authentication
3. **Configurable thresholds:** Properties file for incident detection threshold, time window, file size limit
4. **Log tailing:** Watch mode for real-time log file monitoring
5. **Alerts:** Email/webhook notifications for critical incidents
6. **Export formats:** JSON export, PDF reports
7. **Parser configuration:** Regex patterns configurable via properties file
8. **Performance:** Streaming CSV generation for very large reports (avoid loading full content into memory)

---

## 24. Repository Information

- **GitHub:** https://github.com/Hhari1234/LogSentinel
- **Live URL:** https://logsentinel-three.vercel.app
- **Latest commit:** `9c77523`
- **Branch:** `main`
- **Author:** Hhari1234
- **Purpose:** Educational log analysis and monitoring project for portfolio and interview preparation
