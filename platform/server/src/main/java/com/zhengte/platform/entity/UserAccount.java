package com.zhengte.platform.entity;

import com.zhengte.platform.enums.Role;

public record UserAccount(String username, String displayName, String role, String passwordHash) {

    public boolean isCrAdmin() {
        return Role.CR_ADMIN.code().equals(role);
    }
}
