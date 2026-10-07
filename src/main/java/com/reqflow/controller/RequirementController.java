package com.reqflow.controller;

import com.reqflow.entity.Requirement;
import com.reqflow.service.RequirementService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/requirements")
public class RequirementController {

    @Autowired private RequirementService requirementService;

    @GetMapping
    public ResponseEntity<?> getMyRequirements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            HttpServletRequest request) {
        var userId = (Long) request.getAttribute("userId");
        return ResponseEntity.ok(
                requirementService.getRequirementsByCreator(userId, projectId, page, size));
    }

    @PostMapping
    public ResponseEntity<?> createRequirement(
            @RequestBody Requirement requirement, HttpServletRequest request) {
        var userId = (Long) request.getAttribute("userId");
        var created = requirementService.createRequirement(requirement, userId);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateRequirement(
            @PathVariable Long id,
            @RequestBody Requirement requirement,
            HttpServletRequest request) {
        try {
            var userId = (Long) request.getAttribute("userId");
            var updated = requirementService.updateRequirement(id, requirement, userId);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            if (e instanceof ResponseStatusException responseStatusException) {
                return ResponseEntity.status(responseStatusException.getStatusCode())
                        .body(responseStatusException.getReason());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRequirement(@PathVariable Long id, HttpServletRequest request) {
        try {
            var userId = (Long) request.getAttribute("userId");
            requirementService.deleteRequirement(id, userId);
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
