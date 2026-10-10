package com.reqflow.controller;

import com.reqflow.dto.*;
import com.reqflow.service.VerificationService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/requirements/{requirementId}/verifications")
public class VerificationController {
    private final VerificationService service;

    public VerificationController(VerificationService service) {
        this.service = service;
    }

    @GetMapping
    public Object list(
            @PathVariable Long requirementId,
            @RequestParam(required = false) Long stageId,
            @RequestParam(required = false) Long subTaskId,
            @RequestParam(required = false) String successCriterionId,
            @RequestParam(required = false) String resultStatus,
            @RequestParam(required = false) Boolean valid,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return service.list(
                requirementId,
                user(request),
                stageId,
                subTaskId,
                successCriterionId,
                resultStatus,
                valid,
                page,
                size);
    }

    @GetMapping("/summary")
    public VerificationSummary summary(
            @PathVariable Long requirementId, HttpServletRequest request) {
        return service.summary(requirementId, user(request));
    }

    @GetMapping("/{id}")
    public VerificationResponse get(
            @PathVariable Long requirementId, @PathVariable Long id, HttpServletRequest request) {
        return service.get(requirementId, id, user(request));
    }

    @PostMapping
    public VerificationResponse create(
            @PathVariable Long requirementId,
            @RequestBody VerificationCreateRequest body,
            HttpServletRequest request) {
        return service.create(requirementId, user(request), body);
    }

    public record VoidRequest(String reason) {}

    @PostMapping("/{id}/void")
    public VerificationResponse voidRecord(
            @PathVariable Long requirementId,
            @PathVariable Long id,
            @RequestBody VoidRequest body,
            HttpServletRequest request) {
        return service.voidRecord(requirementId, id, user(request), body.reason());
    }

    @PostMapping("/{id}/repair-tasks")
    public Object repair(
            @PathVariable Long requirementId,
            @PathVariable Long id,
            @RequestBody VerificationService.RepairRequest body,
            HttpServletRequest request) {
        return service.createRepair(requirementId, id, user(request), body);
    }

    private Long user(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> error(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode())
                .body(
                        Map.of(
                                "status",
                                error.getStatusCode().value(),
                                "message",
                                error.getReason()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> contextChanged() {
        return ResponseEntity.status(409)
                .body(Map.of("status", 409, "message", "关联上下文已变化，请保留输入并重新加载"));
    }
}
