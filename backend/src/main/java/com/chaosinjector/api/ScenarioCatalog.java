package com.chaosinjector.api;

import java.util.List;

import org.springframework.stereotype.Component;

import com.chaosinjector.api.dto.ScenarioDto;
import com.chaosinjector.api.dto.ScenarioDto.ParamSpec;
import com.chaosinjector.experiment.ScenarioType;

/**
 * Static schema of the four MVP scenarios and their parameters (spec §7). Drives
 * the UI's dynamic parameter form. {@code dependsOnMode} lets the UI show a
 * parameter only for the relevant network/service mode.
 */
@Component
public class ScenarioCatalog {

    public List<ScenarioDto> all() {
        return List.of(networkFailure(), cpuOverhead(), memoryOverhead(), serviceUnavailable());
    }

    private ScenarioDto networkFailure() {
        return new ScenarioDto(ScenarioType.NETWORK_FAILURE, "Network Failure",
                "Degrade or sever the target's network with tc/netem.",
                List.of(
                        new ParamSpec("mode", "enum", "Mode", true, "LATENCY", null, null,
                                List.of("LATENCY", "PACKET_LOSS", "BANDWIDTH", "PARTITION"), null),
                        new ParamSpec("latencyMs", "int", "Latency (ms)", false, 300, 1.0, 600000.0, null, "LATENCY"),
                        new ParamSpec("jitterMs", "int", "Jitter (ms)", false, 0, 0.0, 600000.0, null, "LATENCY"),
                        new ParamSpec("lossPercent", "number", "Packet loss (%)", false, 20, 0.0, 100.0, null, "PACKET_LOSS"),
                        new ParamSpec("rateKbit", "int", "Bandwidth (kbit)", false, 1000, 1.0, null, null, "BANDWIDTH"),
                        new ParamSpec("iface", "string", "Interface", false, "eth0", null, null, null, null)));
    }

    private ScenarioDto cpuOverhead() {
        return new ScenarioDto(ScenarioType.CPU_OVERHEAD, "CPU Overhead",
                "Saturate CPU inside the target so the app competes for cycles.",
                List.of(
                        new ParamSpec("workers", "int", "Workers", false, 1, 1.0, 1024.0, null, null),
                        new ParamSpec("loadPercent", "int", "Load (%)", false, 100, 1.0, 100.0, null, null)));
    }

    private ScenarioDto memoryOverhead() {
        return new ScenarioDto(ScenarioType.MEMORY_OVERHEAD, "Memory Overhead",
                "Consume memory inside the target to induce pressure / OOM.",
                List.of(
                        new ParamSpec("sizeMb", "int", "Size (MB)", false, 256, 0.0, 1048576.0, null, null),
                        new ParamSpec("percentOfLimit", "int", "Percent of limit (%)", false, 0, 0.0, 100.0, null, null)));
    }

    private ScenarioDto serviceUnavailable() {
        return new ScenarioDto(ScenarioType.SERVICE_UNAVAILABLE, "Service Unavailable",
                "Make the whole target unreachable for the duration, then restore it.",
                List.of(
                        new ParamSpec("mode", "enum", "Mode", true, "PAUSE", null, null,
                                List.of("PAUSE", "STOP", "NETWORK_OFF"), null),
                        new ParamSpec("stopTimeoutSeconds", "int", "Stop timeout (s)", false, 10, 0.0, 600.0, null, "STOP")));
    }
}
