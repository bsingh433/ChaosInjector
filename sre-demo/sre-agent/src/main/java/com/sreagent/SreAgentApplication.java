package com.sreagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * SRE Agent — an LLM-powered root-cause-analysis agent for the Chaos +
 * Observability demo. Reads Prometheus + Docker (read-only), reasons with a
 * configurable LLM (Azure OpenAI Responses API by default; also OpenAI and
 * Anthropic), and produces a structured RCA. Runs as an HTTP service and as a
 * one-shot CLI (`--analyze`).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SreAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(SreAgentApplication.class, args);
    }
}
