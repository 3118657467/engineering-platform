package com.zhengte.platform.enums;

import java.util.Optional;

public enum BatchStatus implements CodedEnum {

    RUNNING("RUNNING"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED"),
    TERMINATED("TERMINATED");

    private final String code;

    BatchStatus(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public boolean isRunning() {
        return this == RUNNING;
    }

    public static Optional<BatchStatus> find(String code) {
        return Enums.find(BatchStatus.class, code);
    }
}
