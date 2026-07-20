package com.reqflow.service;

import com.reqflow.entity.Discussion;
import java.util.List;

public interface DiscussionService {
    List<Discussion> getDiscussionsByRequirement(Long requirementId);
    Discussion createDiscussion(Discussion discussion, Long userId);
}