package com.reqflow.controller;

import com.reqflow.dto.StageUpdateRequest;
import com.reqflow.entity.Stage;
import com.reqflow.service.StageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/stages")
public class StageController {

    @Autowired private StageService stageService;

    @GetMapping("/requirement/{requirementId}")
    public ResponseEntity<?> getByRequirement(
            @PathVariable Long requirementId, HttpServletRequest request) {
        var userId = (Long) request.getAttribute("userId");
        return ResponseEntity.ok(stageService.getStagesByRequirement(requirementId, userId));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Stage stage, HttpServletRequest request) {
        var userId = (Long) request.getAttribute("userId");
        return ResponseEntity.ok(stageService.createStage(stage, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @RequestBody StageUpdateRequest stage,
            HttpServletRequest request) {
        try {
            var userId = (Long) request.getAttribute("userId");
            return ResponseEntity.ok(stageService.updateStage(id, stage, userId));
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
            stageService.deleteStage(id, userId);
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
