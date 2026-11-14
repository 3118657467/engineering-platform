package com.zhengte.platform.entity;

import java.time.LocalDateTime;

public record RequirementRecord(
        long id,
        String projectId,
        String reqNo,
        String title,
        String description,
        String owner,
        String branchName,
        String branchStatus,
        String tipSha,
        LocalDateTime branchCreatedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
