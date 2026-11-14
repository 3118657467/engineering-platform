package com.zhengte.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhengte.platform.entity.ProjectRecord;
import com.zhengte.platform.entity.Tables;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProjectMapper extends BaseMapper<Tables.Project> {

    default List<ProjectRecord> findAll() {
        return selectList(Wrappers.<Tables.Project>lambdaQuery()
                        .orderByAsc(Tables.Project::getCreatedAt))
                .stream()
                .map(p -> new ProjectRecord(p.getId(), p.getName(), p.getService(),
                        p.getRuntimeApp(), p.getRepoUrl(), p.getCreatedAt()))
                .toList();
    }

    default String findService(String id) {
        Tables.Project project = id == null ? null : selectById(id);
        return project == null ? null : project.getService();
    }

    default String findRuntimeApp(String id) {
        Tables.Project project = id == null ? null : selectById(id);
        String runtimeApp = project == null ? null : project.getRuntimeApp();
        return runtimeApp == null || runtimeApp.isBlank() ? id : runtimeApp;
    }

    default boolean exists(String id) {
        return id != null && selectById(id) != null;
    }
}
