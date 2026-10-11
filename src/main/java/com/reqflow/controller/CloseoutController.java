package com.reqflow.controller;

import com.reqflow.dto.CloseoutResponse;
import com.reqflow.service.CloseoutService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/requirements/{id}/closeout")
public class CloseoutController {
    private final CloseoutService service;

    public CloseoutController(CloseoutService service) {
        this.service = service;
    }

    @GetMapping
    public CloseoutResponse get(@PathVariable Long id, HttpServletRequest request) {
        return service.get(id, user(request));
    }

    @PutMapping
    public CloseoutResponse save(
            @PathVariable Long id,
            @RequestBody CloseoutService.Save body,
            HttpServletRequest request) {
        return service.save(id, user(request), body, false);
    }

    @PostMapping("/complete")
    public CloseoutResponse complete(
            @PathVariable Long id,
            @RequestBody CloseoutService.Save body,
            HttpServletRequest request) {
        return service.save(id, user(request), body, true);
    }

    public record Reopen(Long version) {}

    @PostMapping("/reopen")
    public CloseoutResponse reopen(
            @PathVariable Long id, @RequestBody Reopen body, HttpServletRequest request) {
        return service.reopen(id, user(request), body.version());
    }

    private Long user(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> error(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("status", e.getStatusCode().value(), "message", e.getReason()));
    }
}
