package com.reqflow.repository;

import com.reqflow.entity.WikiDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WikiDocumentRepository extends JpaRepository<WikiDocument, Long> {
    Optional<WikiDocument> findByCreatorIdAndClientRequestId(
            Long creatorId, String clientRequestId);

    @org.springframework.data.jpa.repository.Query(
            """
        select d from WikiDocument d where (d.requirementId is null and d.creatorId=:userId) or exists (
          select r.id from Requirement r where r.id=d.requirementId and (
            r.creatorId=:userId or exists (select p.id from Project p, Workspace w
              where p.id=r.projectId and w.id=p.workspaceId and w.ownerId=:userId)
            or exists (select p.id from Project p, WorkspaceMember m where p.id=r.projectId
              and m.workspaceId=p.workspaceId and m.userId=:userId))) order by d.id desc
        """)
    List<WikiDocument> findAccessibleByUser(Long userId);

    List<WikiDocument> findByRequirementIdOrderByIdDesc(Long requirementId);

    List<WikiDocument> findAllByOrderByIdDesc();

    List<WikiDocument> findByParentId(Long parentId);

    void deleteByRequirementId(Long requirementId);

    // 新增：根据安全随机 Token 查找文档
    Optional<WikiDocument> findByShareToken(String shareToken);
}
