package com.sreagent.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sreagent.config.AgentProperties;
import com.sreagent.llm.Messages.LlmMessage;
import com.sreagent.llm.Messages.LlmRequest;
import com.sreagent.llm.Messages.LlmResponse;
import com.sreagent.llm.Messages.ToolCall;
import com.sreagent.llm.Messages.ToolSpec;
import com.sreagent.llm.providers.ChatCompletionsLlmClient;
import com.sreagent.support.FakeHttpExecutor;

class ChatCompletionsClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ChatCompletionsLlmClient client(FakeHttpExecutor http) {
        AgentProperties p = new AgentProperties();
        p.getLlm().setModel("llama-3.3-70b-versatile");
        p.getChat().setApiKey("gsk_secret");
        p.getChat().setBaseUrl("https://api.groq.com/openai/v1");
        return new ChatCompletionsLlmClient(http, mapper, p.getLlm(), p.getChat());
    }

    @Test
    void parsesToolCalls() {
        FakeHttpExecutor http = new FakeHttpExecutor();
        http.postResponse = """
            {"choices":[{"message":{"role":"assistant","content":null,
              "tool_calls":[{"id":"call_1","type":"function",
                "function":{"name":"list_targets","arguments":"{}"}}]}}]}
            """;
        LlmResponse resp = client(http).chat(new LlmRequest(
                List.of(LlmMessage.system("sys"), LlmMessage.user("go")),
                List.of(new ToolSpec("list_targets", "list", Map.of("type", "object")))));

        assertThat(resp.hasToolCalls()).isTrue();
        assertThat(resp.toolCalls().get(0).name()).isEqualTo("list_targets");
        assertThat(resp.toolCalls().get(0).id()).isEqualTo("call_1");
        // Bearer auth + correct endpoint
        assertThat(http.lastPostHeaders).containsEntry("Authorization", "Bearer gsk_secret");
        assertThat(http.lastPostUrl).isEqualTo("https://api.groq.com/openai/v1/chat/completions");
        // tools serialized in OpenAI shape
        assertThat(http.lastPostBody).contains("\"type\":\"function\"");
        assertThat(http.lastPostBody).contains("\"tool_choice\":\"auto\"");
        assertThat(http.lastPostBody).contains("llama-3.3-70b-versatile");
    }

    @Test
    void parsesTextAndMapsToolResult() {
        FakeHttpExecutor http = new FakeHttpExecutor();
        http.postResponse = """
            {"choices":[{"message":{"role":"assistant","content":"root cause found"}}]}
            """;
        // include an assistant tool-call + tool result in history to exercise mapping
        LlmRequest req = new LlmRequest(List.of(
                LlmMessage.user("go"),
                LlmMessage.assistantToolCalls(List.of(new ToolCall("call_1", "list_targets", "{}"))),
                LlmMessage.toolResult("call_1", "list_targets", "targets up")), List.of());

        LlmResponse resp = client(http).chat(req);

        assertThat(resp.text()).isEqualTo("root cause found");
        assertThat(resp.hasToolCalls()).isFalse();
        // history mapped to OpenAI chat shape
        assertThat(http.lastPostBody).contains("\"role\":\"tool\"");
        assertThat(http.lastPostBody).contains("\"tool_call_id\":\"call_1\"");
        assertThat(http.lastPostBody).contains("\"tool_calls\"");
    }

    @Test
    void surfacesErrorPayload() {
        FakeHttpExecutor http = new FakeHttpExecutor();
        http.postResponse = """
            {"error":{"message":"invalid_api_key","type":"invalid_request_error"}}
            """;
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                client(http).chat(new LlmRequest(List.of(LlmMessage.user("go")), List.of())))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("invalid_api_key");
    }
}
