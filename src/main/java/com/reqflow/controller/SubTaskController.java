package com.reqflow.controller;

import com.reqflow.entity.SubTask;
import com.reqflow.service.SubTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/subtasks")
public class SubTaskController {

    @Autowired
    private SubTaskService subTaskService;

    @GetMapping("/requirement/{reqId}")
    public ResponseEntity<?> getByRequirement(@PathVariable Long reqId) {
        return ResponseEntity.ok(subTaskService.getSubTasksByRequirement(reqId));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody SubTask subTask) {
        return ResponseEntity.ok(subTaskService.createSubTask(subTask));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody SubTask subTask) {
        try {
            return ResponseEntity.ok(subTaskService.updateSubTask(id, subTask));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        subTaskService.deleteSubTask(id);
        return ResponseEntity.ok("Delete successful");
    }
}