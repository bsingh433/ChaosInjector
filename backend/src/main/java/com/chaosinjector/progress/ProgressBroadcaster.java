package com.chaosinjector.progress;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

/**
 * Fans out {@link ProgressEvent}s per experiment to live subscribers (the SSE
 * endpoint) and keeps a bounded replay buffer so a subscriber that connects
 * slightly late still sees prior events. Decoupled from the web layer so the
 * engine can publish without depending on it.
 */
@Component
public class ProgressBroadcaster {

    private static final int MAX_BUFFER = 2000;

    private final Map<String, List<Consumer<ProgressEvent>>> listeners = new ConcurrentHashMap<>();
    private final Map<String, List<ProgressEvent>> history = new ConcurrentHashMap<>();

    /** Subscribe to an experiment; immediately replays buffered events. */
    public void subscribe(String experimentId, Consumer<ProgressEvent> listener) {
        listeners.computeIfAbsent(experimentId, k -> new CopyOnWriteArrayList<>()).add(listener);
        List<ProgressEvent> buffered = history.get(experimentId);
        if (buffered != null) {
            for (ProgressEvent e : List.copyOf(buffered)) {
                safe(listener, e);
            }
        }
    }

    public void unsubscribe(String experimentId, Consumer<ProgressEvent> listener) {
        List<Consumer<ProgressEvent>> ls = listeners.get(experimentId);
        if (ls != null) {
            ls.remove(listener);
        }
    }

    public void publish(ProgressEvent event) {
        history.computeIfAbsent(event.experimentId(), k -> new CopyOnWriteArrayList<>()).add(event);
        trim(event.experimentId());
        List<Consumer<ProgressEvent>> ls = listeners.get(event.experimentId());
        if (ls != null) {
            for (Consumer<ProgressEvent> l : ls) {
                safe(l, event);
            }
        }
    }

    private void trim(String experimentId) {
        List<ProgressEvent> buffered = history.get(experimentId);
        while (buffered != null && buffered.size() > MAX_BUFFER) {
            buffered.remove(0);
        }
    }

    private void safe(Consumer<ProgressEvent> listener, ProgressEvent e) {
        try {
            listener.accept(e);
        } catch (RuntimeException ignored) {
            // a broken subscriber must not affect the run or other subscribers
        }
    }
}
