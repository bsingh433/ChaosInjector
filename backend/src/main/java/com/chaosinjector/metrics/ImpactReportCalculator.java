package com.chaosinjector.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure computation of an {@link ImpactReport} from collected {@link
 * MetricSample}s (spec §9). No I/O — fully unit-testable without a daemon.
 */
public final class ImpactReportCalculator {

    private ImpactReportCalculator() {
    }

    public static ImpactReport compute(List<MetricSample> samples) {
        List<MetricSample> baseline = byPhase(samples, MetricPhase.BASELINE);
        List<MetricSample> active = byPhase(samples, MetricPhase.ACTIVE);
        List<MetricSample> post = byPhase(samples, MetricPhase.POST);

        MetricComparison cpu = MetricComparison.of("cpuPercent",
                mean(baseline, s -> s.stats() != null ? s.stats().cpuPercent() : 0),
                mean(active, s -> s.stats() != null ? s.stats().cpuPercent() : 0));

        MetricComparison mem = MetricComparison.of("memoryPercent",
                mean(baseline, s -> s.stats() != null ? s.stats().memoryPercent() : 0),
                mean(active, s -> s.stats() != null ? s.stats().memoryPercent() : 0));

        List<Double> baseLatency = latencies(baseline);
        List<Double> activeLatency = latencies(active);
        MetricComparison latency = MetricComparison.of("latencyMs",
                mean(baseLatency), mean(activeLatency));
        double p95 = percentile(activeLatency, 95);

        Double availability = availability(active);
        Double recovery = recoverySeconds(post);
        String verdict = verdict(cpu, mem, latency, availability, recovery);

        return new ImpactReport(cpu, mem, latency, p95, availability, recovery, verdict,
                baseline.size(), active.size());
    }

    // --- helpers ---

    private static List<MetricSample> byPhase(List<MetricSample> samples, MetricPhase phase) {
        List<MetricSample> out = new ArrayList<>();
        for (MetricSample s : samples) {
            if (s.phase() == phase) {
                out.add(s);
            }
        }
        return out;
    }

    private interface ToDouble {
        double apply(MetricSample s);
    }

    private static double mean(List<MetricSample> samples, ToDouble f) {
        if (samples.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (MetricSample s : samples) {
            sum += f.apply(s);
        }
        return sum / samples.size();
    }

    private static double mean(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.size();
    }

    /** Latencies for samples that had a reachable health probe. */
    private static List<Double> latencies(List<MetricSample> samples) {
        List<Double> out = new ArrayList<>();
        for (MetricSample s : samples) {
            if (s.health() != null && s.health().reachable()) {
                out.add((double) s.health().latencyMs());
            }
        }
        return out;
    }

    private static double percentile(List<Double> values, double p) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        idx = Math.max(0, Math.min(sorted.size() - 1, idx));
        return sorted.get(idx);
    }

    /** Fraction of ACTIVE probes that were healthy, or null if no health data. */
    private static Double availability(List<MetricSample> active) {
        int withHealth = 0;
        int healthy = 0;
        for (MetricSample s : active) {
            if (s.health() != null) {
                withHealth++;
                if (s.health().healthy()) {
                    healthy++;
                }
            }
        }
        return withHealth == 0 ? null : (double) healthy / withHealth;
    }

    /** Seconds from the first POST sample to the first healthy POST sample. */
    private static Double recoverySeconds(List<MetricSample> post) {
        boolean anyHealth = post.stream().anyMatch(s -> s.health() != null);
        if (post.isEmpty() || !anyHealth) {
            return null;
        }
        var start = post.get(0).timestamp();
        for (MetricSample s : post) {
            if (s.health() != null && s.health().healthy()) {
                return Math.max(0, (s.timestamp().toEpochMilli() - start.toEpochMilli()) / 1000.0);
            }
        }
        return null; // never recovered within the observation window
    }

    private static String verdict(MetricComparison cpu, MetricComparison mem,
                                  MetricComparison latency, Double availability, Double recovery) {
        if (availability != null && availability < 0.5) {
            String rec = recovery != null
                    ? String.format(" Recovered ~%.1fs after revert.", recovery)
                    : " Did not recover within the observation window.";
            return "Service was unavailable for most of the injection." + rec;
        }
        if (availability != null && availability < 0.99) {
            return "Service intermittently failed under chaos.";
        }
        boolean cpuUp = cpu.activeMean() > cpu.baselineMean() * 1.2 + 5;
        boolean memUp = mem.activeMean() > mem.baselineMean() * 1.2 + 2;
        boolean latUp = latency.activeMean() > latency.baselineMean() * 1.5
                && latency.activeMean() - latency.baselineMean() > 20;
        if (cpuUp || memUp || latUp) {
            return "Application degraded but stayed available.";
        }
        return "No measurable impact.";
    }
}
