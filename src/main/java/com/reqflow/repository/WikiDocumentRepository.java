package com.reqflow.repository;

import com.reqflow.entity.WikiDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WikiDocumentRepository extends JpaRepository<WikiDocument, Long> {
    List<WikiDocument> findByRequirementIdOrderByIdDesc(Long requirementId);
    List<WikiDocument> findAllByOrderByIdDesc();
    List<WikiDocument> findByParentId(Long parentId);
    void deleteByRequirementId(Long requirementId);
}