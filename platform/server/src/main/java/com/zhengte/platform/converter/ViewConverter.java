package com.zhengte.platform.converter;

import com.zhengte.platform.dto.Views;
import com.zhengte.platform.entity.CrRecord;
import com.zhengte.platform.entity.EnvStateRecord;
import com.zhengte.platform.entity.RequirementRecord;
import com.zhengte.platform.enums.BranchStatus;
import com.zhengte.platform.enums.CrStatus;
import com.zhengte.platform.enums.Env;
import com.zhengte.platform.enums.EnvStatus;
import com.zhengte.platform.utils.Fmt;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ViewConverter {

    public Views.Cr cr(CrRecord cr, String service, Map<String, String> names) {
        if (cr == null) {
            return new Views.Cr(CrStatus.NONE.code().toLowerCase(), service, null, null, null,
                    null, null, null);
        }
        LocalDateTime time = cr.decidedAt() != null ? cr.decidedAt() : cr.requestedAt();
        return new Views.Cr(CrStatus.of(cr.status()).code().toLowerCase(), cr.service(),
                cr.approver(), name(names, cr.approver()), cr.requestedBy(),
                name(names, cr.requestedBy()), Fmt.time(time), cr.comment());
    }

    public Views.EnvState envState(EnvStateRecord state, Map<String, String> names) {
        if (state == null) {
            return new Views.EnvState(EnvStatus.LOCKED.code().toLowerCase(), null, null, null,
                    null, null);
        }
        return new Views.EnvState(EnvStatus.of(state.status()).code().toLowerCase(), state.sha(),
                state.operator(), name(names, state.operator()), Fmt.time(state.deployedAt()),
                state.errorMessage());
    }

    public Views.Label requirementLabel(RequirementRecord requirement,
                                        Map<String, Map<String, EnvStateRecord>> services,
                                        CrRecord cr) {
        if (!BranchStatus.CREATED.code().equals(requirement.branchStatus())) {
            return new Views.Label("待建分支", "muted");
        }
        if (cr != null && CrStatus.of(cr.status()).isPending()) {
            return new Views.Label("待 CR", "warning");
        }
        boolean running = services.values().stream().flatMap(envs -> envs.values().stream())
                .anyMatch(state -> EnvStatus.of(state.status()).isRunning());
        if (running) {
            return new Views.Label("部署中", "running");
        }
        boolean failed = services.values().stream().flatMap(envs -> envs.values().stream())
                .anyMatch(state -> EnvStatus.of(state.status()).isFailed());
        if (failed) {
            return new Views.Label("有失败", "danger");
        }
        boolean online = !services.isEmpty() && services.keySet().stream()
                .allMatch(service -> isSuccess(services, service, Env.PROD.code()));
        if (online) {
            return new Views.Label("已上线", "success");
        }
        return new Views.Label("进行中", "info");
    }

    private boolean isSuccess(Map<String, Map<String, EnvStateRecord>> services, String service,
                              String env) {
        EnvStateRecord state = services.getOrDefault(service, Map.of()).get(env);
        return state != null && EnvStatus.of(state.status()).isSuccess();
    }

    private String name(Map<String, String> names, String username) {
        return username == null ? null : names.getOrDefault(username, username);
    }
}
