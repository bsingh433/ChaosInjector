package com.sreagent.support;

import java.util.Map;

import com.sreagent.llm.HttpExecutor;

/** Capturing/canned {@link HttpExecutor} for tests — no real network. */
public class FakeHttpExecutor implements HttpExecutor {

    public String lastPostUrl;
    public Map<String, String> lastPostHeaders;
    public String lastPostBody;
    public String lastGetUrl;

    public int postStatus = 200;
    public String postResponse = "{}";
    public String getResponse = "{}";

    @Override
    public HttpResult post(String url, Map<String, String> headers, String jsonBody) {
        this.lastPostUrl = url;
        this.lastPostHeaders = headers;
        this.lastPostBody = jsonBody;
        return new HttpResult(postStatus, postResponse);
    }

    @Override
    public HttpResult get(String url, Map<String, String> headers) {
        this.lastGetUrl = url;
        return new HttpResult(200, getResponse);
    }
}
