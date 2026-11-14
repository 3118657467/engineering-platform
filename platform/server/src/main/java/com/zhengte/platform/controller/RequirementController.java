package com.zhengte.platform.controller;

import com.zhengte.platform.constants.PlatformDefaults;
import com.zhengte.platform.config.AuthInterceptor;
import com.zhengte.platform.dto.Views;
import com.zhengte.platform.service.RequirementService;
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
@RequestMapping("/api/requirements")
public class RequirementController {

    private final RequirementService requirementService;

    public RequirementController(RequirementService requirementService) {
        this.requirementService = requirementService;
    }

    public record CreateReq(String projectId, String title, String description, List<String> related) {
    }

    public record BranchReq(String suffix) {
    }

    public record BindBranchReq(String branch) {
    }

    public record CrReq(String service, String approver, String comment) {
    }

    @GetMapping
    public List<Views.Requirement> list(@RequestParam(defaultValue = PlatformDefaults.PROJECT) String projectId,
                                        HttpServletRequest request) {
        return requirementService.listVisible(projectId, AuthInterceptor.current(request).username());
    }

    @GetMapping("/{reqNo}")
    public Views.Requirement get(@PathVariable String reqNo,
                                 @RequestParam(defaultValue = PlatformDefaults.PROJECT) String projectId) {
        return requirementService.get(projectId, reqNo);
    }

    @PostMapping
    public Views.Requirement create(@RequestBody CreateReq req, HttpServletRequest request) {
        String projectId = req.projectId() == null || req.projectId().isBlank()
                ? PlatformDefaults.PROJECT : req.projectId();
        return requirementService.create(projectId, req.title(), req.description(), req.related(),
                AuthInterceptor.current(request).username());
    }

    @PostMapping("/{reqNo}/branch")
    public Views.Requirement createBranch(@PathVariable String reqNo, @RequestBody BranchReq req,
                                          @RequestParam(defaultValue = PlatformDefaults.PROJECT) String projectId,
                                          HttpServletRequest request) {
        return requirementService.createBranch(projectId, reqNo, req.suffix(),
                AuthInterceptor.current(request).username());
    }

    @PostMapping("/{reqNo}/branch/bind")
    public Views.Requirement bindBranch(@PathVariable String reqNo, @RequestBody BindBranchReq req,
                                        @RequestParam(defaultValue = PlatformDefaults.PROJECT) String projectId,
                                        HttpServletRequest request) {
        return requirementService.bindBranch(projectId, reqNo, req.branch(),
                AuthInterceptor.current(request).username());
    }

    @PostMapping("/{reqNo}/cr")
    public Views.Requirement createCr(@PathVariable String reqNo, @RequestBody CrReq req,
                                      @RequestParam(defaultValue = PlatformDefaults.PROJECT) String projectId,
                                      HttpServletRequest request) {
        return requirementService.createCr(projectId, reqNo, req.service(), req.approver(),
                req.comment(), AuthInterceptor.current(request).username());
    }
}
