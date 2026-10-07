package com.reqflow.repository;

import com.reqflow.entity.Workspace;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    List<Workspace> findByOwnerIdOrderByIdAsc(Long ownerId);
}
