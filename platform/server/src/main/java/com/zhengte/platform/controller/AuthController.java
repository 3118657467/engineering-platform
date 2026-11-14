package com.zhengte.platform.controller;

import com.zhengte.platform.config.AuthInterceptor;
import com.zhengte.platform.dto.Views;
import com.zhengte.platform.mapper.UserMapper;
import com.zhengte.platform.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserMapper userMapper;

    public AuthController(AuthService authService, UserMapper userMapper) {
        this.authService = authService;
        this.userMapper = userMapper;
    }

    public record LoginReq(String username, String password) {
    }

    @PostMapping("/login")
    public Views.Login login(@RequestBody LoginReq req) {
        return authService.login(req.username(), req.password());
    }

    @PostMapping("/logout")
    public Views.Result logout(HttpServletRequest request) {
        authService.logout(AuthInterceptor.tokenOf(request));
        return new Views.Result(true, "已退出登录");
    }

    @GetMapping("/me")
    public Views.User me(HttpServletRequest request) {
        return authService.toView(AuthInterceptor.current(request));
    }

    @GetMapping("/approvers")
    public List<Views.Option> approvers() {
        return userMapper.findCrAdmins().stream()
                .map(u -> new Views.Option(u.username(), u.displayName()))
                .toList();
    }
}
