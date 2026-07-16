package com.chaosinjector.target.model;

/** Result of a synchronous exec inside a workload. */
public record ExecResult(long exitCode, String stdout, String stderr) {

    public boolean succeeded() {
        return exitCode == 0;
    }

    public String combinedOutput() {
        return (stdout == null ? "" : stdout) + (stderr == null ? "" : stderr);
    }
}
