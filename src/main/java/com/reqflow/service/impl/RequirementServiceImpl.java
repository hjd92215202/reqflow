package com.reqflow.service.impl;

import com.reqflow.entity.Requirement;
import com.reqflow.repository.RequirementRepository;
import com.reqflow.service.RequirementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RequirementServiceImpl implements RequirementService {

    @Autowired
    private RequirementRepository requirementRepository;

    @Override
    public List<Requirement> getRequirementsByCreator(Long creatorId) {
        return requirementRepository.findByCreatorIdOrderByIdDesc(creatorId);
    }

    @Override
    public Requirement createRequirement(Requirement requirement, Long creatorId) {
        requirement.setCreatorId(creatorId);
        if (requirement.getStatus() == null) requirement.setStatus("TODO");
        if (requirement.getPriority() == null) requirement.setPriority("MEDIUM");
        return requirementRepository.save(requirement);
    }

    @Override
    public Requirement updateRequirement(Long id, Requirement reqDetails) {
        var existing = requirementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Requirement not found"));

        existing.setTitle(reqDetails.getTitle());
        existing.setDescription(reqDetails.getDescription());
        existing.setStatus(reqDetails.getStatus());
        existing.setPriority(reqDetails.getPriority());
        existing.setPlannedEndDate(reqDetails.getPlannedEndDate());
        existing.setActualEndDate(reqDetails.getActualEndDate());
        existing.setUpdatedAt(LocalDateTime.now());

        return requirementRepository.save(existing);
    }

    @Override
    public void deleteRequirement(Long id) {
        requirementRepository.deleteById(id);
    }
}