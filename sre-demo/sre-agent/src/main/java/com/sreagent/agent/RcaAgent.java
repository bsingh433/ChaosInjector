package com.sreagent.agent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sreagent.agent.RcaReport.Fix;
import com.sreagent.agent.RcaReport.ProposedAction;
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

        // Remediation actions already handled inline (so the post-report pass doesn't repeat them).
        Set<String> executed = new HashSet<>();
        for (int i = 0; i < props.getMaxIterations(); i++) {
            LlmResponse resp = llm.chat(new LlmRequest(messages, tools.toolSpecs()));
            if (resp.hasToolCalls()) {
                messages.add(LlmMessage.assistantToolCalls(resp.toolCalls()));
                for (ToolCall call : resp.toolCalls()) {
                    String result = runTool(call, executed);
                    messages.add(LlmMessage.toolResult(call.id(), call.name(), result));
                }
                continue;
            }
            RcaReport report = parseReport(resp.text());
            // Many models describe the fix in recommendedFixes[].proposedAction but never emit a
            // remediation tool_call. Enforce those proposals through the same gate so behaviour is
            // driven by REMEDIATION_MODE regardless of whether the model called the tool inline.
            applyProposedFixes(report, executed);
            return report;
        }
        log.warn("RCA loop hit max iterations ({}) without a final report", props.getMaxIterations());
        return new RcaReport(null, List.of(), List.of(), List.of(),
                "Inconclusive: reached the tool-call limit without a final diagnosis.");
    }

    /** Run each reversible {@code proposedAction} from the report through the gate (unless already done). */
    private void applyProposedFixes(RcaReport report, Set<String> executed) {
        if (report == null || report.recommendedFixes() == null) {
            return;
        }
        for (Fix fix : report.recommendedFixes()) {
            ProposedAction a = fix == null ? null : fix.proposedAction();
            if (a == null || a.tool() == null || a.tool().isBlank()) {
                continue;
            }
            Tool tool = tools.get(a.tool());
            if (tool == null) {
                log.warn("proposed fix references unknown tool '{}'", a.tool());
                continue;
            }
            if (!tool.requiresConfirmation()) {
                continue; // only reversible remediation tools are gated/executed
            }
            String tgt = normalizeTarget(a.target());
            if (executed.contains(sig(a.tool(), tgt))) {
                continue; // the model already invoked this exact action during the loop
            }
            ObjectNode args = mapper.createObjectNode();
            args.put("name", tgt);
            log.info("applying proposed fix: {} on {}", a.tool(), tgt);
            runTool(new ToolCall("proposed-" + a.tool(), a.tool(), args.toString()), executed);
        }
    }

    private String runTool(ToolCall call, Set<String> executed) {
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
                executed.add(sig(call.name(), normalizeTarget(args.path("name").asText(null))));
                return result;
            }
            return tool.execute(args); // read-only
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    private String normalizeTarget(String target) {
        return (target == null || target.isBlank()) ? props.getDefaultTarget() : target;
    }

    private static String sig(String tool, String target) {
        return tool + ":" + target;
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
            start_container, abort_chaos. Whenever a fix is warranted you MUST populate
            recommendedFixes[].proposedAction as {"tool": "<tool>", "target": "%s",
            "reversible": true} — the agent applies it according to its remediation mode.
            If the evidence points to an externally injected fault (a paused container, artificial
            CPU/memory load, or an injected network failure — i.e. a chaos experiment), prefer
            abort_chaos: it is the cleanest fix because it ends the experiment and the platform
            reverts it (unpause / reconnect / kill load). Only choose restart_container /
            unpause_container / start_container when the evidence specifically calls for it (e.g. an
            OOM-killed or stopped container). Never take destructive actions.

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
