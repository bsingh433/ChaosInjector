package com.chaosinjector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed binding of the {@code chaosinjector.*} configuration tree
 * (chaos_injector_spec.md §13). Spring resolves {@code ${ENV_VAR}} placeholders
 * in {@code application.yml} natively; {@link EnvInterpolator} handles the same
 * syntax for strings supplied at runtime (e.g. connection payloads).
 */
@ConfigurationProperties(prefix = "chaosinjector")
public class ChaosInjectorProperties {

    private String version = "0.1.0";
    private final Docker docker = new Docker();
    private final Experiment experiment = new Experiment();
    private final Metrics metrics = new Metrics();
    private final Helper helper = new Helper();

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Docker getDocker() {
        return docker;
    }

    public Experiment getExperiment() {
        return experiment;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public Helper getHelper() {
        return helper;
    }

    public static class Docker {
        /** Default daemon endpoint; MVP path is the local unix socket. */
        private String defaultHost = "unix:///var/run/docker.sock";

        public String getDefaultHost() {
            return defaultHost;
        }

        public void setDefaultHost(String defaultHost) {
            this.defaultHost = defaultHost;
        }
    }

    public static class Experiment {
        /** Hard cap on any experiment's duration, enforced server-side. */
        private int maxDurationSeconds = 3600;
        /** Global vs per-target single-active guard. */
        private boolean singleActiveGlobal = false;
        private int defaultBaselineSeconds = 15;

        public int getMaxDurationSeconds() {
            return maxDurationSeconds;
        }

        public void setMaxDurationSeconds(int maxDurationSeconds) {
            this.maxDurationSeconds = maxDurationSeconds;
        }

        public boolean isSingleActiveGlobal() {
            return singleActiveGlobal;
        }

        public void setSingleActiveGlobal(boolean singleActiveGlobal) {
            this.singleActiveGlobal = singleActiveGlobal;
        }

        public int getDefaultBaselineSeconds() {
            return defaultBaselineSeconds;
        }

        public void setDefaultBaselineSeconds(int defaultBaselineSeconds) {
            this.defaultBaselineSeconds = defaultBaselineSeconds;
        }
    }

    public static class Metrics {
        private int sampleIntervalMs = 1000;
        private int probeTimeoutMs = 3000;

        public int getSampleIntervalMs() {
            return sampleIntervalMs;
        }

        public void setSampleIntervalMs(int sampleIntervalMs) {
            this.sampleIntervalMs = sampleIntervalMs;
        }

        public int getProbeTimeoutMs() {
            return probeTimeoutMs;
        }

        public void setProbeTimeoutMs(int probeTimeoutMs) {
            this.probeTimeoutMs = probeTimeoutMs;
        }
    }

    public static class Helper {
        /** Image (iproute2 + stress-ng) injectors run against the target. */
        private String image = "chaosinjector/helper:latest";

        public String getImage() {
            return image;
        }

        public void setImage(String image) {
            this.image = image;
        }
    }
}
