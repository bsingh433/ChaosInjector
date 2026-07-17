package com.sreagent.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sreagent.config.AgentProperties;
import com.sreagent.llm.Messages.LlmMessage;
import com.sreagent.llm.Messages.LlmRequest;
import com.sreagent.llm.Messages.LlmResponse;
import com.sreagent.llm.Messages.ToolCall;
import com.sreagent.llm.providers.AnthropicMessagesClient;
import com.sreagent.support.FakeHttpExecutor;

class AnthropicClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private AnthropicMessagesClient client(FakeHttpExecutor http) {
        AgentProperties p = new AgentProperties();
        p.getLlm().setModel("claude-x");
        p.getAnthropic().setApiKey("secret");
        return new AnthropicMessagesClient(http, mapper, p.getLlm(), p.getAnthropic());
    }

    @Test
    void parsesToolUseBlock() {
        FakeHttpExecutor http = new FakeHttpExecutor();
        http.postResponse = """
            {"content":[{"type":"tool_use","id":"tu_1","name":"list_targets","input":{}}],
             "stop_reason":"tool_use"}
            """;
        LlmResponse resp = client(http).chat(new LlmRequest(
                List.of(LlmMessage.system("sys"), LlmMessage.user("go")), List.of()));

        assertThat(resp.hasToolCalls()).isTrue();
        assertThat(resp.toolCalls().get(0).name()).isEqualTo("list_targets");
        assertThat(resp.toolCalls().get(0).id()).isEqualTo("tu_1");
        assertThat(http.lastPostHeaders).containsKeys("x-api-key", "anthropic-version");
        assertThat(http.lastPostBody).contains("\"system\":");
    }

    @Test
    void parsesTextAndMapsToolResult() {
        FakeHttpExecutor http = new FakeHttpExecutor();
        http.postResponse = """
            {"content":[{"type":"text","text":"done"}],"stop_reason":"end_turn"}
            """;
        // include an assistant tool-call + tool result in history to exercise mapping
        LlmRequest req = new LlmRequest(List.of(
                LlmMessage.user("go"),
                LlmMessage.assistantToolCalls(List.of(new ToolCall("tu_1", "list_targets", "{}"))),
                LlmMessage.toolResult("tu_1", "list_targets", "targets up")), List.of());

        LlmResponse resp = client(http).chat(req);

        assertThat(resp.text()).isEqualTo("done");
        assertThat(http.lastPostBody).contains("tool_use");
        assertThat(http.lastPostBody).contains("tool_result");
        assertThat(http.lastPostBody).contains("tu_1");
    }
}
