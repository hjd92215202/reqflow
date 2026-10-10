package com.reqflow.repository;

import com.reqflow.entity.SubTask;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubTaskRepository extends JpaRepository<SubTask, Long> {
    List<SubTask> findByStageIdInOrderByIdAsc(List<Long> stageIds);

    java.util.Optional<SubTask> findByRepairVerificationIdAndRepairRequestId(
            Long verificationId, String requestId);

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from SubTask t where t.id=:id")
    java.util.Optional<SubTask> findForVerification(Long id);

    List<SubTask> findByStageIdOrderByIdAsc(Long stageId);

    // 优化新增：级联删除阶段时一键清除所有子任务
    void deleteByStageId(Long stageId);

    List<SubTask> findByParentId(Long parentId);
}
