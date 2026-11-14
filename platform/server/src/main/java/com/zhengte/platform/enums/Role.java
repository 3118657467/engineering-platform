package com.zhengte.platform.enums;

import java.util.Optional;

public enum Role implements CodedEnum {

    DEV("DEV"),
    CR_ADMIN("CR_ADMIN");

    private final String code;

    Role(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public boolean isAdmin() {
        return this == CR_ADMIN;
    }

    public static Optional<Role> find(String code) {
        return Enums.find(Role.class, code);
    }
}
