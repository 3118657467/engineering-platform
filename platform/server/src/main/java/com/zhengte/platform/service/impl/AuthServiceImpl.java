package com.zhengte.platform.service.impl;

import com.zhengte.platform.service.AuthService;
import com.zhengte.platform.common.ApiException;
import com.zhengte.platform.dto.Views;
import com.zhengte.platform.entity.UserAccount;
import com.zhengte.platform.mapper.SessionMapper;
import com.zhengte.platform.mapper.UserMapper;
import com.zhengte.platform.utils.Fmt;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper users;
    private final SessionMapper sessions;
    private final int sessionDays;

    public AuthServiceImpl(UserMapper users, SessionMapper sessions,
                       @Value("${platform.pipeline.session-days:7}") int sessionDays) {
        this.users = users;
        this.sessions = sessions;
        this.sessionDays = sessionDays;
    }

    @Override
    public Views.Login login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw ApiException.badRequest("请输入账号和密码");
        }
        UserAccount user = users.findByUsername(username.trim())
                .orElseThrow(() -> ApiException.unauthorized("账号或密码错误"));
        String hash = Fmt.sha256Hex(username.trim() + ":" + password);
        if (!hash.equalsIgnoreCase(user.passwordHash())) {
            throw ApiException.unauthorized("账号或密码错误");
        }
        sessions.deleteExpired();
        String token = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        sessions.create(token, user.username(), now, now.plusDays(sessionDays));
        return new Views.Login(token, toView(user));
    }

    @Override
    public UserAccount resolve(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String username = sessions.findUsername(token);
        return username == null ? null : users.findByUsername(username).orElse(null);
    }

    @Override
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessions.deleteToken(token);
        }
    }

    @Override
    public Views.User toView(UserAccount user) {
        return new Views.User(user.username(), user.displayName(), user.role());
    }
}
