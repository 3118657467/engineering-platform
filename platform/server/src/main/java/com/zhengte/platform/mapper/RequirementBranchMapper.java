package com.zhengte.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhengte.platform.converter.RecordMapper;
import com.zhengte.platform.entity.RequirementBranchRecord;
import com.zhengte.platform.entity.Tables;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mapper
public interface RequirementBranchMapper extends BaseMapper<Tables.RequirementBranch> {

    @Insert("""
            INSERT INTO requirement_branches (requirement_id, service, branch_name, tip_sha, updated_at)
            VALUES (#{requirementId}, #{service}, #{branchName}, #{tipSha}, #{now})
            ON DUPLICATE KEY UPDATE branch_name = #{branchName}, tip_sha = #{tipSha}, updated_at = #{now}
            """)
    int upsertRow(@Param("requirementId") long requirementId, @Param("service") String service,
                  @Param("branchName") String branchName, @Param("tipSha") String tipSha,
                  @Param("now") LocalDateTime now);

    @Update("""
            UPDATE requirement_branches SET tip_sha = #{tipSha}, updated_at = #{now}
            WHERE requirement_id = #{requirementId} AND service = #{service}
            """)
    int updateTipRow(@Param("requirementId") long requirementId, @Param("service") String service,
                     @Param("tipSha") String tipSha, @Param("now") LocalDateTime now);

    @Select("SELECT * FROM requirement_branches WHERE requirement_id = #{requirementId}")
    List<Tables.RequirementBranch> findByRequirementRows(@Param("requirementId") long requirementId);

    @Select("""
            SELECT rb.* FROM requirement_branches rb
            JOIN requirements r ON r.id = rb.requirement_id
            WHERE r.project_id = #{projectId}
            """)
    List<Tables.RequirementBranch> findByProjectRows(@Param("projectId") String projectId);

    default void upsert(long requirementId, String service, String branchName,
                        String tipSha, LocalDateTime now) {
        upsertRow(requirementId, service, branchName, tipSha, now);
    }

    default void updateTip(long requirementId, String service, String tipSha, LocalDateTime now) {
        updateTipRow(requirementId, service, tipSha, now);
    }

    default List<RequirementBranchRecord> findByRequirement(long requirementId) {
        return findByRequirementRows(requirementId).stream()
                .map(RecordMapper.INSTANCE::toRequirementBranch).toList();
    }

    default Map<Long, Map<String, RequirementBranchRecord>> findByProject(String projectId) {
        Map<Long, Map<String, RequirementBranchRecord>> result = new LinkedHashMap<>();
        for (Tables.RequirementBranch row : findByProjectRows(projectId)) {
            RequirementBranchRecord record = RecordMapper.INSTANCE.toRequirementBranch(row);
            result.computeIfAbsent(record.requirementId(), key -> new LinkedHashMap<>())
                    .put(record.service(), record);
        }
        return result;
    }
}
