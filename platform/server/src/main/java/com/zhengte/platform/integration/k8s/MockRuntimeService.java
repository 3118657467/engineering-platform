package com.zhengte.platform.integration.k8s;

import com.zhengte.platform.dto.Views;
import com.zhengte.platform.entity.EnvStateRecord;
import com.zhengte.platform.entity.RequirementRecord;
import com.zhengte.platform.enums.EnvStatus;
import com.zhengte.platform.mapper.EnvStateMapper;
import com.zhengte.platform.mapper.RequirementMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "platform.runtime.mode", havingValue = "mock", matchIfMissing = true)
public class MockRuntimeService implements RuntimeService {

    private final RequirementMapper requirementMapper;
    private final EnvStateMapper envStateMapper;
    private final Map<String, Integer> replicas = new ConcurrentHashMap<>();

    public MockRuntimeService(RequirementMapper requirementMapper,
                              EnvStateMapper envStateMapper) {
        this.requirementMapper = requirementMapper;
        this.envStateMapper = envStateMapper;
    }

    @Override
    public List<Views.Pod> pods(RuntimeTarget target) {
        List<RequirementRecord> requirements = requirementMapper.findByProject(target.projectId());
        Map<String, EnvStateRecord> states = envStateMapper.findAllByProject(target.projectId()).stream()
                .filter(state -> target.service().equals(state.service()) && target.env().equals(state.env()))
                .collect(Collectors.toMap(state -> String.valueOf(state.requirementId()),
                        Function.identity(), (left, right) -> left));

        List<Views.Pod> pods = new ArrayList<>();
        for (int i = 0; i < requirements.size(); i++) {
            RequirementRecord requirement = requirements.get(i);
            EnvStateRecord state = states.get(String.valueOf(requirement.id()));
            if (state == null) {
                continue;
            }
            EnvStatus status = EnvStatus.of(state.status());
            if (!(status.isRunning() || status.isSuccess() || status.isFailed())) {
                continue;
            }
            boolean failed = status.isFailed();
            String podName = target.service() + "-" + target.env() + "-"
                    + requirement.reqNo().toLowerCase().replace("-", "") + "-"
                    + String.format("%02d", i + 1);
            pods.add(new Views.Pod(
                    podName,
                    failed ? "CrashLoopBackOff" : "Running",
                    failed ? "0/1" : "1/1",
                    failed ? "14m" : "2d 4h",
                    "k3s-node-1",
                    failed ? 5 : 0,
                    "app/" + target.service() + ":" + target.env() + "-"
                            + (state.sha() == null ? "unknown" : state.sha())));
        }
        return applyReplicas(target, pods);
    }

    private List<Views.Pod> applyReplicas(RuntimeTarget target, List<Views.Pod> pods) {
        Integer desired = replicas.get(key(target));
        if (desired == null || pods.isEmpty()) {
            return pods;
        }
        List<Views.Pod> adjusted = new ArrayList<>();
        for (int i = 0; i < desired; i++) {
            Views.Pod base = pods.get(Math.min(i, pods.size() - 1));
            String name = i < pods.size() ? base.name() : base.name() + "-r" + (i + 1);
            adjusted.add(new Views.Pod(name, base.status(), base.ready(), base.age(),
                    base.node(), base.restarts(), base.image()));
        }
        return adjusted;
    }

    @Override
    public String logs(RuntimeTarget target, String podName) {
        return String.join("\n",
                "2026-11-13T23:30:12Z INFO  app started, pod=" + podName,
                "2026-11-13T23:30:13Z INFO  HikariPool-1 - Starting...",
                "2026-11-13T23:30:14Z INFO  HikariPool-1 - Start completed.",
                "2026-11-13T23:30:15Z INFO  Tomcat started on port 8080",
                "2026-11-13T23:30:15Z INFO  Started Application in 4.218 seconds",
                "2026-11-13T23:31:02Z INFO  GET /api/info 200 12ms",
                "2026-11-13T23:31:10Z INFO  GET /api/notes 200 8ms");
    }

    @Override
    public String yaml(RuntimeTarget target, String podName) {
        return String.join("\n",
                "apiVersion: v1",
                "kind: Pod",
                "metadata:",
                "  name: " + podName,
                "  namespace: " + target.namespace(),
                "  labels:",
                "    app: " + target.service(),
                "spec:",
                "  nodeName: k3s-node-1",
                "  containers:",
                "    - name: " + target.service(),
                "      image: app/" + target.service() + ":" + target.env() + "-latest",
                "      ports:",
                "        - containerPort: 8080",
                "      readinessProbe:",
                "        httpGet:",
                "          path: /actuator/health/readiness",
                "          port: 8080",
                "status:",
                "  phase: Running");
    }

    @Override
    public List<Views.PodEvent> events(RuntimeTarget target, String podName) {
        return List.of(
                new Views.PodEvent("Normal", "Scheduled", "Successfully assigned " + podName, 1, "11-13 23:30"),
                new Views.PodEvent("Normal", "Pulled", "Container image already present on machine", 1, "11-13 23:30"),
                new Views.PodEvent("Normal", "Started", "Started container " + target.service(), 1, "11-13 23:30"));
    }

    @Override
    public int replicas(RuntimeTarget target) {
        return replicas.getOrDefault(key(target), 1);
    }

    @Override
    public void scale(RuntimeTarget target, int count) {
        replicas.put(key(target), count);
    }

    @Override
    public void restartPod(RuntimeTarget target, String podName) {
        // mock 模式无需真正重启
    }

    private String key(RuntimeTarget target) {
        return target.namespace() + "/" + target.deployment();
    }
}
