package com.sreagent.agent;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sreagent.config.AgentProperties;
import com.sreagent.llm.LlmClient;
import com.sreagent.llm.Messages.LlmMessage;
import com.sreagent.llm.Messages.LlmRequest;
import com.sreagent.llm.Messages.LlmResponse;
import com.sreagent.llm.Messages.ToolCall;
import com.sreagent.remediation.AuditLog;
import com.sreagent.remediation.ConfirmationGate;
import com.sreagent.tools.Tool;
import com.sreagent.tools.ToolRegistry;

/**
 * The agentic RCA loop: gather evidence via read-only tools → reason with the
 * configured LLM → emit a structured {@link RcaReport}. The agent is not told
 * what fault was injected; it must discover the cause from the tools.
 */
@Service
public class RcaAgent {

    private static final Logger log = LoggerFactory.getLogger(RcaAgent.class);

    private final LlmClient llm;
    private final ToolRegistry tools;
    private final AgentProperties props;
    private final ObjectMapper mapper;
    private final ObjectMapper lenient;
    private final ConfirmationGate gate;
    private final AuditLog audit;

    public RcaAgent(LlmClient llm, ToolRegistry tools, AgentProperties props, ObjectMapper mapper,
                    ConfirmationGate gate, AuditLog audit) {
        this.llm = llm;
        this.tools = tools;
        this.props = props;
        this.mapper = mapper;
        this.gate = gate;
        this.audit = audit;
        this.lenient = mapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public RcaReport analyze(int windowMinutes, String target) {
        String tgt = (target == null || target.isBlank()) ? props.getDefaultTarget() : target;
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.system(systemPrompt(tgt, windowMinutes)));
        messages.add(LlmMessage.user("Investigate the target container '" + tgt + "' over the last "
                + windowMinutes + " minutes. Determine the root cause of any degradation and produce"
                + " the RCA JSON. If everything looks healthy, say so."));

        for (int i = 0; i < props.getMaxIterations(); i++) {
            LlmResponse resp = llm.chat(new LlmRequest(messages, tools.toolSpecs()));
            if (resp.hasToolCalls()) {
                messages.add(LlmMessage.assistantToolCalls(resp.toolCalls()));
                for (ToolCall call : resp.toolCalls()) {
                    String result = runTool(call);
                    messages.add(LlmMessage.toolResult(call.id(), call.name(), result));
                }
                continue;
            }
            return parseReport(resp.text());
        }
        log.warn("RCA loop hit max iterations ({}) without a final report", props.getMaxIterations());
        return new RcaReport(null, List.of(), List.of(), List.of(),
                "Inconclusive: reached the tool-call limit without a final diagnosis.");
    }

    private String runTool(ToolCall call) {
        Tool tool = tools.get(call.name());
        if (tool == null) {
            return "error: unknown tool '" + call.name() + "'";
        }
        try {
            JsonNode args = call.argumentsJson() == null || call.argumentsJson().isBlank()
                    ? mapper.createObjectNode() : mapper.readTree(call.argumentsJson());
            log.info("tool {} args={}", call.name(), args);

            if (tool.requiresConfirmation()) {
                // Gated reversible remediation: model proposes, human approves, code executes.
                if (!gate.confirm(call.name(), args.toString())) {
                    String msg = "PROPOSED (not executed): would run " + call.name() + " with " + args
                            + ". This reversible action needs human approval"
                            + " (remediation mode=" + gate.mode() + ").";
                    audit.add(call.name(), args.toString(), "PROPOSED", msg);
                    return msg;
                }
                String result = tool.execute(args);
                audit.add(call.name(), args.toString(), "APPROVED", result);
                return result;
            }
            return tool.execute(args); // read-only
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    RcaReport parseReport(String text) {
        if (text == null || text.isBlank()) {
            return RcaReport.rawText("(empty response)");
        }
        String json = extractJson(text);
        if (json != null) {
            try {
                return lenient.readValue(json, RcaReport.class);
            } catch (Exception e) {
                log.warn("Could not parse RCA JSON: {}", e.getMessage());
            }
        }
        return RcaReport.rawText(text.trim());
    }

    /** Pull the first {...} block (handles ```json fences and surrounding prose). */
    static String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    private String systemPrompt(String target, int windowMinutes) {
        return """
            You are an SRE root-cause-analysis agent. A backend service may be degraded.
            You have READ-ONLY tools over Prometheus and Docker. Investigate methodically:

            1. Check availability: up{job="%s"} and recent_changes (restarts/new starts).
            2. Compare against a baseline: query the metric over the window to see when it changed.
            3. Localise: is it CPU (container_stats / container CPU), memory (container memory,
               restarts/OOM), the downstream dependency (downstream_request_duration_seconds,
               downstream_errors_total), or the database (db_operation_duration_seconds,
               db_errors_total)? Distinguish "the app is slow" from "a dependency is slow".
            4. Validate each hypothesis against real tool output before asserting it.

            You also have REVERSIBLE remediation tools: restart_container, unpause_container,
            start_container, abort_chaos. If a fix is warranted, CALL the appropriate remediation
            tool — it is gated for human approval, so it may return "PROPOSED (not executed)". Either
            way, record the fix in recommendedFixes[].proposedAction as
            {"tool": "<tool>", "target": "%s", "reversible": true}. abort_chaos is usually the
            cleanest fix (it ends the injected fault). Never take destructive actions.

            Rules:
            - Ground every hypothesis in evidence you actually retrieved. Do not guess.
            - If signal is insufficient, say so rather than inventing a cause.
            - Target container: %s. Window: last %d minutes.
            - Useful metrics: up, http_request_duration_seconds, http_requests_total,
              downstream_request_duration_seconds, downstream_errors_total,
              db_operation_duration_seconds, db_errors_total, container_cpu_usage_seconds_total,
              container_memory_usage_bytes.

            When done, respond with ONLY a JSON object (no prose, no code fences) of this shape:
            {
              "incidentWindow": {"fromIso": "...", "toIso": "..."},
              "timeline": ["t: what happened", ...],
              "hypotheses": [
                {"cause": "...", "confidence": 0.0-1.0, "evidence": ["metric/value ..."], "refutedBy": []}
              ],
              "recommendedFixes": [
                {"summary": "...", "instructions": ["step 1", "step 2"], "proposedAction": null}
              ],
              "verdict": "one-sentence plain-language conclusion"
            }
            Order hypotheses most-likely first.
            """.formatted(target, target, target, windowMinutes);
    }
}
