package com.chaosinjector.target;

import java.util.List;

import com.chaosinjector.target.model.ExecResult;
import com.chaosinjector.target.model.HelperSpec;
import com.chaosinjector.target.model.Stats;
import com.chaosinjector.target.model.TargetWorkload;
import com.chaosinjector.target.model.WorkloadState;

/**
 * Abstraction over the platform hosting the workload under test (spec §6.4).
 * The chaos engine and API depend only on this interface, never on Docker
 * types, so Kubernetes/OpenShift adapters can be added later without touching
 * them. The MVP ships {@link DockerTargetAdapter}.
 */
public interface TargetAdapter {

    /** @throws com.chaosinjector.config.Errors.ConnectionError if unreachable. */
    void verifyConnection();

    /** Running workloads available to target. */
    List<TargetWorkload> listWorkloads();

    /** @throws com.chaosinjector.config.Errors.TargetNotFoundError if absent. */
    WorkloadState describe(String id);

    void pause(String id);

    void unpause(String id);

    void stop(String id, int timeoutSeconds);

    void start(String id);

    /**
     * Start a command inside the workload and return immediately.
     *
     * @return an exec id that can be referenced later
     */
    String execDetached(String id, List<String> cmd);

    /** Run a command inside the workload and wait for it to finish. */
    ExecResult execSync(String id, List<String> cmd);

    /**
     * Create and start a helper container per {@code spec}.
     *
     * @return the helper container id
     */
    String runHelper(HelperSpec spec);

    void removeContainer(String id, boolean force);

    /**
     * Remove every container carrying {@code labelKey=labelValue} (orphan
     * sweep for helper cleanup).
     *
     * @return number of containers removed
     */
    int removeContainersByLabel(String labelKey, String labelValue);

    /** One resource-usage sample for the workload. */
    Stats sampleStats(String id);

    /** Ids of the networks the workload is currently attached to. */
    List<String> networksOf(String id);

    void disconnectNetwork(String containerId, String networkId);

    void connectNetwork(String containerId, String networkId);
}
