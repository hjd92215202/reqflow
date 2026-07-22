package com.reqflow.repository;

import com.reqflow.entity.SubTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SubTaskRepository extends JpaRepository<SubTask, Long> {
    List<SubTask> findByStageIdOrderByIdAsc(Long stageId);

    // 优化新增：级联删除阶段时一键清除所有子任务
    void deleteByStageId(Long stageId);
}