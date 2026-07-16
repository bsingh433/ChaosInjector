package com.chaosinjector.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.chaosinjector.experiment.ScenarioType;

/**
 * Records everything needed to undo a fault, so {@link ChaosInjector#revert}
 * never depends on in-flight memory alone (spec §6.3). Carries the target id,
 * any helper container ids, and scenario-specific attributes.
 */
public class InjectionHandle {

    private final ScenarioType type;
    private final String containerId;
    private final List<String> helperIds = new ArrayList<>();
    private final Map<String, Object> attributes = new HashMap<>();

    public InjectionHandle(ScenarioType type, String containerId) {
        this.type = type;
        this.containerId = containerId;
    }

    public ScenarioType type() {
        return type;
    }

    public String containerId() {
        return containerId;
    }

    public InjectionHandle addHelper(String id) {
        helperIds.add(id);
        return this;
    }

    public List<String> helperIds() {
        return helperIds;
    }

    public InjectionHandle put(String key, Object value) {
        attributes.put(key, value);
        return this;
    }

    public Object get(String key) {
        return attributes.get(key);
    }

    public String getString(String key) {
        Object v = attributes.get(key);
        return v == null ? null : v.toString();
    }

    @SuppressWarnings("unchecked")
    public List<String> getStringList(String key) {
        Object v = attributes.get(key);
        return v == null ? List.of() : (List<String>) v;
    }
}
