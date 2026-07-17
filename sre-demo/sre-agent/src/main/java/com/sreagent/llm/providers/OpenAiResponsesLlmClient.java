package com.sreagent.llm.providers;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sreagent.config.AgentProperties;
import com.sreagent.llm.HttpExecutor;
import com.sreagent.llm.LlmException;

/**
 * OpenAI-direct <b>Responses API</b> ({@code POST {baseUrl}/responses},
 * {@code Authorization: Bearer}). Same request/response shape as Azure.
 */
public class OpenAiResponsesLlmClient extends ResponsesApiLlmClient {

    private final AgentProperties.OpenAi openai;

    public OpenAiResponsesLlmClient(HttpExecutor http, ObjectMapper mapper,
                                    AgentProperties.Llm llm, AgentProperties.OpenAi openai) {
        super(http, mapper, llm);
        this.openai = openai;
    }

    @Override
    public String provider() {
        return "openai-responses";
    }

    @Override
    protected String url() {
        return openai.getBaseUrl().replaceAll("/+$", "") + "/responses";
    }

    @Override
    protected Map<String, String> authHeaders() {
        if (openai.getApiKey() == null || openai.getApiKey().isBlank()) {
            throw new LlmException("OPENAI_API_KEY is not set");
        }
        return Map.of("Authorization", "Bearer " + openai.getApiKey());
    }
}
