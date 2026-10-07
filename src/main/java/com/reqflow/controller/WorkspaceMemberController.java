package com.reqflow.controller;

import com.reqflow.dto.AddWorkspaceMemberRequest;
import com.reqflow.dto.WorkspaceMembersResult;
import com.reqflow.service.WorkspaceMemberService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/members")
public class WorkspaceMemberController {
    @Autowired private WorkspaceMemberService memberService;

    @GetMapping
    public ResponseEntity<WorkspaceMembersResult> list(
            @PathVariable Long workspaceId, HttpServletRequest request) {
        return ResponseEntity.ok(memberService.list(workspaceId, userId(request)));
    }

    @PostMapping
    public ResponseEntity<?> add(
            @PathVariable Long workspaceId,
            @RequestBody AddWorkspaceMemberRequest body,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    memberService.add(workspaceId, body.username(), userId(request)));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        }
    }

    @DeleteMapping("/{memberUserId}")
    public ResponseEntity<?> remove(
            @PathVariable Long workspaceId,
            @PathVariable Long memberUserId,
            HttpServletRequest request) {
        try {
            memberService.remove(workspaceId, memberUserId, userId(request));
            return ResponseEntity.ok().build();
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        }
    }

    private Long userId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }
}
