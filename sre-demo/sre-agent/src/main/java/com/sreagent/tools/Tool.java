package com.sreagent.tools;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A provider-neutral tool the agent can offer to the LLM. Read-only tools return
 * {@code requiresConfirmation() == false}; remediation tools (Phase C) require a
 * human OK before {@link #execute} runs.
 */
public interface Tool {

    String name();

    String description();

    /** JSON-Schema for the tool's arguments (as a Map/JsonNode-serialisable object). */
    Object parametersSchema();

    /** Whether a human must approve before this tool executes. */
    default boolean requiresConfirmation() {
        return false;
    }

    /** Run the tool; return a short text result to feed back to the model. */
    String execute(JsonNode args) throws Exception;
}
