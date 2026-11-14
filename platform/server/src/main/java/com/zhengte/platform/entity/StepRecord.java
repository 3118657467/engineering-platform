package com.zhengte.platform.entity;

public record StepRecord(long id, long deploymentId, int seq, String name, String status,
                         Integer durationMs) {
}
