package com.reqflow.controller;

import com.reqflow.entity.CustomColumn;
import com.reqflow.service.CustomColumnService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/custom-columns")
public class CustomColumnController {

    @Autowired
    private CustomColumnService customColumnService;

    @GetMapping("/requirement/{reqId}")
    public ResponseEntity<?> getByRequirement(@PathVariable Long reqId) {
        return ResponseEntity.ok(customColumnService.getColumnsByRequirement(reqId));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CustomColumn column) {
        return ResponseEntity.ok(customColumnService.createColumn(column));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        customColumnService.deleteColumn(id);
        return ResponseEntity.ok("Delete successful");
    }
}