package com.zhengte.platform.enums;

import java.util.Optional;

public enum StepStatus implements CodedEnum {

    PENDING("PENDING"),
    RUNNING("RUNNING"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED");

    private final String code;

    StepStatus(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public boolean isRunning() {
        return this == RUNNING;
    }

    public boolean isFinished() {
        return this == SUCCESS || this == FAILED;
    }

    public static Optional<StepStatus> find(String code) {
        return Enums.find(StepStatus.class, code);
    }
}
