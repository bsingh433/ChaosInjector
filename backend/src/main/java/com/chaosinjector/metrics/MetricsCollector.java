package com.chaosinjector.metrics;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chaosinjector.target.TargetAdapter;
import com.chaosinjector.target.model.Stats;

/**
 * Takes a single {@link MetricSample}: container stats from the {@link
 * TargetAdapter} plus an optional health probe. A failed stats read is logged
 * and yields a sample with null stats rather than aborting the run (spec §9,
 * §15) — collection must survive a chaotic target.
 */
public class MetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(MetricsCollector.class);

    private final TargetAdapter adapter;
    private final HealthProbe probe;

    public MetricsCollector(TargetAdapter adapter, HealthProbe probe) {
        this.adapter = adapter;
        this.probe = probe;
    }

    public MetricSample sampleOnce(String containerId, String healthUrl, MetricPhase phase) {
        Stats stats = null;
        try {
            stats = adapter.sampleStats(containerId);
        } catch (RuntimeException e) {
            log.warn("Stats sample failed for {} in phase {}: {}", containerId, phase, e.getMessage());
        }
        HealthResult health = null;
        if (healthUrl != null && !healthUrl.isBlank()) {
            health = probe.probe(healthUrl);
        }
        return new MetricSample(Instant.now(), phase, stats, health);
    }
}
