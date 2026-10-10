package com.reqflow.controller;

import com.reqflow.dto.SubTaskUpdateRequest;
import com.reqflow.entity.SubTask;
import com.reqflow.service.SubTaskService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/subtasks")
public class SubTaskController {

    @Autowired private SubTaskService subTaskService;

    @GetMapping("/stage/{stageId}") // 语义化路径调整
    public ResponseEntity<?> getByStage(@PathVariable Long stageId, HttpServletRequest request) {
        var userId = (Long) request.getAttribute("userId");
        return ResponseEntity.ok(subTaskService.getSubTasksByRequirement(stageId, userId));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody SubTask subTask, HttpServletRequest request) {
        var userId = (Long) request.getAttribute("userId");
        return ResponseEntity.ok(subTaskService.createSubTask(subTask, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @RequestBody SubTaskUpdateRequest subTask,
            HttpServletRequest request) {
        try {
            var userId = (Long) request.getAttribute("userId");
            return ResponseEntity.ok(subTaskService.updateSubTask(id, subTask, userId));
        } catch (Exception e) {
            if (e instanceof ResponseStatusException responseStatusException) {
                return ResponseEntity.status(responseStatusException.getStatusCode())
                        .body(responseStatusException.getReason());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest request) {
        try {
            var userId = (Long) request.getAttribute("userId");
            subTaskService.deleteSubTask(id, userId);
            return ResponseEntity.ok("Delete successful");
        } catch (Exception e) {
            if (e instanceof ResponseStatusException responseStatusException) {
                return ResponseEntity.status(responseStatusException.getStatusCode())
                        .body(responseStatusException.getReason());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
