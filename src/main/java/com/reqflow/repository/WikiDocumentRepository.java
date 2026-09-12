package com.reqflow.repository;

import com.reqflow.entity.WikiDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WikiDocumentRepository extends JpaRepository<WikiDocument, Long> {
    List<WikiDocument> findByRequirementIdOrderByIdDesc(Long requirementId);

    List<WikiDocument> findAllByOrderByIdDesc();

    List<WikiDocument> findByParentId(Long parentId);

    void deleteByRequirementId(Long requirementId);

    // 新增：根据安全随机 Token 查找文档
    Optional<WikiDocument> findByShareToken(String shareToken);
}
