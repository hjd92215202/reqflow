package com.reqflow.repository;

import com.reqflow.entity.SubTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SubTaskRepository extends JpaRepository<SubTask, Long> {
    List<SubTask> findByStageIdOrderByIdAsc(Long stageId); // 变更为按阶段ID查询
}