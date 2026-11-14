package com.zhengte.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhengte.platform.entity.Tables;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface SessionMapper extends BaseMapper<Tables.Session> {

    @Delete("DELETE FROM sessions WHERE expires_at <= NOW()")
    int deleteExpired();

    default void create(String token, String username, LocalDateTime now, LocalDateTime expiresAt) {
        Tables.Session session = new Tables.Session();
        session.setToken(token);
        session.setUsername(username);
        session.setExpiresAt(expiresAt);
        session.setCreatedAt(now);
        insert(session);
    }

    default String findUsername(String token) {
        Tables.Session session = selectOne(Wrappers.<Tables.Session>lambdaQuery()
                .eq(Tables.Session::getToken, token)
                .gt(Tables.Session::getExpiresAt, LocalDateTime.now()));
        return session == null ? null : session.getUsername();
    }

    default void deleteToken(String token) {
        deleteById(token);
    }
}
