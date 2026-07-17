package com.sreagent.remediation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Records every proposed and executed remediation action (spec §12 audit). */
@Component
public class AuditLog {

    private static final Logger log = LoggerFactory.getLogger(AuditLog.class);

    public record Entry(String timestamp, String tool, String args, String decision, String result) {
    }

    private final List<Entry> entries = Collections.synchronizedList(new ArrayList<>());

    public void add(String tool, String args, String decision, String result) {
        Entry e = new Entry(Instant.now().toString(), tool, args, decision, result);
        entries.add(e);
        log.info("AUDIT {} {} decision={} result={}", tool, args, decision, result);
    }

    public List<Entry> all() {
        synchronized (entries) {
            return new ArrayList<>(entries);
        }
    }
}
