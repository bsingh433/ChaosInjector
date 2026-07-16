package com.chaosinjector.metrics;

/**
 * The experiment phase a metric sample belongs to (spec §9). BASELINE is the
 * pre-injection window, ACTIVE is while the fault is applied, POST is after
 * revert (used to measure recovery).
 */
public enum MetricPhase {
    BASELINE,
    ACTIVE,
    POST
}
