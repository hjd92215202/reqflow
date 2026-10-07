package com.reqflow.service;

import com.reqflow.repository.RequirementRepository;
import com.reqflow.repository.StageRepository;
import com.reqflow.repository.SubTaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Enforces the current ownership model: only a requirement's creator can access its work. */
@Service
public class RequirementAccessService {

    @Autowired private RequirementRepository requirementRepository;

    @Autowired private StageRepository stageRepository;

    @Autowired private SubTaskRepository subTaskRepository;

    public void requireRequirementOwner(Long requirementId, Long userId) {
        boolean ownsRequirement =
                requirementId != null
                        && userId != null
                        && requirementRepository
                                .findById(requirementId)
                                .map(requirement -> userId.equals(requirement.getCreatorId()))
                                .orElse(false);
        if (!ownsRequirement) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied");
        }
    }

    public void requireParentTaskInStage(Long parentId, Long stageId) {
        if (parentId == null) {
            return;
        }
        boolean belongsToStage =
                subTaskRepository
                        .findById(parentId)
                        .map(task -> stageId != null && stageId.equals(task.getStageId()))
                        .orElse(false);
        if (!belongsToStage) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Parent task must be in the same stage");
        }
    }

    public void requireStageOwner(Long stageId, Long userId) {
        Long requirementId =
                stageRepository
                        .findById(stageId)
                        .map(stage -> stage.getRequirementId())
                        .orElse(null);
        requireRequirementOwner(requirementId, userId);
    }

    public void requireSubTaskOwner(Long subTaskId, Long userId) {
        Long requirementId =
                subTaskRepository
                        .findById(subTaskId)
                        .flatMap(task -> stageRepository.findById(task.getStageId()))
                        .map(stage -> stage.getRequirementId())
                        .orElse(null);
        requireRequirementOwner(requirementId, userId);
    }
}
