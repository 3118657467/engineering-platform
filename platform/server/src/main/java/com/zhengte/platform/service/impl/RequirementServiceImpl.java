package com.zhengte.platform.service.impl;

import com.zhengte.platform.service.RequirementBranchSync;
import com.zhengte.platform.service.RequirementService;
import com.zhengte.platform.common.ApiException;
import com.zhengte.platform.constants.ValidationRules;
import com.zhengte.platform.converter.ViewConverter;
import com.zhengte.platform.dto.Views;
import com.zhengte.platform.entity.CrRecord;
import com.zhengte.platform.entity.EnvStateRecord;
import com.zhengte.platform.entity.RequirementBranchRecord;
import com.zhengte.platform.entity.RequirementRecord;
import com.zhengte.platform.entity.UserAccount;
import com.zhengte.platform.enums.BranchStatus;
import com.zhengte.platform.enums.Env;
import com.zhengte.platform.enums.ServiceType;
import com.zhengte.platform.integration.git.GitService;
import com.zhengte.platform.mapper.CrRequestMapper;
import com.zhengte.platform.mapper.EnvStateMapper;
import com.zhengte.platform.mapper.ProjectMapper;
import com.zhengte.platform.mapper.RequirementBranchMapper;
import com.zhengte.platform.mapper.RequirementMapper;
import com.zhengte.platform.mapper.RequirementRelatedMapper;
import com.zhengte.platform.mapper.RequirementArchiveMapper;
import com.zhengte.platform.mapper.UserMapper;
import com.zhengte.platform.utils.Fmt;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequirementServiceImpl implements RequirementService {

    private static final Logger log = LoggerFactory.getLogger(RequirementServiceImpl.class);

    private final ProjectMapper projectMapper;
    private final RequirementMapper requirementMapper;
    private final RequirementBranchMapper branchMapper;
    private final RequirementRelatedMapper relatedMapper;
    private final CrRequestMapper crMapper;
    private final EnvStateMapper envStateMapper;
    private final UserMapper userMapper;
    private final ViewConverter viewConverter;
    private final GitService gitService;
    private final RequirementBranchSync branchSync;
    private final RequirementArchiveMapper archiveMapper;

    public RequirementServiceImpl(ProjectMapper projectMapper,
                              RequirementMapper requirementMapper,
                              RequirementBranchMapper branchMapper,
                              RequirementRelatedMapper relatedMapper,
                              CrRequestMapper crMapper,
                              EnvStateMapper envStateMapper,
                              UserMapper userMapper,
                              ViewConverter viewConverter,
                              GitService gitService,
                              RequirementBranchSync branchSync,
                              RequirementArchiveMapper archiveMapper) {
        this.projectMapper = projectMapper;
        this.requirementMapper = requirementMapper;
        this.branchMapper = branchMapper;
        this.relatedMapper = relatedMapper;
        this.crMapper = crMapper;
        this.envStateMapper = envStateMapper;
        this.userMapper = userMapper;
        this.viewConverter = viewConverter;
        this.gitService = gitService;
        this.branchSync = branchSync;
        this.archiveMapper = archiveMapper;
    }

    @Override
    public List<Views.Requirement> listVisible(String projectId, String username) {
        requireProject(projectId);
        return build(requirementMapper.findVisible(projectId, username));
    }

    @Override
    public Views.Requirement get(String projectId, String reqNo) {
        requireProject(projectId);
        RequirementRecord record = requirementMapper.findByReqNo(projectId, reqNo)
                .orElseThrow(() -> ApiException.notFound("需求不存在：" + reqNo));
        return build(List.of(record)).get(0);
    }

    @Override
    public Views.Requirement create(String projectId, String title, String description,
                                    List<String> related, String owner) {
        requireProject(projectId);
        String cleanTitle = title == null ? "" : title.trim();
        if (cleanTitle.isEmpty()) {
            throw ApiException.badRequest("请填写需求标题");
        }
        if (cleanTitle.length() > ValidationRules.TITLE_MAX_LENGTH) {
            throw ApiException.badRequest("需求标题不能超过 " + ValidationRules.TITLE_MAX_LENGTH + " 字");
        }
        List<String> relatedUsers = cleanRelated(related, owner);
        String reqNo = requirementMapper.nextReqNo(projectId);
        LocalDateTime now = LocalDateTime.now();
        long id = requirementMapper.insert(projectId, reqNo, cleanTitle,
                description == null ? "" : description.trim(), owner, now);
        relatedMapper.replaceRelated(id, relatedUsers);
        return get(projectId, reqNo);
    }

    @Override
    @Transactional
    public Views.Requirement createBranch(String projectId, String reqNo, String suffix, String operator) {
        RequirementRecord requirement = requireRequirement(projectId, reqNo);
        if (BranchStatus.CREATED.code().equals(requirement.branchStatus())) {
            throw ApiException.badRequest("该需求已经创建过开发分支");
        }
        String cleanSuffix = suffix == null ? "" : suffix.trim();
        if (!ValidationRules.BRANCH_SUFFIX.matcher(cleanSuffix).matches()) {
            throw ApiException.badRequest("分支后缀只能使用小写字母、数字和连字符");
        }
        String service = requireProjectService(projectId);
        LocalDateTime now = LocalDateTime.now();
        String branchName = Fmt.day(now) + "-" + cleanSuffix;
        boolean created = false;
        try {
            String tip = gitService.branchTip(projectId, service, branchName);
            if (tip == null) {
                tip = gitService.createBranch(projectId, service, branchName);
                created = true;
            }
            requirementMapper.updateBranch(requirement.id(), branchName, now);
            branchMapper.upsert(requirement.id(), service, branchName, tip, now);
        } catch (RuntimeException e) {
            if (created) {
                try {
                    gitService.deleteBranch(projectId, service, branchName);
                } catch (RuntimeException cleanupError) {
                    log.error("回滚分支失败 {}/{}：{}", service, branchName, cleanupError.getMessage());
                }
            }
            throw e;
        }
        return get(projectId, reqNo);
    }

    @Override
    @Transactional
    public Views.Requirement bindBranch(String projectId, String reqNo, String branchName,
                                        String operator) {
        RequirementRecord requirement = requireRequirement(projectId, reqNo);
        if (BranchStatus.CREATED.code().equals(requirement.branchStatus())) {
            throw ApiException.badRequest("该需求已经绑定过开发分支");
        }
        String cleanBranch = branchName == null ? "" : branchName.trim();
        if (cleanBranch.isEmpty() || !ValidationRules.BRANCH_NAME.matcher(cleanBranch).matches()) {
            throw ApiException.badRequest("请填写合法的分支名");
        }
        String service = requireProjectService(projectId);
        String tip = gitService.branchTip(projectId, service, cleanBranch);
        if (tip == null) {
            throw ApiException.badRequest("仓库 " + projectId + " 不存在分支 " + cleanBranch);
        }
        LocalDateTime now = LocalDateTime.now();
        requirementMapper.updateBranch(requirement.id(), cleanBranch, now);
        branchMapper.upsert(requirement.id(), service, cleanBranch, tip, now);
        return get(projectId, reqNo);
    }

    @Override
    public Views.Requirement createCr(String projectId, String reqNo, String service, String approver,
                                      String comment, String operator) {
        RequirementRecord requirement = requireRequirement(projectId, reqNo);
        if (ServiceType.find(service).isEmpty()) {
            throw ApiException.badRequest("未知服务：" + service);
        }
        String projectService = requireProjectService(projectId);
        if (!projectService.equals(service)) {
            throw ApiException.badRequest("项目 " + projectId + " 只支持 " + projectService + " 服务");
        }
        if (!BranchStatus.CREATED.code().equals(requirement.branchStatus())) {
            throw ApiException.badRequest("请先创建或绑定开发分支");
        }
        String tip = branchSync.syncTip(projectId, requirement, service);
        if (tip == null) {
            throw ApiException.badRequest("请先创建或绑定 " + service + " 的开发分支");
        }
        if (approver == null || approver.isBlank()) {
            throw ApiException.badRequest("请选择 CR 审批人");
        }
        UserAccount approverAccount = userMapper.findByUsername(approver)
                .orElseThrow(() -> ApiException.badRequest("审批人不存在：" + approver));
        if (!approverAccount.isCrAdmin()) {
            throw ApiException.badRequest("指定的审批人不是 CR 管理员");
        }
        LocalDateTime now = LocalDateTime.now();
        crMapper.upsertPending(requirement.id(), service, approver, operator,
                comment == null ? null : comment.trim(), now);
        envStateMapper.markWaitCr(requirement.id(), service, tip, operator, now);
        return get(projectId, reqNo);
    }

    private List<String> cleanRelated(List<String> related, String owner) {
        if (related == null) {
            return List.of();
        }
        List<String> usernames = related.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        usernames.remove(owner);
        for (String username : usernames) {
            if (userMapper.findByUsername(username).isEmpty()) {
                throw ApiException.badRequest("关联人不存在：" + username);
            }
        }
        return usernames;
    }

    private List<Views.Requirement> build(List<RequirementRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        String projectId = records.get(0).projectId();
        String projectService = projectMapper.findService(projectId);
        List<String> shownServices = projectService == null || projectService.isBlank()
                ? ServiceType.codes() : List.of(projectService);
        Map<Long, List<String>> related = relatedMapper.relatedDisplayNames(projectId);
        Map<Long, Map<String, RequirementBranchRecord>> branches = branchMapper.findByProject(projectId);
        Map<Long, Map<String, Map<String, EnvStateRecord>>> envStates = new HashMap<>();
        envStateMapper.findAllByProject(projectId).forEach(state ->
                envStates.computeIfAbsent(state.requirementId(), key -> new HashMap<>())
                        .computeIfAbsent(state.service(), key -> new HashMap<>())
                        .put(state.env(), state));
        Map<Long, List<CrRecord>> crs = crMapper.findByProject(projectId);
        Map<String, String> names = userMapper.displayNames();
        Set<Long> archived = archiveMapper.findArchivedIds(projectId);

        List<Views.Requirement> result = new ArrayList<>();
        for (RequirementRecord record : records) {
            Map<String, Map<String, EnvStateRecord>> raw = envStates.getOrDefault(record.id(), Map.of());
            Map<String, Map<String, Views.EnvState>> services = new LinkedHashMap<>();
            Map<String, String> tips = new LinkedHashMap<>();
            Map<String, RequirementBranchRecord> recordBranches = branches.getOrDefault(record.id(), Map.of());
            for (String service : shownServices) {
                RequirementBranchRecord branch = recordBranches.get(service);
                tips.put(service, branch == null ? null : branch.tipSha());
                Map<String, Views.EnvState> envViews = new LinkedHashMap<>();
                Map<String, EnvStateRecord> serviceStates = raw.getOrDefault(service, Map.of());
                for (String env : Env.codes()) {
                    envViews.put(env, viewConverter.envState(serviceStates.get(env), names));
                }
                services.put(service, envViews);
            }
            String primaryService = shownServices.get(0);
            Optional<CrRecord> cr = crs.getOrDefault(record.id(), List.of()).stream()
                    .filter(row -> primaryService.equals(row.service())).findFirst()
                    .or(() -> crs.getOrDefault(record.id(), List.of()).stream().findFirst());
            Views.Label label = viewConverter.requirementLabel(record, raw, cr.orElse(null));
            result.add(new Views.Requirement(
                    record.reqNo(),
                    record.projectId(),
                    record.title(),
                    record.description(),
                    record.owner(),
                    names.getOrDefault(record.owner(), record.owner()),
                    related.getOrDefault(record.id(), List.of()),
                    record.branchName(),
                    BranchStatus.of(record.branchStatus()).code().toLowerCase(),
                    tips,
                    Fmt.time(record.createdAt()),
                    label,
                    services,
                    viewConverter.cr(cr.orElse(null), primaryService, names),
                    archived.contains(record.id())));
        }
        return result;
    }

    private RequirementRecord requireRequirement(String projectId, String reqNo) {
        requireProject(projectId);
        return requirementMapper.findByReqNo(projectId, reqNo)
                .orElseThrow(() -> ApiException.notFound("需求不存在：" + reqNo));
    }

    private void requireProject(String projectId) {
        if (projectId == null || projectId.isBlank() || !projectMapper.exists(projectId)) {
            throw ApiException.notFound("项目不存在：" + projectId);
        }
    }

    private String requireProjectService(String projectId) {
        requireProject(projectId);
        String service = projectMapper.findService(projectId);
        if (service == null || service.isBlank()) {
            throw ApiException.badRequest("项目未配置部署服务：" + projectId);
        }
        return service;
    }
}
