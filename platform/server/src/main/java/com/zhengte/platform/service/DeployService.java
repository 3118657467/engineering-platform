package com.zhengte.platform.service;

import com.zhengte.platform.dto.Views;
import java.util.List;

public interface DeployService {

    Views.Board board(String projectId, String service, String env);

    Views.StartResult start(String projectId, String service, String env, List<String> ids,
                            String operator);

    Views.StartResult rollback(String projectId, String service, String env, String reqNo,
                               String operator);

    Views.Result resolveConflict(String projectId, String service, String env, String reqNo);

    void abort(long batchId);

    Views.Content logs(String podName, String projectId, String service, String env);

    Views.Content yaml(String podName, String projectId, String service, String env);

    List<Views.PodEvent> events(String podName, String projectId, String service, String env);

    Views.Scale scaleInfo(String projectId, String service, String env);

    Views.Scale scale(String projectId, String service, String env, Integer replicas, String operator);

    Views.Result restartPod(String projectId, String service, String env, String podName,
                            String operator);
}
