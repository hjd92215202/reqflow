package com.reqflow.service;

import com.reqflow.entity.Requirement;
import java.util.List;

public interface RequirementService {
    List<Requirement> getRequirementsByCreator(Long creatorId);
    Requirement createRequirement(Requirement requirement, Long creatorId);
    Requirement updateRequirement(Long id, Requirement reqDetails);
    void deleteRequirement(Long id);
}