package com.chaosinjector.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.chaosinjector.target.model.Stats;

class ImpactReportCalculatorTest {

    private MetricSample sample(Instant t, MetricPhase phase, double cpu, double memPct, HealthResult health) {
        Stats stats = new Stats(t, cpu, 0, 0, memPct, 0, 0, 0, 0, 0);
        return new MetricSample(t, phase, stats, health);
    }

    @Test
    void computesDeltasAvailabilityAndDegradedVerdict() {
        Instant t0 = Instant.parse("2026-07-16T10:00:00Z");
        List<MetricSample> samples = new ArrayList<>();
        // baseline: cpu ~10, healthy fast
        samples.add(sample(t0, MetricPhase.BASELINE, 10, 20, new HealthResult(true, 200, 20)));
        samples.add(sample(t0.plusSeconds(1), MetricPhase.BASELINE, 12, 22, new HealthResult(true, 200, 25)));
        // active: cpu ~90, still healthy but slow
        samples.add(sample(t0.plusSeconds(2), MetricPhase.ACTIVE, 88, 60, new HealthResult(true, 200, 300)));
        samples.add(sample(t0.plusSeconds(3), MetricPhase.ACTIVE, 92, 62, new HealthResult(true, 200, 350)));

        ImpactReport r = ImpactReportCalculator.compute(samples);

        assertThat(r.cpu().baselineMean()).isCloseTo(11, within(0.001));
        assertThat(r.cpu().activeMean()).isCloseTo(90, within(0.001));
        assertThat(r.cpu().delta()).isCloseTo(79, within(0.001));
        assertThat(r.availabilityDuringActive()).isCloseTo(1.0, within(0.001));
        assertThat(r.latencyActiveP95()).isGreaterThanOrEqualTo(300);
        assertThat(r.baselineSamples()).isEqualTo(2);
        assertThat(r.activeSamples()).isEqualTo(2);
        assertThat(r.verdict()).isEqualTo("Application degraded but stayed available.");
    }

    @Test
    void unavailableServiceProducesLowAvailabilityAndRecovery() {
        Instant t0 = Instant.parse("2026-07-16T10:00:00Z");
        List<MetricSample> samples = new ArrayList<>();
        samples.add(sample(t0, MetricPhase.BASELINE, 5, 10, new HealthResult(true, 200, 15)));
        // active: unreachable
        samples.add(sample(t0.plusSeconds(1), MetricPhase.ACTIVE, 5, 10, new HealthResult(false, null, 3000)));
        samples.add(sample(t0.plusSeconds(2), MetricPhase.ACTIVE, 5, 10, new HealthResult(false, null, 3000)));
        // post: recovers after 2s
        samples.add(sample(t0.plusSeconds(3), MetricPhase.POST, 5, 10, new HealthResult(false, null, 3000)));
        samples.add(sample(t0.plusSeconds(5), MetricPhase.POST, 5, 10, new HealthResult(true, 200, 20)));

        ImpactReport r = ImpactReportCalculator.compute(samples);

        assertThat(r.availabilityDuringActive()).isEqualTo(0.0);
        assertThat(r.recoverySeconds()).isCloseTo(2.0, within(0.001));
        assertThat(r.verdict()).contains("unavailable");
        assertThat(r.verdict()).contains("Recovered");
    }

    @Test
    void noHealthUrlLeavesAvailabilityNull() {
        Instant t0 = Instant.parse("2026-07-16T10:00:00Z");
        List<MetricSample> samples = List.of(
                sample(t0, MetricPhase.BASELINE, 10, 10, null),
                sample(t0.plusSeconds(1), MetricPhase.ACTIVE, 11, 11, null));

        ImpactReport r = ImpactReportCalculator.compute(samples);

        assertThat(r.availabilityDuringActive()).isNull();
        assertThat(r.recoverySeconds()).isNull();
        assertThat(r.verdict()).isEqualTo("No measurable impact.");
    }

    @Test
    void emptySamplesDoNotThrow() {
        ImpactReport r = ImpactReportCalculator.compute(List.of());
        assertThat(r.baselineSamples()).isZero();
        assertThat(r.verdict()).isEqualTo("No measurable impact.");
    }
}
