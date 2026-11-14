package com.zhengte.platform.integration.k8s;

import com.zhengte.platform.common.ApiException;
import com.zhengte.platform.utils.Fmt;
import com.zhengte.platform.dto.Views;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

@Service
@ConditionalOnProperty(name = "platform.runtime.mode", havingValue = "k3s")
public class K3sRuntimeService implements RuntimeService {

    private final KubernetesClient client;
    private final int logTailLines;

    public K3sRuntimeService(KubernetesClient client,
                             @Value("${platform.runtime.log-tail-lines:200}") int logTailLines) {
        this.client = client;
        this.logTailLines = logTailLines;
    }

    @Override
    public List<Views.Pod> pods(RuntimeTarget target) {
        List<Pod> items = query("查询 Pod", () -> client.pods()
                .inNamespace(target.namespace())
                .withLabel("app=" + target.appLabel())
                .list()
                .getItems());
        return items.stream().map(this::toView).toList();
    }

    @Override
    public String logs(RuntimeTarget target, String podName) {
        String content = query("读取日志", () -> client.pods()
                .inNamespace(target.namespace())
                .withName(podName)
                .getLog());
        return tail(content, logTailLines);
    }

    @Override
    public String yaml(RuntimeTarget target, String podName) {
        Pod pod = query("读取 Pod", () -> client.pods()
                .inNamespace(target.namespace())
                .withName(podName)
                .get());
        if (pod == null) {
            throw ApiException.notFound("Pod 不存在：" + podName);
        }
        return Serialization.asYaml(pod);
    }

    @Override
    public List<Views.PodEvent> events(RuntimeTarget target, String podName) {
        List<Event> items = query("查询事件", () -> client.v1()
                .events()
                .inNamespace(target.namespace())
                .list()
                .getItems());
        return items.stream()
                .filter(event -> event.getInvolvedObject() != null
                        && podName.equals(event.getInvolvedObject().getName()))
                .sorted(Comparator.comparing(
                        (Event event) -> event.getLastTimestamp() == null ? "" : event.getLastTimestamp())
                        .reversed())
                .limit(20)
                .map(this::toView)
                .toList();
    }

    @Override
    public int replicas(RuntimeTarget target) {
        Deployment deployment = query("查询 Deployment", () -> client.apps()
                .deployments()
                .inNamespace(target.namespace())
                .withName(target.deployment())
                .get());
        if (deployment == null) {
            throw ApiException.notFound("Deployment 不存在：" + target.deployment());
        }
        Integer replicas = deployment.getSpec().getReplicas();
        return replicas == null ? 0 : replicas;
    }

    @Override
    public void scale(RuntimeTarget target, int replicas) {
        execute("调整副本数", () -> {
            client.apps()
                    .deployments()
                    .inNamespace(target.namespace())
                    .withName(target.deployment())
                    .scale(replicas);
        });
    }

    @Override
    public void restartPod(RuntimeTarget target, String podName) {
        List<?> deleted = query("重启 Pod", () -> client.pods()
                .inNamespace(target.namespace())
                .withName(podName)
                .delete());
        if (deleted == null || deleted.isEmpty()) {
            throw ApiException.notFound("Pod 不存在：" + podName);
        }
    }

    private Views.Pod toView(Pod pod) {
        String phase = pod.getStatus() == null || pod.getStatus().getPhase() == null
                ? "Unknown"
                : pod.getStatus().getPhase();
        List<ContainerStatus> statuses = pod.getStatus() == null
                || pod.getStatus().getContainerStatuses() == null
                ? List.of()
                : pod.getStatus().getContainerStatuses();
        int total = statuses.size();
        long ready = statuses.stream().filter(status -> Boolean.TRUE.equals(status.getReady())).count();
        int restarts = statuses.stream()
                .mapToInt(status -> status.getRestartCount() == null ? 0 : status.getRestartCount())
                .sum();
        String image = pod.getSpec() == null || pod.getSpec().getContainers() == null
                || pod.getSpec().getContainers().isEmpty()
                ? ""
                : pod.getSpec().getContainers().get(0).getImage();
        return new Views.Pod(
                pod.getMetadata().getName(),
                phase,
                ready + "/" + total,
                age(pod.getMetadata().getCreationTimestamp()),
                pod.getSpec() == null ? null : pod.getSpec().getNodeName(),
                restarts,
                image);
    }

    private Views.PodEvent toView(Event event) {
        return new Views.PodEvent(
                event.getType(),
                event.getReason(),
                event.getMessage(),
                event.getCount() == null ? 1 : event.getCount(),
                shortTime(event.getLastTimestamp()));
    }

    private String age(String createdAt) {
        if (createdAt == null) {
            return "-";
        }
        try {
            Duration elapsed = Duration.between(Instant.parse(createdAt), Instant.now());
            long days = elapsed.toDays();
            if (days > 0) {
                return days + "d " + elapsed.toHoursPart() + "h";
            }
            if (elapsed.toHours() > 0) {
                return elapsed.toHours() + "h " + elapsed.toMinutesPart() + "m";
            }
            return Math.max(elapsed.toMinutes(), 0) + "m";
        } catch (DateTimeParseException e) {
            return "-";
        }
    }

    private String shortTime(String timestamp) {
        if (timestamp == null) {
            return "";
        }
        try {
            return Fmt.time(LocalDateTime.ofInstant(Instant.parse(timestamp), ZoneId.systemDefault()));
        } catch (DateTimeParseException e) {
            return timestamp;
        }
    }

    private String tail(String content, int lines) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String[] all = content.split("\\R");
        return String.join("\n", Arrays.copyOfRange(all, Math.max(0, all.length - lines), all.length));
    }

    private <T> T query(String action, Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (KubernetesClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "k3s " + action + "失败：" + e.getMessage());
        }
    }

    private void execute(String action, Runnable runnable) {
        try {
            runnable.run();
        } catch (KubernetesClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "k3s " + action + "失败：" + e.getMessage());
        }
    }
}
