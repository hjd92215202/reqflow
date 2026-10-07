package com.reqflow.service;

import com.reqflow.entity.ActivityLog;
import com.reqflow.entity.Project;
import com.reqflow.entity.Requirement;
import com.reqflow.entity.Stage;
import com.reqflow.entity.SubTask;
import com.reqflow.entity.User;
import com.reqflow.repository.ActivityLogRepository;
import com.reqflow.repository.ProjectRepository;
import com.reqflow.repository.RequirementRepository;
import com.reqflow.repository.StageRepository;
import com.reqflow.repository.SubTaskRepository;
import com.reqflow.repository.UserRepository;
import com.reqflow.repository.WorkspaceMemberRepository;
import com.reqflow.repository.WorkspaceRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ActivityLogService {

    @Autowired private ActivityLogRepository activityLogRepository;

    @Autowired private RequirementRepository requirementRepository;

    @Autowired private StageRepository stageRepository;

    @Autowired private SubTaskRepository subTaskRepository;

    @Autowired private ProjectRepository projectRepository;

    @Autowired private WorkspaceRepository workspaceRepository;

    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private RequirementAccessService requirementAccessService;

    @Autowired private WorkspaceProjectService workspaceProjectService;

    public void record(
            Long requirementId,
            Long userId,
            String targetType,
            Long targetId,
            String actionType,
            String summary) {
        Long workspaceId = resolveWorkspaceId(requirementId, userId);
        if (workspaceId == null) {
            return;
        }
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "User not found"));
        ActivityLog log = new ActivityLog();
        log.setWorkspaceId(workspaceId);
        log.setRequirementId(requirementId);
        log.setUserId(userId);
        log.setUserName(
                user.getNickname() == null || user.getNickname().isBlank()
                        ? user.getUsername()
                        : user.getNickname());
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setActionType(actionType);
        log.setSummary(summary);
        activityLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public Page<ActivityLog> getLogs(
            Long workspaceId, Long requirementId, int page, int size, Long userId) {
        Pageable pageable =
                PageRequest.of(
                        Math.max(0, page),
                        Math.min(Math.max(1, size), 100),
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        if (requirementId != null) {
            requirementAccessService.requireRequirementOwner(requirementId, userId);
            return activityLogRepository.findByRequirementIdOrderByCreatedAtDescIdDesc(
                    requirementId, pageable);
        }
        if (workspaceId != null) {
            workspaceProjectService.requireWorkspaceAccess(workspaceId, userId);
            return activityLogRepository.findByWorkspaceIdOrderByCreatedAtDescIdDesc(
                    workspaceId, pageable);
        }
        java.util.LinkedHashSet<Long> workspaceIds = new java.util.LinkedHashSet<>();
        workspaceRepository
                .findByOwnerIdOrderByIdAsc(userId)
                .forEach(workspace -> workspaceIds.add(workspace.getId()));
        workspaceMemberRepository
                .findByUserIdOrderByWorkspaceIdAsc(userId)
                .forEach(member -> workspaceIds.add(member.getWorkspaceId()));
        if (workspaceIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return activityLogRepository.findByWorkspaceIdInOrderByCreatedAtDescIdDesc(
                List.copyOf(workspaceIds), pageable);
    }

    public Long resolveRequirementIdForStage(Long stageId) {
        return stageRepository.findById(stageId).map(Stage::getRequirementId).orElse(null);
    }

    public Long resolveRequirementIdForTask(Long taskId) {
        return subTaskRepository
                .findById(taskId)
                .map(SubTask::getStageId)
                .flatMap(stageRepository::findById)
                .map(Stage::getRequirementId)
                .orElse(null);
    }

    private Long resolveWorkspaceId(Long requirementId, Long userId) {
        if (requirementId != null) {
            Requirement requirement = requirementRepository.findById(requirementId).orElse(null);
            if (requirement == null || requirement.getProjectId() == null) {
                return null;
            }
            Project project = projectRepository.findById(requirement.getProjectId()).orElse(null);
            return project == null ? null : project.getWorkspaceId();
        }
        return workspaceRepository.findByOwnerIdOrderByIdAsc(userId).stream()
                .findFirst()
                .map(workspace -> workspace.getId())
                .orElse(null);
    }
}
