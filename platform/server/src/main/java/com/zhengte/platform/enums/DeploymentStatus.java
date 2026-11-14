package com.zhengte.platform.enums;

import java.util.Optional;

public enum DeploymentStatus implements CodedEnum {

    PENDING("PENDING"),
    RUNNING("RUNNING"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED"),
    CANCELLED("CANCELLED");

    private final String code;

    DeploymentStatus(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static Optional<DeploymentStatus> find(String code) {
        return Enums.find(DeploymentStatus.class, code);
    }
}
