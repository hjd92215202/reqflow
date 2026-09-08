package com.reqflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqflow.entity.WikiDocument;
import com.reqflow.service.MarkdownRenderService;
import com.reqflow.service.WikiDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping
public class WikiDocumentController {

    @Autowired
    private WikiDocumentService wikiDocumentService;

    @Autowired
    private MarkdownRenderService markdownRenderService;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    // 2. 全端通用的 Web HTML 免登录只读分享页面（根据随机 Token 访问，服务端直出）
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

            // 使用 Jackson 序列化原文以支持一键复制
            String contentJson = objectMapper.writeValueAsString(rawContent);

            String reqHtml = reqTitle.isEmpty() ? "" : "<span class=\"tag tag-req\">📌 " + escapeHtml(reqTitle) + "</span>";
            String tagHtml = tags.isEmpty() ? "" : "<span class=\"tag tag-warn\">🏷️ " + escapeHtml(tags) + "</span>";

            String template = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>{{DOC_TITLE}} - ReqFlow Wiki</title>
              <link rel="icon" type="image/svg+xml" href="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 512 512'%3E%3Cdefs%3E%3ClinearGradient id='bgGrad' x1='0%25' y1='0%25' x2='0%25' y2='100%25'%3E%3Cstop offset='0%25' stop-color='%232188FF'/%3E%3Cstop offset='100%25' stop-color='%230062E3'/%3E%3C/linearGradient%3E%3ClinearGradient id='waveLight' x1='0%25' y1='0%25' x2='100%25' y2='100%25'%3E%3Cstop offset='0%25' stop-color='%2380CAFF' stop-opacity='0.8'/%3E%3Cstop offset='100%25' stop-color='%2340B0FF' stop-opacity='0.3'/%3E%3C/linearGradient%3E%3C/defs%3E%3Crect width='512' height='512' rx='120' fill='url(%23bgGrad)'/%3E%3Cpath d='M 80 320 C 140 220, 220 340, 310 260 C 370 210, 420 250, 440 230' fill='none' stroke='url(%23waveLight)' stroke-width='32' stroke-linecap='round'/%3E%3Cpath d='M 82 260 C 130 360, 240 160, 360 230 C 400 255, 426 230, 440 216' fill='none' stroke='%23FFFFFF' stroke-width='42' stroke-linecap='round'/%3E%3Ccircle cx='260' cy='205' r='22' fill='%23FFFFFF'/%3E%3C/svg%3E">
              
              <!-- 引入高亮样式库 -->
              <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github.min.css">
              <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>

