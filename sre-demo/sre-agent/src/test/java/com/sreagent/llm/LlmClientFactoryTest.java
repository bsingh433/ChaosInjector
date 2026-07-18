package com.sreagent.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sreagent.config.AgentProperties;
import com.sreagent.support.FakeHttpExecutor;

class LlmClientFactoryTest {

    private final LlmClientFactory factory = new LlmClientFactory();
    private final ObjectMapper mapper = new ObjectMapper();
    private final FakeHttpExecutor http = new FakeHttpExecutor();

    private LlmClient build(String provider) {
        AgentProperties p = new AgentProperties();
        p.getLlm().setProvider(provider);
        return factory.llmClient(http, mapper, p);
    }

    @Test
    void selectsProviderByConfig() {
        assertThat(build("azure-responses").provider()).isEqualTo("azure-responses");
        assertThat(build("openai-responses").provider()).isEqualTo("openai-responses");
        assertThat(build("anthropic-messages").provider()).isEqualTo("anthropic-messages");
        assertThat(build("openai-chat").provider()).isEqualTo("openai-chat");
    }

    @Test
    void unknownProviderFails() {
        assertThatThrownBy(() -> build("gpt4all"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("Unknown agent.llm.provider");
    }
}
