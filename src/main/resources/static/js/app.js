/*
 * LogSentinel Dashboard Frontend
 * Vanilla JavaScript - no frameworks
 */

const API_BASE = '';
let currentData = null;

// DOM elements
const navItems = document.querySelectorAll('.nav-item');
const contentSections = document.querySelectorAll('.content-section');
const statusDot = document.querySelector('.status-dot');
const statusText = document.getElementById('statusText');

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    setupNavigation();
    checkHealth();
    setupFileUpload();
    setInterval(checkHealth, 30000);
});

// Navigation
function setupNavigation() {
    navItems.forEach(item => {
        item.addEventListener('click', (e) => {
            e.preventDefault();
            const section = item.getAttribute('data-section');
            switchSection(section);
        });
    });
}

function switchSection(section) {
    // Update nav
    navItems.forEach(item => {
        item.classList.remove('active');
        if (item.getAttribute('data-section') === section) {
            item.classList.add('active');
        }
    });

    // Update sections
    contentSections.forEach(sec => {
        sec.classList.remove('active');
        if (sec.id === section) {
            sec.classList.add('active');
        }
    });

    // Update header
    const titles = {
        overview: ['Overview', 'Real-time log analysis and incident detection'],
        analytics: ['Log Analytics', 'Distribution of log levels and daily activity'],
        errors: ['Error Monitoring', 'Most frequently occurring error messages'],
        incidents: ['Incident Detection', 'Potential incidents from repeated error patterns'],
        explorer: ['Log Explorer', 'Search and filter parsed log entries'],
        upload: ['Upload Log File', 'Upload a .log or .txt file for analysis'],
        reports: ['Reports', 'Download analysis reports in text or CSV format']
    };
    const [title, subtitle] = titles[section] || ['Overview', ''];
    document.getElementById('pageTitle').textContent = title;
    document.getElementById('pageSubtitle').textContent = subtitle;

    // Load data for relevant sections
    if (currentData) {
        if (section === 'overview') renderOverview(currentData);
        if (section === 'analytics') renderAnalytics(currentData);
        if (section === 'errors') renderErrors(currentData);
        if (section === 'incidents') renderIncidents(currentData);
        if (section === 'explorer') renderLogs(currentData);
    }
}

// Health check
async function checkHealth() {
    try {
        const res = await fetch(API_BASE + '/api/health');
        if (res.ok) {
            const data = await res.json();
            setBackendStatus(data.hasSession ? 'connected' : 'connecting');
            if (data.hasSession) {
                loadDashboard();
            }
        } else {
            setBackendStatus('error');
        }
    } catch (e) {
        setBackendStatus('error');
    }
}

function setBackendStatus(state) {
    if (state === 'connected') {
        statusDot.classList.add('connected');
        statusText.textContent = 'Backend Connected';
    } else {
        statusDot.classList.remove('connected');
        statusText.textContent = state === 'error' ? 'Backend Unavailable' : 'Connecting...';
    }
}

// Load dashboard data
async function loadDashboard() {
    try {
        const res = await fetch(API_BASE + '/api/dashboard');
        if (res.ok) {
            currentData = await res.json();
            renderOverview(currentData);
            renderAnalytics(currentData);
            renderErrors(currentData);
            renderIncidents(currentData);
            renderLogs(currentData);
        }
    } catch (e) {
        console.error('Failed to load dashboard:', e);
    }
}

// Renderers
function renderOverview(data) {
    if (!data.hasSession) return;
    document.getElementById('metricTotal').textContent = data.totalLines.toLocaleString();
    document.getElementById('metricInfo').textContent = (data.levelCounts.INFO || 0).toLocaleString();
    document.getElementById('metricWarn').textContent = (data.levelCounts.WARN || 0).toLocaleString();
    document.getElementById('metricError').textContent = (data.levelCounts.ERROR || 0).toLocaleString();
    document.getElementById('metricFatal').textContent = (data.levelCounts.FATAL || 0).toLocaleString();
    document.getElementById('metricDebug').textContent = (data.levelCounts.DEBUG || 0).toLocaleString();
    document.getElementById('metricTrace').textContent = (data.levelCounts.TRACE || 0).toLocaleString();
    document.getElementById('metricUnknown').textContent = (data.levelCounts.UNKNOWN || 0).toLocaleString();
    document.getElementById('metricIncidents').textContent = (data.incidents && data.incidents.length) || 0;
}

