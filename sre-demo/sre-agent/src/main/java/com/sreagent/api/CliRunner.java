package com.sreagent.api;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sreagent.agent.RcaAgent;
import com.sreagent.agent.RcaReport;

/**
 * One-shot CLI mode: {@code java -jar sre-agent.jar --analyze --window=10 --target=sample-app}
 * runs a single RCA, prints the report as JSON, and exits. Without {@code --analyze}
 * the app stays up as the HTTP service.
 */
@Component
@Order(1)
public class CliRunner implements ApplicationRunner {

    private final RcaAgent agent;
    private final ObjectMapper mapper;
    private final ApplicationContext context;

    public CliRunner(RcaAgent agent, ObjectMapper mapper, ApplicationContext context) {
        this.agent = agent;
        this.mapper = mapper;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.containsOption("analyze")) {
            return; // stay up as the HTTP service
        }
        int window = optInt(args, "window", 10);
        String target = args.containsOption("target") ? args.getOptionValues("target").get(0) : null;

        RcaReport report = agent.analyze(window, target);
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));

        int code = SpringApplication.exit(context, () -> 0);
        System.exit(code);
    }

    private int optInt(ApplicationArguments args, String name, int def) {
        if (args.containsOption(name) && !args.getOptionValues(name).isEmpty()) {
            try {
                return Integer.parseInt(args.getOptionValues(name).get(0));
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return def;
    }
}
