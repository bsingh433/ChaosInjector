package com.chaosinjector.metrics;

/**
 * The computed impact of an experiment (spec §9). All values derive from real
 * samples. {@code availabilityDuringActive} and {@code recoverySeconds} are null
 * when no health-check URL was configured.
 *
 * @param cpu                      CPU% baseline-vs-active
 * @param memoryPercent            memory% baseline-vs-active
 * @param latencyMs                health-probe latency baseline-vs-active
 * @param latencyActiveP95         p95 latency during ACTIVE (ms)
 * @param availabilityDuringActive fraction of ACTIVE probes that were healthy (0..1), or null
 * @param recoverySeconds          time from revert to first healthy probe, or null
 * @param verdict                  qualitative summary
 * @param baselineSamples          number of BASELINE samples
 * @param activeSamples            number of ACTIVE samples
 */
public record ImpactReport(
        MetricComparison cpu,
        MetricComparison memoryPercent,
        MetricComparison latencyMs,
        double latencyActiveP95,
        Double availabilityDuringActive,
        Double recoverySeconds,
        String verdict,
        int baselineSamples,
        int activeSamples) {
}
