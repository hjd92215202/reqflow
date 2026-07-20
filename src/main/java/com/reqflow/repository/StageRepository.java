package com.reqflow.repository;

import com.reqflow.entity.Stage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StageRepository extends JpaRepository<Stage, Long> {
    List<Stage> findByRequirementIdOrderByIdAsc(Long requirementId);
}