package com.reqflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqflow.entity.WikiDocument;
import com.reqflow.service.MarkdownRenderService;
import com.reqflow.service.WikiDocumentService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping
public class WikiDocumentController {

    @Autowired
    private WikiDocumentService wikiDocumentService;

    @Autowired
    private MarkdownRenderService markdownRenderService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String shareTemplate;

    @PostConstruct
    public void initTemplate() {
        try {
            var resource = new ClassPathResource("templates/share-template.html");
            this.shareTemplate = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            this.shareTemplate = "<h3>Error loading share template</h3>";
        }
    }

    // 1. 获取/生成指定文档的安全分享 Token (需要登录认证)
    @PostMapping("/api/wikis/{id}/share-token")
    public ResponseEntity<?> getOrCreateShareToken(@PathVariable Long id) {
        try {
            String token = wikiDocumentService.getOrCreateShareToken(id);
            return ResponseEntity.ok(Map.of("shareToken", token));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 2. 全端通用的 Web HTML 免登录只读分享页面（根据随机 Token 访问，服务端模板直出）
    @GetMapping(value = "/share/wiki/{token}", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    @ResponseBody
    public String renderSharedWikiPage(@PathVariable String token) {
        try {
            WikiDocument doc = wikiDocumentService.getWikiDocumentByShareToken(token);
            if (doc == null) {
                return "<h3 style='text-align:center;margin-top:50px;color:#999;'>未找到该分享文档或已被删除</h3>";
            }

            String title = doc.getTitle() != null ? doc.getTitle() : "未命名文档";
            String author = doc.getCreatorNickname() != null ? doc.getCreatorNickname() : "管理员";
            String reqTitle = doc.getRequirementTitle() != null ? doc.getRequirementTitle() : "";
            String tags = doc.getTags() != null ? doc.getTags() : "";
            String updateTime = doc.getUpdatedAt() != null ? doc.getUpdatedAt().toString().replace("T", " ") : "";
            if (updateTime.length() > 16) updateTime = updateTime.substring(0, 16);
            String rawContent = doc.getContent() != null ? doc.getContent() : "";

            // 服务端直接转换为语义安全 HTML
            String renderedBodyHtml = markdownRenderService.renderToHtml(rawContent);

            // 深度安全防护：序列化为 JSON 并转义 HTML 敏感字符，避免在 <script> 块中提前闭合
            String contentJson = objectMapper.writeValueAsString(rawContent)
                    .replace("<", "\\u003c")
                    .replace(">", "\\u003e")
                    .replace("&", "\\u0026");

            String reqHtml = reqTitle.isEmpty() ? "" : "<span class=\"tag tag-req\">📌 " + escapeHtml(reqTitle) + "</span>";
            String tagHtml = tags.isEmpty() ? "" : "<span class=\"tag tag-warn\">🏷️ " + escapeHtml(tags) + "</span>";

            return shareTemplate
                    .replace("{{DOC_TITLE}}", escapeHtml(title))
                    .replace("{{DOC_AUTHOR}}", escapeHtml(author))
                    .replace("{{DOC_TIME}}", escapeHtml(updateTime))
                    .replace("{{REQ_HTML}}", reqHtml)
                    .replace("{{TAG_HTML}}", tagHtml)
                    .replace("{{RENDERED_BODY}}", renderedBodyHtml)
                    .replace("{{RAW_JSON_CONTENT}}", contentJson);

        } catch (Exception e) {
            return "<h3 style='text-align:center;margin-top:50px;color:#999;'>未找到该分享文档或已被删除</h3>";
        }
    }

    // 3. 免鉴权公开 JSON 接口 (根据随机 Token 查找，提供给第三方或客户端)
    @GetMapping("/api/wikis/share/{token}")
    public ResponseEntity<?> getSharedWikiJsonByToken(@PathVariable String token) {
        try {
            return ResponseEntity.ok(wikiDocumentService.getWikiDocumentByShareToken(token));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    // 4. 常规增删改查接口
    @GetMapping("/api/wikis")
    public ResponseEntity<?> getWikis(@RequestParam(required = false) Long requirementId) {
        return ResponseEntity.ok(wikiDocumentService.getWikiDocuments(requirementId));
    }

    @GetMapping("/api/wikis/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(wikiDocumentService.getWikiDocumentById(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/wikis")
    public ResponseEntity<?> create(@RequestBody WikiDocument document, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return ResponseEntity.ok(wikiDocumentService.createWikiDocument(document, userId));
    }

    @PutMapping("/api/wikis/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody WikiDocument document) {
        try {
            return ResponseEntity.ok(wikiDocumentService.updateWikiDocument(id, document));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/api/wikis/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            wikiDocumentService.deleteWikiDocument(id);
            return ResponseEntity.ok("Delete successful");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}