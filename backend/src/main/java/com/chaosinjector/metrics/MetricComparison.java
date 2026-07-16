package com.chaosinjector.metrics;

/**
 * Baseline-vs-active comparison for a single metric (spec §9).
 *
 * @param name          metric name (e.g. "cpuPercent")
 * @param baselineMean  mean during BASELINE
 * @param activeMean    mean during ACTIVE
 * @param delta         activeMean - baselineMean
 * @param percentChange percentage change relative to baseline
 */
public record MetricComparison(String name, double baselineMean, double activeMean,
                               double delta, double percentChange) {

    public static MetricComparison of(String name, double baselineMean, double activeMean) {
        double delta = activeMean - baselineMean;
        double pct;
        if (baselineMean != 0) {
            pct = delta / baselineMean * 100.0;
        } else {
            pct = activeMean != 0 ? 100.0 : 0.0;
        }
        return new MetricComparison(name, baselineMean, activeMean, delta, pct);
    }
}
