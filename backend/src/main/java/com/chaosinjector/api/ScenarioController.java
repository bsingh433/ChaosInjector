package com.chaosinjector.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chaosinjector.api.dto.ScenarioDto;

/** Exposes the scenario schemas that drive the UI form (spec §10, §11.2). */
@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {

    private final ScenarioCatalog catalog;

    public ScenarioController(ScenarioCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<ScenarioDto> scenarios() {
        return catalog.all();
    }
}
