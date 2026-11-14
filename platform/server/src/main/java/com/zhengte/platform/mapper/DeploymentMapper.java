package com.zhengte.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhengte.platform.entity.Tables;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface DeploymentMapper extends BaseMapper<Tables.Deployment> {

    @Select("""
            SELECT * FROM deployments
            WHERE batch_id = #{batchId} AND status IN ('RUNNING', 'PENDING')
            ORDER BY FIELD(status, 'RUNNING', 'PENDING'), id
            LIMIT 1
            """)
    Tables.Deployment findOpenRow(@Param("batchId") long batchId);

    @Update("""
            UPDATE deployments
            SET status = #{status}, started_at = COALESCE(#{startedAt,jdbcType=TIMESTAMP}, started_at),
                finished_at = #{finishedAt,jdbcType=TIMESTAMP}, error_message = #{errorMessage}
            WHERE id = #{id}
            """)
    int updateState(@Param("id") long id, @Param("status") String status,
                    @Param("startedAt") LocalDateTime startedAt,
                    @Param("finishedAt") LocalDateTime finishedAt,
                    @Param("errorMessage") String errorMessage);

    @Update("""
            UPDATE deployments SET status = 'CANCELLED', finished_at = #{now}, error_message = '部署已终止'
            WHERE batch_id = #{batchId} AND status = 'PENDING'
            """)
    int cancelPending(@Param("batchId") long batchId, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE deployments SET status = 'CANCELLED', finished_at = NOW(),
                error_message = '服务重启，部署中断'
            WHERE status IN ('RUNNING', 'PENDING')
            """)
    int reconcileRunning();

    default long createDeployment(long batchId, long requirementId, String service, String env,
                                  String branchName, String sha, String operator, LocalDateTime now) {
        Tables.Deployment deployment = new Tables.Deployment();
        deployment.setBatchId(batchId);
        deployment.setRequirementId(requirementId);
        deployment.setService(service);
        deployment.setEnv(env);
        deployment.setBranchName(branchName);
        deployment.setSha(sha);
        deployment.setStatus("PENDING");
        deployment.setOperator(operator);
        deployment.setStartedAt(now);
        insert(deployment);
        return deployment.getId();
    }

    default List<Tables.Deployment> findDeployments(long batchId) {
        return selectList(Wrappers.<Tables.Deployment>lambdaQuery()
                .eq(Tables.Deployment::getBatchId, batchId)
                .orderByAsc(Tables.Deployment::getId));
    }

    default Optional<Tables.Deployment> findDeployment(long deploymentId) {
        return Optional.ofNullable(selectById(deploymentId));
    }

    default Optional<Tables.Deployment> findOpenDeployment(long batchId) {
        return Optional.ofNullable(findOpenRow(batchId));
    }
}
