package com.zhengte.platform.entity;

public record CrPendingRow(CrRecord cr, String projectId, String reqNo, String title, String branch,
                           String tipSha) {
}
