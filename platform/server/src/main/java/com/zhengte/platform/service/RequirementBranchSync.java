package com.zhengte.platform.service;

import com.zhengte.platform.constants.GitModes;
import com.zhengte.platform.entity.RequirementBranchRecord;
import com.zhengte.platform.entity.RequirementRecord;
import com.zhengte.platform.integration.git.GitService;
import com.zhengte.platform.mapper.RequirementBranchMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RequirementBranchSync {

    private final RequirementBranchMapper branchMapper;
    private final GitService gitService;

    public RequirementBranchSync(RequirementBranchMapper branchMapper, GitService gitService) {
        this.branchMapper = branchMapper;
        this.gitService = gitService;
    }

    public Map<String, RequirementBranchRecord> branches(long requirementId) {
        return branchMapper.findByRequirement(requirementId).stream()
                .collect(Collectors.toMap(RequirementBranchRecord::service, Function.identity()));
    }

    public String syncTip(String projectId, RequirementRecord requirement, String service) {
        RequirementBranchRecord row = branches(requirement.id()).get(service);
        if (row == null || row.branchName() == null || row.branchName().isBlank()) {
            return null;
        }
        if (!GitModes.GITLAB.equals(gitService.mode())) {
            return row.tipSha();
        }
        String tip = gitService.branchTip(projectId, service, row.branchName());
        if (tip == null) {
            return row.tipSha();
        }
        if (!tip.equals(row.tipSha())) {
            branchMapper.updateTip(requirement.id(), service, tip, LocalDateTime.now());
        }
        return tip;
    }

    public List<RequirementBranchRecord> rows(long requirementId) {
        return branchMapper.findByRequirement(requirementId);
    }
}
