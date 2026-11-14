package com.zhengte.platform.enums;

import java.util.Optional;

public enum BranchStatus implements CodedEnum {

    NONE("NONE"),
    CREATED("CREATED");

    private final String code;

    BranchStatus(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public boolean isCreated() {
        return this == CREATED;
    }

    public static Optional<BranchStatus> find(String code) {
        return Enums.find(BranchStatus.class, code);
    }

    public static BranchStatus of(String code) {
        return find(code).orElse(NONE);
    }
}
