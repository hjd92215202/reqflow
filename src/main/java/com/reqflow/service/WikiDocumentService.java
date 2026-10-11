package com.reqflow.service;

import com.reqflow.entity.WikiDocument;
import java.util.List;

public interface WikiDocumentService {
    List<WikiDocument> getWikiDocuments(Long requirementId, Long userId);

    WikiDocument getWikiDocumentById(Long id, Long userId);

    WikiDocument createWikiDocument(WikiDocument document, Long userId);

    WikiDocument updateWikiDocument(Long id, WikiDocument details, Long userId);

    void deleteWikiDocument(Long id, Long userId);

    // 新增安全分享方法
    String getOrCreateShareToken(Long id, Long userId);

    WikiDocument getWikiDocumentByShareToken(String shareToken);
}
