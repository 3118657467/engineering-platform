package com.zhengte.platform.constants;

import java.util.List;

public final class DeploySteps {

    public record Definition(int seq, String name) {
    }

    public static final List<Definition> ALL = List.of(
            new Definition(1, "拉取代码"),
            new Definition(2, "编译"),
            new Definition(3, "镜像构建"),
            new Definition(4, "部署到 k3s"),
            new Definition(5, "健康检查"));

    private DeploySteps() {
    }

    public static int size() {
        return ALL.size();
    }
}
