package com.reqflow.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CapabilityController {
    @GetMapping("/api/capabilities")
    public Map<String, Object> get() {
        return Map.of("requirementDefinition", 1, "executionStandards", 1, "decisionRecords", 1);
    }
}
