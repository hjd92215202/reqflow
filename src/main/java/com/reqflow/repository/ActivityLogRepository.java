package com.reqflow.repository;

import com.reqflow.entity.ActivityLog;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    Page<ActivityLog> findByWorkspaceIdOrderByCreatedAtDescIdDesc(
            Long workspaceId, Pageable pageable);

    Page<ActivityLog> findByWorkspaceIdInOrderByCreatedAtDescIdDesc(
            List<Long> workspaceIds, Pageable pageable);

    Page<ActivityLog> findByRequirementIdOrderByCreatedAtDescIdDesc(
            Long requirementId, Pageable pageable);
}
