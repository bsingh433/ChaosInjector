package com.sreagent.remediation;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.sreagent.config.AgentProperties;

/**
 * Human-in-the-loop gate for reversible remediation tools (read-only tools never
 * pass through here). Modes: {@code propose} (default — deny, just record the
 * proposal), {@code prompt} (ask on the console), {@code auto} (approve).
 */
@Component
public class ConfirmationGate {

    private final String mode;
    private final BufferedReader in;

    @Autowired
    public ConfirmationGate(AgentProperties props) {
        this(props.getRemediation().getMode(),
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)));
    }

    ConfirmationGate(String mode, BufferedReader in) {
        this.mode = mode == null ? "propose" : mode.trim().toLowerCase();
        this.in = in;
    }

    public String mode() {
        return mode;
    }

    /** @return true if the remediation may execute. */
    public boolean confirm(String tool, String args) {
        switch (mode) {
            case "auto":
                return true;
            case "prompt":
                System.out.printf("%n[REMEDIATION] Approve %s %s ? [y/N] ", tool, args);
                try {
                    String line = in.readLine();
                    return line != null && line.trim().toLowerCase().startsWith("y");
                } catch (Exception e) {
                    return false;
                }
            default: // propose
                return false;
        }
    }
}
