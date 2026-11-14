package com.zhengte.platform.integration.git;

import com.zhengte.platform.constants.GitModes;
import com.zhengte.platform.enums.ServiceType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
@ConditionalOnProperty(name = "platform.git.mode", havingValue = "mock", matchIfMissing = true)
public class MockGitService implements GitService {

    private final AtomicLong pipelineIds = new AtomicLong(1000);

    @Override
    public String mode() {
        return GitModes.MOCK;
    }

    @Override
    public String createBranch(String projectId, String service, String branch) {
        return randomSha(service);
    }

    @Override
    public String branchTip(String projectId, String service, String branch) {
        return randomSha(service);
    }

    @Override
    public void deleteBranch(String projectId, String service, String branch) {
    }

    @Override
    public MergeResult merge(String projectId, String service, String sourceBranch, String targetBranch) {
        return new MergeResult(false, "mock merged");
    }

    @Override
    public long triggerPipeline(String projectId, String service, String branch, Map<String, String> variables) {
        return pipelineIds.incrementAndGet();
    }

    @Override
    public PipelineState pipeline(String projectId, String service, long pipelineId) {
        return new PipelineState("success", List.of());
    }

    @Override
    public void cancelPipeline(String projectId, String service, long pipelineId) {
    }

    private String randomSha(String service) {
        String prefix = ServiceType.WEB.code().equals(service) ? "b" : "a";
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }
}
