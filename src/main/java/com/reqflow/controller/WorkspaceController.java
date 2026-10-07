package com.reqflow.controller;

import com.reqflow.entity.Workspace;
import com.reqflow.service.WorkspaceProjectService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    @Autowired private WorkspaceProjectService workspaceProjectService;

    @GetMapping
    public ResponseEntity<List<Workspace>> getMyWorkspaces(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        workspaceProjectService.ensureDefaultWorkspace(userId);
        return ResponseEntity.ok(workspaceProjectService.getWorkspaces(userId));
    }

    @PostMapping
    public ResponseEntity<Workspace> createWorkspace(
            @RequestBody Workspace workspace, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return ResponseEntity.ok(workspaceProjectService.createWorkspace(workspace, userId));
    }
}
