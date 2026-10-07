package com.reqflow.service;

import com.reqflow.entity.Project;
import com.reqflow.entity.Workspace;
import com.reqflow.repository.ProjectRepository;
import com.reqflow.repository.UserRepository;
import com.reqflow.repository.WorkspaceMemberRepository;
import com.reqflow.repository.WorkspaceRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class WorkspaceProjectService {

    @Autowired private WorkspaceRepository workspaceRepository;

    @Autowired private ProjectRepository projectRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;

    @Transactional(readOnly = true)
    public List<Workspace> getWorkspaces(Long userId) {
        java.util.LinkedHashMap<Long, Workspace> workspaces = new java.util.LinkedHashMap<>();
        workspaceRepository
                .findByOwnerIdOrderByIdAsc(userId)
                .forEach(w -> workspaces.put(w.getId(), w));
        workspaceMemberRepository
                .findByUserIdOrderByWorkspaceIdAsc(userId)
                .forEach(
                        member ->
                                workspaceRepository
                                        .findById(member.getWorkspaceId())
                                        .ifPresent(w -> workspaces.put(w.getId(), w)));
        return List.copyOf(workspaces.values());
    }

    public Workspace createWorkspace(Workspace workspace, Long userId) {
        workspace.setId(null);
        workspace.setOwnerId(userId);
        if (workspace.getName() == null || workspace.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workspace name is required");
        }
        if (workspace.getName().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workspace name is too long");
        }
        return workspaceRepository.save(workspace);
    }

    @Transactional(readOnly = true)
    public List<Project> getProjects(Long workspaceId, Long userId) {
        requireWorkspaceAccess(workspaceId, userId);
        return projectRepository.findByWorkspaceIdOrderByIdAsc(workspaceId);
    }

    public Project createProject(Project project, Long userId) {
        requireWorkspaceOwner(project.getWorkspaceId(), userId);
        if (project.getName() == null || project.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project name is required");
        }
        if (project.getName().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project name is too long");
        }
        if (project.getIdentifier() == null || project.getIdentifier().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Project identifier is required");
        }
        project.setId(null);
        project.setIdentifier(project.getIdentifier().trim().toUpperCase());
        if (project.getIdentifier().length() > 10) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Project identifier must be 10 characters or fewer");
        }
        project.setStatus("ACTIVE");
        if (projectRepository.existsByWorkspaceIdAndIdentifierIgnoreCase(
                project.getWorkspaceId(), project.getIdentifier())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Project identifier already exists in this workspace");
        }
        return projectRepository.save(project);
    }

    public void requireWorkspaceOwner(Long workspaceId, Long userId) {
        boolean isOwner =
                workspaceId != null
                        && userId != null
                        && workspaceRepository
                                .findById(workspaceId)
                                .map(workspace -> userId.equals(workspace.getOwnerId()))
                                .orElse(false);
        if (!isOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied");
        }
    }

    public boolean isWorkspaceOwner(Long workspaceId, Long userId) {
        return workspaceId != null
                && userId != null
                && workspaceRepository
                        .findById(workspaceId)
                        .map(workspace -> userId.equals(workspace.getOwnerId()))
                        .orElse(false);
    }

    public boolean hasWorkspaceAccess(Long workspaceId, Long userId) {
        return isWorkspaceOwner(workspaceId, userId)
                || (workspaceId != null
                        && userId != null
                        && workspaceMemberRepository.existsByWorkspaceIdAndUserId(
                                workspaceId, userId));
    }

    public void requireWorkspaceAccess(Long workspaceId, Long userId) {
        if (!hasWorkspaceAccess(workspaceId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied");
        }
    }

    public void requireProjectOwner(Long projectId, Long userId) {
        Long workspaceId =
                projectRepository.findById(projectId).map(Project::getWorkspaceId).orElse(null);
        requireWorkspaceOwner(workspaceId, userId);
    }

    public void requireProjectAccess(Long projectId, Long userId) {
        Long workspaceId =
                projectRepository.findById(projectId).map(Project::getWorkspaceId).orElse(null);
        requireWorkspaceAccess(workspaceId, userId);
    }

    public boolean hasProjectAccess(Long projectId, Long userId) {
        Long workspaceId =
                projectRepository.findById(projectId).map(Project::getWorkspaceId).orElse(null);
        return hasWorkspaceAccess(workspaceId, userId);
    }

    public void ensureDefaultWorkspace(Long userId) {
        if (!workspaceRepository.findByOwnerIdOrderByIdAsc(userId).isEmpty()) {
            return;
        }
        Workspace workspace = new Workspace();
        workspace.setOwnerId(userId);
        workspace.setName(
                userRepository
                                .findById(userId)
                                .map(
                                        user ->
                                                user.getNickname() == null
                                                        ? user.getUsername()
                                                        : user.getNickname())
                                .orElse("我的")
                        + " 的工作空间");
        workspace.setDescription("个人工作空间");
        workspace = workspaceRepository.save(workspace);

        Project project = new Project();
        project.setWorkspaceId(workspace.getId());
        project.setName("默认项目");
        project.setIdentifier("DEFAULT");
        project.setDescription("用于收纳尚未细分的需求");
        project.setStatus("ACTIVE");
        projectRepository.save(project);
    }
}
