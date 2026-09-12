package com.reqflow.repository;

import com.reqflow.entity.Requirement;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementRepository extends JpaRepository<Requirement, Long> {
    List<Requirement> findByCreatorIdOrderByIdDesc(Long creatorId);

    Page<Requirement> findByCreatorIdOrderByIdDesc(Long creatorId, Pageable pageable);
}
