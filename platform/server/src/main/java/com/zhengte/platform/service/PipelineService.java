package com.zhengte.platform.service;

import com.zhengte.platform.dto.Views;
import com.zhengte.platform.entity.RequirementRecord;
import java.util.List;

public interface PipelineService {

    long start(String projectId, String service, String env, List<String> reqNos, String operator);

    long rollback(String projectId, String service, RequirementRecord requirement, String operator);

    void abort(long batchId);

    Views.Pipeline latest(String projectId, String service, String env);
}
