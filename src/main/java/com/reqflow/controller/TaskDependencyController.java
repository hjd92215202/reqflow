package com.reqflow.controller;

import com.reqflow.entity.TaskDependency;
import com.reqflow.service.TaskDependencyService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/matrix/dependencies")
public class TaskDependencyController {

    @Autowired private TaskDependencyService taskDependencyService;

    @GetMapping
    public ResponseEntity<List<TaskDependency>> getByStage(
            @RequestParam Long stageId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return ResponseEntity.ok(taskDependencyService.getByStage(stageId, userId));
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestBody TaskDependency dependency, HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            return ResponseEntity.ok(taskDependencyService.create(dependency, userId));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            taskDependencyService.delete(id, userId);
            return ResponseEntity.ok("Delete successful");
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        }
    }
}
