package com.zhengte.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhengte.platform.entity.Tables;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface DeployLockMapper extends BaseMapper<Tables.DeployLock> {

    default void createLock(String projectId, String service, String env, long batchId,
                            LocalDateTime now) {
        Tables.DeployLock lock = new Tables.DeployLock();
        lock.setProjectId(projectId);
        lock.setService(service);
        lock.setEnv(env);
        lock.setBatchId(batchId);
        lock.setAcquiredAt(now);
        insert(lock);
    }

    default boolean lockExists(String projectId, String service, String env) {
        return selectCount(Wrappers.<Tables.DeployLock>lambdaQuery()
                .eq(Tables.DeployLock::getProjectId, projectId)
                .eq(Tables.DeployLock::getService, service)
                .eq(Tables.DeployLock::getEnv, env)) > 0;
    }

    default void deleteLock(String projectId, String service, String env) {
        delete(Wrappers.<Tables.DeployLock>lambdaQuery()
                .eq(Tables.DeployLock::getProjectId, projectId)
                .eq(Tables.DeployLock::getService, service)
                .eq(Tables.DeployLock::getEnv, env));
    }

    default void deleteLockByBatch(long batchId) {
        delete(Wrappers.<Tables.DeployLock>lambdaQuery().eq(Tables.DeployLock::getBatchId, batchId));
    }
}
