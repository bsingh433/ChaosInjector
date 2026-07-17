package com.sreagent.llm;

import java.util.List;

/**
 * Provider-neutral conversation types. Adapters translate these to/from each
 * vendor's wire format so the agent loop never depends on a provider.
 */
public final class Messages {

    private Messages() {
    }

    /** A tool the model may call. */
    public record ToolCall(String id, String name, String argumentsJson) {
    }

    /** A tool definition offered to the model (JSON-Schema parameters). */
    public record ToolSpec(String name, String description, Object parametersSchema) {
    }

    /** Roles used across providers. */
    public enum Role {
        SYSTEM, USER, ASSISTANT, TOOL
    }

    /**
     * One transcript entry. For {@code TOOL} results, {@code toolCallId}/{@code
     * name} link to the call; for an assistant tool-call turn, {@code toolCalls}
     * is set and {@code content} is null.
     */
    public record LlmMessage(Role role, String content, List<ToolCall> toolCalls,
                             String toolCallId, String name) {

        public static LlmMessage system(String text) {
            return new LlmMessage(Role.SYSTEM, text, List.of(), null, null);
        }

        public static LlmMessage user(String text) {
            return new LlmMessage(Role.USER, text, List.of(), null, null);
        }

        public static LlmMessage assistantText(String text) {
            return new LlmMessage(Role.ASSISTANT, text, List.of(), null, null);
        }

        public static LlmMessage assistantToolCalls(List<ToolCall> calls) {
            return new LlmMessage(Role.ASSISTANT, null, calls, null, null);
        }

        public static LlmMessage toolResult(String toolCallId, String name, String result) {
            return new LlmMessage(Role.TOOL, result, List.of(), toolCallId, name);
        }
    }

    /** A request to the model: the running transcript + available tools. */
    public record LlmRequest(List<LlmMessage> messages, List<ToolSpec> tools) {
    }

    /**
     * A model reply: either {@code toolCalls} to execute (non-empty) or final
     * {@code text}.
     */
    public record LlmResponse(String text, List<ToolCall> toolCalls) {
        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }
}
