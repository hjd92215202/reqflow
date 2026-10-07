package com.reqflow.repository;

import com.reqflow.entity.Requirement;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequirementRepository extends JpaRepository<Requirement, Long> {
    List<Requirement> findByCreatorIdOrderByIdDesc(Long creatorId);

    Page<Requirement> findByCreatorIdOrderByIdDesc(Long creatorId, Pageable pageable);

    Page<Requirement> findByCreatorIdAndProjectIdOrderByIdDesc(
            Long creatorId, Long projectId, Pageable pageable);

    @Query(
            "select r from Requirement r where r.creatorId = :userId or exists "
                    + "(select p.id from Project p, WorkspaceMember m where p.id = r.projectId "
                    + "and m.workspaceId = p.workspaceId and m.userId = :userId) "
                    + "order by r.id desc")
    Page<Requirement> findAccessibleByUser(@Param("userId") Long userId, Pageable pageable);

    @Query(
            "select r from Requirement r where r.projectId = :projectId and (r.creatorId = :userId"
                + " or exists (select p.id from Project p, WorkspaceMember m where p.id ="
                + " r.projectId and m.workspaceId = p.workspaceId and m.userId = :userId)) order by"
                + " r.id desc")
    Page<Requirement> findAccessibleByUserAndProject(
            @Param("userId") Long userId, @Param("projectId") Long projectId, Pageable pageable);
}
