package com.zhengte.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhengte.platform.entity.CrPendingRow;
import com.zhengte.platform.entity.CrRecord;
import com.zhengte.platform.entity.Tables;
import com.zhengte.platform.converter.RecordMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Mapper
public interface CrRequestMapper extends BaseMapper<Tables.CrRequest> {

    @Select("""
            SELECT c.* FROM cr_requests c
            JOIN requirements r ON r.id = c.requirement_id
            WHERE r.project_id = #{projectId}
            ORDER BY c.id
            """)
    List<Tables.CrRequest> findByProjectRows(@Param("projectId") String projectId);

    @Insert("""
            INSERT INTO cr_requests
                (requirement_id, service, approver, status, requested_by, requested_at, comment)
            VALUES (#{requirementId}, #{service}, #{approver}, 'PENDING', #{requester}, #{now}, #{comment})
            ON DUPLICATE KEY UPDATE approver = #{approver}, status = 'PENDING',
                requested_by = #{requester}, requested_at = #{now}, decided_at = NULL, comment = #{comment}
            """)
    int upsertPending(@Param("requirementId") long requirementId, @Param("service") String service,
                      @Param("approver") String approver, @Param("requester") String requester,
                      @Param("comment") String comment, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cr_requests
            SET status = #{status}, approver = #{approver}, decided_at = #{now}, comment = #{comment}
            WHERE id = #{id}
            """)
    int decide(@Param("id") long id, @Param("status") String status,
               @Param("approver") String approver, @Param("comment") String comment,
               @Param("now") LocalDateTime now);

    @Select("""
            SELECT c.id, c.requirement_id AS requirementId, c.service, c.approver, c.status,
                   c.requested_by AS requestedBy, c.requested_at AS requestedAt,
                   c.decided_at AS decidedAt, c.comment,
                   r.project_id AS projectId, r.req_no AS reqNo, r.title,
                   COALESCE(rb.branch_name, r.branch_name) AS branchName, rb.tip_sha AS tipSha
            FROM cr_requests c
            JOIN requirements r ON r.id = c.requirement_id
            LEFT JOIN requirement_branches rb
                ON rb.requirement_id = c.requirement_id AND rb.service = c.service
            WHERE c.status = 'PENDING'
            ORDER BY c.requested_at
            """)
    List<Tables.CrPending> findPendingRows();

    default Map<Long, List<CrRecord>> findByProject(String projectId) {
        return findByProjectRows(projectId).stream().map(RecordMapper.INSTANCE::toCr)
                .collect(Collectors.groupingBy(CrRecord::requirementId));
    }

    default Optional<CrRecord> findById(long id) {
        return Optional.ofNullable(selectById(id)).map(RecordMapper.INSTANCE::toCr);
    }

    default Optional<CrRecord> find(long requirementId, String service) {
        return Optional.ofNullable(selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers
                        .<Tables.CrRequest>lambdaQuery()
                        .eq(Tables.CrRequest::getRequirementId, requirementId)
                        .eq(Tables.CrRequest::getService, service)))
                .map(RecordMapper.INSTANCE::toCr);
    }

    default List<CrPendingRow> findPending() {
        return findPendingRows().stream()
                .map(row -> new CrPendingRow(RecordMapper.INSTANCE.toCrPending(row), row.getProjectId(), row.getReqNo(),
                        row.getTitle(), row.getBranchName(), row.getTipSha()))
                .toList();
    }
}
