package com.chaosinjector.target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.chaosinjector.target.model.ExecResult;
import com.chaosinjector.target.model.HelperSpec;
import com.chaosinjector.target.model.Stats;
import com.chaosinjector.target.model.TargetWorkload;
import com.chaosinjector.target.model.WorkloadState;
import com.github.dockerjava.api.DockerClient;

/**
 * Integration test for {@link DockerTargetAdapter} against a real Docker daemon
 * via Testcontainers. Skipped automatically when Docker is unavailable so the
 * unit build stays green everywhere.
 */
class DockerTargetAdapterTest {

    private static final DockerImageName IMAGE = DockerImageName.parse("alpine:3.20");
    private static GenericContainer<?> target;
    private static DockerTargetAdapter adapter;

    @BeforeAll
    static void setup() {
        assumeTrue(org.testcontainers.DockerClientFactory.instance().isDockerAvailable(),
                "Docker not available — skipping adapter integration test");

        try {
            target = new GenericContainer<>(IMAGE).withCommand("sleep", "600");
            target.start();
        } catch (Throwable t) {
            // e.g. base image cannot be pulled in a restricted environment.
            assumeTrue(false, "Could not start test container (" + IMAGE + "): " + t.getMessage());
        }

        String host = System.getenv().getOrDefault("DOCKER_HOST", "unix:///var/run/docker.sock");
        DockerClient client = new DockerClientFactory().create(host);
        adapter = new DockerTargetAdapter(client);
    }

    @AfterAll
    static void tearDown() {
        if (target != null) {
            target.stop();
        }
    }

    @Test
    void verifyConnectionSucceeds() {
        adapter.verifyConnection();
    }

    @Test
    void listsAndDescribesTheRunningContainer() {
        String id = target.getContainerId();
        List<TargetWorkload> workloads = adapter.listWorkloads();
        assertThat(workloads).anyMatch(w -> w.id().equals(id));

        WorkloadState state = adapter.describe(id);
        assertThat(state.running()).isTrue();
        assertThat(state.image()).contains("alpine");
    }

    @Test
    void samplesStats() {
        Stats stats = adapter.sampleStats(target.getContainerId());
        assertThat(stats).isNotNull();
        assertThat(stats.cpuPercent()).isGreaterThanOrEqualTo(0.0);
        assertThat(stats.memoryUsageBytes()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void execRunsInsideContainer() {
        ExecResult r = adapter.execSync(target.getContainerId(), List.of("sh", "-c", "echo hello-chaos"));
        assertThat(r.succeeded()).isTrue();
        assertThat(r.stdout()).contains("hello-chaos");
    }

    @Test
    void pauseAndUnpauseFlipState() {
        String id = target.getContainerId();
        adapter.pause(id);
        assertThat(adapter.describe(id).paused()).isTrue();
        adapter.unpause(id);
        assertThat(adapter.describe(id).paused()).isFalse();
    }

    @Test
    void helperContainerRunsInTargetNetnsAndIsSweptByLabel() {
        String expId = UUID.randomUUID().toString();
        HelperSpec spec = new HelperSpec(
                IMAGE.asCanonicalNameString(),
                target.getContainerId(),
                List.of("sleep", "60"),
                List.of("NET_ADMIN"),
                Map.of("chaosinjector.experiment", expId),
                false);

        String helperId = adapter.runHelper(spec);
        assertThat(helperId).isNotBlank();
        assertThat(adapter.describe(helperId).running()).isTrue();

        int removed = adapter.removeContainersByLabel("chaosinjector.experiment", expId);
        assertThat(removed).isEqualTo(1);
        assertThat(adapter.listWorkloads()).noneMatch(w -> w.id().equals(helperId));
    }
}
