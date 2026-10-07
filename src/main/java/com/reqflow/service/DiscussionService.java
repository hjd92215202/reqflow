package com.reqflow.service;

import com.reqflow.entity.Discussion;
import java.util.List;

public interface DiscussionService {
    List<Discussion> getDiscussionsByRequirement(Long requirementId, Long userId);

    Discussion createDiscussion(Discussion discussion, Long userId);
}
