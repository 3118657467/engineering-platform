package com.zhengte.platform.controller;

import com.zhengte.platform.constants.PlatformDefaults;
import com.zhengte.platform.config.AuthInterceptor;
import com.zhengte.platform.dto.Views;
import com.zhengte.platform.service.DeployService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deploy")
public class DeployController {

    private final DeployService deployService;

    public DeployController(DeployService deployService) {
        this.deployService = deployService;
    }

    public record StartReq(String projectId, String service, String env, List<String> ids) {
    }

    public record TargetReq(String projectId, String service, String env, String id) {
    }

    public record RollbackReq(String projectId, String service, String env, Long releaseId) {
    }

    public record ScaleReq(String projectId, String service, String env, Integer replicas) {
    }

    public record PodReq(String projectId, String service, String env, String name) {
    }

    @GetMapping("/board")
    public Views.Board board(@RequestParam(defaultValue = PlatformDefaults.PROJECT) String projectId,
                             @RequestParam(defaultValue = PlatformDefaults.SERVICE) String service,
                             @RequestParam(defaultValue = PlatformDefaults.ENV) String env) {
        return deployService.board(projectId, service, env);
    }

    @PostMapping("/start")
    public Views.StartResult start(@RequestBody StartReq req, HttpServletRequest request) {
        return deployService.start(req.projectId(), req.service(), req.env(), req.ids(),
                AuthInterceptor.current(request).username());
    }

    @PostMapping("/{batchId}/abort")
    public Views.Result abort(@PathVariable long batchId) {
        deployService.abort(batchId);
        return new Views.Result(true, "部署已终止");
    }

    @PostMapping("/conflict/resolve")
    public Views.Result resolveConflict(@RequestBody TargetReq req) {
        return deployService.resolveConflict(req.projectId(), req.service(), req.env(), req.id());
    }

    @PostMapping("/rollback")
    public Views.StartResult rollback(@RequestBody RollbackReq req, HttpServletRequest request) {
        return deployService.rollback(req.projectId(), req.service(), req.env(), req.releaseId(),
                AuthInterceptor.current(request).username());
    }

    @GetMapping("/pods/logs")
    public Views.Content logs(@RequestParam String name,
                              @RequestParam(defaultValue = PlatformDefaults.PROJECT) String projectId,
                              @RequestParam(defaultValue = PlatformDefaults.SERVICE) String service,
                              @RequestParam(defaultValue = PlatformDefaults.ENV) String env) {
        return deployService.logs(name, projectId, service, env);
    }

    @GetMapping("/pods/yaml")
    public Views.Content yaml(@RequestParam String name,
                              @RequestParam(defaultValue = PlatformDefaults.PROJECT) String projectId,
                              @RequestParam(defaultValue = PlatformDefaults.SERVICE) String service,
                              @RequestParam(defaultValue = PlatformDefaults.ENV) String env) {
        return deployService.yaml(name, projectId, service, env);
    }

    @GetMapping("/pods/events")
    public List<Views.PodEvent> events(@RequestParam String name,
                                       @RequestParam(defaultValue = PlatformDefaults.PROJECT) String projectId,
                                       @RequestParam(defaultValue = PlatformDefaults.SERVICE) String service,
                                       @RequestParam(defaultValue = PlatformDefaults.ENV) String env) {
        return deployService.events(name, projectId, service, env);
    }

    @GetMapping("/scale")
    public Views.Scale scaleInfo(@RequestParam(defaultValue = PlatformDefaults.PROJECT) String projectId,
                                 @RequestParam(defaultValue = PlatformDefaults.SERVICE) String service,
                                 @RequestParam(defaultValue = PlatformDefaults.ENV) String env) {
        return deployService.scaleInfo(projectId, service, env);
    }

    @PostMapping("/scale")
    public Views.Scale scale(@RequestBody ScaleReq req, HttpServletRequest request) {
        return deployService.scale(req.projectId(), req.service(), req.env(), req.replicas(),
                AuthInterceptor.current(request).username());
    }

    @PostMapping("/pods/restart")
    public Views.Result restart(@RequestBody PodReq req, HttpServletRequest request) {
        return deployService.restartPod(req.projectId(), req.service(), req.env(), req.name(),
                AuthInterceptor.current(request).username());
    }
}
