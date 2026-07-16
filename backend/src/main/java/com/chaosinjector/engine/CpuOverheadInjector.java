package com.chaosinjector.engine;

import java.util.List;

import org.springframework.stereotype.Component;

import com.chaosinjector.experiment.ScenarioType;

/**
 * Saturates CPU inside the target so the app competes for cycles (spec §7.2).
 * Prefers {@code stress-ng}; falls back to portable busy-loops. The load runs
 * inside the target (via exec) so the impact is realistic, and self-terminates
 * at {@code durationSeconds}; revert force-kills it for guaranteed cleanup.
 */
@Component
public class CpuOverheadInjector implements ChaosInjector {

    private static final String ATTR_PIDFILE = "pidfile";

    @Override
    public ScenarioType type() {
        return ScenarioType.CPU_OVERHEAD;
    }

    @Override
    public void validate(ExperimentContext ctx) {
        int workers = Params.intVal(ctx.parameters(), "workers", 1);
        int load = Params.intVal(ctx.parameters(), "loadPercent", 100);
        Params.require(workers >= 1 && workers <= 1024, "workers must be between 1 and 1024");
        Params.require(load >= 1 && load <= 100, "loadPercent must be between 1 and 100");
    }

    @Override
    public InjectionHandle inject(ExperimentContext ctx) {
        int workers = Params.intVal(ctx.parameters(), "workers", 1);
        int load = Params.intVal(ctx.parameters(), "loadPercent", 100);
        int t = ctx.durationSeconds();
        String pidfile = "/tmp/chaos_cpu_" + ctx.experimentId() + ".pids";

        String script = ""
                + "if command -v stress-ng >/dev/null 2>&1; then "
                + "  stress-ng --cpu " + workers + " --cpu-load " + load + " --timeout " + t + "s & "
                + "  echo $! > " + pidfile + "; "
                + "else "
                + "  pids=; i=0; while [ $i -lt " + workers + " ]; do "
                + "    sh -c 'while :; do :; done' & pids=\"$pids $!\"; i=$((i+1)); "
                + "  done; "
                + "  echo $pids > " + pidfile + "; "
                + "  (sleep " + t + "; kill $pids 2>/dev/null) & "
                + "fi";

        ctx.adapter().execDetached(ctx.containerId(), List.of("sh", "-c", script));
        return new InjectionHandle(type(), ctx.containerId()).put(ATTR_PIDFILE, pidfile);
    }

    @Override
    public void revert(ExperimentContext ctx, InjectionHandle handle) {
        String pidfile = handle.getString(ATTR_PIDFILE);
        String kill = "kill $(cat " + pidfile + " 2>/dev/null) 2>/dev/null; "
                + "pkill -f stress-ng 2>/dev/null; "
                + "rm -f " + pidfile + " 2>/dev/null; true";
        ctx.adapter().execSync(ctx.containerId(), List.of("sh", "-c", kill));
    }
}
