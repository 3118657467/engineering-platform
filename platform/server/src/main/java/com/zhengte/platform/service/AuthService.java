package com.zhengte.platform.service;

import com.zhengte.platform.dto.Views;
import com.zhengte.platform.entity.UserAccount;

public interface AuthService {

    Views.Login login(String username, String password);

    UserAccount resolve(String token);

    void logout(String token);

    Views.User toView(UserAccount user);
}
