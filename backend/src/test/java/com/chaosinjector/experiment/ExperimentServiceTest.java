package com.chaosinjector.experiment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chaosinjector.api.dto.ExperimentRequest;
import com.chaosinjector.config.ChaosInjectorProperties;
import com.chaosinjector.config.Errors;
import com.chaosinjector.engine.CpuOverheadInjector;
import com.chaosinjector.engine.InjectorRegistry;
import com.chaosinjector.engine.MemoryOverheadInjector;
import com.chaosinjector.engine.NetworkFailureInjector;
import com.chaosinjector.engine.ServiceUnavailableInjector;
import com.chaosinjector.experiment.ScenarioType;
import com.chaosinjector.progress.ProgressBroadcaster;
import com.chaosinjector.progress.ProgressEvent;
import com.chaosinjector.progress.ProgressType;
import com.chaosinjector.support.FakeTargetAdapter;

class ExperimentServiceTest {

    private static final String CONN = "conn-1";
    private static final String CID = "c1";

    private FakeTargetAdapter adapter;
    private ExperimentService service;
    private ExperimentStore store;
    private ProgressBroadcaster broadcaster;

    @BeforeEach
    void setup() {
        adapter = new FakeTargetAdapter().withWorkload(CID, "web", "nginx");
        InjectorRegistry registry = new InjectorRegistry(List.of(
                new ServiceUnavailableInjector(), new CpuOverheadInjector(),
                new MemoryOverheadInjector(), new NetworkFailureInjector()));
        store = new ExperimentStore();
        broadcaster = new ProgressBroadcaster();
        ChaosInjectorProperties props = new ChaosInjectorProperties();
        props.getExperiment().setPostWindowSeconds(0); // keep tests fast
        service = new ExperimentService(id -> adapter, registry, store, broadcaster, props);
    }

    private ExperimentRequest request(ScenarioType scenario, Map<String, Object> params,
                                      int duration, int baseline) {
        return new ExperimentRequest("t", CONN, CID, null, scenario, params, duration, baseline, 100);
    }

    @Test
    void fullHappyPathRevertsAndReports() {
        List<ProgressEvent> events = new CopyOnWriteArrayList<>();
        Experiment exp = service.create(request(ScenarioType.SERVICE_UNAVAILABLE, Map.of("mode", "PAUSE"), 1, 0));
        broadcaster.subscribe(exp.getId(), events::add);

        await().atMost(Duration.ofSeconds(10)).until(() ->
                store.find(exp.getId()).map(e -> e.getState().isTerminal()).orElse(false));

        Experiment done = store.find(exp.getId()).orElseThrow();
        assertThat(done.getState()).isEqualTo(ExperimentState.COMPLETED);
        // target returned to original state
        assertThat(adapter.state(CID).paused()).isFalse();
        assertThat(adapter.state(CID).running()).isTrue();
        assertThat(done.getReport()).isNotNull();

        // phase order observed
        List<ExperimentState> phases = events.stream()
                .filter(e -> e.type() == ProgressType.PHASE)
                .map(ProgressEvent::state)
                .toList();
        assertThat(phases).containsSubsequence(
                ExperimentState.VALIDATING, ExperimentState.BASELINE, ExperimentState.INJECTING,
                ExperimentState.ACTIVE, ExperimentState.REVERTING);
        assertThat(events).anyMatch(e -> e.type() == ProgressType.COMPLETED);
    }

    @Test
    void abortMidRunRevertsImmediatelyAndEndsAborted() {
        Experiment exp = service.create(
                request(ScenarioType.SERVICE_UNAVAILABLE, Map.of("mode", "PAUSE"), 30, 0));

        // wait until it is ACTIVE (fault applied), then abort
        await().atMost(Duration.ofSeconds(5)).until(() ->
                store.find(exp.getId()).map(e -> e.getState() == ExperimentState.ACTIVE
                        || e.getState().isTerminal()).orElse(false));
        service.abort(exp.getId());

        await().atMost(Duration.ofSeconds(5)).until(() ->
                store.find(exp.getId()).map(e -> e.getState().isTerminal()).orElse(false));

        Experiment done = store.find(exp.getId()).orElseThrow();
        assertThat(done.getState()).isEqualTo(ExperimentState.ABORTED);
        assertThat(adapter.state(CID).paused()).isFalse(); // reverted
    }

    @Test
    void singleActiveGuardRejectsOverlap() {
        service.create(request(ScenarioType.SERVICE_UNAVAILABLE, Map.of("mode", "PAUSE"), 30, 0));
        assertThatThrownBy(() ->
                service.create(request(ScenarioType.CPU_OVERHEAD, Map.of("workers", 1), 30, 0)))
                .isInstanceOf(Errors.ConflictError.class);
    }

    @Test
    void validationFailureEndsFailedAndReverts() {
        // Stop the container so the "running" precondition fails at VALIDATING.
        adapter.stop(CID, 0);
        Experiment exp = service.create(request(ScenarioType.CPU_OVERHEAD, Map.of("workers", 1), 5, 0));

        await().atMost(Duration.ofSeconds(5)).until(() ->
                store.find(exp.getId()).map(e -> e.getState().isTerminal()).orElse(false));

        Experiment done = store.find(exp.getId()).orElseThrow();
        assertThat(done.getState()).isEqualTo(ExperimentState.FAILED);
        assertThat(done.getErrorCode()).isNotNull();
        // no load was ever exec'd
        assertThat(adapter.execDetachedCalls).isEmpty();
    }

    @Test
    void validateOnlyDoesNotInject() {
        service.validateOnly(request(ScenarioType.CPU_OVERHEAD, Map.of("workers", 2), 10, 5));
        assertThat(adapter.execDetachedCalls).isEmpty();
        assertThat(store.recent()).isEmpty();
    }
}
