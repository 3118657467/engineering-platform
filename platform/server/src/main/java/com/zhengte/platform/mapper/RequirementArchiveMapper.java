package com.zhengte.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhengte.platform.entity.Tables;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Mapper
public interface RequirementArchiveMapper extends BaseMapper<Tables.RequirementArchive> {

    @Insert("""
            INSERT IGNORE INTO prod_requirement_archives
                (requirement_id, project_id, service, release_id, archived_at)
            VALUES (#{requirementId}, #{projectId}, #{service}, #{releaseId,jdbcType=BIGINT}, #{archivedAt})
            """)
    int archive(@Param("requirementId") long requirementId, @Param("projectId") String projectId,
                @Param("service") String service, @Param("releaseId") Long releaseId,
                @Param("archivedAt") LocalDateTime archivedAt);

    @Select("SELECT requirement_id FROM prod_requirement_archives WHERE project_id = #{projectId}")
    List<Long> findIdsByProject(@Param("projectId") String projectId);

    @Select("SELECT * FROM prod_requirement_archives WHERE requirement_id = #{requirementId}")
    Tables.RequirementArchive findRow(@Param("requirementId") long requirementId);

    default Set<Long> findArchivedIds(String projectId) {
        return new HashSet<>(findIdsByProject(projectId));
    }

    default Optional<Tables.RequirementArchive> findByRequirementId(long requirementId) {
        return Optional.ofNullable(findRow(requirementId));
    }
}
