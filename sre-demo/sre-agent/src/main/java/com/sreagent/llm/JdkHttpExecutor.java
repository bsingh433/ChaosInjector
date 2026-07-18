package com.sreagent.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Default {@link HttpExecutor} on {@code java.net.http} (no vendor SDKs). */
@Component
public class JdkHttpExecutor implements HttpExecutor {

    private static final Logger log = LoggerFactory.getLogger(JdkHttpExecutor.class);

    /** Retry transient throttling/unavailability (e.g. Groq TPM 429s). */
    private static final int MAX_RETRIES = 4;
    private static final long MAX_BACKOFF_MS = 30_000;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public HttpResult post(String url, Map<String, String> headers, String jsonBody) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .header("Content-Type", "application/json");
        headers.forEach(b::header);
        return send(b.build());
    }

    @Override
    public HttpResult get(String url, Map<String, String> headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET();
        headers.forEach(b::header);
        return send(b.build());
    }

    private HttpResult send(HttpRequest request) {
        long backoffMs = 2_000;
        for (int attempt = 0; ; attempt++) {
            HttpResponse<String> res;
            try {
                res = client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                throw new LlmException("HTTP call failed: " + e.getMessage(), e);
            }
            int status = res.statusCode();
            if ((status == 429 || status == 503) && attempt < MAX_RETRIES) {
                long waitMs = Math.min(retryAfterMs(res).orElse(backoffMs), MAX_BACKOFF_MS);
                log.warn("HTTP {} (throttled); retry {}/{} in {} ms", status, attempt + 1,
                        MAX_RETRIES, waitMs);
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return new HttpResult(status, res.body());
                }
                backoffMs *= 2;
                continue;
            }
            return new HttpResult(status, res.body());
        }
    }

    /** Honour {@code Retry-After} (seconds, possibly fractional) when the server sends it. */
    private static java.util.Optional<Long> retryAfterMs(HttpResponse<String> res) {
        return res.headers().firstValue("retry-after").flatMap(v -> {
            try {
                return java.util.Optional.of((long) (Double.parseDouble(v.trim()) * 1000));
            } catch (NumberFormatException e) {
                return java.util.Optional.empty();
            }
        });
    }
}
