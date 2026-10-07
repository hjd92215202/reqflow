package com.reqflow.controller;

import com.reqflow.entity.Project;
import com.reqflow.service.WorkspaceProjectService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Autowired private WorkspaceProjectService workspaceProjectService;

    @GetMapping
    public ResponseEntity<List<Project>> getProjects(
            @RequestParam Long workspaceId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return ResponseEntity.ok(workspaceProjectService.getProjects(workspaceId, userId));
    }

    @PostMapping
    public ResponseEntity<Project> createProject(
            @RequestBody Project project, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return ResponseEntity.ok(workspaceProjectService.createProject(project, userId));
    }
}
