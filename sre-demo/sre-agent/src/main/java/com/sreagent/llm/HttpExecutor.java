package com.sreagent.llm;

import java.util.Map;

/**
 * Minimal HTTP transport the LLM adapters and Prometheus client use. Abstracted
 * so tests can stub network calls with canned provider responses.
 */
public interface HttpExecutor {

    HttpResult post(String url, Map<String, String> headers, String jsonBody);

    HttpResult get(String url, Map<String, String> headers);

    record HttpResult(int status, String body) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }
}
