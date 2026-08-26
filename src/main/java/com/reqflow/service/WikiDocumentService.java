package com.reqflow.service;

import com.reqflow.entity.WikiDocument;
import java.util.List;

public interface WikiDocumentService {
    List<WikiDocument> getWikiDocuments(Long requirementId);
    WikiDocument getWikiDocumentById(Long id);
    WikiDocument createWikiDocument(WikiDocument document, Long userId);
    WikiDocument updateWikiDocument(Long id, WikiDocument details);
    void deleteWikiDocument(Long id);
}