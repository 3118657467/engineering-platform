package com.zhengte.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhengte.platform.entity.Tables;
import com.zhengte.platform.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Mapper
public interface UserMapper extends BaseMapper<Tables.User> {

    default Optional<UserAccount> findByUsername(String username) {
        return Optional.ofNullable(selectById(username)).map(UserMapper::toAccount);
    }

    default List<UserAccount> findCrAdmins() {
        return selectList(Wrappers.<Tables.User>lambdaQuery()
                        .eq(Tables.User::getRole, "CR_ADMIN")
                        .orderByAsc(Tables.User::getUsername))
                .stream().map(UserMapper::toAccount).toList();
    }

    default Map<String, String> displayNames() {
        return selectList(null).stream()
                .collect(Collectors.toMap(Tables.User::getUsername, Tables.User::getDisplayName));
    }

    static UserAccount toAccount(Tables.User user) {
        return new UserAccount(user.getUsername(), user.getDisplayName(),
                user.getRole(), user.getPasswordHash());
    }
}
