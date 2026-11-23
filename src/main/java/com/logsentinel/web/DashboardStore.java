package com.logsentinel.web;

import com.logsentinel.model.AnalysisSession;

/**
 * Thread-safe holder for the most recent analysis session.
 * The dashboard reads from this store; uploads replace its contents.
 */
public final class DashboardStore {

    private volatile AnalysisSession session;
    private final Object lock = new Object();

    public DashboardStore() {
        this.session = null;
    }

    public AnalysisSession getSession() {
        return session;
    }

    public void setSession(AnalysisSession session) {
        synchronized (lock) {
            this.session = session;
        }
    }

    public boolean hasSession() {
        return session != null;
    }
}
