package com.chaosinjector.target.model;

import java.time.Instant;

/**
 * One resource-usage sample for a workload (spec §9). {@code cpuPercent} is
 * computed the same way the {@code docker stats} CLI does (delta of container
 * CPU over delta of system CPU, scaled by online CPUs).
 */
public record Stats(
        Instant timestamp,
        double cpuPercent,
        long memoryUsageBytes,
        long memoryLimitBytes,
        double memoryPercent,
        long netRxBytes,
        long netTxBytes,
        long blkReadBytes,
        long blkWriteBytes,
        long pids) {
}
