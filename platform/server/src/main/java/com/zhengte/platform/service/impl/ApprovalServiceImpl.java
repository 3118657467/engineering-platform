package com.zhengte.platform.service.impl;

import com.zhengte.platform.common.ApiException;
import com.zhengte.platform.dto.Views;
import com.zhengte.platform.entity.CrRecord;
import com.zhengte.platform.entity.RequirementRecord;
import com.zhengte.platform.entity.UserAccount;
import com.zhengte.platform.enums.CrStatus;
import com.zhengte.platform.enums.Env;
import com.zhengte.platform.mapper.CrRequestMapper;
import com.zhengte.platform.mapper.EnvStateMapper;
import com.zhengte.platform.mapper.RequirementMapper;
import com.zhengte.platform.mapper.UserMapper;
import com.zhengte.platform.service.ApprovalService;
import com.zhengte.platform.service.PipelineService;
import com.zhengte.platform.service.RequirementBranchSync;
import com.zhengte.platform.utils.Fmt;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ApprovalServiceImpl implements ApprovalService {

    private final CrRequestMapper crMapper;
    private final RequirementMapper requirementMapper;
    private final EnvStateMapper envStateMapper;
    private final PipelineService pipelineService;
    private final RequirementBranchSync branchSync;
    private final UserMapper userMapper;

    public ApprovalServiceImpl(CrRequestMapper crMapper,
                               RequirementMapper requirementMapper,
                               EnvStateMapper envStateMapper,
                               PipelineService pipelineService,
                               RequirementBranchSync branchSync,
                               UserMapper userMapper) {
        this.crMapper = crMapper;
        this.requirementMapper = requirementMapper;
        this.envStateMapper = envStateMapper;
        this.pipelineService = pipelineService;
        this.branchSync = branchSync;
        this.userMapper = userMapper;
    }

    @Override
    public List<Views.Approval> pending(UserAccount current) {
        Map<String, String> names = userMapper.displayNames();
        return crMapper.findPending().stream()
                .map(row -> new Views.Approval(
                        row.cr().id(),
                        row.projectId(),
                        row.reqNo(),
                        row.title(),
                        row.cr().service(),
                        row.branch(),
                        row.tipSha(),
                        row.cr().requestedBy(),
                        names.getOrDefault(row.cr().requestedBy(), row.cr().requestedBy()),
                        Fmt.time(row.cr().requestedAt()),
                        "pending",
                        current.isCrAdmin()))
                .toList();
    }

    @Override
    public Views.Result approve(long crId, String comment, UserAccount current) {
        CrRecord cr = requirePending(crId, current);
        RequirementRecord requirement = requirementMapper.findById(cr.requirementId())
                .orElseThrow(() -> ApiException.notFound("需求不存在"));
        LocalDateTime now = LocalDateTime.now();
        crMapper.decide(cr.id(), CrStatus.APPROVED.code(), current.username(), comment, now);
        String operator = cr.requestedBy() == null ? current.username() : cr.requestedBy();
        try {
            pipelineService.start(requirement.projectId(), cr.service(), Env.TEST.code(),
                    List.of(requirement.reqNo()), operator);
            return new Views.Result(true, "已通过，开始部署 test");
        } catch (ApiException e) {
            return new Views.Result(true, "已通过；自动部署未启动：" + e.getMessage());
        }
    }

    @Override
    public Views.Result reject(long crId, String comment, UserAccount current) {
        CrRecord cr = requirePending(crId, current);
        RequirementRecord requirement = requirementMapper.findById(cr.requirementId())
                .orElseThrow(() -> ApiException.notFound("需求不存在"));
        LocalDateTime now = LocalDateTime.now();
        crMapper.decide(cr.id(), CrStatus.REJECTED.code(), current.username(), comment, now);
        String tip = branchSync.syncTip(requirement.projectId(), requirement, cr.service());
        envStateMapper.markRejected(requirement.id(), cr.service(), tip, now);
        return new Views.Result(true, "已驳回");
    }

    private CrRecord requirePending(long crId, UserAccount current) {
        if (!current.isCrAdmin()) {
            throw ApiException.forbidden("仅 CR 管理员可操作审批");
        }
        CrRecord cr = crMapper.findById(crId)
                .orElseThrow(() -> ApiException.notFound("审批记录不存在"));
        if (!CrStatus.PENDING.code().equals(cr.status())) {
            throw ApiException.conflict("该审批已处理");
        }
        return cr;
    }
}
