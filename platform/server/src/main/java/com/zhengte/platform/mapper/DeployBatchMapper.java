package com.zhengte.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhengte.platform.entity.BatchRecord;
import com.zhengte.platform.entity.Tables;
import com.zhengte.platform.converter.RecordMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface DeployBatchMapper extends BaseMapper<Tables.DeployBatch> {

    @Update("UPDATE deploy_batches SET status = #{status}, finished_at = #{finishedAt,jdbcType=TIMESTAMP} WHERE id = #{id}")
    int updateStatus(@Param("id") long id, @Param("status") String status,
                     @Param("finishedAt") LocalDateTime finishedAt);

    @Update("UPDATE deploy_batches SET status = 'TERMINATED', finished_at = NOW() WHERE status = 'RUNNING'")
    int reconcileRunning();

    default long createBatch(String projectId, String service, String env, String operator,
                             LocalDateTime now) {
        Tables.DeployBatch batch = new Tables.DeployBatch();
        batch.setProjectId(projectId);
        batch.setService(service);
        batch.setEnv(env);
        batch.setStatus("RUNNING");
        batch.setOperator(operator);
        batch.setStartedAt(now);
        insert(batch);
        return batch.getId();
    }

    default Optional<BatchRecord> findBatch(long batchId) {
        return Optional.ofNullable(selectById(batchId)).map(RecordMapper.INSTANCE::toBatch);
    }

    default Optional<BatchRecord> findLatest(String projectId, String service, String env) {
        List<Tables.DeployBatch> rows = selectList(Wrappers.<Tables.DeployBatch>lambdaQuery()
                .eq(Tables.DeployBatch::getProjectId, projectId)
                .eq(Tables.DeployBatch::getService, service)
                .eq(Tables.DeployBatch::getEnv, env)
                .orderByDesc(Tables.DeployBatch::getId)
                .last("LIMIT 1"));
        return rows.stream().findFirst().map(RecordMapper.INSTANCE::toBatch);
    }
}
