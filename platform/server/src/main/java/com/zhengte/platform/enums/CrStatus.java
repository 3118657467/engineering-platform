package com.zhengte.platform.enums;

import java.util.Optional;

public enum CrStatus implements CodedEnum {

    NONE("NONE"),
    PENDING("PENDING"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED");

    private final String code;

    CrStatus(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public boolean isPending() {
        return this == PENDING;
    }

    public boolean isApproved() {
        return this == APPROVED;
    }

    public boolean isRejected() {
        return this == REJECTED;
    }

    public static Optional<CrStatus> find(String code) {
        return Enums.find(CrStatus.class, code);
    }

    public static CrStatus of(String code) {
        return find(code).orElse(NONE);
    }
}
