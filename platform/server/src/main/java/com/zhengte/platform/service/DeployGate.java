package com.zhengte.platform.service;

import com.zhengte.platform.dto.Views;
import com.zhengte.platform.entity.CrRecord;
import com.zhengte.platform.entity.EnvStateRecord;
import com.zhengte.platform.entity.RequirementRecord;
import com.zhengte.platform.enums.BranchStatus;
import com.zhengte.platform.enums.CrStatus;
import com.zhengte.platform.enums.Env;
import com.zhengte.platform.enums.EnvStatus;

public final class DeployGate {

    private DeployGate() {
    }

    public static Views.Gate check(RequirementRecord requirement, String service, String envCode,
                                   EnvStateRecord target, EnvStateRecord previous, CrRecord cr,
                                   String tipSha) {
        if (!BranchStatus.CREATED.code().equals(requirement.branchStatus())) {
            return new Views.Gate(false, "需求还没有开发分支", false, false);
        }
        if (target != null && EnvStatus.of(target.status()).isRunning()) {
            return new Views.Gate(false, "当前环境有部署进行中", false, false);
        }
        Env env = Env.find(envCode)
                .orElseThrow(() -> new IllegalArgumentException("未知环境：" + envCode));
        if (env == Env.DEV) {
            return new Views.Gate(true, null, false, false);
        }
        String prevEnv = env.previous().map(Env::code).orElse(envCode);
        if (previous == null || !EnvStatus.of(previous.status()).isSuccess()) {
            return new Views.Gate(false, "需先完成 " + prevEnv + " 的 " + service + " 部署", false, false);
        }
        if (tipSha == null || previous.sha() == null || !previous.sha().equals(tipSha)) {
            return new Views.Gate(false, prevEnv + " 的 SHA 落后于 " + service + " 最新提交", false, false);
        }
        boolean testNotDeployed = target == null || !EnvStatus.of(target.status()).isSuccess();
        if (env == Env.TEST && testNotDeployed) {
            CrStatus crStatus = CrStatus.of(cr == null ? null : cr.status());
            if (crStatus.isPending()) {
                return new Views.Gate(false, "CR 审批中", false, true);
            }
            if (crStatus.isRejected()) {
                return new Views.Gate(false, "CR 已驳回，重新指定后可部署", true, false);
            }
            if (!crStatus.isApproved()) {
                return new Views.Gate(false, "test 首次部署需 CR", true, false);
            }
        }
        return new Views.Gate(true, null, false, false);
    }
}
