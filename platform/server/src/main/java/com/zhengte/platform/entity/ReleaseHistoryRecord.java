package com.zhengte.platform.entity;

import java.time.LocalDateTime;

public record ReleaseHistoryRecord(
        long id,
        String projectId,
        String service,
        long requirementId,
        String reqNo,
        String title,
        String branchName,
        String sha,
        LocalDateTime releasedAt,
        String operator,
        Long batchId,
        Long deploymentId,
        Boolean current) {
}
