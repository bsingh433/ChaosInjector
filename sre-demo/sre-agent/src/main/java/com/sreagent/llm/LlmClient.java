package com.sreagent.llm;

import com.sreagent.llm.Messages.LlmRequest;
import com.sreagent.llm.Messages.LlmResponse;

/**
 * Provider-neutral chat client with tool calling. The agent talks only to this
 * interface; each vendor is a thin adapter selected by configuration.
 */
public interface LlmClient {

    LlmResponse chat(LlmRequest request);

    /** Provider id, e.g. {@code azure-responses}. */
    String provider();
}
