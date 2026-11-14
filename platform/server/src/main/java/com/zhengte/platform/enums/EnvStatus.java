package com.zhengte.platform.enums;

import java.util.Optional;

public enum EnvStatus implements CodedEnum {

    LOCKED("LOCKED"),
    NONE("NONE"),
    READY("READY"),
    WAIT_CR("WAIT_CR"),
    REJECTED("REJECTED"),
    RUNNING("RUNNING"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED"),
    CONFLICT("CONFLICT");

    private final String code;

    EnvStatus(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public boolean isSuccess() {
        return this == SUCCESS;
    }

    public boolean isRunning() {
        return this == RUNNING;
    }

    public boolean isFailed() {
        return this == FAILED;
    }

    public boolean isDeployed() {
        return this == SUCCESS || this == FAILED || this == RUNNING || this == CONFLICT;
    }

    public boolean isPendingDeploy() {
        return this == LOCKED || this == READY || this == WAIT_CR || this == REJECTED;
    }

    public static Optional<EnvStatus> find(String code) {
        return Enums.find(EnvStatus.class, code);
    }

    public static EnvStatus of(String code) {
        return find(code).orElse(NONE);
    }
}
