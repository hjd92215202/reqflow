package com.reqflow.controller;

import com.reqflow.dto.DefinitionConfirmRequest;
import com.reqflow.dto.DefinitionResponse;
import com.reqflow.dto.DefinitionWriteRequest;
import com.reqflow.service.RequirementDefinitionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/requirements/{id}/definition")
public class RequirementDefinitionController {
    private final RequirementDefinitionService service;

    public RequirementDefinitionController(RequirementDefinitionService service) {
        this.service = service;
    }

    @GetMapping
    public DefinitionResponse get(@PathVariable Long id, HttpServletRequest request) {
        return service.get(id, (Long) request.getAttribute("userId"));
    }

    @PutMapping
    public DefinitionResponse save(
            @PathVariable Long id,
            @RequestBody DefinitionWriteRequest body,
            HttpServletRequest request) {
        return service.save(
                id, (Long) request.getAttribute("userId"), body.version(), body.definition());
    }

    @PostMapping("/confirm")
    public DefinitionResponse confirm(
            @PathVariable Long id,
            @RequestBody DefinitionConfirmRequest body,
            HttpServletRequest request) {
        return service.confirm(id, (Long) request.getAttribute("userId"), body.version());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handle(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(
                        Map.of(
                                "status",
                                exception.getStatusCode().value(),
                                "message",
                                exception.getReason()));
    }
}
