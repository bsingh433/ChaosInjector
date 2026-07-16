package com.chaosinjector.target;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.chaosinjector.config.ChaosInjectorProperties;
import com.chaosinjector.config.EnvInterpolator;
import com.chaosinjector.config.Errors;

/**
 * Holds Docker daemon connections registered by the operator, keyed by an opaque
 * connection id (spec §8, §10). Verifies reachability on register.
 */
@Component
public class ConnectionRegistry implements AdapterResolver {

    private final DockerClientFactory factory;
    private final ChaosInjectorProperties props;
    private final EnvInterpolator interpolator = new EnvInterpolator();
    private final Map<String, TargetAdapter> byId = new ConcurrentHashMap<>();

    public ConnectionRegistry(DockerClientFactory factory, ChaosInjectorProperties props) {
        this.factory = factory;
        this.props = props;
    }

    /**
     * Register a connection and verify it is reachable.
     *
     * @param host      daemon host, or null/blank to use the configured default
     * @param certPath  TLS cert dir, or null
     * @param tlsVerify whether to verify TLS
     * @return the new connection id
     */
    public String connect(String host, String certPath, boolean tlsVerify) {
        String resolvedHost = interpolator.interpolate(
                host == null || host.isBlank() ? props.getDocker().getDefaultHost() : host);
        String resolvedCert = interpolator.interpolate(certPath);
        TargetAdapter adapter = new DockerTargetAdapter(factory.create(resolvedHost, resolvedCert, tlsVerify));
        adapter.verifyConnection();
        String id = UUID.randomUUID().toString();
        byId.put(id, adapter);
        return id;
    }

    @Override
    public TargetAdapter resolve(String connectionId) {
        TargetAdapter adapter = byId.get(connectionId);
        if (adapter == null) {
            throw new Errors.ConnectionError("Unknown or expired connection: " + connectionId);
        }
        return adapter;
    }

    public boolean exists(String connectionId) {
        return byId.containsKey(connectionId);
    }

    /** Register a pre-built adapter (used by tests). */
    public String register(TargetAdapter adapter) {
        String id = UUID.randomUUID().toString();
        byId.put(id, adapter);
        return id;
    }
}
