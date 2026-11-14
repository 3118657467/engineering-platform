package com.zhengte.platform.entity;

import java.time.LocalDateTime;

public record CrRecord(
        long id,
        long requirementId,
        String service,
        String approver,
        String status,
        String requestedBy,
        LocalDateTime requestedAt,
        LocalDateTime decidedAt,
        String comment) {
}
