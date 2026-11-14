package com.zhengte.platform.integration.git;

import java.util.List;
import java.util.Map;

public interface GitService {

    String mode();

    String createBranch(String projectId, String service, String branch);

    String branchTip(String projectId, String service, String branch);

    void deleteBranch(String projectId, String service, String branch);

    MergeResult merge(String projectId, String service, String sourceBranch, String targetBranch);

    long triggerPipeline(String projectId, String service, String branch, Map<String, String> variables);

    PipelineState pipeline(String projectId, String service, long pipelineId);

    void cancelPipeline(String projectId, String service, long pipelineId);

    record MergeResult(boolean conflict, String message) {
    }

    record JobState(String name, String stage, String status, Integer durationSeconds) {
    }

    record PipelineState(String status, List<JobState> jobs) {

        public boolean terminal() {
            return List.of("success", "failed", "canceled", "skipped").contains(status);
        }

        public boolean success() {
            return "success".equals(status);
        }
    }
}