function renderAnalytics(data) {
    if (!data.hasSession) return;

    const levelCounts = data.levelCounts || {};
    const levels = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL', 'UNKNOWN'];
    const total = data.totalLines || 1;

    // Bar chart - Log Level Distribution
    const chartEl = document.getElementById('levelChart');
    chartEl.innerHTML = '';
    const maxCount = Math.max(...levels.map(l => levelCounts[l] || 0));
    levels.forEach(level => {
        const count = levelCounts[level] || 0;
        const pct = total > 0 ? (count / total * 100) : 0;
        const bar = document.createElement('div');
        bar.className = 'metric-bar';
        bar.style.height = (maxCount > 0 ? (count / maxCount * 100) : 0) + '%';
        bar.title = level + ': ' + count + ' (' + pct.toFixed(1) + '%)';
        const color = getLevelColor(level);
        bar.innerHTML =
            '<div class="bar-fill" style="background-color:' + color + ';"></div>' +
            '<div class="bar-label">' + escapeHtml(level) + '</div>' +
            '<div class="bar-value">' + count.toLocaleString() + '</div>';
        chartEl.appendChild(bar);
    });

    // Level table
    const tbody = document.getElementById('levelTableBody');
    tbody.innerHTML = '';
    levels.forEach(level => {
        const count = levelCounts[level] || 0;
        const pct = total > 0 ? ((count / total) * 100).toFixed(1) : 0;
        const row = document.createElement('tr');
        row.innerHTML =
            '<td><span class="level-badge level-' + escapeHtml(level) + '">' + escapeHtml(level) + '</span></td>' +
            '<td>' + count.toLocaleString() + '</td>' +
            '<td>' + pct + '%</td>' +
            '<td><div class="distribution-bar"><div class="distribution-fill" style="width:' + pct + '%;background-color:' + getLevelColor(level) + ';"></div></div></td>';
        tbody.appendChild(row);
    });

    // Daily chart
    renderDailyChart(data.dailyStats || []);
}

function getLevelColor(level) {
    const colors = {
        INFO: '#22d3ee', WARN: '#fbbf24', ERROR: '#f87171',
        FATAL: '#ef4444', DEBUG: '#94a3b8', TRACE: '#94a3b8', UNKNOWN: '#94a3b8'
    };
    return colors[level] || '#94a3b8';
}

function renderDailyChart(dailyStats) {
    const chartEl = document.getElementById('dailyChart');
    chartEl.innerHTML = '';
    if (!dailyStats || dailyStats.length === 0) {
        chartEl.innerHTML = '<div style="text-align:center;color:var(--text-secondary);padding:2rem;">No daily data</div>';
        return;
    }
    const maxErrors = Math.max(...dailyStats.map(d => d.errors), 1);
    dailyStats.forEach(stat => {
        const bar = document.createElement('div');
        bar.className = 'daily-bar';
        bar.style.height = ((stat.errors / maxErrors) * 100) + '%';
        bar.title = stat.date + ': ' + stat.errors + ' errors, ' + stat.warnings + ' warnings';
        bar.innerHTML =
            '<div class="bar-fill" style="background-color:var(--accent);"></div>' +
            '<div class="bar-label">' + escapeHtml(stat.date) + '</div>';
        chartEl.appendChild(bar);
    });
}

