package com.zhengte.platform.service;

import com.zhengte.platform.dto.Views;
import java.util.List;

public interface RequirementService {

    List<Views.Requirement> listVisible(String projectId, String username);

    Views.Requirement get(String projectId, String reqNo);

    Views.Requirement create(String projectId, String title, String description,
                             List<String> related, String owner);

    Views.Requirement createBranch(String projectId, String reqNo, String suffix, String operator);

    Views.Requirement bindBranch(String projectId, String reqNo, String branchName, String operator);

    Views.Requirement createCr(String projectId, String reqNo, String service, String approver,
                               String comment, String operator);
}
