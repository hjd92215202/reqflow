package com.reqflow.repository;

import com.reqflow.entity.Discussion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DiscussionRepository extends JpaRepository<Discussion, Long> {

    // 核心优化：利用 EntityGraph 强制联表抓取 user 信息，将 N+1 查询完美缩减为 1 次 SQL 查询
    @EntityGraph(attributePaths = {"user"})
    List<Discussion> findByStageIdOrderByCreatedAtAsc(Long stageId);
}