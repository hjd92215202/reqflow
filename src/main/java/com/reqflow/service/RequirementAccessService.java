package com.reqflow.service;

import com.reqflow.repository.RequirementRepository;
import com.reqflow.repository.StageRepository;
import com.reqflow.repository.SubTaskRepository;
import com.reqflow.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Enforces access inherited from the workspace that contains the requirement. */
@Service
public class RequirementAccessService {

    @Autowired private RequirementRepository requirementRepository;

    @Autowired private StageRepository stageRepository;

    @Autowired private SubTaskRepository subTaskRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private WorkspaceProjectService workspaceProjectService;

    public void requireProjectOwner(Long projectId, Long userId) {
        if (projectId != null) {
            workspaceProjectService.requireProjectAccess(projectId, userId);
        }
    }

    public void requireRequirementOwner(Long requirementId, Long userId) {
        boolean canAccessRequirement =
                requirementId != null
                        && userId != null
                        && requirementRepository
                                .findById(requirementId)
                                .map(
                                        requirement -> {
                                            if (userId.equals(requirement.getCreatorId()))
                                                return true;
                                            return requirement.getProjectId() != null
                                                    && workspaceProjectService.hasProjectAccess(
                                                            requirement.getProjectId(), userId);
                                        })
                                .orElse(false);
        if (!canAccessRequirement) {
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

    public void requireSubTaskOwnerOrAssignee(Long subTaskId, Long userId) {
        var task =
                subTaskRepository
                        .findById(subTaskId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Task not found"));
        Long requirementId =
                stageRepository
                        .findById(task.getStageId())
                        .map(stage -> stage.getRequirementId())
                        .orElse(null);
        boolean hasAccess =
                requirementId != null
                        && userId != null
                        && requirementRepository
                                .findById(requirementId)
                                .map(
                                        requirement ->
                                                userId.equals(requirement.getCreatorId())
                                                        || (requirement.getProjectId() != null
                                                                && workspaceProjectService
                                                                        .hasProjectAccess(
                                                                                requirement
                                                                                        .getProjectId(),
                                                                                userId)))
                                .orElse(false);
        if (hasAccess) {
            return;
        }

        boolean isAssignee =
                userId != null
                        && task.getAssignee() != null
                        && userRepository
                                .findById(userId)
                                .map(
                                        user -> {
                                            String assignee = task.getAssignee().trim();
                                            return (user.getNickname() != null
                                                            && assignee.equalsIgnoreCase(
                                                                    user.getNickname().trim()))
                                                    || (user.getUsername() != null
                                                            && assignee.equalsIgnoreCase(
                                                                    user.getUsername().trim()));
                                        })
                                .orElse(false);
        if (!isAssignee) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied");
        }
    }
}
