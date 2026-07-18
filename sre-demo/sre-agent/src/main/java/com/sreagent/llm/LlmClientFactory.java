package com.sreagent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.sreagent.config.AgentProperties;
import com.sreagent.llm.providers.AnthropicMessagesClient;
import com.sreagent.llm.providers.AzureResponsesLlmClient;
import com.sreagent.llm.providers.ChatCompletionsLlmClient;
import com.sreagent.llm.providers.OpenAiResponsesLlmClient;

/**
 * Builds the single {@link LlmClient} bean from {@code agent.llm.provider}.
 * Adding a provider = one more case here plus its adapter.
 */
@Configuration
public class LlmClientFactory {

    @Bean
    public LlmClient llmClient(HttpExecutor http, ObjectMapper mapper, AgentProperties props) {
        String provider = props.getLlm().getProvider();
        return switch (provider == null ? "" : provider.trim().toLowerCase()) {
            case "azure-responses" ->
                    new AzureResponsesLlmClient(http, mapper, props.getLlm(), props.getAzure());
            case "openai-responses" ->
                    new OpenAiResponsesLlmClient(http, mapper, props.getLlm(), props.getOpenai());
            case "anthropic-messages" ->
                    new AnthropicMessagesClient(http, mapper, props.getLlm(), props.getAnthropic());
            case "openai-chat" ->
                    new ChatCompletionsLlmClient(http, mapper, props.getLlm(), props.getChat());
            default -> throw new LlmException("Unknown agent.llm.provider: '" + provider
                    + "' (expected azure-responses | openai-responses | anthropic-messages | openai-chat)");
        };
    }
}
