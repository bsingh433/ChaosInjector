package com.sreagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All agent configuration under the {@code agent.*} tree. Secrets arrive via
 * {@code ${ENV_VAR}} placeholders in application.yml and are never logged.
 */
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    private final Llm llm = new Llm();
    private final Azure azure = new Azure();
    private final OpenAi openai = new OpenAi();
    private final Chat chat = new Chat();
    private final Anthropic anthropic = new Anthropic();
    private final Remediation remediation = new Remediation();

    private String prometheusUrl = "http://localhost:9090";
    private String dockerHost = "unix:///var/run/docker.sock";
    private String defaultTarget = "sample-app";
    private int maxIterations = 12;
    /** ChaosInjector base URL (Phase C remediation: abort_chaos). */
    private String chaosinjectorUrl = "http://localhost:8081";

    public Llm getLlm() {
        return llm;
    }

    public Azure getAzure() {
        return azure;
    }

    public OpenAi getOpenai() {
        return openai;
    }

    public Chat getChat() {
        return chat;
    }

    public Anthropic getAnthropic() {
        return anthropic;
    }

    public Remediation getRemediation() {
        return remediation;
    }

    public String getPrometheusUrl() {
        return prometheusUrl;
    }

    public void setPrometheusUrl(String prometheusUrl) {
        this.prometheusUrl = prometheusUrl;
    }

    public String getDockerHost() {
        return dockerHost;
    }

    public void setDockerHost(String dockerHost) {
        this.dockerHost = dockerHost;
    }

    public String getDefaultTarget() {
        return defaultTarget;
    }

    public void setDefaultTarget(String defaultTarget) {
        this.defaultTarget = defaultTarget;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public String getChaosinjectorUrl() {
        return chaosinjectorUrl;
    }

    public void setChaosinjectorUrl(String chaosinjectorUrl) {
        this.chaosinjectorUrl = chaosinjectorUrl;
    }

    /** provider ∈ azure-responses | openai-responses | anthropic-messages | openai-chat */
    public static class Llm {
        private String provider = "azure-responses";
        private String model = "gpt-5";
        private double temperature = 0.2;
        private int maxOutputTokens = 2000;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getMaxOutputTokens() {
            return maxOutputTokens;
        }

        public void setMaxOutputTokens(int maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
        }
    }

    /**
     * How remediation (reversible) tools are gated:
     * {@code propose} (default — never executes, records the proposed action),
     * {@code prompt} (asks on the console), {@code auto} (executes without asking).
     */
    public static class Remediation {
        private String mode = "propose";

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }
    }

    public static class Azure {
        private String endpoint = "";
        private String apiVersion = "2025-04-01-preview";
        private String apiKey = "";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getApiVersion() {
            return apiVersion;
        }

        public void setApiVersion(String apiVersion) {
            this.apiVersion = apiVersion;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }

    public static class OpenAi {
        private String apiKey = "";
        private String baseUrl = "https://api.openai.com/v1";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    /** OpenAI-compatible Chat Completions (OpenAI classic / Groq / other gateways). */
    public static class Chat {
        private String apiKey = "";
        private String baseUrl = "https://api.openai.com/v1";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class Anthropic {
        private String apiKey = "";
        private String version = "2023-06-01";
        private String baseUrl = "https://api.anthropic.com/v1";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
}
