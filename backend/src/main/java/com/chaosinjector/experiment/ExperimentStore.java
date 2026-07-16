package com.chaosinjector.experiment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * In-memory experiment store (spec §14 — no database in MVP). Keeps recent
 * experiments for status polling and history.
 */
@Component
public class ExperimentStore {

    private final Map<String, Experiment> byId = new ConcurrentHashMap<>();

    public void save(Experiment experiment) {
        byId.put(experiment.getId(), experiment);
    }

    public Optional<Experiment> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** All experiments, newest first. */
    public List<Experiment> recent() {
        List<Experiment> all = new ArrayList<>(byId.values());
        all.sort(Comparator.comparing(Experiment::getCreatedAt).reversed());
        return all;
    }
}
