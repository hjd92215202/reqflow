package com.reqflow.controller;

import com.reqflow.entity.Discussion;
import com.reqflow.service.DiscussionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/discussions")
public class DiscussionController {

    @Autowired
    private DiscussionService discussionService;

    @GetMapping("/requirement/{reqId}")
    public ResponseEntity<?> getByRequirement(@PathVariable Long reqId) {
        return ResponseEntity.ok(discussionService.getDiscussionsByRequirement(reqId));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Discussion discussion, HttpServletRequest request) {
        var userId = (Long) request.getAttribute("userId");
        var created = discussionService.createDiscussion(discussion, userId);
        return ResponseEntity.ok(created);
    }
}