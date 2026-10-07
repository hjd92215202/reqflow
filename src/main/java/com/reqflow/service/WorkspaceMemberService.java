package com.reqflow.service;

import com.reqflow.dto.WorkspaceMemberView;
import com.reqflow.entity.WorkspaceMember;
import com.reqflow.repository.UserRepository;
import com.reqflow.repository.WorkspaceMemberRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class WorkspaceMemberService {
    @Autowired private WorkspaceMemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceProjectService workspaceProjectService;

    @Transactional(readOnly = true)
    public List<WorkspaceMemberView> list(Long workspaceId, Long userId) {
        workspaceProjectService.requireWorkspaceAccess(workspaceId, userId);
        return memberRepository.findByWorkspaceIdOrderByCreatedAtAsc(workspaceId).stream()
                .map(
                        member ->
                                userRepository
                                        .findById(member.getUserId())
                                        .map(
                                                user ->
                                                        new WorkspaceMemberView(
                                                                user.getId(),
                                                                user.getUsername(),
                                                                user.getNickname() == null
                                                                                || user.getNickname()
                                                                                        .isBlank()
                                                                        ? user.getUsername()
                                                                        : user.getNickname(),
                                                                member.getRole()))
                                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public WorkspaceMemberView add(Long workspaceId, String username, Long actorId) {
        workspaceProjectService.requireWorkspaceOwner(workspaceId, actorId);
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
        }
        var user =
                userRepository
                        .findByUsername(username.trim())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "User not found"));
        if (actorId.equals(user.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Workspace owner is already a member");
        }
        if (memberRepository.existsByWorkspaceIdAndUserId(workspaceId, user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a member");
        }
        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspaceId(workspaceId);
        member.setUserId(user.getId());
        member = memberRepository.save(member);
        return new WorkspaceMemberView(
                user.getId(),
                user.getUsername(),
                user.getNickname() == null || user.getNickname().isBlank()
                        ? user.getUsername()
                        : user.getNickname(),
                member.getRole());
    }

    public void remove(Long workspaceId, Long memberUserId, Long actorId) {
        workspaceProjectService.requireWorkspaceOwner(workspaceId, actorId);
        if (!memberRepository.existsByWorkspaceIdAndUserId(workspaceId, memberUserId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace member not found");
        }
        memberRepository.deleteByWorkspaceIdAndUserId(workspaceId, memberUserId);
    }
}
