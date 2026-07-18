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
 * OpenAI-compatible <b>Chat Completions API</b> ({@code POST {baseUrl}/chat/completions},
 * {@code Authorization: Bearer}). Works with any OpenAI-compatible endpoint by
 * setting the base URL — notably <b>Groq</b> ({@code https://api.groq.com/openai/v1}),
 * as well as OpenAI's own Chat Completions API and gateways like Together/Fireworks.
 * Uses OpenAI-style {@code tools} + {@code tool_calls}.
 */
public class ChatCompletionsLlmClient implements LlmClient {

    private final HttpExecutor http;
    private final ObjectMapper mapper;
    private final AgentProperties.Llm llm;
    private final AgentProperties.Chat chat;

    public ChatCompletionsLlmClient(HttpExecutor http, ObjectMapper mapper,
                                    AgentProperties.Llm llm, AgentProperties.Chat chat) {
        this.http = http;
        this.mapper = mapper;
        this.llm = llm;
        this.chat = chat;
    }

    @Override
    public String provider() {
        return "openai-chat";
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        if (chat.getApiKey() == null || chat.getApiKey().isBlank()) {
            throw new LlmException("OPENAI_CHAT_API_KEY (or OPENAI_API_KEY) is not set");
        }
        String url = chat.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";
        HttpExecutor.HttpResult res = http.post(url,
                Map.of("Authorization", "Bearer " + chat.getApiKey()), buildBody(request));
        if (!res.ok()) {
            throw new LlmException("openai-chat HTTP " + res.status() + ": " + res.body());
        }
        return parse(res.body());
    }

    String buildBody(LlmRequest request) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", llm.getModel());
        body.put("temperature", llm.getTemperature());
        body.put("max_tokens", llm.getMaxOutputTokens());

        ArrayNode messages = body.putArray("messages");
        for (LlmMessage m : request.messages()) {
            switch (m.role()) {
                case SYSTEM -> messages.add(textMessage("system", m.content()));
                case USER -> messages.add(textMessage("user", m.content()));
                case ASSISTANT -> {
                    if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                        ObjectNode msg = messages.addObject();
                        msg.put("role", "assistant");
                        msg.putNull("content");
                        ArrayNode tc = msg.putArray("tool_calls");
                        for (ToolCall c : m.toolCalls()) {
                            ObjectNode call = tc.addObject();
                            call.put("id", c.id());
                            call.put("type", "function");
                            ObjectNode fn = call.putObject("function");
                            fn.put("name", c.name());
                            fn.put("arguments", c.argumentsJson() == null ? "{}" : c.argumentsJson());
                        }
                    } else if (m.content() != null) {
                        messages.add(textMessage("assistant", m.content()));
                    }
                }
                case TOOL -> {
                    ObjectNode msg = messages.addObject();
                    msg.put("role", "tool");
                    msg.put("tool_call_id", m.toolCallId());
                    msg.put("content", m.content() == null ? "" : m.content());
                }
                default -> throw new LlmException("unhandled role " + m.role());
            }
        }

        if (request.tools() != null && !request.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (ToolSpec t : request.tools()) {
                ObjectNode tool = tools.addObject();
                tool.put("type", "function");
                ObjectNode fn = tool.putObject("function");
                fn.put("name", t.name());
                fn.put("description", t.description());
                fn.set("parameters", mapper.valueToTree(t.parametersSchema()));
            }
            body.put("tool_choice", "auto");
        }
        return body.toString();
    }

    LlmResponse parse(String responseBody) {
        try {
            JsonNode root = mapper.readTree(responseBody);
            if (root.has("error") && !root.get("error").isNull()) {
                throw new LlmException("openai-chat error: " + root.get("error").toString());
            }
            JsonNode message = root.path("choices").path(0).path("message");
            JsonNode toolCalls = message.path("tool_calls");
            if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                List<ToolCall> calls = new ArrayList<>();
                for (JsonNode c : toolCalls) {
                    calls.add(new ToolCall(
                            c.path("id").asText(""),
                            c.path("function").path("name").asText(""),
                            c.path("function").path("arguments").asText("{}")));
                }
                return new LlmResponse(null, calls);
            }
            return new LlmResponse(message.path("content").asText(""), List.of());
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Failed to parse openai-chat response", e);
        }
    }

    private ObjectNode textMessage(String role, String text) {
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", role);
        msg.put("content", text == null ? "" : text);
        return msg;
    }
}
