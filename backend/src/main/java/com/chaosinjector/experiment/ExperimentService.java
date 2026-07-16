package com.chaosinjector.experiment;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.chaosinjector.api.dto.ExperimentRequest;
import com.chaosinjector.config.ChaosInjectorException;
import com.chaosinjector.config.ChaosInjectorProperties;
import com.chaosinjector.config.Errors;
import com.chaosinjector.engine.ChaosInjector;
import com.chaosinjector.engine.ExperimentContext;
import com.chaosinjector.engine.InjectionHandle;
import com.chaosinjector.engine.InjectorRegistry;
import com.chaosinjector.engine.Labels;
import com.chaosinjector.metrics.HealthProbe;
import com.chaosinjector.metrics.ImpactReport;
import com.chaosinjector.metrics.ImpactReportCalculator;
import com.chaosinjector.metrics.MetricPhase;
import com.chaosinjector.metrics.MetricsCollector;
import com.chaosinjector.metrics.PhaseSampler;
import com.chaosinjector.progress.ProgressBroadcaster;
import com.chaosinjector.progress.ProgressEvent;
import com.chaosinjector.target.AdapterResolver;
import com.chaosinjector.target.TargetAdapter;
import com.chaosinjector.target.model.WorkloadState;

import jakarta.annotation.PreDestroy;

/**
 * Drives an experiment end to end through the state machine (spec §6.2, §9):
 * VALIDATING → BASELINE → INJECTING → ACTIVE → REVERTING → COMPLETED, with a
 * duration watchdog, an abort path, guaranteed revert on error/shutdown, a
 * single-active guard, and progress broadcasting.
 */
@Service
public class ExperimentService {

    private static final Logger log = LoggerFactory.getLogger(ExperimentService.class);

    private final AdapterResolver resolver;
    private final InjectorRegistry injectors;
    private final ExperimentStore store;
    private final ProgressBroadcaster broadcaster;
    private final ChaosInjectorProperties props;

