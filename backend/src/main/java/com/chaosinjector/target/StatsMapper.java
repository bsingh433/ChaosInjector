package com.chaosinjector.target;

import java.time.Instant;

import com.chaosinjector.target.model.Stats;
import com.github.dockerjava.api.model.BlkioStatEntry;
import com.github.dockerjava.api.model.StatisticNetworksConfig;
import com.github.dockerjava.api.model.Statistics;

/**
 * Maps a docker-java {@link Statistics} frame to our {@link Stats}. Kept
 * separate from the adapter so this arithmetic (notably the {@code docker
 * stats}-style CPU percentage) is unit-testable without a live daemon.
 */
final class StatsMapper {

    private StatsMapper() {
    }

    static Stats toStats(Statistics s) {
        double cpuPercent = computeCpuPercent(s);

        long memUsage = 0;
        long memLimit = 0;
        double memPercent = 0;
        if (s.getMemoryStats() != null) {
            memUsage = orZero(s.getMemoryStats().getUsage());
            memLimit = orZero(s.getMemoryStats().getLimit());
            memPercent = memLimit > 0 ? (memUsage * 100.0 / memLimit) : 0;
        }

        long rx = 0;
        long tx = 0;
        if (s.getNetworks() != null) {
            for (StatisticNetworksConfig n : s.getNetworks().values()) {
                rx += orZero(n.getRxBytes());
                tx += orZero(n.getTxBytes());
            }
        }

        long blkRead = 0;
        long blkWrite = 0;
        if (s.getBlkioStats() != null && s.getBlkioStats().getIoServiceBytesRecursive() != null) {
            for (BlkioStatEntry e : s.getBlkioStats().getIoServiceBytesRecursive()) {
                if ("read".equalsIgnoreCase(e.getOp())) {
                    blkRead += e.getValue();
                } else if ("write".equalsIgnoreCase(e.getOp())) {
                    blkWrite += e.getValue();
                }
            }
        }

        long pids = s.getPidsStats() != null ? orZero(s.getPidsStats().getCurrent()) : 0;

        return new Stats(Instant.now(), cpuPercent, memUsage, memLimit, memPercent,
                rx, tx, blkRead, blkWrite, pids);
    }

    static double computeCpuPercent(Statistics s) {
        if (s.getCpuStats() == null || s.getPreCpuStats() == null
                || s.getCpuStats().getCpuUsage() == null) {
            return 0;
        }
        long cpuTotal = orZero(s.getCpuStats().getCpuUsage().getTotalUsage());
        long preTotal = s.getPreCpuStats().getCpuUsage() == null
                ? 0 : orZero(s.getPreCpuStats().getCpuUsage().getTotalUsage());
        long systemUsage = orZero(s.getCpuStats().getSystemCpuUsage());
        long preSystem = orZero(s.getPreCpuStats().getSystemCpuUsage());

        long onlineCpus = s.getCpuStats().getOnlineCpus() != null
                ? s.getCpuStats().getOnlineCpus()
                : (s.getCpuStats().getCpuUsage().getPercpuUsage() != null
                        ? s.getCpuStats().getCpuUsage().getPercpuUsage().size() : 1);

        double cpuDelta = cpuTotal - preTotal;
        double sysDelta = systemUsage - preSystem;
        if (cpuDelta > 0 && sysDelta > 0) {
            return (cpuDelta / sysDelta) * Math.max(onlineCpus, 1) * 100.0;
        }
        return 0;
    }

    private static long orZero(Long v) {
        return v == null ? 0 : v;
    }
}
