package com.chaosinjector.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.chaosinjector.config.Errors;
import com.chaosinjector.experiment.ScenarioType;
import com.chaosinjector.support.FakeTargetAdapter;

class InjectorsTest {

    private static final String CID = "c1";
    private static final String HELPER_IMAGE = "chaosinjector/helper:latest";

    private ExperimentContext ctx(FakeTargetAdapter adapter, Map<String, Object> params) {
        return new ExperimentContext("exp-1", CID, params, 30, adapter, HELPER_IMAGE);
    }

    private FakeTargetAdapter adapter() {
        return new FakeTargetAdapter().withWorkload(CID, "web", "nginx");
    }

    // --- Service Unavailable ---

    @Test
    void serviceUnavailablePauseAndRevert() {
        FakeTargetAdapter a = adapter();
        var injector = new ServiceUnavailableInjector();
        var context = ctx(a, Map.of("mode", "PAUSE"));

        injector.validate(context);
        InjectionHandle h = injector.inject(context);
        assertThat(a.state(CID).paused()).isTrue();

        injector.revert(context, h);
        assertThat(a.state(CID).paused()).isFalse();
        assertThat(a.state(CID).running()).isTrue();
    }

    @Test
    void serviceUnavailableStopAndRevertRestartsIt() {
        FakeTargetAdapter a = adapter();
        var injector = new ServiceUnavailableInjector();
        var context = ctx(a, Map.of("mode", "STOP"));

        InjectionHandle h = injector.inject(context);
        assertThat(a.state(CID).running()).isFalse();

        injector.revert(context, h);
        assertThat(a.state(CID).running()).isTrue();
    }

    @Test
    void serviceUnavailableNetworkOffDisconnectsThenReconnects() {
        FakeTargetAdapter a = adapter();
        var injector = new ServiceUnavailableInjector();
        var context = ctx(a, Map.of("mode", "NETWORK_OFF"));

        InjectionHandle h = injector.inject(context);
        assertThat(a.state(CID).networks()).isEmpty();
        assertThat(a.networkDisconnects).isNotEmpty();

        injector.revert(context, h);
        assertThat(a.state(CID).networks()).contains("bridge");
    }

    // --- CPU ---

    @Test
    void cpuInjectExecsLoadAndRevertKillsIt() {
        FakeTargetAdapter a = adapter();
        var injector = new CpuOverheadInjector();
        var context = ctx(a, Map.of("workers", 2, "loadPercent", 90));

        injector.validate(context);
        InjectionHandle h = injector.inject(context);

        assertThat(a.execDetachedCalls).hasSize(1);
        String script = String.join(" ", a.execDetachedCalls.get(0));
        assertThat(script).contains("stress-ng --cpu 2 --cpu-load 90");
        assertThat(script).contains("/tmp/chaos_cpu_exp-1.pids");

        injector.revert(context, h);
        assertThat(a.execSyncCalls).hasSize(1);
        assertThat(String.join(" ", a.execSyncCalls.get(0))).contains("kill");
    }

    @Test
    void cpuValidationRejectsBadLoad() {
        var injector = new CpuOverheadInjector();
        var context = ctx(adapter(), Map.of("loadPercent", 500));
        assertThatThrownBy(() -> injector.validate(context))
                .isInstanceOf(Errors.ValidationError.class);
    }

    // --- Memory ---

    @Test
    void memoryInjectExecsAllocatorAndRevertFrees() {
        FakeTargetAdapter a = adapter();
        var injector = new MemoryOverheadInjector();
        var context = ctx(a, Map.of("sizeMb", 128));

        injector.validate(context);
        InjectionHandle h = injector.inject(context);

        String script = String.join(" ", a.execDetachedCalls.get(0));
        assertThat(script).contains("--vm-bytes 128m");
        assertThat(script).contains("/dev/shm/chaos_mem_exp-1");

        injector.revert(context, h);
        assertThat(String.join(" ", a.execSyncCalls.get(0))).contains("rm -f /dev/shm/chaos_mem_exp-1");
    }

    @Test
    void memoryValidationRequiresASize() {
        var injector = new MemoryOverheadInjector();
        var context = ctx(adapter(), Map.of());
        assertThatThrownBy(() -> injector.validate(context))
                .isInstanceOf(Errors.ValidationError.class);
    }

    // --- Network ---

    @Test
    void networkLatencyRunsHelperInNetnsAndRevertSweeps() {
        FakeTargetAdapter a = adapter();
        var injector = new NetworkFailureInjector();
        var context = ctx(a, Map.of("mode", "LATENCY", "latencyMs", 300, "jitterMs", 50));

        injector.validate(context);
        InjectionHandle h = injector.inject(context);

        assertThat(a.helperRuns).hasSize(1);
        var helper = a.helperRuns.get(0);
        assertThat(helper.capAdd()).contains("NET_ADMIN");
        assertThat(helper.targetContainerId()).isEqualTo(CID);
        assertThat(helper.labels()).containsEntry(Labels.EXPERIMENT, "exp-1");
        assertThat(String.join(" ", helper.cmd())).contains("tc qdisc add dev eth0 root netem delay 300ms 50ms");

        injector.revert(context, h);
        // del helper ran, and no experiment-labelled helpers remain
        assertThat(a.helperRuns).hasSize(2);
        assertThat(String.join(" ", a.helperRuns.get(1).cmd())).contains("tc qdisc del dev eth0 root");
        assertThat(a.listWorkloads()).noneMatch(w -> w.id().startsWith("helper-"));
    }

    @Test
    void networkPartitionUsesFullLoss() {
        FakeTargetAdapter a = adapter();
        var injector = new NetworkFailureInjector();
        var context = ctx(a, Map.of("mode", "PARTITION"));

        injector.inject(context);
        assertThat(String.join(" ", a.helperRuns.get(0).cmd())).contains("netem loss 100%");
    }

    @Test
    void registryResolvesEveryScenario() {
        InjectorRegistry registry = new InjectorRegistry(java.util.List.of(
                new NetworkFailureInjector(), new CpuOverheadInjector(),
                new MemoryOverheadInjector(), new ServiceUnavailableInjector()));
        for (ScenarioType type : ScenarioType.values()) {
            assertThat(registry.get(type).type()).isEqualTo(type);
        }
    }
}
