package com.zhengte.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhengte.platform.entity.RequirementRecord;
import com.zhengte.platform.entity.Tables;
import com.zhengte.platform.converter.RecordMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface RequirementMapper extends BaseMapper<Tables.Requirement> {

    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(req_no, 5) AS UNSIGNED)), 0) FROM requirements WHERE project_id = #{projectId}")
    Integer maxReqSeq(@Param("projectId") String projectId);

    @Select("""
            SELECT r.* FROM requirements r
            WHERE r.project_id = #{projectId}
              AND (r.owner = #{username} OR EXISTS (
                    SELECT 1 FROM requirement_related rr
                    WHERE rr.requirement_id = r.id AND rr.username = #{username}))
            ORDER BY r.req_no
            """)
    List<Tables.Requirement> findVisibleRows(@Param("projectId") String projectId,
                                             @Param("username") String username);

    @Update("""
            UPDATE requirements
            SET branch_name = #{branchName}, branch_status = 'CREATED',
                branch_created_at = COALESCE(branch_created_at, #{now}), updated_at = #{now}
            WHERE id = #{id}
            """)
    int updateBranch(@Param("id") long id, @Param("branchName") String branchName,
                     @Param("now") LocalDateTime now);

    default List<RequirementRecord> findByProject(String projectId) {
        return selectList(Wrappers.<Tables.Requirement>lambdaQuery()
                        .eq(Tables.Requirement::getProjectId, projectId)
                        .orderByAsc(Tables.Requirement::getReqNo))
                .stream().map(RecordMapper.INSTANCE::toRequirement).toList();
    }

    default List<RequirementRecord> findVisible(String projectId, String username) {
        return findVisibleRows(projectId, username).stream().map(RecordMapper.INSTANCE::toRequirement).toList();
    }

    default Optional<RequirementRecord> findByReqNo(String projectId, String reqNo) {
        return Optional.ofNullable(selectOne(Wrappers.<Tables.Requirement>lambdaQuery()
                        .eq(Tables.Requirement::getProjectId, projectId)
                        .eq(Tables.Requirement::getReqNo, reqNo)))
                .map(RecordMapper.INSTANCE::toRequirement);
    }

    default Optional<RequirementRecord> findById(long id) {
        return Optional.ofNullable(selectById(id)).map(RecordMapper.INSTANCE::toRequirement);
    }

    default String nextReqNo(String projectId) {
        Integer max = maxReqSeq(projectId);
        return String.format("REQ-%03d", (max == null ? 0 : max) + 1);
    }

    default long insert(String projectId, String reqNo, String title, String description,
                        String owner, LocalDateTime now) {
        Tables.Requirement requirement = new Tables.Requirement();
        requirement.setProjectId(projectId);
        requirement.setReqNo(reqNo);
        requirement.setTitle(title);
        requirement.setDescription(description);
        requirement.setOwner(owner);
        requirement.setBranchStatus("NONE");
        requirement.setCreatedAt(now);
        requirement.setUpdatedAt(now);
        insert(requirement);
        return requirement.getId() == null ? 0 : requirement.getId();
    }
}
