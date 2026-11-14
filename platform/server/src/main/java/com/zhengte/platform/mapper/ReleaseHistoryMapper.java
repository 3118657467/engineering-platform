package com.zhengte.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhengte.platform.converter.RecordMapper;
import com.zhengte.platform.entity.ReleaseHistoryRecord;
import com.zhengte.platform.entity.Tables;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface ReleaseHistoryMapper extends BaseMapper<Tables.ReleaseHistory> {

    String COLUMNS = "id, project_id, service, requirement_id, req_no, title, branch_name, sha, "
            + "released_at, operator, batch_id, deployment_id, is_current AS current ";

    @Update("""
            UPDATE prod_release_history SET is_current = NULL
            WHERE project_id = #{projectId} AND service = #{service} AND is_current = 1
            """)
    int clearCurrent(@Param("projectId") String projectId, @Param("service") String service);

    @Insert("""
            INSERT INTO prod_release_history
                (project_id, service, requirement_id, req_no, title, branch_name, sha,
                 released_at, operator, batch_id, deployment_id, is_current)
            VALUES (#{projectId}, #{service}, #{requirementId}, #{reqNo}, #{title}, #{branchName},
                    #{sha}, #{releasedAt}, #{operator}, #{batchId,jdbcType=BIGINT},
                    #{deploymentId,jdbcType=BIGINT}, #{current,jdbcType=TINYINT})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRelease(Tables.ReleaseHistory row);

    @Update("""
            UPDATE prod_release_history
            SET released_at = #{releasedAt}, operator = #{operator},
                batch_id = #{batchId}, deployment_id = #{deploymentId}
            WHERE id = #{id}
            """)
    int updateCurrentRelease(@Param("id") long id, @Param("releasedAt") LocalDateTime releasedAt,
                             @Param("operator") String operator, @Param("batchId") Long batchId,
                             @Param("deploymentId") Long deploymentId);

    @Update("UPDATE prod_release_history SET is_current = 1 WHERE id = #{id}")
    int markCurrentRow(@Param("id") long id);

    @Select("SELECT " + COLUMNS + " FROM prod_release_history WHERE id = #{id}")
    Tables.ReleaseHistory findRow(@Param("id") long id);

    @Select("SELECT " + COLUMNS + """
            FROM prod_release_history
            WHERE project_id = #{projectId} AND service = #{service} AND is_current = 1
            LIMIT 1
            """)
    Tables.ReleaseHistory findCurrentRow(@Param("projectId") String projectId,
                                         @Param("service") String service);

    @Select("SELECT " + COLUMNS + """
            FROM prod_release_history
            WHERE project_id = #{projectId} AND service = #{service}
            ORDER BY released_at DESC, id DESC
            """)
    List<Tables.ReleaseHistory> findHistoryRows(@Param("projectId") String projectId,
                                                @Param("service") String service);

    default long insertCurrent(Tables.ReleaseHistory row) {
        row.setCurrent(Boolean.TRUE);
        insertRelease(row);
        return row.getId();
    }

    default Optional<ReleaseHistoryRecord> findById(long id) {
        return Optional.ofNullable(findRow(id)).map(RecordMapper.INSTANCE::toReleaseHistory);
    }

    default Optional<ReleaseHistoryRecord> findCurrent(String projectId, String service) {
        return Optional.ofNullable(findCurrentRow(projectId, service))
                .map(RecordMapper.INSTANCE::toReleaseHistory);
    }

    default List<ReleaseHistoryRecord> findHistory(String projectId, String service) {
        return findHistoryRows(projectId, service).stream()
                .map(RecordMapper.INSTANCE::toReleaseHistory).toList();
    }
}
