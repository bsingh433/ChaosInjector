package com.sreagent.llm.providers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sreagent.config.AgentProperties;
import com.sreagent.llm.HttpExecutor;
import com.sreagent.llm.LlmClient;
import com.sreagent.llm.LlmException;
import com.sreagent.llm.Messages.LlmMessage;
import com.sreagent.llm.Messages.LlmRequest;
import com.sreagent.llm.Messages.LlmResponse;
import com.sreagent.llm.Messages.ToolCall;
import com.sreagent.llm.Messages.ToolSpec;

/**
 * Anthropic / Claude <b>Messages API</b> ({@code POST {baseUrl}/messages},
 * {@code x-api-key} + {@code anthropic-version}). Uses Anthropic-style
 * {@code tool_use}/{@code tool_result} blocks; the neutral transcript is
 * translated here.
 */
public class AnthropicMessagesClient implements LlmClient {

    private final HttpExecutor http;
    private final ObjectMapper mapper;
    private final AgentProperties.Llm llm;
    private final AgentProperties.Anthropic anthropic;

    public AnthropicMessagesClient(HttpExecutor http, ObjectMapper mapper,
                                   AgentProperties.Llm llm, AgentProperties.Anthropic anthropic) {
        this.http = http;
        this.mapper = mapper;
        this.llm = llm;
        this.anthropic = anthropic;
    }

    @Override
    public String provider() {
        return "anthropic-messages";
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        if (anthropic.getApiKey() == null || anthropic.getApiKey().isBlank()) {
            throw new LlmException("ANTHROPIC_API_KEY is not set");
        }
        String url = anthropic.getBaseUrl().replaceAll("/+$", "") + "/messages";
        Map<String, String> headers = Map.of(
                "x-api-key", anthropic.getApiKey(),
                "anthropic-version", anthropic.getVersion());
        HttpExecutor.HttpResult res = http.post(url, headers, buildBody(request));
        if (!res.ok()) {
            throw new LlmException("anthropic HTTP " + res.status() + ": " + res.body());
        }
        return parse(res.body());
    }

    String buildBody(LlmRequest request) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", llm.getModel());
        body.put("max_tokens", llm.getMaxOutputTokens());
        body.put("temperature", llm.getTemperature());

        StringBuilder system = new StringBuilder();
        ArrayNode messages = body.putArray("messages");
        for (LlmMessage m : request.messages()) {
            switch (m.role()) {
                case SYSTEM -> {
                    if (m.content() != null) {
                        if (system.length() > 0) {
                            system.append("\n\n");
                        }
                        system.append(m.content());
                    }
                }
                case USER -> messages.add(textMessage("user", m.content()));
                case ASSISTANT -> {
                    if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                        ObjectNode msg = messages.addObject();
                        msg.put("role", "assistant");
                        ArrayNode content = msg.putArray("content");
                        for (ToolCall c : m.toolCalls()) {
                            ObjectNode use = content.addObject();
                            use.put("type", "tool_use");
                            use.put("id", c.id());
                            use.put("name", c.name());
                            use.set("input", parseJsonOrEmpty(c.argumentsJson()));
                        }
                    } else if (m.content() != null) {
                        messages.add(textMessage("assistant", m.content()));
                    }
                }
                case TOOL -> {
                    ObjectNode msg = messages.addObject();
                    msg.put("role", "user");
                    ArrayNode content = msg.putArray("content");
                    ObjectNode tr = content.addObject();
                    tr.put("type", "tool_result");
                    tr.put("tool_use_id", m.toolCallId());
                    tr.put("content", m.content() == null ? "" : m.content());
                }
                default -> throw new LlmException("unhandled role " + m.role());
            }
        }
        if (system.length() > 0) {
            body.put("system", system.toString());
        }

        if (request.tools() != null && !request.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (ToolSpec t : request.tools()) {
                ObjectNode tool = tools.addObject();
                tool.put("name", t.name());
                tool.put("description", t.description());
                tool.set("input_schema", mapper.valueToTree(t.parametersSchema()));
            }
        }
        return body.toString();
    }

    LlmResponse parse(String responseBody) {
        try {
            JsonNode root = mapper.readTree(responseBody);
            if (root.has("error") && !root.get("error").isNull()) {
                throw new LlmException("anthropic error: " + root.get("error").toString());
            }
            List<ToolCall> calls = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            for (JsonNode block : root.path("content")) {
                String type = block.path("type").asText("");
                if ("text".equals(type)) {
                    text.append(block.path("text").asText(""));
                } else if ("tool_use".equals(type)) {
                    calls.add(new ToolCall(block.path("id").asText(""),
                            block.path("name").asText(""),
                            block.path("input").toString()));
                }
            }
            return new LlmResponse(calls.isEmpty() ? text.toString() : null, calls);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Failed to parse anthropic response", e);
        }
    }

    private ObjectNode textMessage(String role, String text) {
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", role);
        ArrayNode content = msg.putArray("content");
        ObjectNode block = content.addObject();
        block.put("type", "text");
        block.put("text", text == null ? "" : text);
        return msg;
    }

    private JsonNode parseJsonOrEmpty(String json) {
        try {
            return (json == null || json.isBlank()) ? mapper.createObjectNode() : mapper.readTree(json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }
}
