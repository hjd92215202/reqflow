package com.reqflow.repository;

import com.reqflow.entity.CustomColumn;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CustomColumnRepository extends JpaRepository<CustomColumn, Long> {
    List<CustomColumn> findByRequirementId(Long requirementId);
}