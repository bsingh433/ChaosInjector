package com.chaosinjector.engine;

import java.util.List;

import org.springframework.stereotype.Component;

import com.chaosinjector.experiment.ScenarioType;
import com.chaosinjector.target.model.HelperSpec;

/**
 * Degrades or severs the target's network with {@code tc/netem} (spec §7.1).
 * Because the target image may lack {@code tc}, a helper container joined to the
 * target's network namespace ({@code --net=container:<id>}, cap {@code
 * NET_ADMIN}) applies the qdisc. Revert deletes the qdisc and sweeps helpers.
 */
@Component
public class NetworkFailureInjector implements ChaosInjector {

    public enum Mode {
        LATENCY, PACKET_LOSS, BANDWIDTH, PARTITION
    }

    private static final String ATTR_IFACE = "iface";

    @Override
    public ScenarioType type() {
        return ScenarioType.NETWORK_FAILURE;
    }

    @Override
    public void validate(ExperimentContext ctx) {
        Mode mode = Params.enumVal(ctx.parameters(), "mode", Mode.class, Mode.LATENCY);
        switch (mode) {
            case LATENCY -> Params.require(Params.intVal(ctx.parameters(), "latencyMs", 100) > 0,
                    "latencyMs must be > 0");
            case PACKET_LOSS -> {
                double loss = Params.dbl(ctx.parameters(), "lossPercent", 10);
                Params.require(loss > 0 && loss <= 100, "lossPercent must be between 0 and 100");
            }
            case BANDWIDTH -> Params.require(Params.intVal(ctx.parameters(), "rateKbit", 1000) > 0,
                    "rateKbit must be > 0");
            case PARTITION -> { /* no params */ }
            default -> throw new IllegalStateException("unhandled mode " + mode);
        }
    }

    @Override
    public InjectionHandle inject(ExperimentContext ctx) {
        Mode mode = Params.enumVal(ctx.parameters(), "mode", Mode.class, Mode.LATENCY);
        String iface = Params.str(ctx.parameters(), "iface", "eth0");
        String netem = netemArgs(ctx, mode);

        // Apply the qdisc, then keep the helper alive for the duration so it is
        // traceable; revert removes both the qdisc and the helper.
        String script = "tc qdisc add dev " + iface + " root " + netem
                + " && sleep " + (ctx.durationSeconds() + 30);

        HelperSpec spec = new HelperSpec(
                ctx.helperImage(),
                ctx.containerId(),
                List.of("sh", "-c", script),
                List.of("NET_ADMIN"),
                ctx.helperLabels(),
                false);

        String helperId = ctx.adapter().runHelper(spec);
        return new InjectionHandle(type(), ctx.containerId())
                .addHelper(helperId)
                .put(ATTR_IFACE, iface);
    }

    @Override
    public void revert(ExperimentContext ctx, InjectionHandle handle) {
        String iface = handle.getString(ATTR_IFACE);

        // Stop the (sleeping) apply-helper first so the namespace is quiet.
        for (String helperId : handle.helperIds()) {
            ctx.adapter().removeContainer(helperId, true);
        }

        // Delete the qdisc via a short-lived helper in the same namespace.
        String delScript = "tc qdisc del dev " + iface + " root 2>/dev/null || true";
        HelperSpec delSpec = new HelperSpec(
                ctx.helperImage(),
                ctx.containerId(),
                List.of("sh", "-c", delScript),
                List.of("NET_ADMIN"),
                ctx.helperLabels(),
                true);
        ctx.adapter().runHelper(delSpec);

        // Sweep any remaining helpers for this experiment (orphan-proof cleanup).
        ctx.adapter().removeContainersByLabel(Labels.EXPERIMENT, ctx.experimentId());
    }

    private String netemArgs(ExperimentContext ctx, Mode mode) {
        return switch (mode) {
            case LATENCY -> {
                int latency = Params.intVal(ctx.parameters(), "latencyMs", 100);
                int jitter = Params.intVal(ctx.parameters(), "jitterMs", 0);
                yield jitter > 0
                        ? "netem delay " + latency + "ms " + jitter + "ms"
                        : "netem delay " + latency + "ms";
            }
            case PACKET_LOSS -> "netem loss " + Params.dbl(ctx.parameters(), "lossPercent", 10) + "%";
            case BANDWIDTH -> "netem rate " + Params.intVal(ctx.parameters(), "rateKbit", 1000) + "kbit";
            case PARTITION -> "netem loss 100%";
            default -> throw new IllegalStateException("unhandled mode " + mode);
        };
    }
}
