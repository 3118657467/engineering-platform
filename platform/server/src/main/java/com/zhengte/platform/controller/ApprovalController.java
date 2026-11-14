package com.zhengte.platform.controller;

import com.zhengte.platform.config.AuthInterceptor;
import com.zhengte.platform.dto.Views;
import com.zhengte.platform.service.ApprovalService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    public record DecisionReq(String comment) {
    }

    @GetMapping
    public List<Views.Approval> list(HttpServletRequest request) {
        return approvalService.pending(AuthInterceptor.current(request));
    }

    @PostMapping("/{crId}/approve")
    public Views.Result approve(@PathVariable long crId, @RequestBody(required = false) DecisionReq req,
                               HttpServletRequest request) {
        return approvalService.approve(crId, req == null ? null : req.comment(),
                AuthInterceptor.current(request));
    }

    @PostMapping("/{crId}/reject")
    public Views.Result reject(@PathVariable long crId, @RequestBody(required = false) DecisionReq req,
                               HttpServletRequest request) {
        return approvalService.reject(crId, req == null ? null : req.comment(),
                AuthInterceptor.current(request));
    }
}
