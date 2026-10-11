package com.reqflow.service.impl;

import com.reqflow.entity.Requirement;
import com.reqflow.entity.User;
import com.reqflow.entity.WikiDocument;
import com.reqflow.repository.RequirementRepository;
import com.reqflow.repository.UserRepository;
import com.reqflow.repository.WikiDocumentRepository;
import com.reqflow.service.ActivityLogService;
import com.reqflow.service.RequirementAccessService;
import com.reqflow.service.WikiDocumentService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WikiDocumentServiceImpl implements WikiDocumentService {

    @Autowired private WikiDocumentRepository wikiDocumentRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private RequirementRepository requirementRepository;

    @Autowired private RequirementAccessService requirementAccessService;
    @Autowired private ActivityLogService activity;

    @Override
    @Transactional(readOnly = true)
    public List<WikiDocument> getWikiDocuments(Long requirementId, Long userId) {
        List<WikiDocument> list;
        if (requirementId != null) {
            requirementAccessService.requireRequirementOwner(requirementId, userId);
            list = wikiDocumentRepository.findByRequirementIdOrderByIdDesc(requirementId);
        } else {
            list = wikiDocumentRepository.findAccessibleByUser(userId);
        }

        Map<Long, User> userMap =
                userRepository
                        .findAllById(
                                list.stream()
                                        .map(WikiDocument::getCreatorId)
                                        .filter(java.util.Objects::nonNull)
                                        .distinct()
                                        .toList())
                        .stream()
                        .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        Map<Long, Requirement> reqMap =
                requirementRepository
                        .findAllById(
                                list.stream()
                                        .map(WikiDocument::getRequirementId)
                                        .filter(java.util.Objects::nonNull)
                                        .distinct()
                                        .toList())
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        Requirement::getId, Function.identity(), (a, b) -> a));

        for (WikiDocument doc : list) {
            if (doc.getCreatorId() != null && userMap.containsKey(doc.getCreatorId())) {
                doc.setCreatorNickname(userMap.get(doc.getCreatorId()).getNickname());
            }
            if (doc.getRequirementId() != null && reqMap.containsKey(doc.getRequirementId())) {
                doc.setRequirementTitle(reqMap.get(doc.getRequirementId()).getTitle());
            }
        }
        return list;
    }

    @Override
    @Transactional(readOnly = true)
    public WikiDocument getWikiDocumentById(Long id, Long userId) {
        WikiDocument doc = requireDocumentMutationAccess(id, userId);
        if (doc.getCreatorId() != null) {
            userRepository
                    .findById(doc.getCreatorId())
                    .ifPresent(user -> doc.setCreatorNickname(user.getNickname()));
        }
        if (doc.getRequirementId() != null) {
            requirementRepository
                    .findById(doc.getRequirementId())
                    .ifPresent(req -> doc.setRequirementTitle(req.getTitle()));
        }
        return doc;
    }

    @Override
    public WikiDocument createWikiDocument(WikiDocument document, Long userId) {
        if (document.getRequirementId() != null) {
            requirementAccessService.requireRequirementOwner(document.getRequirementId(), userId);
        }
        requireParentMutationAccess(document.getParentId(), userId);
        validate(document);
        String key = document.getClientRequestId();
        String fingerprint = null;
        if (key != null) {
            try {
                if (!UUID.fromString(key).toString().equals(key))
                    throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) {
                throw bad("提交标识无效");
            }
            userRepository.findForWikiCreate(userId).orElseThrow(() -> bad("用户不存在"));
            fingerprint = fingerprint(document);
            var retry = wikiDocumentRepository.findByCreatorIdAndClientRequestId(userId, key);
            if (retry.isPresent()) {
                requireDocumentMutationAccess(retry.get().getId(), userId);
                if (!fingerprint.equals(retry.get().getRequestFingerprint()))
                    throw new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.CONFLICT, "提交标识已用于其他内容");
                return retry.get();
            }
        }
        document.setId(null);
        document.setShareToken(null);
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(document.getCreatedAt());
        document.setRequestFingerprint(fingerprint);
        document.setCreatorId(userId);
        if (document.getTitle() == null || document.getTitle().trim().isEmpty()) {
            document.setTitle("未命名知识文档");
        }
        var saved = wikiDocumentRepository.saveAndFlush(document);
        record(saved.getRequirementId(), saved, userId, "WIKI_CREATE", "创建");
        return saved;
    }

    @Override
    public WikiDocument updateWikiDocument(Long id, WikiDocument details, Long userId) {
        WikiDocument existing = requireDocumentMutationAccess(id, userId);
        validate(details);
        Long oldRequirement = existing.getRequirementId();
        if (details.getRequirementId() != null
                && !details.getRequirementId().equals(existing.getRequirementId())) {
            requirementAccessService.requireRequirementOwner(details.getRequirementId(), userId);
        }
        requireParentMutationAccess(details.getParentId(), userId);
        Long ancestor = details.getParentId();
        java.util.Set<Long> visited = new java.util.HashSet<>();
        while (ancestor != null) {
            if (ancestor.equals(id) || !visited.add(ancestor)) throw bad("文档目录不能循环关联");
            ancestor =
                    wikiDocumentRepository
                            .findById(ancestor)
                            .orElseThrow(() -> bad("父文档不存在"))
                            .getParentId();
        }
        if (details.getRequirementId() == null && !userId.equals(existing.getCreatorId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Permission denied");
        }
        existing.setTitle(details.getTitle());
        existing.setContent(details.getContent());
        existing.setTags(details.getTags());
        if (details.isDocumentTypeProvided()) existing.setDocumentType(details.getDocumentType());
        existing.setRequirementId(details.getRequirementId());
        existing.setParentId(details.getParentId());
        existing.setUpdatedAt(LocalDateTime.now());
        var saved = wikiDocumentRepository.saveAndFlush(existing);
        if (!java.util.Objects.equals(oldRequirement, saved.getRequirementId()))
            record(oldRequirement, saved, userId, "WIKI_UNLINK", "解除关联");
        record(saved.getRequirementId(), saved, userId, "WIKI_UPDATE", "更新");
        return saved;
    }

    @Override
    public void deleteWikiDocument(Long id, Long userId) {
        var document = requireDocumentMutationAccess(id, userId);
        List<WikiDocument> children = wikiDocumentRepository.findByParentId(id);
        for (WikiDocument child : children) {
            deleteWikiDocument(child.getId(), userId);
        }
        record(document.getRequirementId(), document, userId, "WIKI_DELETE", "删除");
        wikiDocumentRepository.deleteById(id);
    }

    // 核心实现：生成或获取不可预测的高强度 16 位随机分享令牌
    @Override
    public String getOrCreateShareToken(Long id, Long userId) {
        WikiDocument doc = requireDocumentMutationAccess(id, userId);
        if (doc.getShareToken() == null || doc.getShareToken().trim().isEmpty()) {
            // 生成 16 位全局唯一的十六进制随机字符串
            String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            doc.setShareToken(token);
            wikiDocumentRepository.save(doc);
        }
        return doc.getShareToken();
    }

    private WikiDocument requireDocumentMutationAccess(Long id, Long userId) {
        WikiDocument document =
                wikiDocumentRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new org.springframework.web.server.ResponseStatusException(
                                                org.springframework.http.HttpStatus.FORBIDDEN,
                                                "无权访问文档或文档不存在"));
        if (document.getRequirementId() != null) {
            requirementAccessService.requireRequirementOwner(document.getRequirementId(), userId);
            return document;
        }
        if (userId != null && userId.equals(document.getCreatorId())) {
            return document;
        }
        requirementAccessService.requireRequirementOwner(document.getRequirementId(), userId);
        return document;
    }

    private void requireParentMutationAccess(Long parentId, Long userId) {
        if (parentId != null) {
            requireDocumentMutationAccess(parentId, userId);
        }
    }

    private org.springframework.web.server.ResponseStatusException bad(String message) {
        return new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, message);
    }

    private void validate(WikiDocument doc) {
        if (doc.getTitle() != null && doc.getTitle().length() > 255) throw bad("文档标题不能超过 255 字符");
        if (doc.getTags() != null && doc.getTags().length() > 255) throw bad("标签不能超过 255 字符");
        if (doc.getDocumentType() != null
                && !java.util.Set.of(
                                "GENERAL",
                                "TECHNICAL_DESIGN",
                                "PITFALL",
                                "RETROSPECTIVE",
                                "CHANGELOG",
                                "EXPERIMENT",
                                "PRACTICE",
                                "OTHER")
                        .contains(doc.getDocumentType())) throw bad("文档类型无效");
    }

    private String fingerprint(WikiDocument doc) {
        try {
            var content =
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .writeValueAsBytes(
                                    java.util.Arrays.asList(
                                            doc.getRequirementId(),
                                            doc.getParentId(),
                                            doc.getTitle(),
                                            doc.getContent(),
                                            doc.getTags(),
                                            doc.getDocumentType()));
            return java.util.HexFormat.of()
                    .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void record(
            Long requirement, WikiDocument document, Long user, String action, String verb) {
        if (requirement != null)
            activity.record(
                    requirement,
                    user,
                    "WIKI",
                    document.getId(),
                    action,
                    verb + "了知识文档「" + document.getTitle() + "」");
    }

    // 核心实现：仅根据随机 Token 查找文档（完全杜绝通过递增 ID 穷举猜测）
    @Override
    @Transactional(readOnly = true)
    public WikiDocument getWikiDocumentByShareToken(String shareToken) {
        WikiDocument doc =
                wikiDocumentRepository
                        .findByShareToken(shareToken)
                        .orElseThrow(() -> new RuntimeException("该分享文档不存在或已被撤销"));
        if (doc.getCreatorId() != null) {
            userRepository
                    .findById(doc.getCreatorId())
                    .ifPresent(user -> doc.setCreatorNickname(user.getNickname()));
        }
        if (doc.getRequirementId() != null) {
            requirementRepository
                    .findById(doc.getRequirementId())
                    .ifPresent(req -> doc.setRequirementTitle(req.getTitle()));
        }
        return doc;
    }
}
