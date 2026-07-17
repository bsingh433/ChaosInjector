package com.sreagent.agent;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Structured root-cause-analysis result (spec §6.6). Produced by the LLM as JSON
 * and parsed into this shape. Every hypothesis should cite evidence.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RcaReport(
        IncidentWindow incidentWindow,
        List<String> timeline,
        List<Hypothesis> hypotheses,
        List<Fix> recommendedFixes,
        String verdict) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IncidentWindow(String fromIso, String toIso) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hypothesis(String cause, Double confidence,
                             List<String> evidence, List<String> refutedBy) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fix(String summary, List<String> instructions, ProposedAction proposedAction) {
    }

    /** Reversible remediation the agent could apply (Phase C); null in read-only mode. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProposedAction(String tool, String target, boolean reversible) {
    }

    public static RcaReport rawText(String text) {
        return new RcaReport(null, List.of(), List.of(), List.of(),
                text == null ? "(no output)" : text);
    }
}
