package com.zhengte.platform.entity;

import java.time.LocalDateTime;

public record DeploymentRecord(
        long id,
        long batchId,
        long requirementId,
        String service,
        String env,
        String branchName,
        String sha,
        String status,
        String operator,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String errorMessage) {
}