    private final ExecutorService runner = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "chaos-experiment");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, Runtime> runtimes = new ConcurrentHashMap<>();
    private final Set<String> activeGuardKeys = ConcurrentHashMap.newKeySet();

    public ExperimentService(AdapterResolver resolver, InjectorRegistry injectors,
                             ExperimentStore store, ProgressBroadcaster broadcaster,
                             ChaosInjectorProperties props) {
        this.resolver = resolver;
        this.injectors = injectors;
        this.store = store;
        this.broadcaster = broadcaster;
        this.props = props;
    }

    /** Dry-run validation without injecting (spec §10 /experiments/validate). */
    public void validateOnly(ExperimentRequest req) {
        TargetAdapter adapter = resolver.resolve(req.connectionId());
        adapter.verifyConnection();
        WorkloadState state = adapter.describe(req.containerId());
        if (!state.running()) {
            throw new Errors.ValidationError("Target container is not running: " + req.containerId());
        }
        ChaosInjector injector = injectors.get(req.scenario());
        injector.validate(context(newExperiment(req), adapter));
    }

    /** Create and start an experiment. Reserves the single-active guard first. */
    public Experiment create(ExperimentRequest req) {
        Experiment exp = newExperiment(req);
        String guardKey = guardKey(exp);
        if (!activeGuardKeys.add(guardKey)) {
            throw new Errors.ConflictError("An experiment is already active on this target");
        }
        Runtime rt = new Runtime(guardKey);
        runtimes.put(exp.getId(), rt);
        store.save(exp);
        runner.submit(() -> run(exp, rt));
        return exp;
    }

    /** Request an abort; the run reverts immediately and finishes as ABORTED. */
    public void abort(String experimentId) {
        Runtime rt = runtimes.get(experimentId);
        if (rt == null) {
            return; // already finished or unknown
        }
        rt.abortRequested.set(true);
        Thread t = rt.thread;
        if (t != null) {
            t.interrupt();
        }
    }

    // --- orchestration ---

    private void run(Experiment exp, Runtime rt) {
        rt.thread = Thread.currentThread();
        TargetAdapter adapter = resolver.resolve(exp.getConnectionId());
        ChaosInjector injector = injectors.get(exp.getScenario());
        ExperimentContext ctx = context(exp, adapter);

        PhaseSampler sampler = new PhaseSampler(
                new MetricsCollector(adapter, new HealthProbe(props.getMetrics().getProbeTimeoutMs())),
                exp.getContainerId(), exp.getHealthCheckUrl(), exp.getSampleIntervalMs());
        sampler.onSample(s -> broadcaster.publish(ProgressEvent.sample(exp.getId(), s)));

        InjectionHandle handle = null;
        boolean aborted = false;
        try {
            phase(exp, ExperimentState.VALIDATING);
            adapter.verifyConnection();
            WorkloadState state = adapter.describe(exp.getContainerId());
            if (!state.running()) {
                throw new Errors.ValidationError("Target container is not running");
            }
            injector.validate(ctx);

            phase(exp, ExperimentState.BASELINE);
            sampler.setPhase(MetricPhase.BASELINE);
            sampler.start();
            aborted = sleepOrAbort(rt, exp.getBaselineSeconds() * 1000L);

            if (!aborted) {
                phase(exp, ExperimentState.INJECTING);
                handle = injector.inject(ctx);
                broadcaster.publish(ProgressEvent.log(exp.getId(),
                        "Injected " + exp.getScenario() + " into " + exp.getContainerId()));

                phase(exp, ExperimentState.ACTIVE);
                sampler.setPhase(MetricPhase.ACTIVE);
                aborted = sleepOrAbort(rt, exp.getDurationSeconds() * 1000L);
            }

            phase(exp, ExperimentState.REVERTING);
            sampler.setPhase(MetricPhase.POST);
            if (handle != null) {
                injector.revert(ctx, handle);
                handle = null;
            }
            // brief post-window to observe recovery
            quietSleep(props.getExperiment().getPostWindowSeconds() * 1000L);
            sampler.stop();

            ImpactReport report = ImpactReportCalculator.compute(sampler.getSamples());
            exp.setReport(report);
            exp.transitionTo(aborted ? ExperimentState.ABORTED : ExperimentState.COMPLETED);
            store.save(exp);
            broadcaster.publish(ProgressEvent.completed(exp.getId(), exp.getState(), report));
            log.info("Experiment {} finished: {}", exp.getId(), exp.getState());
        } catch (Throwable t) {
            log.error("Experiment {} failed: {}", exp.getId(), t.toString());
            // Guaranteed revert of any applied fault.
            if (handle != null) {
                try {
                    injector.revert(ctx, handle);
                } catch (Throwable revertEx) {
                    log.error("REVERT FAILED for experiment {} — target may be altered: {}",
                            exp.getId(), revertEx.toString());
                    exp.recordError("REVERT_ERROR", "Revert failed: " + revertEx.getMessage());
                }
            }
            sampler.stop();
            try {
                exp.setReport(ImpactReportCalculator.compute(sampler.getSamples()));
            } catch (RuntimeException ignored) {
                // partial run — report best-effort
            }
            if (exp.getErrorCode() == null) {
                String code = t instanceof ChaosInjectorException ce ? ce.code() : "INTERNAL_ERROR";
                exp.recordError(code, t.getMessage());
            }
            safeTransition(exp, ExperimentState.FAILED);
            store.save(exp);
            broadcaster.publish(ProgressEvent.error(exp.getId(), t.getMessage()));
        } finally {
            sampler.stop();
            try {
                adapter.removeContainersByLabel(Labels.EXPERIMENT, exp.getId());
            } catch (RuntimeException ignored) {
                // best-effort orphan sweep
            }
            activeGuardKeys.remove(rt.guardKey);
            runtimes.remove(exp.getId());
        }
    }

    private void phase(Experiment exp, ExperimentState state) {
        exp.transitionTo(state);
        store.save(exp);
        broadcaster.publish(ProgressEvent.phase(exp.getId(), state));
    }

    private void safeTransition(Experiment exp, ExperimentState state) {
        try {
            exp.transitionTo(state);
        } catch (IllegalStateException e) {
            log.warn("Could not transition {} to {} from {}", exp.getId(), state, exp.getState());
        }
    }

    /** Sleep up to millis, returning true if an abort was requested. */
    private boolean sleepOrAbort(Runtime rt, long millis) {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            if (rt.abortRequested.get()) {
                return true;
            }
            long remaining = deadline - System.currentTimeMillis();
            try {
                Thread.sleep(Math.min(100, Math.max(1, remaining)));
            } catch (InterruptedException e) {
                if (rt.abortRequested.get()) {
                    return true;
                }
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return rt.abortRequested.get();
    }

    private void quietSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Experiment newExperiment(ExperimentRequest req) {
        int maxDuration = props.getExperiment().getMaxDurationSeconds();
        int duration = Math.min(Math.max(1, req.durationSeconds()), maxDuration);
        int baseline = Math.max(0, req.baselineSeconds());
        return new Experiment(req.name(), req.connectionId(), req.containerId(), req.healthCheckUrl(),
                req.scenario(), req.parametersOrEmpty(), duration, baseline, req.sampleIntervalMs());
    }

    private ExperimentContext context(Experiment exp, TargetAdapter adapter) {
        return new ExperimentContext(exp.getId(), exp.getContainerId(), exp.getParameters(),
                exp.getDurationSeconds(), adapter, props.getHelper().getImage());
    }

    private String guardKey(Experiment exp) {
        return props.getExperiment().isSingleActiveGlobal()
                ? "GLOBAL"
                : exp.getConnectionId() + "/" + exp.getContainerId();
    }

    @PreDestroy
    void shutdown() {
        // Ask all in-flight runs to abort so their guaranteed-revert path runs.
        runtimes.values().forEach(rt -> {
            rt.abortRequested.set(true);
            if (rt.thread != null) {
                rt.thread.interrupt();
            }
        });
        runner.shutdown();
    }

    /** Per-run mutable state: abort flag + the executing thread + guard key. */
    private static final class Runtime {
        final AtomicBoolean abortRequested = new AtomicBoolean(false);
        final String guardKey;
        volatile Thread thread;

        Runtime(String guardKey) {
            this.guardKey = guardKey;
        }
    }
}
