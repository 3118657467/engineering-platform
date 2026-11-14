package com.zhengte.platform.entity;

import java.time.LocalDateTime;

public record EnvStateRecord(
        long requirementId,
        String service,
        String env,
        String status,
        String sha,
        LocalDateTime deployedAt,
        String operator,
        LocalDateTime updatedAt,
        String errorMessage) {
}
