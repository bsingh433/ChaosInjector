package com.chaosinjector.progress;

import java.time.Instant;

import com.chaosinjector.experiment.ExperimentState;
import com.chaosinjector.metrics.ImpactReport;
import com.chaosinjector.metrics.MetricSample;

/**
 * A progress update streamed to subscribers (SSE in the web UI). One record
 * covers every event kind; irrelevant fields are null (spec §10 SSE events).
 */
public record ProgressEvent(
        ProgressType type,
        String experimentId,
        ExperimentState state,
        MetricSample sample,
        String message,
        ImpactReport report,
        Instant timestamp) {

    public static ProgressEvent phase(String id, ExperimentState state) {
        return new ProgressEvent(ProgressType.PHASE, id, state, null, null, null, Instant.now());
    }

    public static ProgressEvent sample(String id, MetricSample sample) {
        return new ProgressEvent(ProgressType.SAMPLE, id, null, sample, null, null, Instant.now());
    }

    public static ProgressEvent log(String id, String message) {
        return new ProgressEvent(ProgressType.LOG, id, null, null, message, null, Instant.now());
    }

    public static ProgressEvent completed(String id, ExperimentState state, ImpactReport report) {
        return new ProgressEvent(ProgressType.COMPLETED, id, state, null, null, report, Instant.now());
    }

    public static ProgressEvent error(String id, String message) {
        return new ProgressEvent(ProgressType.ERROR, id, ExperimentState.FAILED, null, message, null, Instant.now());
    }
}
