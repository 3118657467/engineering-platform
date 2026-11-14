package com.zhengte.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表实体集中定义，字段名依赖 map-underscore-to-camel-case 默认映射。
 */
public final class Tables {

    private Tables() {
    }

    @Data
    @TableName("users")
    public static class User {
        @TableId(type = IdType.INPUT)
        private String username;
        private String displayName;
        private String role;
        private String passwordHash;
    }

    @Data
    @TableName("sessions")
    public static class Session {
        @TableId(type = IdType.INPUT)
        private String token;
        private String username;
        private LocalDateTime expiresAt;
        private LocalDateTime createdAt;
    }

    @Data
    @TableName("projects")
    public static class Project {
        @TableId(type = IdType.INPUT)
        private String id;
        private String name;
        private String service;
        private String runtimeApp;
        private String repoUrl;
        private LocalDateTime createdAt;
    }

    @Data
    @TableName("requirements")
    public static class Requirement {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String projectId;
        private String reqNo;
        private String title;
        private String description;
        private String owner;
        private String branchName;
        private String branchStatus;
        private String tipSha;
        private LocalDateTime branchCreatedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @TableName("requirement_related")
    public static class RequirementRelated {
        @TableId(value = "requirement_id", type = IdType.INPUT)
        private Long requirementId;
        private String username;
    }

    @Data
    @TableName("requirement_branches")
    public static class RequirementBranch {
        @TableId(value = "requirement_id", type = IdType.INPUT)
        private Long requirementId;
        private String service;
        private String branchName;
        private String tipSha;
        private LocalDateTime updatedAt;
    }

    @Data
    @TableName("cr_requests")
    public static class CrRequest {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long requirementId;
        private String service;
        private String approver;
        private String status;
        private String requestedBy;
        private LocalDateTime requestedAt;
        private LocalDateTime decidedAt;
        private String comment;
    }

    @Data
    @TableName("env_states")
    public static class EnvState {
        @TableId(value = "requirement_id", type = IdType.INPUT)
        private Long requirementId;
        private String service;
        private String env;
        private String status;
        private String sha;
        private LocalDateTime deployedAt;
        private String operator;
        private LocalDateTime updatedAt;
        private String errorMessage;
    }

    @Data
    @TableName("deploy_batches")
    public static class DeployBatch {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String projectId;
        private String service;
        private String env;
        private String status;
        private String operator;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
    }

    @Data
    @TableName("deployments")
    public static class Deployment {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long batchId;
        private Long requirementId;
        private String service;
        private String env;
        private String branchName;
        private String sha;
        private String status;
        private String operator;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private String errorMessage;
    }

    @Data
    @TableName("deployment_steps")
    public static class DeploymentStep {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long deploymentId;
        private Integer seq;
        private String name;
        private String status;
        private Integer durationMs;
    }

    @Data
    @TableName("prod_releases")
    public static class ProdRelease {
        @TableId(value = "requirement_id", type = IdType.INPUT)
        private Long requirementId;
        private String service;
        private String currentSha;
        private LocalDateTime releasedAt;
        private String previousSha;
        private LocalDateTime prevReleasedAt;
    }

    @Data
    @TableName("deploy_locks")
    public static class DeployLock {
        @TableId(value = "project_id", type = IdType.INPUT)
        private String projectId;
        private String service;
        private String env;
        private Long batchId;
        private LocalDateTime acquiredAt;
    }

    @Data
    @TableName("deployment_pipelines")
    public static class DeploymentPipeline {
        @TableId(value = "deployment_id", type = IdType.INPUT)
        private Long deploymentId;
        private Long pipelineId;
        private String status;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class CrPending {
        private Long id;
        private Long requirementId;
        private String service;
        private String approver;
        private String status;
        private String requestedBy;
        private LocalDateTime requestedAt;
        private LocalDateTime decidedAt;
        private String comment;
        private String projectId;
        private String reqNo;
        private String title;
        private String branchName;
        private String tipSha;
    }

    @Data
    public static class RelatedName {
        private Long requirementId;
        private String displayName;
    }
}
