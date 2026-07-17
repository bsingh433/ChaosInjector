package com.sreagent.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Component;

/** Default {@link HttpExecutor} on {@code java.net.http} (no vendor SDKs). */
@Component
public class JdkHttpExecutor implements HttpExecutor {

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
        try {
            HttpResponse<String> res = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new HttpResult(res.statusCode(), res.body());
        } catch (Exception e) {
            throw new LlmException("HTTP call failed: " + e.getMessage(), e);
        }
    }
}
