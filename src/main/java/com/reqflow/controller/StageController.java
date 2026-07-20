package com.reqflow.controller;

import com.reqflow.entity.Stage;
import com.reqflow.service.StageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stages")
public class StageController {

    @Autowired
    private StageService stageService;

    @GetMapping("/requirement/{requirementId}")
    public ResponseEntity<?> getByRequirement(@PathVariable Long requirementId) {
        return ResponseEntity.ok(stageService.getStagesByRequirement(requirementId));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Stage stage) {
        return ResponseEntity.ok(stageService.createStage(stage));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Stage stage) {
        try {
            return ResponseEntity.ok(stageService.updateStage(id, stage));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        stageService.deleteStage(id);
        return ResponseEntity.ok("Delete successful");
    }
}