package com.zhengte.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhengte.platform.constants.HttpHeaders;
import com.zhengte.platform.entity.UserAccount;
import com.zhengte.platform.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER = "platform.currentUser";

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(AuthService authService, ObjectMapper objectMapper) {
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        UserAccount user = authService.resolve(tokenOf(request));
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(Map.of("message", "登录已过期，请重新登录")));
            return false;
        }
        request.setAttribute(ATTR_USER, user);
        return true;
    }

    public static String tokenOf(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(HttpHeaders.BEARER_PREFIX)) {
            return header.substring(HttpHeaders.BEARER_PREFIX.length()).trim();
        }
        String token = request.getHeader(HttpHeaders.TOKEN);
        return token == null ? null : token.trim();
    }

    public static UserAccount current(HttpServletRequest request) {
        return (UserAccount) request.getAttribute(ATTR_USER);
    }
}
