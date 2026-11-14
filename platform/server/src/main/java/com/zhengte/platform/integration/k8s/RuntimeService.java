package com.zhengte.platform.integration.k8s;

import com.zhengte.platform.dto.Views;

import java.util.List;

public interface RuntimeService {

    List<Views.Pod> pods(RuntimeTarget target);

    String logs(RuntimeTarget target, String podName);

    String yaml(RuntimeTarget target, String podName);

    List<Views.PodEvent> events(RuntimeTarget target, String podName);

    int replicas(RuntimeTarget target);

    void scale(RuntimeTarget target, int replicas);

    void restartPod(RuntimeTarget target, String podName);
}
