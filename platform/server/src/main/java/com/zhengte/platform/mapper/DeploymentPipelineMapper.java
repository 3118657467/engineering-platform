package com.zhengte.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhengte.platform.entity.Tables;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Optional;

@Mapper
public interface DeploymentPipelineMapper extends BaseMapper<Tables.DeploymentPipeline> {

    @Insert("""
            INSERT INTO deployment_pipelines (deployment_id, pipeline_id, status, updated_at)
            VALUES (#{deploymentId}, #{pipelineId}, #{status}, #{now})
            ON DUPLICATE KEY UPDATE status = #{status}, updated_at = #{now}
            """)
    int upsert(@Param("deploymentId") long deploymentId, @Param("pipelineId") long pipelineId,
               @Param("status") String status, @Param("now") LocalDateTime now);

    default Optional<Long> findPipelineId(long deploymentId) {
        Tables.DeploymentPipeline row = selectById(deploymentId);
        return row == null ? Optional.empty() : Optional.ofNullable(row.getPipelineId());
    }
}
