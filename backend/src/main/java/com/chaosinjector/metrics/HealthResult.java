package com.chaosinjector.metrics;

/**
 * Result of one health-check probe. {@code reachable} is false on
 * timeout/connection error; {@code httpStatus} is null when unreachable.
 */
public record HealthResult(boolean reachable, Integer httpStatus, long latencyMs) {

    /** Healthy = reachable with a non-error (&lt; 400) status. */
    public boolean healthy() {
        return reachable && httpStatus != null && httpStatus < 400;
    }
}
