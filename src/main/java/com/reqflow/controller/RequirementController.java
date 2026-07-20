package com.reqflow.controller;

import com.reqflow.entity.Requirement;
import com.reqflow.service.RequirementService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/requirements")
public class RequirementController {

    @Autowired
    private RequirementService requirementService;

    @GetMapping
    public ResponseEntity<?> getMyRequirements(HttpServletRequest request) {
        var userId = (Long) request.getAttribute("userId");
        return ResponseEntity.ok(requirementService.getRequirementsByCreator(userId));
    }

    @PostMapping
    public ResponseEntity<?> createRequirement(@RequestBody Requirement requirement, HttpServletRequest request) {
        var userId = (Long) request.getAttribute("userId");
        var created = requirementService.createRequirement(requirement, userId);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateRequirement(@PathVariable Long id, @RequestBody Requirement requirement) {
        try {
            var updated = requirementService.updateRequirement(id, requirement);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRequirement(@PathVariable Long id) {
        try {
            requirementService.deleteRequirement(id);
            return ResponseEntity.ok("Delete successful");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}