              <style>
                * { box-sizing: border-box; }
                body { margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif; background-color: #fcfcfb; color: #24292f; line-height: 1.7; -webkit-font-smoothing: antialiased; }
                .share-header { height: 50px; background: #fff; border-bottom: 1px solid rgba(55,53,47,0.09); display: flex; justify-content: space-between; align-items: center; padding: 0 24px; position: sticky; top: 0; z-index: 100; box-shadow: 0 1px 3px rgba(0,0,0,0.02); }
                .share-brand { font-weight: 700; font-size: 15px; display: flex; align-items: center; gap: 8px; }
                .brand-svg-logo { width: 22px; height: 22px; border-radius: 5px; flex-shrink: 0; }
                .badge { font-size: 11px; background: #f0f0f0; color: #666; padding: 2px 8px; border-radius: 4px; font-weight: normal; }
                .btn { background: #fff; border: 1px solid #dcdfe6; color: #606266; padding: 6px 14px; border-radius: 4px; cursor: pointer; font-size: 12px; transition: all 0.15s; }
                .btn:hover { color: #2383e2; border-color: #c6e2ff; background: #ecf5ff; }
                .main-card { max-width: 880px; margin: 32px auto 80px auto; background: #fff; padding: 40px 48px; border-radius: 8px; border: 1px solid rgba(55,53,47,0.08); box-shadow: 0 2px 12px rgba(0,0,0,0.03); }
                .article-title { margin: 0 0 16px 0; font-size: 28px; font-weight: 700; line-height: 1.3; }
                .meta-row { display: flex; justify-content: space-between; flex-wrap: wrap; gap: 12px; font-size: 12px; color: #8c8c8c; border-bottom: 1px solid #f0f0f0; padding-bottom: 16px; margin-bottom: 24px; }
                .meta-left { display: flex; gap: 16px; }
                .meta-right { display: flex; gap: 8px; }
                .tag { font-size: 11px; padding: 2px 8px; border-radius: 3px; }
                .tag-req { background: #e0f0ff; color: #0f73da; }
                .tag-warn { background: #fdecc8; color: #b36b00; }
                
                /* Markdown GitHub 主题标准规则 */
                .markdown-body { font-size: 14.5px; line-height: 1.7; word-break: break-word; }
                .markdown-body h1, .markdown-body h2, .markdown-body h3, .markdown-body h4 { color: #24292f; font-weight: 600; margin-top: 24px; margin-bottom: 14px; line-height: 1.35; }
                .markdown-body h1 { font-size: 1.75em; padding-bottom: 0.3em; border-bottom: 1px solid #d0d7de; }
                .markdown-body h2 { font-size: 1.35em; padding-bottom: 0.3em; border-bottom: 1px solid #d0d7de; }
                .markdown-body h3 { font-size: 1.15em; }
                .markdown-body a { color: #0969da; text-decoration: none; }
                .markdown-body a:hover { text-decoration: underline; }
                .markdown-body code:not(pre code) { padding: 0.2em 0.4em; background: rgba(175, 184, 193, 0.2); border-radius: 6px; font-size: 85%; font-family: ui-monospace, monospace; color: #cf222e; }
                .markdown-body pre { background: #f6f8fa; padding: 14px 16px; border-radius: 6px; border: 1px solid #d0d7de; overflow-x: auto; }
                .markdown-body pre code { background: transparent; padding: 0; font-family: ui-monospace, monospace; font-size: 13px; }
                .markdown-body blockquote { margin: 16px 0; padding: 0 1em; color: #57606a; border-left: 0.25em solid #d0d7de; }
                .markdown-body table { width: 100%; border-collapse: collapse; margin: 16px 0; font-size: 13.5px; }
                .markdown-body table th, .markdown-body table td { border: 1px solid #d0d7de; padding: 8px 13px; }
                .markdown-body table th { background: #f6f8fa; font-weight: 600; }
                .markdown-body table tr:nth-child(2n) { background: #f6f8fa; }
                .markdown-body hr { border: none; height: 1px; background: #d0d7de; margin: 24px 0; }
                .markdown-body input[type="checkbox"] { margin-right: 6px; vertical-align: middle; }
                
                @media (max-width: 768px) { .main-card { padding: 24px 16px; margin: 16px 12px 60px 12px; } .article-title { font-size: 22px; } .share-header { padding: 0 16px; } }
              </style>
            </head>
            <body>
              <header class="share-header">
                <div class="share-brand">
                  <svg class="brand-svg-logo" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">
                    <defs>
                      <linearGradient id="headerBgGrad" x1="0%" y1="0%" x2="0%" y2="100%">
                        <stop offset="0%" stop-color="#2188FF"/>
                        <stop offset="100%" stop-color="#0062E3"/>
                      </linearGradient>
                      <linearGradient id="headerWaveLight" x1="0%" y1="0%" x2="100%" y2="100%">
                        <stop offset="0%" stop-color="#80CAFF" stop-opacity="0.8"/>
                        <stop offset="100%" stop-color="#40B0FF" stop-opacity="0.3"/>
                      </linearGradient>
                    </defs>
                    <rect width="512" height="512" rx="120" fill="url(#headerBgGrad)"/>
                    <path d="M 80 320 C 140 220, 220 340, 310 260 C 370 210, 420 250, 440 230" fill="none" stroke="url(#headerWaveLight)" stroke-width="32" stroke-linecap="round"/>
                    <path d="M 82 260 C 130 360, 240 160, 360 230 C 400 255, 426 230, 440 216" fill="none" stroke="#FFFFFF" stroke-width="42" stroke-linecap="round"/>
                    <circle cx="260" cy="205" r="22" fill="#FFFFFF"/>
                  </svg>
                  <span>ReqFlow Wiki</span>
                  <span class="badge">📖 只读分享</span>
                </div>
                <div>
                  <button class="btn" onclick="copyContent()">📋 复制正文</button>
                </div>
              </header>
              <main class="main-card">
                <h1 class="article-title">{{DOC_TITLE}}</h1>
                <div class="meta-row">
                  <div class="meta-left">
                    <span>👤 作者: <b>{{DOC_AUTHOR}}</b></span>
                    <span>🕒 更新于: {{DOC_TIME}}</span>
                  </div>
                  <div class="meta-right">
                    {{REQ_HTML}}
                    {{TAG_HTML}}
                  </div>
                </div>
                <div id="content" class="markdown-body">
                  {{RENDERED_BODY}}
                </div>
              </main>

              <script>
                const rawMarkdown = {{RAW_JSON_CONTENT}};

                // 初始化代码高亮
                document.addEventListener('DOMContentLoaded', () => {
                  hljs.highlightAll();
                });

                function copyContent() {
                  navigator.clipboard.writeText(rawMarkdown).then(() => {
                    alert('已复制正文 Markdown 内容！');
                  });
                }
              </script>
            </body>
            </html>
            """;

            return template
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

    // 3. 免鉴权公开 JSON 接口 (根据随机 Token 查找)
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

    // 4. 后续常规增删改查接口
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