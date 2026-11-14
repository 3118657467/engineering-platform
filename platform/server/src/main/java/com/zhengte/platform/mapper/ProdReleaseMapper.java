package com.zhengte.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhengte.platform.entity.Tables;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface ProdReleaseMapper extends BaseMapper<Tables.ProdRelease> {

    @Select("""
            SELECT p.* FROM prod_releases p
            JOIN requirements r ON r.id = p.requirement_id
            WHERE r.project_id = #{projectId} AND p.service = #{service}
            ORDER BY r.req_no
            """)
    List<Tables.ProdRelease> findByProject(String projectId, String service);

    @Insert("""
            INSERT INTO prod_releases
                (requirement_id, service, current_sha, released_at, previous_sha, prev_released_at)
            VALUES (#{requirementId}, #{service}, #{sha}, #{now}, NULL, NULL)
            ON DUPLICATE KEY UPDATE previous_sha = current_sha, prev_released_at = released_at,
                current_sha = #{sha}, released_at = #{now}
            """)
    int saveRelease(@Param("requirementId") long requirementId, @Param("service") String service,
                    @Param("sha") String sha, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE prod_releases
            SET previous_sha = current_sha, prev_released_at = released_at,
                current_sha = #{sha}, released_at = #{now}
            WHERE requirement_id = #{requirementId} AND service = #{service}
            """)
    int rollback(@Param("requirementId") long requirementId, @Param("service") String service,
                 @Param("sha") String sha, @Param("now") LocalDateTime now);

    default Optional<Tables.ProdRelease> findRelease(long requirementId, String service) {
        return Optional.ofNullable(selectOne(Wrappers.<Tables.ProdRelease>lambdaQuery()
                .eq(Tables.ProdRelease::getRequirementId, requirementId)
                .eq(Tables.ProdRelease::getService, service)));
    }
}
