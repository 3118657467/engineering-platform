package com.zhengte.platform.service;

import com.zhengte.platform.entity.ReleaseHistoryRecord;
import com.zhengte.platform.entity.RequirementRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface ReleaseHistoryService {

    void publish(String projectId, String service, RequirementRecord requirement, String sha,
                 String operator, Long batchId, Long deploymentId, LocalDateTime now);

    void markCurrent(long releaseId, String projectId, String service);

    List<ReleaseHistoryRecord> history(String projectId, String service);

    Set<Long> archivedIds(String projectId);
}
