package com.reqflow.repository;

import com.reqflow.entity.Project;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByWorkspaceIdOrderByIdAsc(Long workspaceId);

    boolean existsByWorkspaceIdAndIdentifierIgnoreCase(Long workspaceId, String identifier);
}
