package com.chaosinjector.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.chaosinjector.support.FakeTargetAdapter;
import com.chaosinjector.target.model.Stats;

class PhaseSamplerTest {

    @Test
    void collectsSamplesTaggedByCurrentPhase() {
        AtomicInteger tick = new AtomicInteger();
        FakeTargetAdapter adapter = new FakeTargetAdapter()
                .withWorkload("c1", "web", "img")
                .withStats(() -> new Stats(Instant.now(), tick.incrementAndGet(), 0, 0, 0, 0, 0, 0, 0, 0));

        MetricsCollector collector = new MetricsCollector(adapter, new HealthProbe(500));
        PhaseSampler sampler = new PhaseSampler(collector, "c1", null, 20);

        sampler.start();
        await().atMost(Duration.ofSeconds(2)).until(() ->
                sampler.getSamples().stream().anyMatch(s -> s.phase() == MetricPhase.BASELINE));
        sampler.setPhase(MetricPhase.ACTIVE);
        await().atMost(Duration.ofSeconds(2)).until(() ->
                sampler.getSamples().stream().anyMatch(s -> s.phase() == MetricPhase.ACTIVE));
        sampler.stop();

        assertThat(sampler.getSamples()).isNotEmpty();
        assertThat(sampler.getSamples()).anyMatch(s -> s.phase() == MetricPhase.BASELINE);
        assertThat(sampler.getSamples()).anyMatch(s -> s.phase() == MetricPhase.ACTIVE);
        assertThat(sampler.getSamples()).allMatch(s -> s.stats() != null);
    }
}
