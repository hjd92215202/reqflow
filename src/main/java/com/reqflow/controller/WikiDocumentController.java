package com.reqflow.controller;

import com.reqflow.entity.WikiDocument;
import com.reqflow.service.WikiDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping
public class WikiDocumentController {

    @Autowired
    private WikiDocumentService wikiDocumentService;

    // 1. 【核心修复】免鉴权公开 JSON 接口（供前端 WikiShareView.vue 页面异步调用）
    @GetMapping("/api/wikis/share/{id}")
    public ResponseEntity<?> getSharedWikiJsonById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(wikiDocumentService.getWikiDocumentById(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 2. 免鉴权 Web 纯 HTML 页面（浏览器直接直连后端时秒开）
    @GetMapping(value = "/share/wiki/{id}", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    @ResponseBody
    public String renderSharedWikiPage(@PathVariable Long id) {
        try {
            WikiDocument doc = wikiDocumentService.getWikiDocumentById(id);
            String title = doc.getTitle() != null ? doc.getTitle() : "未命名文档";
            String author = doc.getCreatorNickname() != null ? doc.getCreatorNickname() : "管理员";
            String reqTitle = doc.getRequirementTitle() != null ? doc.getRequirementTitle() : "";
            String tags = doc.getTags() != null ? doc.getTags() : "";
            String updateTime = doc.getUpdatedAt() != null ? doc.getUpdatedAt().toString().replace("T", " ") : "";
            if (updateTime.length() > 16) updateTime = updateTime.substring(0, 16);
            String rawContent = doc.getContent() != null ? doc.getContent() : "";

            String jsonContent = rawContent
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\r", "")
                    .replace("\n", "\\n");

            return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>%s - ReqFlow Wiki</title>
              <style>
                * { box-sizing: border-box; }
                body { margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif; background-color: #fcfcfb; color: #37352f; line-height: 1.7; }
                .share-header { height: 50px; background: #fff; border-bottom: 1px solid rgba(55,53,47,0.09); display: flex; justify-content: space-between; align-items: center; padding: 0 24px; position: sticky; top: 0; z-index: 100; }
                .share-brand { font-weight: 700; font-size: 15px; display: flex; align-items: center; gap: 8px; }
                .badge { font-size: 11px; background: #f0f0f0; color: #666; padding: 2px 8px; border-radius: 4px; }
                .btn { background: #fff; border: 1px solid #dcdfe6; color: #606266; padding: 6px 14px; border-radius: 4px; cursor: pointer; font-size: 12px; }
                .btn:hover { color: #2383e2; border-color: #c6e2ff; background: #ecf5ff; }
                .main-card { max-width: 880px; margin: 32px auto 80px auto; background: #fff; padding: 40px 48px; border-radius: 8px; border: 1px solid rgba(55,53,47,0.08); box-shadow: 0 2px 12px rgba(0,0,0,0.03); }
                .article-title { margin: 0 0 16px 0; font-size: 28px; font-weight: 700; line-height: 1.3; }
                .meta-row { display: flex; justify-content: space-between; flex-wrap: wrap; gap: 12px; font-size: 12px; color: #8c8c8c; border-bottom: 1px solid #f0f0f0; padding-bottom: 16px; margin-bottom: 24px; }
                .meta-left { display: flex; gap: 16px; }
                .tag { font-size: 11px; padding: 2px 8px; border-radius: 3px; }
                .tag-req { background: #e0f0ff; color: #0f73da; }
                .tag-warn { background: #fdecc8; color: #b36b00; }
                .markdown-body h1 { font-size: 22px; font-weight: 700; margin: 24px 0 12px 0; padding-bottom: 6px; border-bottom: 1px solid #eaecef; }
                .markdown-body h2 { font-size: 18px; font-weight: 700; margin: 20px 0 10px 0; color: #2383e2; }
                .markdown-body .inline-code { background: #f2f2f1; color: #eb5757; padding: 2px 6px; border-radius: 4px; font-family: monospace; }
                .code-wrapper { background: #282c34; border-radius: 6px; margin: 14px 0; overflow: hidden; }
                .code-header { background: #21252b; color: #abb2bf; font-size: 11px; padding: 4px 12px; }
                .code-block { margin: 0; padding: 14px 16px; color: #abb2bf; font-family: Consolas, Monaco, monospace; font-size: 13px; line-height: 1.5; overflow-x: auto; }
                .markdown-quote { margin: 12px 0; padding: 8px 16px; border-left: 4px solid #2383e2; background: #f7f9fc; color: #606266; }
                .task-item { display: flex; align-items: center; gap: 8px; margin: 6px 0; }
                .task-item.checked { text-decoration: line-through; color: #909399; }
                .markdown-table { width: 100%; border-collapse: collapse; margin: 16px 0; font-size: 13.5px; }
                .markdown-table th, .markdown-table td { border: 1px solid #dcdfe6; padding: 8px 12px; text-align: left; }
                .markdown-table th { background: #f5f7fa; font-weight: 600; }
                .markdown-hr { border: none; height: 1px; background: #e4e7ed; margin: 20px 0; }
                .markdown-link { color: #2383e2; text-decoration: none; }
                @media (max-width: 768px) { .main-card { padding: 24px 16px; margin: 16px 12px 60px 12px; } .article-title { font-size: 22px; } }
              </style>
            </head>
            <body>
              <header class="share-header">
                <div class="share-brand">
                  <span>🌊 ReqFlow Wiki</span>
                  <span class="badge">📖 只读分享</span>
                </div>
                <div>
                  <button class="btn" onclick="copyContent()">📋 复制正文</button>
                </div>
              </header>
              <main class="main-card">
                <h1 class="article-title">%s</h1>
                <div class="meta-row">
                  <div class="meta-left">
                    <span>👤 作者: <b>%s</b></span>
                    <span>🕒 更新于: %s</span>
                  </div>
                  <div>
                    %s
                    %s
                  </div>
                </div>
                <div id="content" class="markdown-body"></div>
              </main>
              <script>
                const rawMarkdown = "%s";
                function renderMarkdown(raw) {
                  if (!raw) return '<div style="color:#999;text-align:center;padding:40px 0;">（文档暂无正文）</div>';
                  let t = raw.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
                  t = t.replace(/```([a-zA-Z0-9_-]*)\\n([\\s\\S]*?)```/g, (m, lang, code) => `<div class="code-wrapper"><div class="code-header">${lang||'code'}</div><pre class="code-block"><code>${code.trim()}</code></pre></div>`);
                  t = t.replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>');
                  t = t.replace(/^###### (.*$)/gim, '<h6>$1</h6>');
                  t = t.replace(/^##### (.*$)/gim, '<h5>$1</h5>');
                  t = t.replace(/^#### (.*$)/gim, '<h4>$1</h4>');
                  t = t.replace(/^### (.*$)/gim, '<h3>$1</h3>');
                  t = t.replace(/^## (.*$)/gim, '<h2>$1</h2>');
                  t = t.replace(/^# (.*$)/gim, '<h1>$1</h1>');
                  t = t.replace(/^(?:---|\\*\\*\\*|___)\\s*$/gim, '<hr class="markdown-hr" />');
                  t = t.replace(/^\\s*-\\s*\\[x\\]\\s+(.*$)/gim, '<div class="task-item checked"><span>✓</span><span>$1</span></div>');
                  t = t.replace(/^\\s*-\\s*\\[\\s*\\]\\s+(.*$)/gim, '<div class="task-item"><span>○</span><span>$1</span></div>');
                  t = t.replace(/^\\> (.*$)/gim, '<blockquote class="markdown-quote">$1</blockquote>');
                  t = t.replace(/^\\s*-\\s+(.*$)/gim, '<div style="margin:4px 0 4px 12px;">• $1</div>');
                  t = t.replace(/^\\s*(\\d+)\\.\\s+(.*$)/gim, '<div style="margin:4px 0 4px 12px;">$1. $2</div>');
                  t = t.replace(/\\*\\*(.*?)\\*\\*/g, '<strong>$1</strong>');
                  t = t.replace(/~~(.*?)~~/g, '<del>$1</del>');
                  t = t.replace(/\\*(.*?)\\*/g, '<em>$1</em>');
                  t = t.replace(/((?:\\|[^\\n]+\\|\\n?)+)/g, (match) => {
                    const lines = match.trim().split('\\n').filter(l => l.trim().length > 0);
                    if (lines.length < 2) return match;
                    let html = '<table class="markdown-table">';
                    lines.forEach((line, index) => {
                      if (line.includes('---')) return;
                      const cols = line.split('|').filter((_, i, arr) => i > 0 && i < arr.length - 1);
                      if (index === 0) { html += '<thead><tr>' + cols.map(c => `<th>${c.trim()}</th>`).join('') + '</tr></thead><tbody>'; }
                      else { html += '<tr>' + cols.map(c => `<td>${c.trim()}</td>`).join('') + '</tr>'; }
                    });
                    html += '</tbody></table>';
                    return html;
                  });
                  t = t.replace(/!\\[([^\\]]*)\\]\\(([^)]+)\\)/g, '<img src="$2" alt="$1" style="max-width:100%%;border-radius:4px;" />');
                  t = t.replace(/\\[([^\\]]+)\\]\\(([^)]+)\\)/g, '<a href="$2" target="_blank" class="markdown-link">$1 🔗</a>');
                  t = t.replace(/\\n\\n/g, '<div style="height:12px;"></div>');
                  t = t.replace(/\\n/g, '<br/>');
                  return t;
                }
                document.getElementById('content').innerHTML = renderMarkdown(rawMarkdown);
                function copyContent() {
                  navigator.clipboard.writeText(rawMarkdown).then(() => { alert('已复制正文 Markdown 内容！'); });
                }
              </script>
            </body>
            </html>
            """.formatted(
                    title,
                    title,
                    author,
                    updateTime,
                    reqTitle.isEmpty() ? "" : "<span class=\"tag tag-req\">📌 " + reqTitle + "</span>",
                    tags.isEmpty() ? "" : "<span class=\"tag tag-warn\">🏷️ " + tags + "</span>",
                    jsonContent
            );
        } catch (Exception e) {
            return "<h3 style='text-align:center;margin-top:50px;color:#999;'>未找到该分享文档或已被删除</h3>";
        }
    }

    // 后续常规接口
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