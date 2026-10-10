package com.reqflow.controller;

import com.reqflow.dto.*;
import com.reqflow.service.DecisionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/requirements/{requirementId}/decisions")
public class DecisionController {
    private final DecisionService service;

    public DecisionController(DecisionService service) {
        this.service = service;
    }

    @GetMapping
    public Object list(
            @PathVariable Long requirementId,
            @RequestParam(required = false) Long stageId,
            @RequestParam(required = false) Long subTaskId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return service.list(requirementId, user(request), stageId, subTaskId, status, page, size);
    }

    @GetMapping("/{id}")
    public DecisionResponse get(
            @PathVariable Long requirementId, @PathVariable Long id, HttpServletRequest request) {
        return service.get(requirementId, id, user(request));
    }

    @PostMapping
    public DecisionResponse create(
            @PathVariable Long requirementId,
            @RequestBody DecisionCreateRequest body,
            HttpServletRequest request) {
        return service.create(requirementId, user(request), body);
    }

    @PutMapping("/{id}")
    public DecisionResponse update(
            @PathVariable Long requirementId,
            @PathVariable Long id,
            @RequestBody DecisionWriteRequest body,
            HttpServletRequest request) {
        return service.update(requirementId, id, user(request), body);
    }

    @PostMapping("/{id}/supersede")
    public DecisionResponse supersede(
            @PathVariable Long requirementId,
            @PathVariable Long id,
            @RequestBody DecisionWriteRequest body,
            HttpServletRequest request) {
        return service.supersede(requirementId, id, user(request), body);
    }

    private Long user(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handle(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(
                        Map.of(
                                "status",
                                exception.getStatusCode().value(),
                                "message",
                                exception.getReason()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> changedContext() {
        return ResponseEntity.status(409)
                .body(Map.of("status", 409, "message", "关联上下文已变化，请保留输入并重新加载"));
    }
}
