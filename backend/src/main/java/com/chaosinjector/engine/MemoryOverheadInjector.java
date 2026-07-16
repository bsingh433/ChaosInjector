package com.chaosinjector.engine;

import java.util.List;

import org.springframework.stereotype.Component;

import com.chaosinjector.experiment.ScenarioType;
import com.chaosinjector.target.model.WorkloadState;

/**
 * Consumes memory inside the target to induce pressure / OOM behaviour
 * (spec §7.3). Prefers {@code stress-ng --vm}; falls back to filling tmpfs
 * ({@code /dev/shm}) which is RAM-backed. Size is given directly ({@code
 * sizeMb}) or as a share of the container's memory limit ({@code percentOfLimit}).
 */
@Component
public class MemoryOverheadInjector implements ChaosInjector {

    private static final String ATTR_PIDFILE = "pidfile";
    private static final String ATTR_SHMFILE = "shmfile";

    @Override
    public ScenarioType type() {
        return ScenarioType.MEMORY_OVERHEAD;
    }

    @Override
    public void validate(ExperimentContext ctx) {
        int sizeMb = Params.intVal(ctx.parameters(), "sizeMb", 0);
        int pct = Params.intVal(ctx.parameters(), "percentOfLimit", 0);
        Params.require(sizeMb > 0 || pct > 0, "either sizeMb (>0) or percentOfLimit (>0) must be set");
        Params.require(pct >= 0 && pct <= 100, "percentOfLimit must be between 0 and 100");
        Params.require(sizeMb >= 0 && sizeMb <= 1_048_576, "sizeMb must be between 0 and 1048576");
    }

    @Override
    public InjectionHandle inject(ExperimentContext ctx) {
        int sizeMb = resolveSizeMb(ctx);
        int t = ctx.durationSeconds();
        String pidfile = "/tmp/chaos_mem_" + ctx.experimentId() + ".pid";
        String shmfile = "/dev/shm/chaos_mem_" + ctx.experimentId();

        String script = ""
                + "if command -v stress-ng >/dev/null 2>&1; then "
                + "  stress-ng --vm 1 --vm-bytes " + sizeMb + "m --vm-hold --timeout " + t + "s & "
                + "  echo $! > " + pidfile + "; "
                + "else "
                + "  dd if=/dev/zero of=" + shmfile + " bs=1M count=" + sizeMb + " 2>/dev/null; "
                + "  (sleep " + t + "; rm -f " + shmfile + ") & "
                + "fi";

        ctx.adapter().execDetached(ctx.containerId(), List.of("sh", "-c", script));
        return new InjectionHandle(type(), ctx.containerId())
                .put(ATTR_PIDFILE, pidfile)
                .put(ATTR_SHMFILE, shmfile);
    }

    @Override
    public void revert(ExperimentContext ctx, InjectionHandle handle) {
        String pidfile = handle.getString(ATTR_PIDFILE);
        String shmfile = handle.getString(ATTR_SHMFILE);
        String kill = "kill $(cat " + pidfile + " 2>/dev/null) 2>/dev/null; "
                + "pkill -f stress-ng 2>/dev/null; "
                + "rm -f " + shmfile + " " + pidfile + " 2>/dev/null; true";
        ctx.adapter().execSync(ctx.containerId(), List.of("sh", "-c", kill));
    }

    private int resolveSizeMb(ExperimentContext ctx) {
        int sizeMb = Params.intVal(ctx.parameters(), "sizeMb", 0);
        if (sizeMb > 0) {
            return sizeMb;
        }
        int pct = Params.intVal(ctx.parameters(), "percentOfLimit", 0);
        WorkloadState state = ctx.adapter().describe(ctx.containerId());
        Long limit = state.memoryLimitBytes();
        if (limit == null || limit <= 0) {
            throw new com.chaosinjector.config.Errors.ValidationError(
                    "percentOfLimit requires the target to have a memory limit set");
        }
        return (int) Math.max(1, (limit / (1024 * 1024)) * pct / 100);
    }
}
