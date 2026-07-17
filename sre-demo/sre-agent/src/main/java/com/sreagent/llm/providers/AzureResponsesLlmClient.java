package com.sreagent.llm.providers;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sreagent.config.AgentProperties;
import com.sreagent.llm.HttpExecutor;
import com.sreagent.llm.LlmException;

/**
 * Azure OpenAI <b>Responses API</b> (default provider).
 * {@code POST {endpoint}/openai/responses?api-version=...}, {@code api-key}
 * header, model in the body.
 */
public class AzureResponsesLlmClient extends ResponsesApiLlmClient {

    private final AgentProperties.Azure azure;

    public AzureResponsesLlmClient(HttpExecutor http, ObjectMapper mapper,
                                   AgentProperties.Llm llm, AgentProperties.Azure azure) {
        super(http, mapper, llm);
        this.azure = azure;
    }

    @Override
    public String provider() {
        return "azure-responses";
    }

    @Override
    protected String url() {
        if (azure.getEndpoint() == null || azure.getEndpoint().isBlank()) {
            throw new LlmException("AZURE_OPENAI_ENDPOINT is not set");
        }
        String base = azure.getEndpoint().replaceAll("/+$", "");
        return base + "/openai/responses?api-version=" + azure.getApiVersion();
    }

    @Override
    protected Map<String, String> authHeaders() {
        if (azure.getApiKey() == null || azure.getApiKey().isBlank()) {
            throw new LlmException("AZURE_OPENAI_API_KEY is not set");
        }
        return Map.of("api-key", azure.getApiKey());
    }
}
