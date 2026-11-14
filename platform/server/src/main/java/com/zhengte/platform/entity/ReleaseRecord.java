package com.zhengte.platform.entity;

import java.time.LocalDateTime;

public record ReleaseRecord(
        long requirementId,
        String service,
        String currentSha,
        LocalDateTime currentTime,
        String previousSha,
        LocalDateTime previousTime) {
}
