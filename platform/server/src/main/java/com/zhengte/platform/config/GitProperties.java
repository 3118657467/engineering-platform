package com.zhengte.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "platform.git")
public class GitProperties {

    private String mode = "mock";
    private Gitlab gitlab = new Gitlab();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Gitlab getGitlab() {
        return gitlab;
    }

    public void setGitlab(Gitlab gitlab) {
        this.gitlab = gitlab;
    }

    public static class Gitlab {

        private String url = "http://localhost:8080";
        private String token = "";
        private String defaultRef = "main";
        private Map<String, Map<String, Long>> projects = new HashMap<>();

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getDefaultRef() {
            return defaultRef;
        }

        public void setDefaultRef(String defaultRef) {
            this.defaultRef = defaultRef;
        }

        public Map<String, Map<String, Long>> getProjects() {
            return projects;
        }

        public void setProjects(Map<String, Map<String, Long>> projects) {
            this.projects = projects;
        }

        public Long repoId(String projectId, String service) {
            Map<String, Long> repos = projects.get(projectId);
            return repos == null ? null : repos.get(service);
        }
    }
}
