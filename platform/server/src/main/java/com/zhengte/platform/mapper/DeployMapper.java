package com.zhengte.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhengte.platform.entity.BatchRecord;
import com.zhengte.platform.entity.DeploymentRecord;
import com.zhengte.platform.entity.ReleaseRecord;
import com.zhengte.platform.entity.StepRecord;
import com.zhengte.platform.entity.Tables;
import com.zhengte.platform.converter.RecordMapper;
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
public interface DeployMapper extends BaseMapper<Tables.DeployBatch> {

    @Insert("""
            INSERT INTO deployments
                (batch_id, requirement_id, service, env, branch_name, sha, status, operator, started_at)
            VALUES (#{batchId}, #{requirementId}, #{service}, #{env}, #{branchName}, #{sha}, 'PENDING', #{operator}, #{startedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertDeployment(Tables.Deployment row);

    @Insert("INSERT INTO deployment_steps (deployment_id, seq, name, status) VALUES (#{deploymentId}, #{seq}, #{name}, 'PENDING')")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertStep(Tables.DeploymentStep row);

    @Insert("INSERT INTO deploy_locks (project_id, service, env, batch_id, acquired_at) VALUES (#{projectId}, #{service}, #{env}, #{batchId}, #{acquiredAt})")
    int insertLock(Tables.DeployLock row);

    @Insert("""
            INSERT INTO prod_releases
                (requirement_id, service, current_sha, released_at, previous_sha, prev_released_at)
            VALUES (#{requirementId}, #{service}, #{sha}, #{now}, NULL, NULL)
            ON DUPLICATE KEY UPDATE previous_sha = current_sha, prev_released_at = released_at,
                current_sha = #{sha}, released_at = #{now}
            """)
    int saveReleaseRow(@Param("requirementId") long requirementId, @Param("service") String service,
                       @Param("sha") String sha, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO deployment_pipelines (deployment_id, pipeline_id, status, updated_at)
            VALUES (#{deploymentId}, #{pipelineId}, #{status}, #{now})
            ON DUPLICATE KEY UPDATE status = #{status}, updated_at = #{now}
            """)
    int savePipelineRow(@Param("deploymentId") long deploymentId, @Param("pipelineId") long pipelineId,
                        @Param("status") String status, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE deployments
            SET status = #{status}, started_at = COALESCE(#{startedAt,jdbcType=TIMESTAMP}, started_at),
                finished_at = #{finishedAt,jdbcType=TIMESTAMP}, error_message = #{errorMessage}
            WHERE id = #{id}
            """)
    int updateDeploymentRow(@Param("id") long id, @Param("status") String status,
                            @Param("startedAt") LocalDateTime startedAt,
                            @Param("finishedAt") LocalDateTime finishedAt,
                            @Param("errorMessage") String errorMessage);

    @Update("UPDATE deployment_steps SET status = #{status}, duration_ms = #{durationMs,jdbcType=INTEGER} WHERE id = #{id}")
    int updateStepRow(@Param("id") long id, @Param("status") String status,
                      @Param("durationMs") Integer durationMs);

    @Update("""
            UPDATE prod_releases
            SET previous_sha = current_sha, prev_released_at = released_at,
                current_sha = #{sha}, released_at = #{now}
            WHERE requirement_id = #{requirementId} AND service = #{service}
            """)
    int rollbackRow(@Param("requirementId") long requirementId, @Param("service") String service,
                    @Param("sha") String sha, @Param("now") LocalDateTime now);

    @Select("""
            SELECT * FROM deployments
            WHERE batch_id = #{batchId} AND status IN ('RUNNING', 'PENDING')
            ORDER BY FIELD(status, 'RUNNING', 'PENDING'), id
            LIMIT 1
            """)
    Tables.Deployment findOpenRow(@Param("batchId") long batchId);

    @Select("""
            SELECT s.* FROM deployment_steps s
            JOIN deployments d ON d.id = s.deployment_id
            WHERE d.batch_id = #{batchId}
            ORDER BY s.deployment_id, s.seq
            """)
    List<Tables.DeploymentStep> findStepRowsByBatch(@Param("batchId") long batchId);

    @Select("SELECT * FROM prod_releases WHERE requirement_id = #{requirementId} AND service = #{service}")
    Tables.ProdRelease findReleaseRow(@Param("requirementId") long requirementId,
                                      @Param("service") String service);

    @Select("""
            SELECT p.* FROM prod_releases p
            JOIN requirements r ON r.id = p.requirement_id
            WHERE r.project_id = #{projectId} AND p.service = #{service}
            ORDER BY r.req_no
            """)
    List<Tables.ProdRelease> findReleaseRows(@Param("projectId") String projectId,
                                             @Param("service") String service);

    @Select("SELECT pipeline_id FROM deployment_pipelines WHERE deployment_id = #{deploymentId}")
    Long findPipelineRow(@Param("deploymentId") long deploymentId);

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

    default long createDeployment(long batchId, long requirementId, String service, String env,
                                  String branchName, String sha, String operator, LocalDateTime now) {
        Tables.Deployment row = new Tables.Deployment();
        row.setBatchId(batchId);
        row.setRequirementId(requirementId);
        row.setService(service);
        row.setEnv(env);
        row.setBranchName(branchName);
        row.setSha(sha);
        row.setOperator(operator);
        row.setStartedAt(now);
        insertDeployment(row);
        return row.getId();
    }

    default void createStep(long deploymentId, int seq, String name) {
        Tables.DeploymentStep row = new Tables.DeploymentStep();
        row.setDeploymentId(deploymentId);
        row.setSeq(seq);
        row.setName(name);
        insertStep(row);
    }

    default void createLock(String projectId, String service, String env, long batchId,
                            LocalDateTime now) {
        Tables.DeployLock row = new Tables.DeployLock();
        row.setProjectId(projectId);
        row.setService(service);
        row.setEnv(env);
        row.setBatchId(batchId);
        row.setAcquiredAt(now);
        insertLock(row);
    }

    @Select("SELECT COUNT(*) FROM deploy_locks WHERE project_id = #{projectId} AND service = #{service} AND env = #{env}")
    int countLock(@Param("projectId") String projectId, @Param("service") String service,
                  @Param("env") String env);

    default boolean lockExists(String projectId, String service, String env) {
        return countLock(projectId, service, env) > 0;
    }

    default Optional<BatchRecord> findBatch(long batchId) {
        return Optional.ofNullable(selectById(batchId)).map(RecordMapper.INSTANCE::toBatch);
    }

    default Optional<BatchRecord> findLatestBatch(String projectId, String service, String env) {
        List<Tables.DeployBatch> rows = selectList(Wrappers.<Tables.DeployBatch>lambdaQuery()
                .eq(Tables.DeployBatch::getProjectId, projectId)
                .eq(Tables.DeployBatch::getService, service)
                .eq(Tables.DeployBatch::getEnv, env)
                .orderByDesc(Tables.DeployBatch::getId)
                .last("LIMIT 1"));
        return rows.stream().findFirst().map(RecordMapper.INSTANCE::toBatch);
    }

    default Optional<BatchRecord> findRunningBatch(String projectId, String service, String env) {
        List<Tables.DeployBatch> rows = selectList(Wrappers.<Tables.DeployBatch>lambdaQuery()
                .eq(Tables.DeployBatch::getProjectId, projectId)
                .eq(Tables.DeployBatch::getService, service)
                .eq(Tables.DeployBatch::getEnv, env)
                .eq(Tables.DeployBatch::getStatus, "RUNNING")
                .orderByDesc(Tables.DeployBatch::getId)
                .last("LIMIT 1"));
        return rows.stream().findFirst().map(RecordMapper.INSTANCE::toBatch);
    }

    default List<DeploymentRecord> findDeployments(long batchId) {
        List<Tables.Deployment> rows = getBaseMapperDeployments(batchId);
        return rows.stream().map(RecordMapper.INSTANCE::toDeployment).toList();
    }

    default List<Tables.Deployment> getBaseMapperDeployments(long batchId) {
        return selectDeployments(batchId);
    }

    @Select("SELECT * FROM deployments WHERE batch_id = #{batchId} ORDER BY id")
    List<Tables.Deployment> selectDeployments(@Param("batchId") long batchId);

    default Optional<DeploymentRecord> findOpenDeployment(long batchId) {
        return Optional.ofNullable(findOpenRow(batchId)).map(RecordMapper.INSTANCE::toDeployment);
    }

    default List<StepRecord> findSteps(long deploymentId) {
        return selectSteps(deploymentId).stream().map(RecordMapper.INSTANCE::toStep).toList();
    }

    @Select("SELECT * FROM deployment_steps WHERE deployment_id = #{deploymentId} ORDER BY seq")
    List<Tables.DeploymentStep> selectSteps(@Param("deploymentId") long deploymentId);

    default List<StepRecord> findStepsByBatch(long batchId) {
        return findStepRowsByBatch(batchId).stream().map(RecordMapper.INSTANCE::toStep).toList();
    }

    default void updateBatchStatus(long batchId, String status, LocalDateTime finishedAt) {
        update(null, Wrappers.<Tables.DeployBatch>lambdaUpdate()
                .set(Tables.DeployBatch::getStatus, status)
                .set(Tables.DeployBatch::getFinishedAt, finishedAt)
                .eq(Tables.DeployBatch::getId, batchId));
    }

    default void cancelPendingDeployments(long batchId, LocalDateTime now) {
        cancelPendingRows(batchId, now);
    }

    @Update("""
            UPDATE deployments SET status = 'CANCELLED', finished_at = #{now}, error_message = '部署已终止'
            WHERE batch_id = #{batchId} AND status = 'PENDING'
            """)
    int cancelPendingRows(@Param("batchId") long batchId, @Param("now") LocalDateTime now);

    default void updateDeploymentStatus(long deploymentId, String status, LocalDateTime startedAt,
                                        LocalDateTime finishedAt, String errorMessage) {
        updateDeploymentRow(deploymentId, status, startedAt, finishedAt, errorMessage);
    }

    default void updateStepStatus(long stepId, String status, Integer durationMs) {
        updateStepRow(stepId, status, durationMs);
    }

    default void savePipeline(long deploymentId, long pipelineId, String status, LocalDateTime now) {
        savePipelineRow(deploymentId, pipelineId, status, now);
    }

    default Optional<Long> findPipelineId(long deploymentId) {
        return Optional.ofNullable(findPipelineRow(deploymentId));
    }

    default Optional<ReleaseRecord> findRelease(long requirementId, String service) {
        return Optional.ofNullable(findReleaseRow(requirementId, service)).map(RecordMapper.INSTANCE::toRelease);
    }

    default List<ReleaseRecord> findReleases(String projectId, String service) {
        return findReleaseRows(projectId, service).stream().map(RecordMapper.INSTANCE::toRelease).toList();
    }

    default void saveRelease(long requirementId, String service, String newSha, LocalDateTime now) {
        saveReleaseRow(requirementId, service, newSha, now);
    }

    default void rollbackRelease(long requirementId, String service, String targetSha,
                                 LocalDateTime now) {
        rollbackRow(requirementId, service, targetSha, now);
    }

    default void deleteLockByBatch(long batchId) {
        deleteLocks(batchId);
    }

    @Update("DELETE FROM deploy_locks WHERE batch_id = #{batchId}")
    int deleteLocks(@Param("batchId") long batchId);

    default void reconcileAfterRestart() {
        reconcileDeployments();
        reconcileSteps();
        reconcileBatches();
        clearLocks();
    }

    @Update("""
            UPDATE deployments SET status = 'CANCELLED', finished_at = NOW(),
                error_message = '服务重启，部署中断'
            WHERE status IN ('RUNNING', 'PENDING')
            """)
    int reconcileDeployments();

    @Update("UPDATE deployment_steps SET status = 'FAILED' WHERE status = 'RUNNING'")
    int reconcileSteps();

    @Update("UPDATE deploy_batches SET status = 'TERMINATED', finished_at = NOW() WHERE status = 'RUNNING'")
    int reconcileBatches();

    @Update("DELETE FROM deploy_locks")
    int clearLocks();
}
