package com.chaosinjector.target;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.chaosinjector.config.Errors;
import com.chaosinjector.target.model.ExecResult;
import com.chaosinjector.target.model.HelperSpec;
import com.chaosinjector.target.model.Stats;
import com.chaosinjector.target.model.TargetWorkload;
import com.chaosinjector.target.model.WorkloadState;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.api.model.StreamType;

/**
 * Docker implementation of {@link TargetAdapter} over docker-java. Uses only its
 * own primitives (exec, helper containers, pause/stop, network connect) so no
 * external chaos framework is required (spec §7, §8).
 */
public class DockerTargetAdapter implements TargetAdapter {

    private final DockerClient docker;

    public DockerTargetAdapter(DockerClient docker) {
        this.docker = docker;
    }

    @Override
    public void verifyConnection() {
        try {
            docker.pingCmd().exec();
        } catch (RuntimeException e) {
            throw new Errors.ConnectionError("Cannot reach Docker daemon: " + e.getMessage(), e);
        }
    }

    @Override
    public List<TargetWorkload> listWorkloads() {
        List<Container> containers = docker.listContainersCmd().withShowAll(false).exec();
        List<TargetWorkload> out = new ArrayList<>(containers.size());
        for (Container c : containers) {
            out.add(new TargetWorkload(c.getId(), cleanName(c.getNames()), c.getImage(), c.getStatus()));
        }
        return out;
    }

    @Override
    public WorkloadState describe(String id) {
        InspectContainerResponse r = inspect(id);
        InspectContainerResponse.ContainerState state = r.getState();
        List<String> networks = r.getNetworkSettings() == null || r.getNetworkSettings().getNetworks() == null
                ? List.of()
                : new ArrayList<>(r.getNetworkSettings().getNetworks().keySet());
        Long memLimit = r.getHostConfig() != null ? r.getHostConfig().getMemory() : null;
        return new WorkloadState(
                r.getId(),
                cleanName(r.getName()),
                r.getConfig() != null ? r.getConfig().getImage() : null,
                state != null ? state.getStatus() : null,
                state != null && Boolean.TRUE.equals(state.getRunning()),
                state != null && Boolean.TRUE.equals(state.getPaused()),
                r.getRestartCount() == null ? 0 : r.getRestartCount(),
                memLimit != null && memLimit > 0 ? memLimit : null,
                networks);
    }

    @Override
    public void pause(String id) {
        docker.pauseContainerCmd(id).exec();
    }

    @Override
    public void unpause(String id) {
        docker.unpauseContainerCmd(id).exec();
    }

    @Override
    public void stop(String id, int timeoutSeconds) {
        docker.stopContainerCmd(id).withTimeout(timeoutSeconds).exec();
    }

    @Override
    public void start(String id) {
        docker.startContainerCmd(id).exec();
    }

    @Override
    public String execDetached(String id, List<String> cmd) {
        ExecCreateCmdResponse ec = docker.execCreateCmd(id)
                .withCmd(cmd.toArray(new String[0]))
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec();
        // Fire and forget: start detached so the process keeps running.
        docker.execStartCmd(ec.getId()).withDetach(true).exec(new ResultCallback.Adapter<>());
        return ec.getId();
    }

    @Override
    public ExecResult execSync(String id, List<String> cmd) {
        ExecCreateCmdResponse ec = docker.execCreateCmd(id)
                .withCmd(cmd.toArray(new String[0]))
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            docker.execStartCmd(ec.getId()).exec(new ResultCallback.Adapter<Frame>() {
                @Override
                public void onNext(Frame frame) {
                    try {
                        if (frame.getStreamType() == StreamType.STDERR) {
                            err.write(frame.getPayload());
                        } else {
                            out.write(frame.getPayload());
                        }
                    } catch (Exception ignored) {
                        // best-effort capture
                    }
                }
            }).awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Errors.InjectionError("Interrupted while exec'ing in " + id, e);
        }
        Long exit = docker.inspectExecCmd(ec.getId()).exec().getExitCodeLong();
        return new ExecResult(exit == null ? -1 : exit,
                out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Override
    public String runHelper(HelperSpec spec) {
        HostConfig hostConfig = new HostConfig().withAutoRemove(spec.autoRemove());
        if (spec.targetContainerId() != null) {
            hostConfig.withNetworkMode("container:" + spec.targetContainerId());
        }
        if (spec.capAdd() != null && !spec.capAdd().isEmpty()) {
            Capability[] caps = spec.capAdd().stream()
                    .map(Capability::valueOf)
                    .toArray(Capability[]::new);
            hostConfig.withCapAdd(caps);
        }
        CreateContainerResponse created = docker.createContainerCmd(spec.image())
                .withHostConfig(hostConfig)
                .withCmd(spec.cmd().toArray(new String[0]))
                .withLabels(spec.labels() == null ? Map.of() : spec.labels())
                .exec();
        docker.startContainerCmd(created.getId()).exec();
        return created.getId();
    }

    @Override
    public void removeContainer(String id, boolean force) {
        try {
            docker.removeContainerCmd(id).withForce(force).exec();
        } catch (NotFoundException ignored) {
            // already gone — cleanup is idempotent
        }
    }

    @Override
    public int removeContainersByLabel(String labelKey, String labelValue) {
        List<Container> containers = docker.listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(Map.of(labelKey, labelValue))
                .exec();
        int removed = 0;
        for (Container c : containers) {
            removeContainer(c.getId(), true);
            removed++;
        }
        return removed;
    }

    @Override
    public Stats sampleStats(String id) {
        LinkedBlockingQueue<Statistics> queue = new LinkedBlockingQueue<>();
        ResultCallback.Adapter<Statistics> callback = new ResultCallback.Adapter<>() {
            @Override
            public void onNext(Statistics stats) {
                queue.offer(stats);
            }
        };
        docker.statsCmd(id).exec(callback);
        try {
            // The first frame carries a zeroed precpu baseline; the second frame
            // gives a meaningful CPU delta. Take the second when available.
            Statistics first = queue.poll(5, TimeUnit.SECONDS);
            if (first == null) {
                throw new Errors.MetricsError("Timed out sampling stats for " + id);
            }
            Statistics second = queue.poll(2, TimeUnit.SECONDS);
            return StatsMapper.toStats(second != null ? second : first);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Errors.MetricsError("Interrupted sampling stats for " + id, e);
        } finally {
            try {
                callback.close();
            } catch (Exception ignored) {
                // stream teardown is best-effort
            }
        }
    }

    @Override
    public List<String> networksOf(String id) {
        return describe(id).networks();
    }

    @Override
    public void disconnectNetwork(String containerId, String networkId) {
        docker.disconnectFromNetworkCmd().withContainerId(containerId).withNetworkId(networkId).exec();
    }

    @Override
    public void connectNetwork(String containerId, String networkId) {
        docker.connectToNetworkCmd().withContainerId(containerId).withNetworkId(networkId).exec();
    }

    // --- helpers ---

    private InspectContainerResponse inspect(String id) {
        try {
            return docker.inspectContainerCmd(id).exec();
        } catch (NotFoundException e) {
            throw new Errors.TargetNotFoundError("Container not found: " + id);
        }
    }

    private static String cleanName(String[] names) {
        if (names == null || names.length == 0) {
            return null;
        }
        return cleanName(names[0]);
    }

    private static String cleanName(String name) {
        return name != null && name.startsWith("/") ? name.substring(1) : name;
    }
}
