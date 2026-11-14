package com.zhengte.platform.entity;

import java.time.LocalDateTime;

public record BatchRecord(
        long id,
        String projectId,
        String service,
        String env,
        String status,
        String operator,
        LocalDateTime startedAt,
        LocalDateTime finishedAt) {
}
