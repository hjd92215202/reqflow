package com.reqflow.repository;

import com.reqflow.entity.TaskDependency;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskDependencyRepository extends JpaRepository<TaskDependency, Long> {
    List<TaskDependency> findByStageIdOrderByIdAsc(Long stageId);

    boolean existsByStageIdAndPredecessorIdAndSuccessorId(
            Long stageId, Long predecessorId, Long successorId);
}
