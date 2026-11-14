package com.zhengte.platform.service.impl;

import com.zhengte.platform.entity.ReleaseHistoryRecord;
import com.zhengte.platform.entity.RequirementRecord;
import com.zhengte.platform.entity.Tables;
import com.zhengte.platform.mapper.ReleaseHistoryMapper;
import com.zhengte.platform.mapper.RequirementArchiveMapper;
import com.zhengte.platform.service.ReleaseHistoryService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ReleaseHistoryServiceImpl implements ReleaseHistoryService {

    private final ReleaseHistoryMapper releaseHistoryMapper;
    private final RequirementArchiveMapper archiveMapper;

    public ReleaseHistoryServiceImpl(ReleaseHistoryMapper releaseHistoryMapper,
                                     RequirementArchiveMapper archiveMapper) {
        this.releaseHistoryMapper = releaseHistoryMapper;
        this.archiveMapper = archiveMapper;
    }

    @Override
    public void publish(String projectId, String service, RequirementRecord requirement, String sha,
                        String operator, Long batchId, Long deploymentId, LocalDateTime now) {
        Optional<ReleaseHistoryRecord> current = releaseHistoryMapper.findCurrent(projectId, service);
        long releaseId;
        if (current.isPresent() && sha.equals(current.get().sha())) {
            releaseId = current.get().id();
            releaseHistoryMapper.updateCurrentRelease(releaseId, now, operator, batchId, deploymentId);
        } else {
            releaseHistoryMapper.clearCurrent(projectId, service);
            Tables.ReleaseHistory row = new Tables.ReleaseHistory();
            row.setProjectId(projectId);
            row.setService(service);
            row.setRequirementId(requirement == null ? 0 : requirement.id());
            row.setReqNo(requirement == null ? "" : requirement.reqNo());
            row.setTitle(requirement == null ? "" : requirement.title());
            row.setBranchName(requirement == null ? "" : requirement.branchName());
            row.setSha(sha);
            row.setReleasedAt(now);
            row.setOperator(operator);
            row.setBatchId(batchId);
            row.setDeploymentId(deploymentId);
            releaseId = releaseHistoryMapper.insertCurrent(row);
        }
        if (requirement != null) {
            archiveMapper.archive(requirement.id(), projectId, service, releaseId, now);
        }
    }

    @Override
    public void markCurrent(long releaseId, String projectId, String service) {
        releaseHistoryMapper.clearCurrent(projectId, service);
        releaseHistoryMapper.markCurrentRow(releaseId);
    }

    @Override
    public List<ReleaseHistoryRecord> history(String projectId, String service) {
        return releaseHistoryMapper.findHistory(projectId, service);
    }

    @Override
    public Set<Long> archivedIds(String projectId) {
        return archiveMapper.findArchivedIds(projectId);
    }
}
