package com.sreagent.docker;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sreagent.config.AgentProperties;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

/**
 * Read-only Docker access for the agent's tools (container state, stats, logs)
 * plus reversible remediation actions (restart/unpause/start) used in Phase C.
 * Resilient: methods return error text rather than throwing so the agent can
 * keep reasoning if Docker is unreachable.
 */
@Component
public class DockerFacade {

    private final DockerClient docker;
    private final ObjectMapper mapper;

    public DockerFacade(AgentProperties props, ObjectMapper mapper) {
        this.mapper = mapper;
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(props.getDockerHost())
                .build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .connectionTimeout(Duration.ofSeconds(10))
                .responseTimeout(Duration.ofSeconds(45))
                .build();
        this.docker = DockerClientImpl.getInstance(config, httpClient);
    }

    /** Running containers with the "what changed" signals. */
    public String describeAll() {
        try {
            List<Container> containers = docker.listContainersCmd().withShowAll(false).exec();
            ArrayNode arr = mapper.createArrayNode();
            for (Container c : containers) {
                String id = c.getId();
                ObjectNode n = arr.addObject();
                n.put("name", cleanName(c.getNames() == null || c.getNames().length == 0
                        ? id : c.getNames()[0]));
                n.put("image", c.getImage());
                n.put("status", c.getStatus());
                try {
                    InspectContainerResponse r = docker.inspectContainerCmd(id).exec();
                    n.put("restartCount", r.getRestartCount() == null ? 0 : r.getRestartCount());
                    if (r.getState() != null) {
                        n.put("running", Boolean.TRUE.equals(r.getState().getRunning()));
                        n.put("startedAt", r.getState().getStartedAt());
                    }
                } catch (RuntimeException ignored) {
                    // best-effort enrichment
                }
            }
            return arr.toString();
        } catch (RuntimeException e) {
            return "error: docker unreachable (" + e.getMessage() + ")";
        }
    }

    public String stats(String name) {
        String id = findId(name);
        if (id == null) {
            return "error: no such container '" + name + "'";
        }
        try {
            LinkedBlockingQueue<Statistics> q = new LinkedBlockingQueue<>();
            ResultCallback.Adapter<Statistics> cb = new ResultCallback.Adapter<>() {
                @Override
                public void onNext(Statistics s) {
                    q.offer(s);
                }
            };
            docker.statsCmd(id).exec(cb);
            Statistics first = q.poll(5, TimeUnit.SECONDS);
            Statistics second = q.poll(2, TimeUnit.SECONDS);
            try {
                cb.close();
            } catch (Exception ignored) {
                // best-effort
            }
            Statistics s = second != null ? second : first;
            if (s == null) {
                return "error: no stats for '" + name + "'";
            }
            InspectContainerResponse r = docker.inspectContainerCmd(id).exec();
            ObjectNode n = mapper.createObjectNode();
            n.put("name", name);
            n.put("cpuPercent", round(cpuPercent(s)));
            long memUsage = orZero(s.getMemoryStats() != null ? s.getMemoryStats().getUsage() : null);
            long memLimit = orZero(s.getMemoryStats() != null ? s.getMemoryStats().getLimit() : null);
            n.put("memUsageBytes", memUsage);
            n.put("memLimitBytes", memLimit);
            n.put("memPercent", memLimit > 0 ? round(memUsage * 100.0 / memLimit) : 0);
            n.put("restartCount", r.getRestartCount() == null ? 0 : r.getRestartCount());
            n.put("running", r.getState() != null && Boolean.TRUE.equals(r.getState().getRunning()));
            n.put("paused", r.getState() != null && Boolean.TRUE.equals(r.getState().getPaused()));
            return n.toString();
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    public String logs(String name, int tail) {
        String id = findId(name);
        if (id == null) {
            return "error: no such container '" + name + "'";
        }
        try {
            StringBuilder sb = new StringBuilder();
            docker.logContainerCmd(id).withStdOut(true).withStdErr(true)
                    .withTail(Math.max(1, Math.min(tail, 500)))
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame f) {
                            sb.append(new String(f.getPayload()));
                        }
                    }).awaitCompletion(10, TimeUnit.SECONDS);
            String out = sb.toString();
            return out.length() > 6000 ? out.substring(out.length() - 6000) : out;
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    // --- reversible remediation (used by Phase C tools) ---

    public String restart(String name) {
        return act(name, id -> docker.restartContainerCmd(id).exec(), "restarted");
    }

    public String unpause(String name) {
        return act(name, id -> docker.unpauseContainerCmd(id).exec(), "unpaused");
    }

    public String start(String name) {
        return act(name, id -> docker.startContainerCmd(id).exec(), "started");
    }

    private interface Action {
        void run(String id);
    }

    private String act(String name, Action a, String verb) {
        String id = findId(name);
        if (id == null) {
            return "error: no such container '" + name + "'";
        }
        try {
            a.run(id);
            return verb + " " + name;
        } catch (RuntimeException e) {
            return "error: " + e.getMessage();
        }
    }

    // --- helpers ---

    public String findId(String name) {
        try {
            for (Container c : docker.listContainersCmd().withShowAll(true).exec()) {
                if (c.getId().startsWith(name)) {
                    return c.getId();
                }
                if (c.getNames() != null) {
                    for (String n : c.getNames()) {
                        if (cleanName(n).equals(name)) {
                            return c.getId();
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return null;
    }

    private static double cpuPercent(Statistics s) {
        if (s.getCpuStats() == null || s.getPreCpuStats() == null
                || s.getCpuStats().getCpuUsage() == null) {
            return 0;
        }
        long cpu = orZero(s.getCpuStats().getCpuUsage().getTotalUsage());
        long pre = s.getPreCpuStats().getCpuUsage() == null ? 0
                : orZero(s.getPreCpuStats().getCpuUsage().getTotalUsage());
        long sys = orZero(s.getCpuStats().getSystemCpuUsage());
        long presys = orZero(s.getPreCpuStats().getSystemCpuUsage());
        long online = s.getCpuStats().getOnlineCpus() != null ? s.getCpuStats().getOnlineCpus() : 1;
        double cpuDelta = cpu - pre;
        double sysDelta = sys - presys;
        return (cpuDelta > 0 && sysDelta > 0) ? (cpuDelta / sysDelta) * Math.max(online, 1) * 100.0 : 0;
    }

    private static long orZero(Long v) {
        return v == null ? 0 : v;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String cleanName(String name) {
        return name != null && name.startsWith("/") ? name.substring(1) : name;
    }
}
