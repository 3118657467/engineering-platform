package com.zhengte.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhengte.platform.entity.Tables;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DeploymentStepMapper extends BaseMapper<Tables.DeploymentStep> {

    @Select("""
            SELECT s.* FROM deployment_steps s
            JOIN deployments d ON d.id = s.deployment_id
            WHERE d.batch_id = #{batchId}
            ORDER BY s.deployment_id, s.seq
            """)
    List<Tables.DeploymentStep> findByBatch(@Param("batchId") long batchId);

    @Update("UPDATE deployment_steps SET status = #{status}, duration_ms = #{durationMs,jdbcType=INTEGER} WHERE id = #{id}")
    int updateStatus(@Param("id") long id, @Param("status") String status,
                     @Param("durationMs") Integer durationMs);

    @Update("UPDATE deployment_steps SET status = 'FAILED' WHERE status = 'RUNNING'")
    int reconcileRunning();

    default void createStep(long deploymentId, int seq, String name) {
        Tables.DeploymentStep step = new Tables.DeploymentStep();
        step.setDeploymentId(deploymentId);
        step.setSeq(seq);
        step.setName(name);
        step.setStatus("PENDING");
        insert(step);
    }

    default List<Tables.DeploymentStep> findSteps(long deploymentId) {
        return selectList(Wrappers.<Tables.DeploymentStep>lambdaQuery()
                .eq(Tables.DeploymentStep::getDeploymentId, deploymentId)
                .orderByAsc(Tables.DeploymentStep::getSeq));
    }
}
