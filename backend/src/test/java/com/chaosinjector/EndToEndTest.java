package com.chaosinjector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.chaosinjector.api.dto.ExperimentRequest;
import com.chaosinjector.config.ChaosInjectorProperties;
import com.chaosinjector.engine.CpuOverheadInjector;
import com.chaosinjector.engine.InjectorRegistry;
import com.chaosinjector.engine.MemoryOverheadInjector;
import com.chaosinjector.engine.NetworkFailureInjector;
import com.chaosinjector.engine.ServiceUnavailableInjector;
import com.chaosinjector.experiment.Experiment;
import com.chaosinjector.experiment.ExperimentService;
import com.chaosinjector.experiment.ExperimentState;
import com.chaosinjector.experiment.ExperimentStore;
import com.chaosinjector.experiment.ScenarioType;
import com.chaosinjector.progress.ProgressBroadcaster;
import com.chaosinjector.target.DockerClientFactory;
import com.chaosinjector.target.DockerTargetAdapter;
import com.chaosinjector.target.TargetAdapter;

/**
 * End-to-end: the real {@link DockerTargetAdapter} + {@link ExperimentService}
 * run a full SERVICE_UNAVAILABLE (PAUSE) experiment against a real container and
 * verify inject → observe → revert leaves it running. Self-skips when Docker or
 * a pullable image is unavailable.
 */
class EndToEndTest {

    private static final DockerImageName IMAGE = DockerImageName.parse("alpine:3.20");
    private static GenericContainer<?> target;
    private static TargetAdapter adapter;
    private static ExperimentService service;
    private static ExperimentStore store;

    @BeforeAll
    static void setup() {
        assumeTrue(org.testcontainers.DockerClientFactory.instance().isDockerAvailable(),
                "Docker not available — skipping end-to-end test");
        try {
            target = new GenericContainer<>(IMAGE).withCommand("sleep", "600");
            target.start();
        } catch (Throwable t) {
            assumeTrue(false, "Could not start test container: " + t.getMessage());
        }

        String host = System.getenv().getOrDefault("DOCKER_HOST", "unix:///var/run/docker.sock");
        adapter = new DockerTargetAdapter(new DockerClientFactory().create(host));

        InjectorRegistry registry = new InjectorRegistry(List.of(
                new ServiceUnavailableInjector(), new CpuOverheadInjector(),
                new MemoryOverheadInjector(), new NetworkFailureInjector()));
        store = new ExperimentStore();
        ChaosInjectorProperties props = new ChaosInjectorProperties();
        props.getExperiment().setPostWindowSeconds(1);
        service = new ExperimentService(id -> adapter, registry, store, new ProgressBroadcaster(), props);
    }

    @AfterAll
    static void tearDown() {
        if (target != null) {
            target.stop();
        }
    }

    @Test
    void pauseExperimentInjectsAndRevertsAgainstRealContainer() {
        String cid = target.getContainerId();
        ExperimentRequest req = new ExperimentRequest("e2e", "conn", cid, null,
                ScenarioType.SERVICE_UNAVAILABLE, Map.of("mode", "PAUSE"), 2, 1, 500);

        Experiment exp = service.create(req);

        await().atMost(Duration.ofSeconds(30)).until(() ->
                store.find(exp.getId()).map(e -> e.getState().isTerminal()).orElse(false));

        Experiment done = store.find(exp.getId()).orElseThrow();
        assertThat(done.getState()).isEqualTo(ExperimentState.COMPLETED);
        assertThat(done.getReport()).isNotNull();

        // target must be back to running & not paused
        assertThat(adapter.describe(cid).running()).isTrue();
        assertThat(adapter.describe(cid).paused()).isFalse();
    }
}
