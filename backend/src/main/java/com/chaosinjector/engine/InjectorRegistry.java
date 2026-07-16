package com.chaosinjector.engine;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.chaosinjector.config.Errors;
import com.chaosinjector.experiment.ScenarioType;

/**
 * Resolves a {@link ScenarioType} to its {@link ChaosInjector}. Populated from
 * all injector beans Spring discovers, so adding a scenario is just adding a
 * {@code @Component ChaosInjector}.
 */
@Component
public class InjectorRegistry {

    private final Map<ScenarioType, ChaosInjector> byType = new EnumMap<>(ScenarioType.class);

    public InjectorRegistry(List<ChaosInjector> injectors) {
        for (ChaosInjector injector : injectors) {
            byType.put(injector.type(), injector);
        }
    }

    public ChaosInjector get(ScenarioType type) {
        ChaosInjector injector = byType.get(type);
        if (injector == null) {
            throw new Errors.ValidationError("No injector registered for scenario " + type);
        }
        return injector;
    }
}
