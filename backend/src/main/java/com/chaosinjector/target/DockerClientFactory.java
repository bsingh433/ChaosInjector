package com.chaosinjector.target;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

/**
 * Builds {@link DockerClient} instances for a given daemon host. The MVP path is
 * the local unix socket; a remote {@code tcp://} host with TLS material is also
 * supported (spec §8.2).
 */
@Component
public class DockerClientFactory {

    /** Build a client for the local/default daemon (no explicit TLS). */
    public DockerClient create(String dockerHost) {
        return create(dockerHost, null, false);
    }

    /**
     * @param dockerHost daemon endpoint, e.g. {@code unix:///var/run/docker.sock}
     *                   or {@code tcp://host:2376}
     * @param certPath   directory with ca/cert/key PEMs for TLS, or null
     * @param tlsVerify  whether to verify TLS
     */
    public DockerClient create(String dockerHost, String certPath, boolean tlsVerify) {
        DefaultDockerClientConfig.Builder builder = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .withDockerTlsVerify(tlsVerify);
        if (certPath != null && !certPath.isBlank()) {
            builder.withDockerCertPath(certPath);
        }
        DockerClientConfig config = builder.build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(50)
                .connectionTimeout(Duration.ofSeconds(10))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }
}
