package com.jasmine.studioai.streaming;

import java.time.Instant;

public record StepEvent(String step, String status, String detail, long timestampMs) {

    public static StepEvent start(String step) {
        return new StepEvent(step, "running", null, System.currentTimeMillis());
    }

    public static StepEvent start(String step, String detail) {
        return new StepEvent(step, "running", detail, System.currentTimeMillis());
    }

    public static StepEvent done(String step) {
        return new StepEvent(step, "done", null, System.currentTimeMillis());
    }

    public static StepEvent done(String step, String detail) {
        return new StepEvent(step, "done", detail, System.currentTimeMillis());
    }
}
