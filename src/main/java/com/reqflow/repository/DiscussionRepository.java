package com.reqflow.repository;

import com.reqflow.entity.Discussion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DiscussionRepository extends JpaRepository<Discussion, Long> {
    List<Discussion> findByRequirementIdOrderByCreatedAtAsc(Long requirementId);
}