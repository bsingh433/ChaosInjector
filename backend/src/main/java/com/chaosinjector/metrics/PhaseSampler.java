package com.chaosinjector.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Runs a {@link MetricsCollector} on a fixed interval in the background, tagging
 * every sample with the current {@link MetricPhase}. The experiment orchestrator
 * flips the phase (BASELINE → ACTIVE → POST) as the run progresses and reads the
 * accumulated samples at the end to build the {@link ImpactReport}.
 */
public class PhaseSampler {

    private final MetricsCollector collector;
    private final String containerId;
    private final String healthUrl;
    private final long intervalMs;
    private final List<MetricSample> samples = Collections.synchronizedList(new ArrayList<>());

    private volatile MetricPhase phase = MetricPhase.BASELINE;
    private volatile Consumer<MetricSample> listener = s -> { };
    private ScheduledExecutorService exec;

    public PhaseSampler(MetricsCollector collector, String containerId, String healthUrl, long intervalMs) {
        this.collector = collector;
        this.containerId = containerId;
        this.healthUrl = healthUrl;
        this.intervalMs = intervalMs;
    }

    public void onSample(Consumer<MetricSample> listener) {
        this.listener = listener == null ? s -> { } : listener;
    }

    public void setPhase(MetricPhase phase) {
        this.phase = phase;
    }

    public synchronized void start() {
        if (exec != null) {
            return;
        }
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "metrics-sampler-" + containerId);
            t.setDaemon(true);
            return t;
        });
        exec.scheduleAtFixedRate(this::tick, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void tick() {
        try {
            MetricSample s = collector.sampleOnce(containerId, healthUrl, phase);
            samples.add(s);
            listener.accept(s);
        } catch (RuntimeException ignored) {
            // never let a sampling error kill the scheduled task
        }
    }

    public synchronized void stop() {
        if (exec != null) {
            exec.shutdownNow();
            exec = null;
        }
    }

    public List<MetricSample> getSamples() {
        synchronized (samples) {
            return new ArrayList<>(samples);
        }
    }
}
