package com.zhengte.platform.integration.k8s;

import com.zhengte.platform.common.ApiException;
import com.zhengte.platform.enums.Env;

public record RuntimeTarget(String projectId, String service, String env,
                            String namespace, String deployment, String appLabel) {

    public static RuntimeTarget of(String projectId, String service, String env, String runtimeApp) {
        if (Env.find(env).isEmpty()) {
            throw ApiException.badRequest("未知环境：" + env);
        }
        String app = runtimeApp == null || runtimeApp.isBlank() ? projectId : runtimeApp;
        return new RuntimeTarget(projectId, service, env, app + "-" + env, service, service);
    }
}
