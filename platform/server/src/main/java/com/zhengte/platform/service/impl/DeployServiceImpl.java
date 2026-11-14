package com.zhengte.platform.service.impl;

import com.zhengte.platform.service.DeployGate;
import com.zhengte.platform.service.PipelineService;
import com.zhengte.platform.service.RequirementBranchSync;
import com.zhengte.platform.service.DeployService;
import com.zhengte.platform.common.ApiException;
import com.zhengte.platform.constants.PlatformDefaults;
import com.zhengte.platform.converter.ViewConverter;
import com.zhengte.platform.dto.Views;
import com.zhengte.platform.entity.CrRecord;
import com.zhengte.platform.entity.EnvStateRecord;
import com.zhengte.platform.entity.RequirementRecord;
import com.zhengte.platform.entity.UserAccount;
import com.zhengte.platform.enums.BranchStatus;
import com.zhengte.platform.enums.CrStatus;
import com.zhengte.platform.enums.Env;
import com.zhengte.platform.enums.EnvStatus;
import com.zhengte.platform.enums.ServiceType;
import com.zhengte.platform.integration.k8s.RuntimeService;
import com.zhengte.platform.integration.k8s.RuntimeTarget;
import com.zhengte.platform.mapper.CrRequestMapper;
import com.zhengte.platform.mapper.DeployMapper;
import com.zhengte.platform.mapper.EnvStateMapper;
import com.zhengte.platform.mapper.ProjectMapper;
import com.zhengte.platform.mapper.RequirementMapper;
import com.zhengte.platform.mapper.UserMapper;
import com.zhengte.platform.utils.Fmt;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DeployServiceImpl implements DeployService {

    private static final Logger log = LoggerFactory.getLogger(DeployService.class);

    private final ProjectMapper projectMapper;
    private final RequirementMapper requirementMapper;
    private final CrRequestMapper crMapper;
    private final EnvStateMapper envStateMapper;
    private final DeployMapper deployMapper;
    private final PipelineService pipelineService;
    private final RuntimeService runtimeService;
    private final UserMapper userMapper;
    private final ViewConverter viewConverter;
    private final RequirementBranchSync branchSync;
    private final String runtimeMode;

    public DeployServiceImpl(ProjectMapper projectMapper,
                         RequirementMapper requirementMapper,
                         CrRequestMapper crMapper,
                         EnvStateMapper envStateMapper,
                         DeployMapper deployMapper,
                         PipelineService pipelineService,
                         RuntimeService runtimeService,
                         UserMapper userMapper,
                         ViewConverter viewConverter,
                         RequirementBranchSync branchSync,
                         @Value("${platform.runtime.mode:mock}") String runtimeMode) {
        this.projectMapper = projectMapper;
        this.requirementMapper = requirementMapper;
        this.crMapper = crMapper;
        this.envStateMapper = envStateMapper;
        this.deployMapper = deployMapper;
        this.pipelineService = pipelineService;
        this.runtimeService = runtimeService;
        this.userMapper = userMapper;
        this.viewConverter = viewConverter;
        this.branchSync = branchSync;
        this.runtimeMode = runtimeMode;
    }

    @Override
    public Views.Board board(String projectId, String service, String env) {
        validate(projectId, service, env);
        List<RequirementRecord> requirements = requirementMapper.findByProject(projectId);
        Map<Long, Map<String, EnvStateRecord>> states = new HashMap<>();
        envStateMapper.findAllByProject(projectId).stream()
                .filter(state -> service.equals(state.service()))
                .forEach(state -> states
                        .computeIfAbsent(state.requirementId(), key -> new HashMap<>())
                        .put(state.env(), state));
        Map<Long, List<CrRecord>> crs = crMapper.findByProject(projectId);
        Map<String, String> names = userMapper.displayNames();

        Env envType = Env.find(env).orElseThrow(() -> ApiException.badRequest("未知环境：" + env));
        String prevEnv = envType.previous().map(Env::code).orElse(null);

        List<DeployRowWithTime> deployedTimes = new ArrayList<>();
        List<Views.PendingRow> pending = new ArrayList<>();

        for (RequirementRecord raw : requirements) {
            if (!BranchStatus.CREATED.code().equals(raw.branchStatus())) {
                continue;
            }
            RequirementRecord requirement = raw;
            String tipSha = branchSync.syncTip(projectId, requirement, service);
            Map<String, EnvStateRecord> byEnv = states.getOrDefault(requirement.id(), Map.of());
            EnvStateRecord target = byEnv.get(env);
            EnvStateRecord previous = prevEnv == null ? null : byEnv.get(prevEnv);
            CrRecord cr = crs.getOrDefault(requirement.id(), List.of()).stream()
                    .filter(row -> service.equals(row.service()))
                    .findFirst().orElse(null);
            EnvStatus status = target == null ? EnvStatus.LOCKED : EnvStatus.of(target.status());
            Views.Gate gate = DeployGate.check(requirement, service, env, target, previous, cr, tipSha);
            boolean hasNew = target != null && target.sha() != null
                    && !target.sha().equals(tipSha);

            if (status.isDeployed()) {
                List<Views.Tag> tags = tagsFor(env, status, cr, hasNew);
                Views.DeployRow row = new Views.DeployRow(
                        requirement.reqNo(),
                        requirement.title(),
                        requirement.branchName(),
                        target.sha(),
                        Fmt.time(target.deployedAt()),
                        tags,
                        status.code().toLowerCase(),
                        target.errorMessage(),
                        viewConverter.cr(cr, service, names),
                        gate,
                        hasNew);
                deployedTimes.add(new DeployRowWithTime(row, target.deployedAt()));
            }

            boolean visiblePending = gate.ok() || gate.needCr() || gate.pendingCr()
                    || status == EnvStatus.WAIT_CR || status == EnvStatus.REJECTED
                    || status == EnvStatus.LOCKED;
            if (status.isPendingDeploy() && visiblePending) {
                List<Views.Tag> tags = new ArrayList<>();
                if (envType == Env.TEST) {
                    tags.addAll(crPendingTags(cr, gate));
                }
                if (gate.ok()) {
                    tags.add(Views.Tag.of("可部署", "info"));
                }
                pending.add(new Views.PendingRow(requirement.reqNo(), requirement.title(),
                        requirement.branchName(), tipSha, status.code().toLowerCase(),
                        tags, viewConverter.cr(cr, service, names), gate));
            }
        }

        deployedTimes.sort(Comparator.comparing(DeployRowWithTime::time,
                Comparator.nullsLast(Comparator.reverseOrder())));
        List<Views.DeployRow> sortedDeployed = deployedTimes.stream()
                .map(DeployRowWithTime::row).toList();
        int failedCount = (int) sortedDeployed.stream()
                .filter(row -> EnvStatus.FAILED.code().toLowerCase().equals(row.status())).count();

        List<Views.ProdRelease> releases = envType.isProd()
                ? prodReleases(projectId, service, requirements)
                : List.of();

        Views.Deploying running = deployMapper.findRunningBatch(projectId, service, env)
                .map(batch -> new Views.Deploying(batch.env(), batch.operator(),
                        names.getOrDefault(batch.operator(), batch.operator())))
                .orElse(null);
        return new Views.Board(projectId, service, env, sortedDeployed, pending, failedCount,
                pipelineService.latest(projectId, service, env), releases,
                runtimeService.pods(RuntimeTarget.of(projectId, service, env,
                        projectMapper.findRuntimeApp(projectId))),
                running);
    }

    private record DeployRowWithTime(Views.DeployRow row, LocalDateTime time) {
    }

    private List<Views.Tag> tagsFor(String env, EnvStatus status, CrRecord cr, boolean hasNew) {
        List<Views.Tag> tags = new ArrayList<>();
        if (Env.TEST.code().equals(env)) {
            tags.addAll(crTags(cr));
        }
        if (status.isRunning()) {
            tags.add(Views.Tag.of("部署中", "running"));
        } else if (status.isFailed()) {
            // 重试入口在卡片标题栏
        } else if (hasNew) {
            tags.add(Views.Tag.action("有新提交", "info", "deploy"));
        }
        return tags;
    }

    private List<Views.Tag> crTags(CrRecord cr) {
        return switch (CrStatus.of(cr == null ? null : cr.status())) {
            case APPROVED -> List.of(Views.Tag.of("已CR", "success"));
            case PENDING -> List.of(Views.Tag.action("待CR", "warning", "cr"));
            case REJECTED -> List.of(Views.Tag.action("CR驳回", "danger", "cr"));
            default -> List.of(Views.Tag.action("未CR", "muted", "cr"));
        };
    }

    private List<Views.Tag> crPendingTags(CrRecord cr, Views.Gate gate) {
        return switch (CrStatus.of(cr == null ? null : cr.status())) {
            case APPROVED -> List.of(Views.Tag.of("已CR", "success"));
            case PENDING -> List.of(Views.Tag.action("待CR", "warning", "cr"));
            case REJECTED -> List.of(Views.Tag.action("CR驳回", "danger", "cr"));
            default -> gate.needCr()
                    ? List.of(Views.Tag.action("需CR", "warning", "cr"))
                    : List.of(Views.Tag.action("未CR", "muted", "cr"));
        };
    }

    private List<Views.ProdRelease> prodReleases(String projectId, String service,
                                                 List<RequirementRecord> requirements) {
        Map<Long, RequirementRecord> byId = requirements.stream()
                .collect(Collectors.toMap(RequirementRecord::id, requirement -> requirement));
        Map<Long, EnvStateRecord> prodStates = envStateMapper.findAllByProject(projectId).stream()
                .filter(state -> service.equals(state.service()) && Env.PROD.code().equals(state.env()))
                .collect(Collectors.toMap(EnvStateRecord::requirementId, state -> state));
        return deployMapper.findReleases(projectId, service).stream()
                .filter(release -> {
                    RequirementRecord requirement = byId.get(release.requirementId());
                    EnvStateRecord state = prodStates.get(release.requirementId());
                    return requirement != null
                            && BranchStatus.CREATED.code().equals(requirement.branchStatus())
                            && state != null && EnvStatus.SUCCESS.code().equals(state.status());
                })
                .map(release -> new Views.ProdRelease(
                        byId.get(release.requirementId()).reqNo(),
                        byId.get(release.requirementId()).title(),
                        release.currentSha(),
                        Fmt.time(release.currentTime()),
                        release.previousSha(),
                        Fmt.time(release.previousTime())))
                .toList();
    }

    @Override
    public Views.StartResult start(String projectId, String service, String env, List<String> ids,
                                   String operator) {
        validate(projectId, service, env);
        if (ids == null || ids.isEmpty()) {
            throw ApiException.badRequest("请选择要部署的分支");
        }
        long batchId = pipelineService.start(projectId, service, env, ids, operator);
        return new Views.StartResult(true, "已加入部署队列", batchId);
    }

    @Override
    public Views.StartResult rollback(String projectId, String service, String env, String reqNo,
                                      String operator) {
        validate(projectId, service, env);
        if (!Env.PROD.code().equals(env)) {
            throw ApiException.badRequest("只有生产环境支持回滚");
        }
        RequirementRecord requirement = requirementMapper.findByReqNo(projectId, reqNo)
                .orElseThrow(() -> ApiException.notFound("需求不存在：" + reqNo));
        long batchId = pipelineService.rollback(projectId, service, requirement, operator);
        return new Views.StartResult(true, "回滚已开始", batchId);
    }

    @Override
    public Views.Result resolveConflict(String projectId, String service, String env, String reqNo) {
        validate(projectId, service, env);
        RequirementRecord requirement = requirementMapper.findByReqNo(projectId, reqNo)
                .orElseThrow(() -> ApiException.notFound("需求不存在：" + reqNo));
        EnvStateRecord state = envStateMapper.find(requirement.id(), service, env)
                .orElseThrow(() -> ApiException.badRequest("该环境没有部署记录"));
        if (EnvStatus.of(state.status()) != EnvStatus.CONFLICT) {
            throw ApiException.badRequest("该环境没有合并冲突");
        }
        envStateMapper.markReady(requirement.id(), service, env, LocalDateTime.now());
        return new Views.Result(true, "冲突已标记解决，请确认重新部署");
    }

    @Override
    public void abort(long batchId) {
        pipelineService.abort(batchId);
    }

    @Override
    public Views.Content logs(String podName, String projectId, String service, String env) {
        RuntimeTarget target = runtimeTarget(projectId, service, env);
        return new Views.Content(runtimeService.logs(target, requirePod(podName)));
    }

    @Override
    public Views.Content yaml(String podName, String projectId, String service, String env) {
        RuntimeTarget target = runtimeTarget(projectId, service, env);
        return new Views.Content(runtimeService.yaml(target, requirePod(podName)));
    }

    @Override
    public List<Views.PodEvent> events(String podName, String projectId, String service, String env) {
        RuntimeTarget target = runtimeTarget(projectId, service, env);
        return runtimeService.events(target, requirePod(podName));
    }

    @Override
    public Views.Scale scaleInfo(String projectId, String service, String env) {
        RuntimeTarget target = runtimeTarget(projectId, service, env);
        return new Views.Scale(runtimeService.replicas(target), minReplicas(env),
                PlatformDefaults.MAX_REPLICAS, runtimeMode);
    }

    @Override
    public Views.Scale scale(String projectId, String service, String env, Integer replicas,
                             String operator) {
        requireAdmin(operator);
        RuntimeTarget target = runtimeTarget(projectId, service, env);
        if (replicas == null) {
            throw ApiException.badRequest("请填写副本数");
        }
        int min = minReplicas(env);
        if (replicas < min || replicas > PlatformDefaults.MAX_REPLICAS) {
            throw ApiException.badRequest("副本数需在 " + min + "~" + PlatformDefaults.MAX_REPLICAS + " 之间");
        }
        runtimeService.scale(target, replicas);
        log.info("scale {}/{} in {} -> {} replicas by {}", projectId, service, env, replicas, operator);
        return new Views.Scale(replicas, min, PlatformDefaults.MAX_REPLICAS, runtimeMode);
    }

    @Override
    public Views.Result restartPod(String projectId, String service, String env, String podName,
                                   String operator) {
        requireAdmin(operator);
        RuntimeTarget target = runtimeTarget(projectId, service, env);
        String name = requirePod(podName);
        runtimeService.restartPod(target, name);
        log.info("restart pod {} in {}/{}/{} by {}", name, projectId, service, env, operator);
        return new Views.Result(true, "已重启 " + name);
    }

    private RuntimeTarget runtimeTarget(String projectId, String service, String env) {
        validate(projectId, service, env);
        return RuntimeTarget.of(projectId, service, env, projectMapper.findRuntimeApp(projectId));
    }

    private String requirePod(String podName) {
        if (podName == null || podName.isBlank()) {
            throw ApiException.badRequest("缺少 Pod 名称");
        }
        return podName.trim();
    }

    private int minReplicas(String env) {
        return Env.find(env).orElse(Env.DEV).minReplicas();
    }

    private void requireAdmin(String username) {
        UserAccount account = userMapper.findByUsername(username)
                .orElseThrow(() -> ApiException.unauthorized("请先登录"));
        if (!account.isCrAdmin()) {
            throw ApiException.forbidden("只有管理员可以执行该操作");
        }
    }

    private void validate(String projectId, String service, String env) {
        if (!projectMapper.exists(projectId)) {
            throw ApiException.notFound("项目不存在：" + projectId);
        }
        if (ServiceType.find(service).isEmpty()) {
            throw ApiException.badRequest("未知服务：" + service);
        }
        String projectService = projectMapper.findService(projectId);
        if (projectService != null && !projectService.isBlank() && !projectService.equals(service)) {
            throw ApiException.badRequest("项目 " + projectId + " 只支持 " + projectService + " 服务");
        }
        if (Env.find(env).isEmpty()) {
            throw ApiException.badRequest("未知环境：" + env);
        }
    }
}
