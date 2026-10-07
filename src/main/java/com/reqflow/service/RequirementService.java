package com.reqflow.service;

import com.reqflow.entity.Requirement;
import org.springframework.data.domain.Page;

public interface RequirementService {
    Page<Requirement> getRequirementsByCreator(Long creatorId, Long projectId, int page, int size);

    Requirement createRequirement(Requirement requirement, Long creatorId);

    Requirement updateRequirement(Long id, Requirement reqDetails, Long userId);

    void deleteRequirement(Long id, Long userId);
}
