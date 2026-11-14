package com.zhengte.platform.integration.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhengte.platform.common.ApiException;
import com.zhengte.platform.config.GitProperties;
import com.zhengte.platform.constants.GitModes;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "platform.git.mode", havingValue = "gitlab")
public class GitLabGitService implements GitService {

    private static final Logger log = LoggerFactory.getLogger(GitLabGitService.class);

    private final GitProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public GitLabGitService(GitProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    public String mode() {
        return GitModes.GITLAB;
    }

    @Override
    public String createBranch(String projectId, String service, String branch) {
        long gitlabProject = project(projectId, service);
        JsonNode created = call("POST", "/projects/" + gitlabProject + "/repository/branches",
                Map.of("branch", branch, "ref", properties.getGitlab().getDefaultRef()));
        return shortSha(created.path("commit").path("short_id").asText(null));
    }

    @Override
    public String branchTip(String projectId, String service, String branch) {
        long gitlabProject = project(projectId, service);
        JsonNode node = call("GET", "/projects/" + gitlabProject + "/repository/branches/"
                + URLEncoder.encode(branch, StandardCharsets.UTF_8), null, true);
        if (node == null) {
            return null;
        }
        return shortSha(node.path("commit").path("short_id").asText(null));
    }

    @Override
    public void deleteBranch(String projectId, String service, String branch) {
        long gitlabProject = project(projectId, service);
        call("DELETE", "/projects/" + gitlabProject + "/repository/branches/"
                + URLEncoder.encode(branch, StandardCharsets.UTF_8), null, true);
    }

    @Override
    public MergeResult merge(String projectId, String service, String sourceBranch, String targetBranch) {
        long gitlabProject = project(projectId, service);
        String base = "/projects/" + gitlabProject + "/merge_requests";
        String source = URLEncoder.encode(sourceBranch, StandardCharsets.UTF_8);
        String target = URLEncoder.encode(targetBranch, StandardCharsets.UTF_8);
        JsonNode existing = call("GET", base + "?state=opened&source_branch=" + source
                + "&target_branch=" + target, null);
        long iid;
        if (existing != null && existing.isArray() && !existing.isEmpty()) {
            iid = existing.get(0).path("iid").asLong();
        } else {
            JsonNode compare = call("GET", "/projects/" + gitlabProject + "/repository/compare?from="
                    + target + "&to=" + source, null);
            JsonNode commits = compare == null ? null : compare.path("commits");
            if (commits != null && commits.isArray() && commits.isEmpty()) {
                return new MergeResult(false, "无需合并");
            }
            JsonNode created;
            try {
                created = call("POST", base, Map.of(
                        "source_branch", sourceBranch,
                        "target_branch", targetBranch,
                        "title", "平台集成 " + sourceBranch + " → " + targetBranch,
                        "remove_source_branch", false));
            } catch (ApiException e) {
                String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
                if (message.contains("no changes") || message.contains("already merged")
                        || message.contains("already exists") || message.contains("没有需要合并")
                        || message.contains("已是最新")) {
                    return new MergeResult(false, "无需合并");
                }
                throw e;
            }
            iid = created.path("iid").asLong();
        }

        JsonNode mr = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            mr = call("GET", base + "/" + iid, null);
            String detailed = mr.path("detailed_merge_status").asText("");
            if (!"checking".equals(detailed) && !"unchecked".equals(detailed)
                    && !"preparing".equals(detailed)) {
                break;
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApiException(HttpStatus.BAD_GATEWAY, "合并检查被中断");
            }
        }
        String mergeRequest = "Merge Request !" + iid;
        if (mr == null || mr.path("has_conflicts").asBoolean(false)
                || "cannot_be_merged".equals(mr.path("merge_status").asText(""))
                || "conflict".equals(mr.path("detailed_merge_status").asText(""))) {
            return new MergeResult(true, mergeRequest + " 存在合并冲突，请在 GitLab 解决");
        }

        HttpResponse<String> response;
        try {
            response = send("PUT", base + "/" + iid + "/merge",
                    Map.of("should_remove_source_branch", false));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "连不上 GitLab：" + e.getMessage());
        }
        int code = response.statusCode();
        if (code / 100 == 2) {
            return new MergeResult(false, mergeRequest + " 已合并");
        }
        String message = mergeMessage(response.body());
        String lower = message.toLowerCase();
        if (code == 405 || code == 406 || code == 409
                || lower.contains("conflict") || lower.contains("cannot be merged")) {
            return new MergeResult(true, mergeRequest + " 无法合并：" + message);
        }
        if (lower.contains("no changes") || lower.contains("already merged")) {
            return new MergeResult(false, mergeRequest + " 无需合并");
        }
        throw new ApiException(HttpStatus.BAD_GATEWAY, "GitLab 合并失败(" + code + ")：" + message);
    }

    @Override
    public long triggerPipeline(String projectId, String service, String branch, Map<String, String> variables) {
        long gitlabProject = project(projectId, service);
        List<Map<String, String>> vars = new ArrayList<>();
        variables.forEach((key, value) -> vars.add(Map.of("key", key, "value", value)));
        JsonNode created = call("POST", "/projects/" + gitlabProject + "/pipeline",
                Map.of("ref", branch, "variables", vars));
        return created.path("id").asLong();
    }

    @Override
    public PipelineState pipeline(String projectId, String service, long pipelineId) {
        long gitlabProject = project(projectId, service);
        JsonNode node = call("GET", "/projects/" + gitlabProject + "/pipelines/" + pipelineId, null);
        String status = node.path("status").asText("unknown");
        List<JobState> jobs = new ArrayList<>();
        JsonNode list = call("GET", "/projects/" + gitlabProject + "/pipelines/" + pipelineId
                + "/jobs?per_page=100", null);
        if (list != null && list.isArray()) {
            for (JsonNode job : list) {
                jobs.add(new JobState(job.path("name").asText(""),
                        job.path("stage").asText(""), job.path("status").asText(""),
                        job.hasNonNull("duration") ? job.path("duration").asInt() : null));
            }
        }
        return new PipelineState(status, jobs);
    }

    @Override
    public void cancelPipeline(String projectId, String service, long pipelineId) {
        long gitlabProject = project(projectId, service);
        try {
            call("POST", "/projects/" + gitlabProject + "/pipelines/" + pipelineId + "/cancel", null);
        } catch (ApiException e) {
            log.warn("取消 GitLab pipeline {} 失败：{}", pipelineId, e.getMessage());
        }
    }

    private long project(String projectId, String service) {
        Long id = properties.getGitlab().repoId(projectId, service);
        if (id == null) {
            throw ApiException.badRequest("项目未配置服务仓库：" + projectId + "/" + service);
        }
        return id;
    }

    private JsonNode call(String method, String path, Object body) {
        return call(method, path, body, false);
    }

    private JsonNode call(String method, String path, Object body, boolean allowNotFound) {
        try {
            HttpResponse<String> response = send(method, path, body);
            int code = response.statusCode();
            if (code == 404 && allowNotFound) {
                return null;
            }
            if (code / 100 != 2) {
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "GitLab 请求失败(" + code + ")：" + describe(response.body()));
            }
            String text = response.body();
            return text == null || text.isBlank() ? null : mapper.readTree(text);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "连不上 GitLab：" + e.getMessage());
        }
    }

    private HttpResponse<String> send(String method, String path, Object body) throws Exception {
        String url = trimSlash(properties.getGitlab().getUrl()) + "/api/v4" + path;
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("PRIVATE-TOKEN", properties.getGitlab().getToken())
                .header("Accept", "application/json");
        if (body != null) {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String mergeMessage(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            String message = mapper.readTree(body).path("message").asText("");
            if (!message.isBlank()) {
                return message;
            }
        } catch (Exception ignored) {
            // 非 JSON 响应按原文返回
        }
        return body.length() > 240 ? body.substring(0, 240) : body;
    }

    private String describe(String body) {
        if (body == null) {
            return "";
        }
        String text = body.length() > 240 ? body.substring(0, 240) : body;
        if (text.contains("already exists")) {
            return "分支已存在";
        }
        return text;
    }

    private String shortSha(String sha) {
        if (sha == null || sha.isBlank()) {
            return null;
        }
        return sha.length() > 7 ? sha.substring(0, 7) : sha;
    }

    private String trimSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
