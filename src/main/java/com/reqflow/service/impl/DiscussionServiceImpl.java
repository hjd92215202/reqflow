package com.reqflow.service.impl;

import com.reqflow.entity.Discussion;
import com.reqflow.repository.DiscussionRepository;
import com.reqflow.service.DiscussionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class DiscussionServiceImpl implements DiscussionService {

    @Autowired
    private DiscussionRepository discussionRepository;

    @Override
    @Transactional(readOnly = true) // 只读事务，提高查询吞吐量
    public List<Discussion> getDiscussionsByRequirement(Long stageId) {
        // 底层仓库已被 EntityGraph 优化为 1 次 SQL 联表查询，不再有 N+1 数据库损耗
        return discussionRepository.findByStageIdOrderByCreatedAtAsc(stageId);
    }

    @Override
    @Transactional // 写事务，确保进程中断时数据原子性回滚
    public Discussion createDiscussion(Discussion discussion, Long userId) {
        discussion.setUserId(userId);
        return discussionRepository.save(discussion);
    }
}