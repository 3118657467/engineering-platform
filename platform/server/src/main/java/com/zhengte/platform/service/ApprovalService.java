package com.zhengte.platform.service;

import com.zhengte.platform.dto.Views;
import com.zhengte.platform.entity.UserAccount;
import java.util.List;

public interface ApprovalService {

    List<Views.Approval> pending(UserAccount current);

    Views.Result approve(long crId, String comment, UserAccount current);

    Views.Result reject(long crId, String comment, UserAccount current);
}
