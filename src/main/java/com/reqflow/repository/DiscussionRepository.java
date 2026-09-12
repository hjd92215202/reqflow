package com.reqflow.repository;

import com.reqflow.entity.Discussion;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscussionRepository extends JpaRepository<Discussion, Long> {

    @EntityGraph(attributePaths = {"user"})
    List<Discussion> findByStageIdOrderByCreatedAtAsc(Long stageId);

    // 优化新增：级联删除阶段时一键清除所有跟进日志
    void deleteByStageId(Long stageId);
}
