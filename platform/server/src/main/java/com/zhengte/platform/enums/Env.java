package com.zhengte.platform.enums;

import java.util.List;
import java.util.Optional;

public enum Env implements CodedEnum {

    DEV("dev", "开发", 1, 1),
    TEST("test", "测试", 2, 1),
    UAT("uat", "验收", 3, 1),
    PROD("prod", "生产", 4, 2);

    private final String code;
    private final String label;
    private final int order;
    private final int minReplicas;

    Env(String code, String label, int order, int minReplicas) {
        this.code = code;
        this.label = label;
        this.order = order;
        this.minReplicas = minReplicas;
    }

    @Override
    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public int order() {
        return order;
    }

    public int minReplicas() {
        return minReplicas;
    }

    public String branch() {
        return this == PROD ? "main" : code;
    }

    public boolean isProd() {
        return this == PROD;
    }

    public Optional<Env> previous() {
        return order <= 1 ? Optional.empty() : Optional.of(values()[order - 2]);
    }

    public static Optional<Env> find(String code) {
        return Enums.find(Env.class, code);
    }

    public static List<String> codes() {
        return Enums.codes(Env.class);
    }
}
