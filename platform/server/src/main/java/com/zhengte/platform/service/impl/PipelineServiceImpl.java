package com.zhengte.platform.service.impl;

import com.zhengte.platform.common.ApiException;
import com.zhengte.platform.constants.DeploySteps;
import com.zhengte.platform.constants.GitModes;
import com.zhengte.platform.constants.GitVariables;
import com.zhengte.platform.constants.PlatformDefaults;
import com.zhengte.platform.dto.Views;
import com.zhengte.platform.entity.BatchRecord;
import com.zhengte.platform.entity.CrRecord;
import com.zhengte.platform.entity.DeploymentRecord;
import com.zhengte.platform.entity.EnvStateRecord;
import com.zhengte.platform.entity.ReleaseRecord;
import com.zhengte.platform.entity.RequirementRecord;
import com.zhengte.platform.entity.StepRecord;
import com.zhengte.platform.enums.BatchStatus;
import com.zhengte.platform.enums.BranchStatus;
import com.zhengte.platform.enums.DeploymentStatus;
import com.zhengte.platform.enums.Env;
import com.zhengte.platform.enums.EnvStatus;
import com.zhengte.platform.enums.StepStatus;
import com.zhengte.platform.integration.git.GitService;
import com.zhengte.platform.mapper.CrRequestMapper;
import com.zhengte.platform.mapper.DeployMapper;
import com.zhengte.platform.mapper.EnvStateMapper;
import com.zhengte.platform.mapper.RequirementMapper;
import com.zhengte.platform.mapper.UserMapper;
import com.zhengte.platform.service.DeployGate;
import com.zhengte.platform.service.PipelineService;
import com.zhengte.platform.service.RequirementBranchSync;
import com.zhengte.platform.utils.Fmt;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PipelineServiceImpl implements PipelineService {

    private static final Logger log = LoggerFactory.getLogger(PipelineServiceImpl.class);
    private static final List<String> NOT_STARTED_JOB_STATUSES =
            List.of("created", "pending", "manual");

    private final DeployMapper deployMapper;
    private final EnvStateMapper envStateMapper;
    private final RequirementMapper requirementMapper;
    private final CrRequestMapper crMapper;
    private final UserMapper userMapper;
    private final GitService gitService;
    private final RequirementBranchSync branchSync;
    private final TransactionTemplate transactionTemplate;
    private final int stepDelayMs;
    private final long pollIntervalMs;
    private final long timeoutMs;
    private final ExecutorService executor;
    private final Set<Long> abortedBatches = ConcurrentHashMap.newKeySet();

    public PipelineServiceImpl(DeployMapper deployMapper,
                               EnvStateMapper envStateMapper,
                               RequirementMapper requirementMapper,
                               CrRequestMapper crMapper,
                               UserMapper userMapper,
                               GitService gitService,
                               RequirementBranchSync branchSync,
                               PlatformTransactionManager transactionManager,
                               @Value("${platform.pipeline.step-delay-ms:1500}") int stepDelayMs,
                               @Value("${platform.pipeline.poll-interval-ms:"
                                       + PlatformDefaults.POLL_INTERVAL_MS + "}") long pollIntervalMs,
                               @Value("${platform.pipeline.timeout-seconds:"
                                       + PlatformDefaults.PIPELINE_TIMEOUT_SECONDS + "}")
                               long timeoutSeconds) {
        this.deployMapper = deployMapper;
        this.envStateMapper = envStateMapper;
        this.requirementMapper = requirementMapper;
        this.crMapper = crMapper;
        this.userMapper = userMapper;
        this.gitService = gitService;
        this.branchSync = branchSync;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.stepDelayMs = stepDelayMs;
        this.pollIntervalMs = pollIntervalMs;
        this.timeoutMs = timeoutSeconds * 1000;
        AtomicInteger counter = new AtomicInteger(1);
        this.executor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "pipeline-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
    }

    private record Task(RequirementRecord requirement, String sha) {
    }

    @Override
    public long start(String projectId, String service, String env, List<String> reqNos, String operator) {
        List<Task> tasks = new ArrayList<>();
        for (String reqNo : reqNos) {
            RequirementRecord requirement = requirementMapper.findByReqNo(projectId, reqNo)
                    .orElseThrow(() -> ApiException.notFound("需求不存在：" + reqNo));
            String tipSha = branchSync.syncTip(projectId, requirement, service);
            validateGate(requirement, service, env, tipSha);
            tasks.add(new Task(requirement, tipSha));
        }
        return createAndRun(projectId, service, env, tasks, operator, false);
    }

    @Override
    public long rollback(String projectId, String service, RequirementRecord requirement, String operator) {
        ReleaseRecord release = deployMapper.findRelease(requirement.id(), service)
                .orElseThrow(() -> ApiException.badRequest("没有可回滚的历史版本"));
        if (release.previousSha() == null || release.previousSha().isBlank()) {
            throw ApiException.badRequest("没有可回滚的历史版本");
        }
        List<Task> tasks = List.of(new Task(requirement, release.previousSha()));
        return createAndRun(projectId, service, Env.PROD.code(), tasks, operator, true);
    }

    private void validateGate(RequirementRecord requirement, String service, String env, String tipSha) {
        if (!BranchStatus.CREATED.code().equals(requirement.branchStatus()) || tipSha == null) {
            throw ApiException.badRequest(requirement.reqNo() + " 还没有可部署的 " + service + " 开发分支");
        }
        Env envType = Env.find(env).orElseThrow(() -> ApiException.badRequest("未知环境：" + env));
        EnvStateRecord target = envStateMapper.find(requirement.id(), service, env).orElse(null);
        EnvStateRecord previous = envType.previous()
                .map(prev -> envStateMapper.find(requirement.id(), service, prev.code()).orElse(null))
                .orElse(null);
        CrRecord cr = crMapper.find(requirement.id(), service).orElse(null);
        Views.Gate gate = DeployGate.check(requirement, service, env, target, previous, cr, tipSha);
        if (!gate.ok()) {
            throw ApiException.badRequest(requirement.reqNo() + "：" + gate.reason());
        }
    }

    private long createAndRun(String projectId, String service, String env, List<Task> tasks,
                              String operator, boolean rollback) {
        if (Env.find(env).isEmpty()) {
            throw ApiException.badRequest("未知环境：" + env);
        }
        if (tasks.isEmpty()) {
            throw ApiException.badRequest("没有可部署的分支");
        }
        LocalDateTime now = LocalDateTime.now();
        long batchId;
        try {
            Long created = transactionTemplate.execute(status -> {
                long id = deployMapper.createBatch(projectId, service, env, operator, now);
                for (Task task : tasks) {
                    RequirementRecord requirement = task.requirement();
                    long deploymentId = deployMapper.createDeployment(id, requirement.id(), service, env,
                            requirement.branchName(), task.sha(), operator, now);
                    for (DeploySteps.Definition step : DeploySteps.ALL) {
                        deployMapper.createStep(deploymentId, step.seq(), step.name());
                    }
                }
                deployMapper.createLock(projectId, service, env, id, now);
                return id;
            });
            if (created == null) {
                throw ApiException.conflict("部署任务创建失败");
            }
            batchId = created;
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("当前环境正在部署，请勿重复提交");
        }

        executor.submit(() -> runPipeline(projectId, batchId, service, env, rollback));
        return batchId;
    }

    private enum Outcome {
        SUCCESS, ABORTED, FAILED, CONFLICT
    }

    private record RunResult(Outcome outcome, String error) {

        static RunResult success() {
            return new RunResult(Outcome.SUCCESS, null);
        }

        static RunResult aborted() {
            return new RunResult(Outcome.ABORTED, null);
        }

        static RunResult failed(String error) {
            return new RunResult(Outcome.FAILED, error);
        }

        static RunResult conflict(String error) {
            return new RunResult(Outcome.CONFLICT, error);
        }
    }

    private void runPipeline(String projectId, long batchId, String service, String env, boolean rollback) {
        try {
            List<DeploymentRecord> deployments = deployMapper.findDeployments(batchId);
            for (DeploymentRecord deployment : deployments) {
                if (isAborted(batchId)) {
                    finishTerminated(batchId, deployment);
                    return;
                }
                LocalDateTime start = LocalDateTime.now();
                deployMapper.updateDeploymentStatus(deployment.id(),
                        DeploymentStatus.RUNNING.code(), start, null, null);
                envStateMapper.saveState(deployment.requirementId(), service, env,
                        EnvStatus.RUNNING.code(), deployment.sha(), deployment.operator(), start, null);

                RunResult result = GitModes.GITLAB.equals(gitService.mode())
                        ? runGitlab(projectId, batchId, deployment, rollback)
                        : runSimulated(batchId, deployment);

                if (result.outcome() == Outcome.ABORTED) {
                    finishTerminated(batchId, deployment);
                    return;
                }

                LocalDateTime finished = LocalDateTime.now();
                if (result.outcome() == Outcome.FAILED) {
                    deployMapper.updateDeploymentStatus(deployment.id(),
                            DeploymentStatus.FAILED.code(), null, finished, result.error());
                    envStateMapper.markReady(deployment.requirementId(), service, env, finished);
                    deployMapper.updateBatchStatus(batchId, BatchStatus.TERMINATED.code(), finished);
                    return;
                }
                if (result.outcome() == Outcome.CONFLICT) {
                    deployMapper.updateDeploymentStatus(deployment.id(),
                            DeploymentStatus.FAILED.code(), null, finished, result.error());
                    envStateMapper.saveState(deployment.requirementId(), service, env,
                            EnvStatus.CONFLICT.code(), deployment.sha(), deployment.operator(),
                            finished, result.error());
                    deployMapper.updateBatchStatus(batchId, BatchStatus.TERMINATED.code(), finished);
                    return;
                }

                deployMapper.updateDeploymentStatus(deployment.id(),
                        DeploymentStatus.SUCCESS.code(), null, finished, null);
                envStateMapper.saveState(deployment.requirementId(), service, env,
                        EnvStatus.SUCCESS.code(), deployment.sha(), deployment.operator(),
                        finished, null);
                if (Env.PROD.code().equals(env)) {
                    if (rollback) {
                        deployMapper.rollbackRelease(deployment.requirementId(), service,
                                deployment.sha(), finished);
                    } else {
                        deployMapper.saveRelease(deployment.requirementId(), service,
                                deployment.sha(), finished);
                    }
                }
            }
            deployMapper.updateBatchStatus(batchId, BatchStatus.SUCCESS.code(), LocalDateTime.now());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            deployMapper.updateBatchStatus(batchId, BatchStatus.TERMINATED.code(), LocalDateTime.now());
        } catch (Exception e) {
            log.error("部署任务执行失败 batchId={}", batchId, e);
            LocalDateTime now = LocalDateTime.now();
            deployMapper.findOpenDeployment(batchId).ifPresent(deployment -> {
                failOpenSteps(deployment.id());
                deployMapper.updateDeploymentStatus(deployment.id(),
                        DeploymentStatus.FAILED.code(), null, now, e.getMessage());
                envStateMapper.markReady(deployment.requirementId(), deployment.service(),
                        deployment.env(), now);
            });
            deployMapper.updateBatchStatus(batchId, BatchStatus.TERMINATED.code(), now);
        } finally {
            abortedBatches.remove(batchId);
            deployMapper.deleteLockByBatch(batchId);
        }
    }

    private RunResult runSimulated(long batchId, DeploymentRecord deployment) throws InterruptedException {
        for (StepRecord step : deployMapper.findSteps(deployment.id())) {
            if (isAborted(batchId)) {
                return RunResult.aborted();
            }
            deployMapper.updateStepStatus(step.id(), StepStatus.RUNNING.code(), null);
            long startedAt = System.currentTimeMillis();
            Thread.sleep(stepDelayMs);
            int duration = (int) (System.currentTimeMillis() - startedAt);
            if (isAborted(batchId)) {
                deployMapper.updateStepStatus(step.id(), StepStatus.FAILED.code(), duration);
                return RunResult.aborted();
            }
            deployMapper.updateStepStatus(step.id(), StepStatus.SUCCESS.code(), duration);
        }
        return RunResult.success();
    }

    private RunResult runGitlab(String projectId, long batchId, DeploymentRecord deployment,
                                boolean rollback) throws InterruptedException {
        if (!rollback) {
            String targetBranch = Env.find(deployment.env())
                    .map(Env::branch).orElse(deployment.env());
            GitService.MergeResult merge = gitService.merge(projectId,
                    deployment.service(), deployment.branchName(), targetBranch);
            if (merge.conflict()) {
                failOpenSteps(deployment.id());
                return RunResult.conflict("合并到 " + targetBranch + " 冲突：" + merge.message());
            }
        }
        RequirementRecord requirement = requirementMapper.findById(deployment.requirementId()).orElse(null);
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put(GitVariables.DEPLOY_ENV, deployment.env());
        variables.put(GitVariables.DEPLOY_SERVICE, deployment.service());
        variables.put(GitVariables.DEPLOY_SHA, deployment.sha());
        variables.put(GitVariables.REQUIREMENT_NO, requirement == null ? "" : requirement.reqNo());

        long pipelineId = gitService.triggerPipeline(projectId, deployment.service(),
                deployment.branchName(), variables);
        deployMapper.savePipeline(deployment.id(), pipelineId, "created", LocalDateTime.now());
        log.info("触发 GitLab pipeline {} deployment={} branch={} env={}",
                pipelineId, deployment.id(), deployment.branchName(), deployment.env());

        long deadline = System.currentTimeMillis() + timeoutMs;
        GitService.PipelineState state = gitService.pipeline(projectId, deployment.service(), pipelineId);
        while (true) {
            applyPipelineState(deployment.id(), state);
            deployMapper.savePipeline(deployment.id(), pipelineId, state.status(), LocalDateTime.now());
            if (state.terminal()) {
                break;
            }
            if (isAborted(batchId)) {
                gitService.cancelPipeline(projectId, deployment.service(), pipelineId);
                failOpenSteps(deployment.id());
                return RunResult.aborted();
            }
            if (System.currentTimeMillis() > deadline) {
                gitService.cancelPipeline(projectId, deployment.service(), pipelineId);
                failOpenSteps(deployment.id());
                return RunResult.failed("GitLab 流水线超时（" + timeoutMs / 60000 + " 分钟）");
            }
            Thread.sleep(pollIntervalMs);
            state = gitService.pipeline(projectId, deployment.service(), pipelineId);
        }
        if (state.success()) {
            return RunResult.success();
        }
        failOpenSteps(deployment.id());
        return RunResult.failed("GitLab 流水线 " + state.status());
    }

    private void applyPipelineState(long deploymentId, GitService.PipelineState state) {
        Map<String, GitService.JobState> jobs = state.jobs().stream()
                .collect(Collectors.toMap(GitService.JobState::name, job -> job, (left, right) -> left));
        for (StepRecord step : deployMapper.findSteps(deploymentId)) {
            String target = switch (step.seq()) {
                case 1 -> checkoutStatus(state);
                case 2, 3 -> jobStatus(jobs.get("build"));
                case 4 -> jobStatus(jobs.get("deploy"));
                case 5 -> jobStatus(jobs.get("verify"));
                default -> null;
            };
            if (target == null || target.equals(step.status())) {
                continue;
            }
            Integer durationMs = step.durationMs();
            GitService.JobState job = jobFor(step.seq(), jobs);
            if (job != null && job.durationSeconds() != null
                    && (StepStatus.SUCCESS.code().equals(target)
                    || StepStatus.FAILED.code().equals(target))) {
                durationMs = job.durationSeconds() * 1000;
            }
            deployMapper.updateStepStatus(step.id(), target, durationMs);
        }
    }

    private String checkoutStatus(GitService.PipelineState state) {
        if (state.terminal()) {
            return StepStatus.SUCCESS.code();
        }
        boolean started = state.jobs().stream()
                .anyMatch(job -> !NOT_STARTED_JOB_STATUSES.contains(job.status()));
        return started ? StepStatus.SUCCESS.code() : StepStatus.RUNNING.code();
    }

    private String jobStatus(GitService.JobState job) {
        if (job == null) {
            return StepStatus.PENDING.code();
        }
        return switch (job.status()) {
            case "success" -> StepStatus.SUCCESS.code();
            case "running" -> StepStatus.RUNNING.code();
            case "failed", "canceled", "skipped" -> StepStatus.FAILED.code();
            default -> StepStatus.PENDING.code();
        };
    }

    private GitService.JobState jobFor(int seq, Map<String, GitService.JobState> jobs) {
        return switch (seq) {
            case 2, 3 -> jobs.get("build");
            case 4 -> jobs.get("deploy");
            case 5 -> jobs.get("verify");
            default -> null;
        };
    }

    private void failOpenSteps(long deploymentId) {
        deployMapper.findSteps(deploymentId).stream()
                .filter(step -> !StepStatus.SUCCESS.code().equals(step.status())
                        && !StepStatus.FAILED.code().equals(step.status()))
                .forEach(step -> deployMapper.updateStepStatus(step.id(),
                        StepStatus.FAILED.code(), step.durationMs()));
    }

    private void finishTerminated(long batchId, DeploymentRecord deployment) {
        LocalDateTime now = LocalDateTime.now();
        deployMapper.updateDeploymentStatus(deployment.id(), DeploymentStatus.CANCELLED.code(),
                null, now, "部署已终止");
        deployMapper.findSteps(deployment.id()).stream()
                .filter(step -> StepStatus.RUNNING.code().equals(step.status()))
                .forEach(step -> deployMapper.updateStepStatus(step.id(),
                        StepStatus.FAILED.code(), step.durationMs()));
        envStateMapper.markReady(deployment.requirementId(), deployment.service(), deployment.env(), now);
        deployMapper.updateBatchStatus(batchId, BatchStatus.TERMINATED.code(), now);
    }

    @Override
    public void abort(long batchId) {
        BatchRecord batch = deployMapper.findBatch(batchId)
                .orElseThrow(() -> ApiException.notFound("部署任务不存在"));
        if (!BatchStatus.RUNNING.code().equals(batch.status())) {
            throw ApiException.conflict("部署已经结束");
        }
        abortedBatches.add(batchId);
        LocalDateTime now = LocalDateTime.now();
        deployMapper.updateBatchStatus(batchId, BatchStatus.TERMINATED.code(), now);
        Optional<DeploymentRecord> open = deployMapper.findOpenDeployment(batchId);
        open.ifPresent(deployment -> {
            deployMapper.updateDeploymentStatus(deployment.id(), DeploymentStatus.CANCELLED.code(),
                    null, now, "部署已终止");
            deployMapper.findSteps(deployment.id()).stream()
                    .filter(step -> StepStatus.RUNNING.code().equals(step.status()))
                    .forEach(step -> deployMapper.updateStepStatus(step.id(),
                            StepStatus.FAILED.code(), step.durationMs()));
            envStateMapper.markReady(deployment.requirementId(), deployment.service(), deployment.env(), now);
        });
        deployMapper.cancelPendingDeployments(batchId, now);
        deployMapper.deleteLockByBatch(batchId);
    }

    private boolean isAborted(long batchId) {
        if (abortedBatches.contains(batchId)) {
            return true;
        }
        return deployMapper.findBatch(batchId)
                .map(batch -> !BatchStatus.RUNNING.code().equals(batch.status()))
                .orElse(true);
    }

    @Override
    public Views.Pipeline latest(String projectId, String service, String env) {
        Optional<BatchRecord> found = deployMapper.findLatestBatch(projectId, service, env);
        if (found.isEmpty()) {
            return null;
        }
        BatchRecord batch = found.get();
        if (batch.finishedAt() != null && batch.finishedAt().isBefore(LocalDateTime.now().minusMinutes(30))) {
            return null;
        }
        List<DeploymentRecord> deployments = deployMapper.findDeployments(batch.id());
        Map<Long, List<StepRecord>> steps = deployMapper.findStepsByBatch(batch.id()).stream()
                .collect(Collectors.groupingBy(StepRecord::deploymentId));
        Map<String, String> names = userMapper.displayNames();
        List<Views.PipelineReq> requirements = deployments.stream()
                .map(deployment -> {
                    RequirementRecord requirement = requirementMapper.findById(deployment.requirementId())
                            .orElse(null);
                    List<Views.Step> stepViews = steps.getOrDefault(deployment.id(), List.of()).stream()
                            .map(step -> new Views.Step(step.seq(), step.name(),
                                    step.status().toLowerCase(), Fmt.duration(step.durationMs())))
                            .toList();
                    return new Views.PipelineReq(
                            requirement == null ? String.valueOf(deployment.requirementId()) : requirement.reqNo(),
                            requirement == null ? "" : requirement.title(),
                            deployment.branchName(),
                            deployment.sha(),
                            deployment.status().toLowerCase(),
                            stepViews);
                })
                .toList();
        return new Views.Pipeline(batch.id(), batch.status().toLowerCase(),
                Fmt.full(batch.startedAt()), names.getOrDefault(batch.operator(), batch.operator()),
                requirements);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileOnStartup() {
        deployMapper.reconcileAfterRestart();
        envStateMapper.reconcileRunning();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
