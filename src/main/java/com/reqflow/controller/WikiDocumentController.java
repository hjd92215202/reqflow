package com.reqflow.controller;

import com.reqflow.entity.WikiDocument;
import com.reqflow.service.WikiDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wikis")
public class WikiDocumentController {

    @Autowired
    private WikiDocumentService wikiDocumentService;

    @GetMapping
    public ResponseEntity<?> getWikis(@RequestParam(required = false) Long requirementId) {
        return ResponseEntity.ok(wikiDocumentService.getWikiDocuments(requirementId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(wikiDocumentService.getWikiDocumentById(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody WikiDocument document, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return ResponseEntity.ok(wikiDocumentService.createWikiDocument(document, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody WikiDocument document) {
        try {
            return ResponseEntity.ok(wikiDocumentService.updateWikiDocument(id, document));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            wikiDocumentService.deleteWikiDocument(id);
            return ResponseEntity.ok("Delete successful");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}