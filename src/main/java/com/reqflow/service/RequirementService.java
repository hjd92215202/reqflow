package com.reqflow.service;

import com.reqflow.entity.Requirement;
import org.springframework.data.domain.Page;

import java.util.List;

public interface RequirementService {
    Page<Requirement> getRequirementsByCreator(Long creatorId, int page, int size);
    Requirement createRequirement(Requirement requirement, Long creatorId);
    Requirement updateRequirement(Long id, Requirement reqDetails);
    void deleteRequirement(Long id);
}