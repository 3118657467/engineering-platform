package com.zhengte.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhengte.platform.entity.EnvStateRecord;
import com.zhengte.platform.entity.Tables;
import com.zhengte.platform.converter.RecordMapper;
import com.zhengte.platform.enums.Env;
import com.zhengte.platform.enums.EnvStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface EnvStateMapper extends BaseMapper<Tables.EnvState> {

    @Select("""
            SELECT e.* FROM env_states e
            JOIN requirements r ON r.id = e.requirement_id
            WHERE r.project_id = #{projectId}
            ORDER BY e.requirement_id, e.service, e.env
            """)
    List<Tables.EnvState> findAllByProjectRows(String projectId);

    @Insert("""
            INSERT INTO env_states
                (requirement_id, service, env, status, sha, deployed_at, operator, updated_at, error_message)
            VALUES (#{requirementId}, #{service}, #{env}, #{status}, #{sha},
                    #{deployedAt,jdbcType=TIMESTAMP}, #{operator}, #{now}, #{errorMessage})
            ON DUPLICATE KEY UPDATE status = #{status}, sha = #{sha},
                deployed_at = #{deployedAt,jdbcType=TIMESTAMP}, operator = #{operator},
                updated_at = #{now}, error_message = #{errorMessage}
            """)
    int upsert(@Param("requirementId") long requirementId, @Param("service") String service,
               @Param("env") String env, @Param("status") String status, @Param("sha") String sha,
               @Param("deployedAt") LocalDateTime deployedAt, @Param("operator") String operator,
               @Param("now") LocalDateTime now, @Param("errorMessage") String errorMessage);

    @Update("""
            UPDATE env_states SET status = 'READY', sha = NULL, deployed_at = NULL,
                operator = NULL, error_message = NULL, updated_at = NOW()
            WHERE status = 'RUNNING'
            """)
    int reconcileRunning();

    default List<EnvStateRecord> findAllByProject(String projectId) {
        return findAllByProjectRows(projectId).stream().map(RecordMapper.INSTANCE::toEnvState).toList();
    }

    default Optional<EnvStateRecord> find(long requirementId, String service, String env) {
        return Optional.ofNullable(selectOne(Wrappers.<Tables.EnvState>lambdaQuery()
                        .eq(Tables.EnvState::getRequirementId, requirementId)
                        .eq(Tables.EnvState::getService, service)
                        .eq(Tables.EnvState::getEnv, env)))
                .map(RecordMapper.INSTANCE::toEnvState);
    }

    default void saveState(long requirementId, String service, String env, String status, String sha,
                           String operator, LocalDateTime now, String errorMessage) {
        upsert(requirementId, service, env, status, sha, sha == null ? null : now,
                operator, now, errorMessage);
    }

    default void markWaitCr(long requirementId, String service, String sha, String operator,
                            LocalDateTime now) {
        EnvStateRecord current = find(requirementId, service, Env.TEST.code()).orElse(null);
        if (current != null && EnvStatus.SUCCESS.code().equals(current.status())) {
            return;
        }
        saveState(requirementId, service, Env.TEST.code(), EnvStatus.WAIT_CR.code(), sha,
                operator, now, null);
    }

    default void markRejected(long requirementId, String service, String sha, LocalDateTime now) {
        EnvStateRecord current = find(requirementId, service, Env.TEST.code()).orElse(null);
        if (current != null && EnvStatus.SUCCESS.code().equals(current.status())) {
            return;
        }
        saveState(requirementId, service, Env.TEST.code(), EnvStatus.REJECTED.code(), sha,
                null, now, null);
    }

    default void markReady(long requirementId, String service, String env, LocalDateTime now) {
        saveState(requirementId, service, env, EnvStatus.READY.code(), null, null, now, null);
    }
}
