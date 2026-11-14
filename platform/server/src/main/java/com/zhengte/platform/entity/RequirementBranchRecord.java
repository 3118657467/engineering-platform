package com.zhengte.platform.entity;

import java.time.LocalDateTime;

public record RequirementBranchRecord(
        long requirementId,
        String service,
        String branchName,
        String tipSha,
        LocalDateTime updatedAt) {
}
