package com.chaosinjector.support;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.chaosinjector.config.Errors;
import com.chaosinjector.target.TargetAdapter;
import com.chaosinjector.target.model.ExecResult;
import com.chaosinjector.target.model.HelperSpec;
import com.chaosinjector.target.model.Stats;
import com.chaosinjector.target.model.TargetWorkload;
import com.chaosinjector.target.model.WorkloadState;

/**
 * In-memory {@link TargetAdapter} for tests. Simulates container state and
 * records every command so injector/orchestration behaviour can be verified
 * without a Docker daemon. Reused by Phases 3–5 tests.
 */
public class FakeTargetAdapter implements TargetAdapter {

    private final Map<String, WorkloadState> workloads = new LinkedHashMap<>();
    private final Map<String, HelperSpec> helpers = new LinkedHashMap<>();
    private final AtomicInteger helperSeq = new AtomicInteger();

    public final List<List<String>> execDetachedCalls = new ArrayList<>();
    public final List<List<String>> execSyncCalls = new ArrayList<>();
    public final List<HelperSpec> helperRuns = new ArrayList<>();
    public final List<String> removedContainers = new ArrayList<>();
    public final List<String[]> networkDisconnects = new ArrayList<>();
    public final List<String[]> networkConnects = new ArrayList<>();

    private Supplier<Stats> statsSupplier = () -> new Stats(Instant.now(), 5.0, 100, 1000, 10.0, 0, 0, 0, 0, 3);
    private ExecResult execResult = new ExecResult(0, "", "");
    private boolean connectionOk = true;

    public FakeTargetAdapter withWorkload(String id, String name, String image) {
        workloads.put(id, new WorkloadState(id, name, image, "running", true, false, 0, 1_000_000L,
                new ArrayList<>(List.of("bridge"))));
        return this;
    }

    public FakeTargetAdapter withStats(Supplier<Stats> supplier) {
        this.statsSupplier = supplier;
        return this;
    }

    public FakeTargetAdapter withExecResult(ExecResult result) {
        this.execResult = result;
        return this;
    }

    public FakeTargetAdapter withConnectionFailing() {
        this.connectionOk = false;
        return this;
    }

    public WorkloadState state(String id) {
        return workloads.get(id);
    }

    // --- TargetAdapter ---

    @Override
    public void verifyConnection() {
        if (!connectionOk) {
            throw new Errors.ConnectionError("fake daemon unreachable");
        }
    }

    @Override
    public List<TargetWorkload> listWorkloads() {
        List<TargetWorkload> out = new ArrayList<>();
        workloads.values().forEach(w ->
                out.add(new TargetWorkload(w.id(), w.name(), w.image(), w.status())));
        return out;
    }

    @Override
    public WorkloadState describe(String id) {
        WorkloadState w = workloads.get(id);
        if (w == null) {
            throw new Errors.TargetNotFoundError("no such container: " + id);
        }
        return w;
    }

    @Override
    public void pause(String id) {
        mutate(id, w -> new WorkloadState(w.id(), w.name(), w.image(), "paused", true, true,
                w.restartCount(), w.memoryLimitBytes(), w.networks()));
    }

    @Override
    public void unpause(String id) {
        mutate(id, w -> new WorkloadState(w.id(), w.name(), w.image(), "running", true, false,
                w.restartCount(), w.memoryLimitBytes(), w.networks()));
    }

    @Override
    public void stop(String id, int timeoutSeconds) {
        mutate(id, w -> new WorkloadState(w.id(), w.name(), w.image(), "exited", false, false,
                w.restartCount(), w.memoryLimitBytes(), w.networks()));
    }

    @Override
    public void start(String id) {
        mutate(id, w -> new WorkloadState(w.id(), w.name(), w.image(), "running", true, false,
                w.restartCount(), w.memoryLimitBytes(), w.networks()));
    }

    @Override
    public String execDetached(String id, List<String> cmd) {
        execDetachedCalls.add(new ArrayList<>(cmd));
        return "exec-" + execDetachedCalls.size();
    }

    @Override
    public ExecResult execSync(String id, List<String> cmd) {
        execSyncCalls.add(new ArrayList<>(cmd));
        return execResult;
    }

    @Override
    public String runHelper(HelperSpec spec) {
        helperRuns.add(spec);
        String id = "helper-" + helperSeq.incrementAndGet();
        helpers.put(id, spec);
        workloads.put(id, new WorkloadState(id, id, spec.image(), "running", true, false, 0, null,
                new ArrayList<>()));
        return id;
    }

    @Override
    public void removeContainer(String id, boolean force) {
        removedContainers.add(id);
        workloads.remove(id);
        helpers.remove(id);
    }

    @Override
    public int removeContainersByLabel(String labelKey, String labelValue) {
        List<String> toRemove = new ArrayList<>();
        helpers.forEach((id, spec) -> {
            if (spec.labels() != null && labelValue.equals(spec.labels().get(labelKey))) {
                toRemove.add(id);
            }
        });
        toRemove.forEach(id -> removeContainer(id, true));
        return toRemove.size();
    }

    @Override
    public Stats sampleStats(String id) {
        return statsSupplier.get();
    }

    @Override
    public List<String> networksOf(String id) {
        return describe(id).networks();
    }

    @Override
    public void disconnectNetwork(String containerId, String networkId) {
        networkDisconnects.add(new String[]{containerId, networkId});
        mutate(containerId, w -> {
            List<String> nets = new ArrayList<>(w.networks());
            nets.remove(networkId);
            return new WorkloadState(w.id(), w.name(), w.image(), w.status(), w.running(), w.paused(),
                    w.restartCount(), w.memoryLimitBytes(), nets);
        });
    }

    @Override
    public void connectNetwork(String containerId, String networkId) {
        networkConnects.add(new String[]{containerId, networkId});
        mutate(containerId, w -> {
            List<String> nets = new ArrayList<>(w.networks());
            if (!nets.contains(networkId)) {
                nets.add(networkId);
            }
            return new WorkloadState(w.id(), w.name(), w.image(), w.status(), w.running(), w.paused(),
                    w.restartCount(), w.memoryLimitBytes(), nets);
        });
    }

    private void mutate(String id, java.util.function.UnaryOperator<WorkloadState> op) {
        WorkloadState w = workloads.get(id);
        if (w == null) {
            throw new Errors.TargetNotFoundError("no such container: " + id);
        }
        workloads.put(id, op.apply(w));
    }
}
