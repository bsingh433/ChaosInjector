package com.chaosinjector.engine;

/** Shared label keys applied to helper containers so they can be swept. */
public final class Labels {

    /** Value is the experiment id. */
    public static final String EXPERIMENT = "com.chaosinjector.experiment";

    /** Present on every helper (value "true") so orphans can be swept on connect. */
    public static final String MARKER = "com.chaosinjector.helper";
    public static final String MARKER_VALUE = "true";

    private Labels() {
    }
}