function renderErrors(data) {
    const errors = data.errorFrequencies || [];
    const tbody = document.getElementById('errorTableBody');
    const empty = document.getElementById('errorsEmpty');
    const tablePanel = document.querySelector('#errors .table-panel');

    if (errors.length === 0) {
        tbody.innerHTML = '';
        tablePanel.style.display = 'none';
        empty.style.display = 'block';
        return;
    }

    tablePanel.style.display = 'block';
    empty.style.display = 'none';
    tbody.innerHTML = '';
    errors.forEach(err => {
        const pct = data.totalLines > 0 ? ((err.count / data.totalLines) * 100).toFixed(1) : 0;
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${err.rank}</td>
            <td>${escapeHtml(err.message)}</td>
            <td>${escapeHtml(err.service)}</td>
            <td><strong>${err.count.toLocaleString()}</strong></td>
            <td>${pct}%</td>
        `;
        tbody.appendChild(row);
    });
}

function renderIncidents(data) {
    const incidents = data.incidents || [];
    const tbody = document.getElementById('incidentTableBody');
    const empty = document.getElementById('incidentsEmpty');
    const tablePanel = document.querySelector('#incidents .table-panel');

    if (incidents.length === 0) {
        tbody.innerHTML = '';
        tablePanel.style.display = 'none';
        empty.style.display = 'block';
        return;
    }

    tablePanel.style.display = 'block';
    empty.style.display = 'none';
    tbody.innerHTML = '';
    incidents.forEach(inc => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td><code>${escapeHtml(inc.id)}</code></td>
            <td>${escapeHtml(inc.service)}</td>
            <td>${escapeHtml(inc.pattern)}</td>
            <td><strong>${inc.occurrences}</strong></td>
            <td>${escapeHtml(inc.firstOccurrence)}</td>
            <td>${escapeHtml(inc.lastOccurrence)}</td>
            <td><span class="severity-badge severity-${inc.severity.toLowerCase()}">${inc.severity}</span></td>
            <td>${inc.potential ? '<span style="color:var(--warn)">Potential</span>' : 'Pattern'}</td>
        `;
        tbody.appendChild(row);
    });
}

