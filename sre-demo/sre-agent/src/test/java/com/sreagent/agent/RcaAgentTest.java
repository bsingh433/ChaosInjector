package com.sreagent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sreagent.config.AgentProperties;
import com.sreagent.llm.LlmClient;
import com.sreagent.llm.Messages.LlmRequest;
import com.sreagent.llm.Messages.LlmResponse;
import com.sreagent.llm.Messages.ToolCall;
import com.sreagent.remediation.AuditLog;
import com.sreagent.remediation.ConfirmationGate;
import com.sreagent.tools.Tool;
import com.sreagent.tools.ToolRegistry;

class RcaAgentTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ConfirmationGate gate(String mode) {
        AgentProperties p = new AgentProperties();
        p.getRemediation().setMode(mode);
        return new ConfirmationGate(p);
    }

    private ConfirmationGate proposeGate() {
        return gate("propose");
    }

    /** LLM stub returning scripted responses in order. */
    static class StubLlm implements LlmClient {
        final Deque<LlmResponse> scripted = new ArrayDeque<>();

        @Override
        public LlmResponse chat(LlmRequest request) {
            return scripted.poll();
        }

        @Override
        public String provider() {
            return "stub";
        }
    }

    /** Fake read-only tool that records invocation. */
    static class FakeTool implements Tool {
        boolean called;

        @Override
        public String name() {
            return "prometheus_instant";
        }

        @Override
        public String description() {
            return "fake";
        }

        @Override
        public Object parametersSchema() {
            return java.util.Map.of("type", "object");
        }

        @Override
        public String execute(JsonNode args) {
            called = true;
            return "series: up == 1";
        }
    }

    @Test
    void runsToolThenParsesStructuredReport() {
        StubLlm llm = new StubLlm();
        llm.scripted.add(new LlmResponse(null,
                List.of(new ToolCall("c1", "prometheus_instant", "{\"query\":\"up\"}"))));
        llm.scripted.add(new LlmResponse("""
            {"incidentWindow":{"fromIso":"t0","toIso":"t1"},
             "timeline":["t1: cpu spike"],
             "hypotheses":[{"cause":"CPU saturation on sample-app","confidence":0.9,
                            "evidence":["cpu ~100%"],"refutedBy":[]}],
             "recommendedFixes":[{"summary":"relieve CPU","instructions":["abort chaos"],"proposedAction":null}],
             "verdict":"CPU overhead degraded latency; app stayed up."}
            """, List.of()));

        FakeTool tool = new FakeTool();
        AgentProperties props = new AgentProperties();
        RcaAgent agent = new RcaAgent(llm, new ToolRegistry(List.of(tool)), props, mapper,
                proposeGate(), new AuditLog());

        RcaReport report = agent.analyze(10, "sample-app");

        assertThat(tool.called).isTrue();
        assertThat(report.hypotheses()).hasSize(1);
        assertThat(report.hypotheses().get(0).cause()).contains("CPU saturation");
        assertThat(report.hypotheses().get(0).confidence()).isEqualTo(0.9);
        assertThat(report.verdict()).contains("CPU overhead");
    }

    /** A reversible remediation tool (requires confirmation). */
    static class FakeRemediation implements Tool {
        boolean executed;

        @Override
        public String name() {
            return "abort_chaos";
        }

        @Override
        public String description() {
            return "fake remediation";
        }

        @Override
        public Object parametersSchema() {
            return java.util.Map.of("type", "object");
        }

        @Override
        public boolean requiresConfirmation() {
            return true;
        }

        @Override
        public String execute(JsonNode args) {
            executed = true;
            return "aborted";
        }
    }

    private RcaReport runWithRemediation(String mode, FakeRemediation tool, AuditLog audit) {
        StubLlm llm = new StubLlm();
        llm.scripted.add(new LlmResponse(null, List.of(new ToolCall("c1", "abort_chaos", "{}"))));
        llm.scripted.add(new LlmResponse("{\"verdict\":\"done\"}", List.of()));
        RcaAgent agent = new RcaAgent(llm, new ToolRegistry(List.of(tool)),
                new AgentProperties(), mapper, gate(mode), audit);
        return agent.analyze(10, "sample-app");
    }

    @Test
    void remediationInProposeModeIsNotExecutedButAudited() {
        FakeRemediation tool = new FakeRemediation();
        AuditLog audit = new AuditLog();
        runWithRemediation("propose", tool, audit);

        assertThat(tool.executed).isFalse();
        assertThat(audit.all()).hasSize(1);
        assertThat(audit.all().get(0).decision()).isEqualTo("PROPOSED");
    }

    @Test
    void remediationInAutoModeExecutesAndAudits() {
        FakeRemediation tool = new FakeRemediation();
        AuditLog audit = new AuditLog();
        runWithRemediation("auto", tool, audit);

        assertThat(tool.executed).isTrue();
        assertThat(audit.all()).hasSize(1);
        assertThat(audit.all().get(0).decision()).isEqualTo("APPROVED");
    }

    @Test
    void parsesJsonWrappedInFences() {
        AgentProperties props = new AgentProperties();
        RcaAgent agent = new RcaAgent(new StubLlm(), new ToolRegistry(List.of()), props, mapper,
                proposeGate(), new AuditLog());
        RcaReport r = agent.parseReport("```json\n{\"verdict\":\"ok\"}\n```");
        assertThat(r.verdict()).isEqualTo("ok");
    }

    @Test
    void fallsBackToRawTextWhenNotJson() {
        AgentProperties props = new AgentProperties();
        RcaAgent agent = new RcaAgent(new StubLlm(), new ToolRegistry(List.of()), props, mapper,
                proposeGate(), new AuditLog());
        RcaReport r = agent.parseReport("no json here");
        assertThat(r.verdict()).isEqualTo("no json here");
    }
}
