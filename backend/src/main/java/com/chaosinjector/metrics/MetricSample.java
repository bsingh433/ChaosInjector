package com.chaosinjector.metrics;

import java.time.Instant;

import com.chaosinjector.target.model.Stats;

/**
 * One point-in-time sample: container resource stats plus an optional health
 * probe (null when no health-check URL was supplied), tagged with the phase it
 * was taken in.
 */
public record MetricSample(Instant timestamp, MetricPhase phase, Stats stats, HealthResult health) {
}
