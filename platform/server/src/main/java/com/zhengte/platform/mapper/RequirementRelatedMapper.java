package com.zhengte.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhengte.platform.entity.Tables;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mapper
public interface RequirementRelatedMapper extends BaseMapper<Tables.RequirementRelated> {

    @Insert("INSERT IGNORE INTO requirement_related (requirement_id, username) VALUES (#{requirementId}, #{username})")
    int insertIgnore(@Param("requirementId") long requirementId, @Param("username") String username);

    @Select("""
            SELECT rr.requirement_id AS requirementId, u.display_name AS displayName
            FROM requirement_related rr
            JOIN users u ON u.username = rr.username
            JOIN requirements r ON r.id = rr.requirement_id
            WHERE r.project_id = #{projectId}
            ORDER BY u.display_name
            """)
    List<Tables.RelatedName> findRelatedNames(@Param("projectId") String projectId);

    default void replaceRelated(long requirementId, List<String> usernames) {
        delete(Wrappers.<Tables.RequirementRelated>lambdaQuery()
                .eq(Tables.RequirementRelated::getRequirementId, requirementId));
        if (usernames == null) {
            return;
        }
        usernames.stream().distinct().forEach(username -> insertIgnore(requirementId, username));
    }

    default Map<Long, List<String>> relatedDisplayNames(String projectId) {
        return findRelatedNames(projectId).stream()
                .collect(Collectors.groupingBy(Tables.RelatedName::getRequirementId,
                        Collectors.mapping(Tables.RelatedName::getDisplayName, Collectors.toList())));
    }
}
