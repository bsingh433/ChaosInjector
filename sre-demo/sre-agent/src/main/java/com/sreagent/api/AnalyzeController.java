package com.sreagent.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sreagent.agent.RcaAgent;
import com.sreagent.agent.RcaReport;
import com.sreagent.config.AgentProperties;
import com.sreagent.llm.LlmClient;

/** HTTP surface: run an RCA on demand. */
@RestController
@RequestMapping("/api")
public class AnalyzeController {

    private final RcaAgent agent;
    private final LlmClient llm;
    private final AgentProperties props;

    public AnalyzeController(RcaAgent agent, LlmClient llm, AgentProperties props) {
        this.agent = agent;
        this.llm = llm;
        this.props = props;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "provider", llm.provider(),
                "model", props.getLlm().getModel());
    }

    @PostMapping("/analyze")
    public RcaReport analyze(@RequestParam(defaultValue = "10") int windowMinutes,
                             @RequestParam(required = false) String target) {
        return agent.analyze(windowMinutes, target);
    }
}
