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
import com.sreagent.llm.Messages.ToolSpec;
import com.sreagent.llm.providers.AzureResponsesLlmClient;
import com.sreagent.support.FakeHttpExecutor;

class ResponsesApiClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private AgentProperties props() {
        AgentProperties p = new AgentProperties();
        p.getLlm().setModel("gpt-5");
        p.getAzure().setEndpoint("https://demo.openai.azure.com");
        p.getAzure().setApiKey("secret-key");
        return p;
    }

    private LlmRequest request() {
        ToolSpec spec = new ToolSpec("prometheus_instant", "instant query",
                Map.of("type", "object", "properties",
                        Map.of("query", Map.of("type", "string")), "required", List.of("query")));
        return new LlmRequest(List.of(
                LlmMessage.system("you are an SRE agent"),
                LlmMessage.user("investigate")), List.of(spec));
    }

    @Test
    void parsesFunctionCallAndBuildsBody() {
        FakeHttpExecutor http = new FakeHttpExecutor();
        http.postResponse = """
            {"output":[{"type":"function_call","call_id":"call_1","name":"prometheus_instant",
              "arguments":"{\\"query\\":\\"up\\"}"}]}
            """;
        AgentProperties p = props();
        AzureResponsesLlmClient client =
                new AzureResponsesLlmClient(http, mapper, p.getLlm(), p.getAzure());

        LlmResponse resp = client.chat(request());

        assertThat(resp.hasToolCalls()).isTrue();
        assertThat(resp.toolCalls()).hasSize(1);
        assertThat(resp.toolCalls().get(0).name()).isEqualTo("prometheus_instant");
        assertThat(resp.toolCalls().get(0).id()).isEqualTo("call_1");
        assertThat(resp.toolCalls().get(0).argumentsJson()).contains("query");

        // request wiring: model in body, tools present, Azure URL + api-key header
        assertThat(http.lastPostBody).contains("\"model\":\"gpt-5\"");
        assertThat(http.lastPostBody).contains("prometheus_instant");
        assertThat(http.lastPostBody).contains("\"input\"");
        assertThat(http.lastPostUrl).contains("/openai/responses?api-version=");
        assertThat(http.lastPostHeaders).containsKey("api-key");
    }

    @Test
    void parsesFinalTextMessage() {
        FakeHttpExecutor http = new FakeHttpExecutor();
        http.postResponse = """
            {"output":[{"type":"message","role":"assistant",
              "content":[{"type":"output_text","text":"all healthy"}]}]}
            """;
        AgentProperties p = props();
        AzureResponsesLlmClient client =
                new AzureResponsesLlmClient(http, mapper, p.getLlm(), p.getAzure());

        LlmResponse resp = client.chat(request());

        assertThat(resp.hasToolCalls()).isFalse();
        assertThat(resp.text()).isEqualTo("all healthy");
    }
}
