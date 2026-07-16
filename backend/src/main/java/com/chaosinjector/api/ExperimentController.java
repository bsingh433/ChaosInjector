package com.chaosinjector.api;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.chaosinjector.api.dto.ExperimentRequest;
import com.chaosinjector.api.dto.ExperimentResponse;
import com.chaosinjector.experiment.Experiment;
import com.chaosinjector.experiment.ExperimentService;
import com.chaosinjector.experiment.ExperimentStore;
import com.chaosinjector.progress.ProgressBroadcaster;
import com.chaosinjector.progress.ProgressEvent;
import com.chaosinjector.progress.ProgressType;

import jakarta.validation.Valid;

/** Experiment lifecycle + live SSE stream (spec §10). */
@RestController
@RequestMapping("/api/experiments")
public class ExperimentController {

    private final ExperimentService service;
    private final ExperimentStore store;
    private final ProgressBroadcaster broadcaster;

    public ExperimentController(ExperimentService service, ExperimentStore store,
                                ProgressBroadcaster broadcaster) {
        this.service = service;
        this.store = store;
        this.broadcaster = broadcaster;
    }

    @PostMapping("/validate")
    public Map<String, Object> validate(@Valid @RequestBody ExperimentRequest req) {
        service.validateOnly(req);
        return Map.of("valid", true);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> create(@Valid @RequestBody ExperimentRequest req) {
        Experiment exp = service.create(req);
        return Map.of("experimentId", exp.getId());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExperimentResponse> get(@PathVariable String id) {
        return store.find(id)
                .map(e -> ResponseEntity.ok(ExperimentResponse.from(e)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<ExperimentResponse> list() {
        return store.recent().stream().map(ExperimentResponse::from).toList();
    }

    @PostMapping("/{id}/abort")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void abort(@PathVariable String id) {
        service.abort(id);
    }

    @GetMapping("/{id}/stream")
    public SseEmitter stream(@PathVariable String id) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout; completes on terminal event
        Consumer<ProgressEvent> listener = new Consumer<>() {
            @Override
            public void accept(ProgressEvent ev) {
                try {
                    emitter.send(SseEmitter.event()
                            .name(ev.type().name().toLowerCase())
                            .data(ev));
                    if (ev.type() == ProgressType.COMPLETED || ev.type() == ProgressType.ERROR) {
                        emitter.complete();
                    }
                } catch (IOException | IllegalStateException e) {
                    emitter.completeWithError(e);
                }
            }
        };
        broadcaster.subscribe(id, listener);
        emitter.onCompletion(() -> broadcaster.unsubscribe(id, listener));
        emitter.onTimeout(() -> broadcaster.unsubscribe(id, listener));
        emitter.onError(e -> broadcaster.unsubscribe(id, listener));
        return emitter;
    }
}