function renderLogs(data) {
    const levelFilter = document.getElementById('levelFilter').value;
    const keyword = document.getElementById('keywordFilter').value.toLowerCase();
    const entries = data.logs || [];
    const tbody = document.getElementById('logTableBody');
    const empty = document.getElementById('logsEmpty');
    const tablePanel = document.querySelector('#explorer .table-panel');
    const countEl = document.getElementById('logShown');
    const totalEl = document.getElementById('logTotal');

    const filtered = entries.filter(entry => {
        if (levelFilter && entry.level !== levelFilter) return false;
        if (keyword) {
            const msg = (entry.message || '').toLowerCase();
            const svc = (entry.service || '').toLowerCase();
            return msg.includes(keyword) || svc.includes(keyword);
        }
        return true;
    });

    totalEl.textContent = (data.total || 0).toLocaleString();
    countEl.textContent = filtered.length.toLocaleString();

    if (filtered.length === 0) {
        tbody.innerHTML = '';
        tablePanel.style.display = 'none';
        empty.style.display = 'block';
        return;
    }

    tablePanel.style.display = 'block';
    empty.style.display = 'none';
    tbody.innerHTML = '';
    filtered.forEach(entry => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${entry.lineNumber}</td>
            <td>${escapeHtml(entry.timestamp)}</td>
            <td><span class="level-badge level-${entry.level}">${entry.level}</span></td>
            <td>${escapeHtml(entry.service)}</td>
            <td>${escapeHtml(entry.thread)}</td>
            <td>${escapeHtml(entry.message)}</td>
            <td>${escapeHtml(entry.exceptionName)}</td>
        `;
        tbody.appendChild(row);
    });
}

// File Upload
function setupFileUpload() {
    const dropzone = document.getElementById('dropzone');
    const fileInput = document.getElementById('fileInput');
    const result = document.getElementById('uploadResult');

    dropzone.addEventListener('click', () => fileInput.click());

    dropzone.addEventListener('dragover', (e) => {
        e.preventDefault();
        dropzone.classList.add('drag-over');
    });

    dropzone.addEventListener('dragleave', () => {
        dropzone.classList.remove('drag-over');
    });

    dropzone.addEventListener('drop', (e) => {
        e.preventDefault();
        dropzone.classList.remove('drag-over');
        const files = e.dataTransfer.files;
        if (files.length > 0) handleFile(files[0]);
    });

    fileInput.addEventListener('change', (e) => {
        if (e.target.files.length > 0) handleFile(e.target.files[0]);
    });
}

async function handleFile(file) {
    const dropzone = document.getElementById('dropzone');
    const result = document.getElementById('uploadResult');

    const lower = file.name.toLowerCase();
    if (!lower.endsWith('.log') && !lower.endsWith('.txt')) {
        showUploadResult('error', 'Invalid file type. Only .log and .txt files are accepted.');
        return;
    }

    if (file.size > 50 * 1024 * 1024) {
        showUploadResult('error', 'File too large. Maximum size is 50 MB.');
        return;
    }

    dropzone.innerHTML = '<div class="upload-icon" style="font-size:2rem;">&#128352;</div><div class="upload-text"><strong>Analyzing file...</strong><p>Please wait while the log file is processed.</p></div>';

    const formData = new FormData();
    formData.append('file', file);

    try {
        const res = await fetch(API_BASE + '/api/upload', {
            method: 'POST',
            body: formData
        });
        const data = await res.json();

        if (!res.ok) {
            showUploadResult('error', data.error || 'Upload failed');
            checkHealth();
            return;
        }

        currentData = await fetch(API_BASE + '/api/dashboard').then(r => r.json());
        showUploadResult('success', 'File analyzed successfully', data);
        checkHealth();
        // Refresh current section
        const activeSection = document.querySelector('.nav-item.active').getAttribute('data-section');
        if (currentData.hasSession) {
            if (activeSection === 'overview') renderOverview(currentData);
            if (activeSection === 'analytics') renderAnalytics(currentData);
            if (activeSection === 'errors') renderErrors(currentData);
            if (activeSection === 'incidents') renderIncidents(currentData);
            if (activeSection === 'explorer') renderLogs(currentData);
        }
    } catch (e) {
        showUploadResult('error', 'Upload failed: ' + e.message);
    } finally {
        dropzone.innerHTML = '<div class="upload-icon">&#128247;</div><div class="upload-text"><strong>Drag and drop a log file here</strong><p>or click to browse</p><p class="upload-hint">Supports .log and .txt files (max 50 MB)</p></div>';
    }
}

function showUploadResult(type, title, data) {
    const result = document.getElementById('uploadResult');
    const isSuccess = type === 'success';
    result.style.backgroundColor = isSuccess
        ? 'rgba(74, 222, 128, 0.1)'
        : 'rgba(248, 113, 113, 0.1)';
    result.style.border = isSuccess
        ? '1px solid var(--success)'
        : '1px solid var(--error)';
    result.style.display = 'block';
    result.innerHTML = `
        <h4 style="color:${isSuccess ? 'var(--success)' : 'var(--error)'}">${title}</h4>
        ${data ? `<p><strong>File:</strong> ${escapeHtml(data.fileName)}</p>
        <p><strong>Total lines:</strong> ${data.totalLines.toLocaleString()}</p>
        <p><strong>Valid entries:</strong> ${data.validEntries.toLocaleString()}</p>
        <p><strong>Errors:</strong> ${data.errorCount.toLocaleString()}</p>
        <p><strong>Warnings:</strong> ${data.warningCount.toLocaleString()}</p>
        <p><strong>Incidents:</strong> ${data.incidentCount}</p>` : `<p>${escapeHtml(title)}</p>`}
    `;
    setTimeout(() => { result.style.display = 'none'; }, 8000);
}

// Filters
document.getElementById('levelFilter').addEventListener('change', () => {
    if (currentData) renderLogs(currentData);
});
document.getElementById('keywordFilter').addEventListener('input', () => {
    if (currentData) renderLogs(currentData);
});
document.getElementById('clearFilters').addEventListener('click', () => {
    document.getElementById('levelFilter').value = '';
    document.getElementById('keywordFilter').value = '';
    if (currentData) renderLogs(currentData);
});

// Reports
async function downloadReport(type) {
    if (!currentData || !currentData.hasSession) {
        showReportMessage('error', 'No analysis session available. Upload a log file first.');
        return;
    }
    try {
        const res = await fetch(API_BASE + '/api/reports/download?type=' + type);
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            showReportMessage('error', err.error || 'Report download failed');
            return;
        }
        const blob = await res.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'logsentinel-report.' + type;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);
        showReportMessage('success', type.toUpperCase() + ' report downloaded successfully.');
    } catch (e) {
        showReportMessage('error', 'Download failed: ' + e.message);
    }
}

function showReportMessage(type, message) {
    const el = document.getElementById('reportMessage');
    el.style.backgroundColor = type === 'success'
        ? 'rgba(74, 222, 128, 0.1)'
        : 'rgba(248, 113, 113, 0.1)';
    el.style.border = type === 'success'
        ? '1px solid var(--success)'
        : '1px solid var(--error)';
    el.textContent = message;
    el.classList.add('show');
    setTimeout(() => el.classList.remove('show'), 5000);
}

// Utility
function escapeHtml(text) {
    if (text == null) return '';
    return String(text)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}
