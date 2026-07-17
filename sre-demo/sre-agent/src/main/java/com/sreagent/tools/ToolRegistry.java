package com.sreagent.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.sreagent.llm.Messages.ToolSpec;

/** Collects all {@link Tool} beans and exposes them to the agent + LLM. */
@Component
public class ToolRegistry {

    private final Map<String, Tool> byName = new LinkedHashMap<>();

    public ToolRegistry(List<Tool> tools) {
        for (Tool t : tools) {
            byName.put(t.name(), t);
        }
    }

    public Tool get(String name) {
        return byName.get(name);
    }

    public List<ToolSpec> toolSpecs() {
        return byName.values().stream()
                .map(t -> new ToolSpec(t.name(), t.description(), t.parametersSchema()))
                .toList();
    }
}
