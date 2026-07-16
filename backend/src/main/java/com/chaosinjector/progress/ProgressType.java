package com.chaosinjector.progress;

/** SSE event kinds (spec §10). */
public enum ProgressType {
    PHASE,
    SAMPLE,
    LOG,
    COMPLETED,
    ERROR
}
