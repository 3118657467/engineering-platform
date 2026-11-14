package com.zhengte.platform.entity;

import java.time.LocalDateTime;

public record ProjectRecord(String id, String name, String service, String runtimeApp,
                            String repoUrl, LocalDateTime createdAt) {
}
