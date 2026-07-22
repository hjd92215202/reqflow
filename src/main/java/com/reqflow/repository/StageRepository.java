package com.reqflow.repository;

import com.reqflow.entity.Stage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StageRepository extends JpaRepository<Stage, Long> {
    List<Stage> findByRequirementIdOrderByIdAsc(Long requirementId);

    // 优化新增：级联删除需求时一键清除所有阶段
    void deleteByRequirementId(Long requirementId);
}