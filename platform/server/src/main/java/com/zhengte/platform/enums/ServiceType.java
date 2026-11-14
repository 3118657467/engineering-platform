package com.zhengte.platform.enums;

import java.util.List;
import java.util.Optional;

public enum ServiceType implements CodedEnum {

    API("api", "后端 api", "server"),
    WEB("web", "前端 web", "web");

    private final String code;
    private final String label;
    private final String contextDir;

    ServiceType(String code, String label, String contextDir) {
        this.code = code;
        this.label = label;
        this.contextDir = contextDir;
    }

    @Override
    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public String contextDir() {
        return contextDir;
    }

    public static Optional<ServiceType> find(String code) {
        return Enums.find(ServiceType.class, code);
    }

    public static List<String> codes() {
        return Enums.codes(ServiceType.class);
    }
}
