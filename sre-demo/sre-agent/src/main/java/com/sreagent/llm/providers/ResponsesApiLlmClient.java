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
 * Base for the OpenAI/Azure <b>Responses API</b> ({@code POST .../responses},
 * model in the request body). Subclasses supply the URL and auth headers.
 * Request/response are built here so both providers share the tool-calling
 * translation.
 */
public abstract class ResponsesApiLlmClient implements LlmClient {

    protected final HttpExecutor http;
    protected final ObjectMapper mapper;
    protected final AgentProperties.Llm llm;

    protected ResponsesApiLlmClient(HttpExecutor http, ObjectMapper mapper, AgentProperties.Llm llm) {
        this.http = http;
        this.mapper = mapper;
        this.llm = llm;
    }

    protected abstract String url();

    protected abstract Map<String, String> authHeaders();

    @Override
    public LlmResponse chat(LlmRequest request) {
        String body = buildBody(request);
        HttpExecutor.HttpResult res = http.post(url(), authHeaders(), body);
        if (!res.ok()) {
            throw new LlmException(provider() + " HTTP " + res.status() + ": " + res.body());
        }
        return parse(res.body());
    }

    String buildBody(LlmRequest request) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", llm.getModel());
        body.put("temperature", llm.getTemperature());
        body.put("max_output_tokens", llm.getMaxOutputTokens());

        ArrayNode input = body.putArray("input");
        for (LlmMessage m : request.messages()) {
            switch (m.role()) {
                case SYSTEM, USER, ASSISTANT -> {
                    if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                        for (ToolCall c : m.toolCalls()) {
                            ObjectNode fc = input.addObject();
                            fc.put("type", "function_call");
                            fc.put("call_id", c.id());
                            fc.put("name", c.name());
                            fc.put("arguments", c.argumentsJson());
                        }
                    } else if (m.content() != null) {
                        ObjectNode item = input.addObject();
                        item.put("role", m.role().name().toLowerCase());
                        item.put("content", m.content());
                    }
                }
                case TOOL -> {
                    ObjectNode out = input.addObject();
                    out.put("type", "function_call_output");
                    out.put("call_id", m.toolCallId());
                    out.put("output", m.content() == null ? "" : m.content());
                }
                default -> throw new LlmException("unhandled role " + m.role());
            }
        }

        if (request.tools() != null && !request.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (ToolSpec t : request.tools()) {
                ObjectNode fn = tools.addObject();
                fn.put("type", "function");
                fn.put("name", t.name());
                fn.put("description", t.description());
                fn.set("parameters", mapper.valueToTree(t.parametersSchema()));
            }
        }
        return body.toString();
    }

    LlmResponse parse(String responseBody) {
        try {
            JsonNode root = mapper.readTree(responseBody);
            if (root.has("error") && !root.get("error").isNull()) {
                throw new LlmException(provider() + " error: " + root.get("error").toString());
            }
            List<ToolCall> calls = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            JsonNode output = root.path("output");
            for (JsonNode item : output) {
                String type = item.path("type").asText("");
                if ("function_call".equals(type)) {
                    String id = item.path("call_id").asText(item.path("id").asText(""));
                    calls.add(new ToolCall(id, item.path("name").asText(""),
                            item.path("arguments").asText("{}")));
                } else if ("message".equals(type)) {
                    for (JsonNode c : item.path("content")) {
                        if ("output_text".equals(c.path("type").asText(""))) {
                            text.append(c.path("text").asText(""));
                        }
                    }
                }
            }
            return new LlmResponse(calls.isEmpty() ? text.toString() : null, calls);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Failed to parse " + provider() + " response", e);
        }
    }
}
