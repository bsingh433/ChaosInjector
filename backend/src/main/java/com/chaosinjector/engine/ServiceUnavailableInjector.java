package com.chaosinjector.engine;

import java.util.List;

import org.springframework.stereotype.Component;

import com.chaosinjector.experiment.ScenarioType;
import com.chaosinjector.target.model.WorkloadState;

/**
 * Makes the whole target unreachable for the duration, then restores it
 * (spec §7.4). Modes: PAUSE (default, freeze), STOP (graceful stop), NETWORK_OFF
 * (disconnect from all networks). All fully reversible.
 */
@Component
public class ServiceUnavailableInjector implements ChaosInjector {

    public enum Mode {
        PAUSE, STOP, NETWORK_OFF
    }

    private static final String ATTR_MODE = "mode";
    private static final String ATTR_NETWORKS = "networks";

    @Override
    public ScenarioType type() {
        return ScenarioType.SERVICE_UNAVAILABLE;
    }

    @Override
    public void validate(ExperimentContext ctx) {
        Params.enumVal(ctx.parameters(), "mode", Mode.class, Mode.PAUSE);
        int stopTimeout = Params.intVal(ctx.parameters(), "stopTimeoutSeconds", 10);
        Params.require(stopTimeout >= 0, "stopTimeoutSeconds must be >= 0");
    }

    @Override
    public InjectionHandle inject(ExperimentContext ctx) {
        Mode mode = Params.enumVal(ctx.parameters(), "mode", Mode.class, Mode.PAUSE);
        InjectionHandle handle = new InjectionHandle(type(), ctx.containerId()).put(ATTR_MODE, mode.name());
        switch (mode) {
            case PAUSE -> ctx.adapter().pause(ctx.containerId());
            case STOP -> ctx.adapter().stop(ctx.containerId(),
                    Params.intVal(ctx.parameters(), "stopTimeoutSeconds", 10));
            case NETWORK_OFF -> {
                WorkloadState state = ctx.adapter().describe(ctx.containerId());
                List<String> networks = state.networks();
                handle.put(ATTR_NETWORKS, networks);
                for (String net : networks) {
                    ctx.adapter().disconnectNetwork(ctx.containerId(), net);
                }
            }
            default -> throw new IllegalStateException("unhandled mode " + mode);
        }
        return handle;
    }

    @Override
    public void revert(ExperimentContext ctx, InjectionHandle handle) {
        Mode mode = Mode.valueOf(handle.getString(ATTR_MODE));
        switch (mode) {
            case PAUSE -> {
                if (ctx.adapter().describe(ctx.containerId()).paused()) {
                    ctx.adapter().unpause(ctx.containerId());
                }
            }
            case STOP -> {
                if (!ctx.adapter().describe(ctx.containerId()).running()) {
                    ctx.adapter().start(ctx.containerId());
                }
            }
            case NETWORK_OFF -> {
                List<String> current = ctx.adapter().describe(ctx.containerId()).networks();
                for (String net : handle.getStringList(ATTR_NETWORKS)) {
                    if (!current.contains(net)) {
                        ctx.adapter().connectNetwork(ctx.containerId(), net);
                    }
                }
            }
            default -> throw new IllegalStateException("unhandled mode " + mode);
        }
    }
}
