package com.zhengte.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhengte.platform.entity.Tables;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface DeploymentReleaseTargetMapper extends BaseMapper<Tables.DeploymentReleaseTarget> {

    @Insert("""
            INSERT INTO deployment_release_target (deployment_id, release_id)
            VALUES (#{deploymentId}, #{releaseId})
            """)
    int insertTarget(@Param("deploymentId") long deploymentId, @Param("releaseId") long releaseId);

    @Select("SELECT release_id FROM deployment_release_target WHERE deployment_id = #{deploymentId}")
    Long findReleaseIdRow(@Param("deploymentId") long deploymentId);

    default Optional<Long> findReleaseId(long deploymentId) {
        return Optional.ofNullable(findReleaseIdRow(deploymentId));
    }
}
