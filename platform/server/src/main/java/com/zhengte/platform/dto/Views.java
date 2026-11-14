/*
 * Decompiled with CFR 0.152.
 */
package com.zhengte.platform.dto;

import java.util.List;
import java.util.Map;

public final class Views {
    private Views() {
    }

    public record Content(String content) {
    }

    public record StartResult(boolean ok, String message, Long batchId) {
    }

    public record Result(boolean ok, String message) {
    }

    public record Option(String username, String displayName) {
    }

    public record Approval(long crId, String projectId, String id, String title, String service, String branch, String tipSha, String requester, String requesterName, String time, String status, boolean canAct) {
    }

    public record Board(String projectId, String service, String env, List<DeployRow> deployed, List<PendingRow> pending, int failedCount, Pipeline pipeline, List<ReleaseVersion> releases, List<Pod> pods, Deploying running) {
    }

    public record Deploying(String env, String operator, String operatorName) {
    }

    public record Scale(int replicas, int min, int max, String mode) {
    }

    public record PodEvent(String type, String reason, String message, int count, String time) {
    }

    public record Pod(String name, String status, String ready, String age, String node, int restarts, String image) {
    }

    public record ReleaseVersion(long id, String reqNo, String title, String branch, String sha, String time, String operator, String operatorName, boolean current) {
    }

    public record Pipeline(long batchId, String status, String startedAt, String operator, List<PipelineReq> requirements) {
    }

    public record PipelineReq(String id, String title, String branch, String sha, String status, List<Step> steps) {
    }

    public record Step(int seq, String name, String status, String duration) {
    }

    public record PendingRow(String id, String title, String branch, String tipSha, String status, List<Tag> tags, Cr cr, Gate gate) {
    }

    public record DeployRow(String id, String title, String branch, String sha, String time, List<Tag> tags, String status, String error, Cr cr, Gate gate, boolean hasNew) {
    }

    public record Requirement(String id, String projectId, String title, String description, String owner, String ownerName, List<String> related, String branch, String branchStatus, Map<String, String> tips, String createdAt, Label status, Map<String, Map<String, EnvState>> services, Cr cr, boolean archived) {
    }

    public record Label(String label, String cls) {
    }

    public record Gate(boolean ok, String reason, boolean needCr, boolean pendingCr) {
    }

    public record Cr(String status, String service, String approver, String approverName, String requester, String requesterName, String time, String comment) {
    }

    public record EnvState(String status, String sha, String operator, String operatorName, String time, String error) {
    }

    public record Tag(String label, String tone, String action) {
        public static Tag of(String label, String tone) {
            return new Tag(label, tone, null);
        }

        public static Tag action(String label, String tone, String action) {
            return new Tag(label, tone, action);
        }
    }

    public record Project(String id, String name, String service, String repoUrl) {
    }

    public record Login(String token, User user) {
    }

    public record User(String username, String displayName, String role) {
    }
}
