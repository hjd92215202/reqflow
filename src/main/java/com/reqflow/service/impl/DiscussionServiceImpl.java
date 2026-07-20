package com.reqflow.service.impl;

import com.reqflow.entity.Discussion;
import com.reqflow.repository.DiscussionRepository;
import com.reqflow.service.DiscussionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class DiscussionServiceImpl implements DiscussionService {

    @Autowired
    private DiscussionRepository discussionRepository;

    @Override
    public List<Discussion> getDiscussionsByRequirement(Long requirementId) {
        return discussionRepository.findByRequirementIdOrderByCreatedAtAsc(requirementId);
    }

    @Override
    public Discussion createDiscussion(Discussion discussion, Long userId) {
        discussion.setUserId(userId);
        return discussionRepository.save(discussion);
    }
}