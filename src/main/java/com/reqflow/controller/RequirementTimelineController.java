package com.reqflow.controller;

import com.reqflow.service.RequirementTimelineService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class RequirementTimelineController {
    private final RequirementTimelineService service;

    public RequirementTimelineController(RequirementTimelineService service) {
        this.service = service;
    }

    @GetMapping("/api/requirements/{id}/timeline")
    public RequirementTimelineService.Result list(
            @PathVariable Long id,
            @RequestParam(defaultValue = "ALL") String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long snapshotId,
            HttpServletRequest request) {
        return service.list(
                id, (Long) request.getAttribute("userId"), category, page, size, snapshotId);
    }
}
