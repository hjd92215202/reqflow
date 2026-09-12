package com.reqflow.service.impl;

import com.reqflow.entity.Requirement;
import com.reqflow.entity.User;
import com.reqflow.entity.WikiDocument;
import com.reqflow.repository.RequirementRepository;
import com.reqflow.repository.UserRepository;
import com.reqflow.repository.WikiDocumentRepository;
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

    @Override
    @Transactional(readOnly = true)
    public List<WikiDocument> getWikiDocuments(Long requirementId) {
        List<WikiDocument> list;
        if (requirementId != null) {
            list = wikiDocumentRepository.findByRequirementIdOrderByIdDesc(requirementId);
        } else {
            list = wikiDocumentRepository.findAllByOrderByIdDesc();
        }

        Map<Long, User> userMap =
                userRepository.findAll().stream()
                        .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        Map<Long, Requirement> reqMap =
                requirementRepository.findAll().stream()
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
    public WikiDocument getWikiDocumentById(Long id) {
        WikiDocument doc =
                wikiDocumentRepository
                        .findById(id)
                        .orElseThrow(() -> new RuntimeException("Wiki document not found"));
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
        document.setCreatorId(userId);
        if (document.getTitle() == null || document.getTitle().trim().isEmpty()) {
            document.setTitle("未命名知识文档");
        }
        return wikiDocumentRepository.save(document);
    }

    @Override
    public WikiDocument updateWikiDocument(Long id, WikiDocument details) {
        WikiDocument existing =
                wikiDocumentRepository
                        .findById(id)
                        .orElseThrow(() -> new RuntimeException("Wiki document not found"));
        existing.setTitle(details.getTitle());
        existing.setContent(details.getContent());
        existing.setTags(details.getTags());
        existing.setRequirementId(details.getRequirementId());
        existing.setParentId(details.getParentId());
        existing.setUpdatedAt(LocalDateTime.now());
        return wikiDocumentRepository.save(existing);
    }

    @Override
    public void deleteWikiDocument(Long id) {
        List<WikiDocument> children = wikiDocumentRepository.findByParentId(id);
        for (WikiDocument child : children) {
            deleteWikiDocument(child.getId());
        }
        wikiDocumentRepository.deleteById(id);
    }

    // 核心实现：生成或获取不可预测的高强度 16 位随机分享令牌
    @Override
    public String getOrCreateShareToken(Long id) {
        WikiDocument doc =
                wikiDocumentRepository
                        .findById(id)
                        .orElseThrow(() -> new RuntimeException("Wiki document not found"));
        if (doc.getShareToken() == null || doc.getShareToken().trim().isEmpty()) {
            // 生成 16 位全局唯一的十六进制随机字符串
            String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            doc.setShareToken(token);
            wikiDocumentRepository.save(doc);
        }
        return doc.getShareToken();
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
