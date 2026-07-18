package com.sreagent.api;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sreagent.agent.RcaAgent;
import com.sreagent.agent.RcaReport;
import com.sreagent.config.AgentProperties;
import com.sreagent.llm.LlmClient;
import com.sreagent.llm.LlmException;
import com.sreagent.remediation.AuditLog;

/** HTTP surface: run an RCA on demand. */
@RestController
@RequestMapping("/api")
public class AnalyzeController {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeController.class);

    private final RcaAgent agent;
    private final LlmClient llm;
    private final AgentProperties props;
    private final AuditLog audit;

    public AnalyzeController(RcaAgent agent, LlmClient llm, AgentProperties props, AuditLog audit) {
        this.agent = agent;
        this.llm = llm;
        this.props = props;
        this.audit = audit;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "provider", llm.provider(),
                "model", props.getLlm().getModel(),
                "remediationMode", props.getRemediation().getMode());
    }

    @PostMapping("/analyze")
    public RcaReport analyze(@RequestParam(defaultValue = "10") int windowMinutes,
                             @RequestParam(required = false) String target) {
        return agent.analyze(windowMinutes, target);
    }

    @GetMapping("/audit")
    public List<AuditLog.Entry> audit() {
        return audit.all();
    }

    /** LLM transport/parse failures — surface the (secret-free) message, not a blank 500. */
    @ExceptionHandler(LlmException.class)
    public ResponseEntity<Map<String, Object>> handleLlm(LlmException e) {
        log.error("LLM error during analyze", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "llm_error", "message", String.valueOf(e.getMessage())));
    }

    /** Any other failure — return the message so the caller can see what went wrong. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
        log.error("Analyze failed", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getClass().getSimpleName(),
                        "message", String.valueOf(e.getMessage())));
    }
}
