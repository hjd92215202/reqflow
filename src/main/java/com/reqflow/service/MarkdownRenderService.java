package com.reqflow.service;

import java.util.Arrays;
import java.util.List;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Service;

@Service
public class MarkdownRenderService {

    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownRenderService() {
        List<Extension> extensions =
                Arrays.asList(
                        TablesExtension.create(),
                        StrikethroughExtension.create(),
                        AutolinkExtension.create(),
                        HeadingAnchorExtension.create(),
                        TaskListItemsExtension.create());

        this.parser = Parser.builder().extensions(extensions).build();

        this.renderer =
                HtmlRenderer.builder()
                        .extensions(extensions)
                        .escapeHtml(true) // 彻底转义原始非法 HTML 标签，杜绝 XSS
                        .build();
    }

    /**
     * 将 Markdown 源码渲染为服务端语义 HTML
     *
     * @param markdownContent Markdown 原始内容
     * @return 净化后的标准 HTML 字符串
     */
    public String renderToHtml(String markdownContent) {
        if (markdownContent == null || markdownContent.trim().isEmpty()) {
            return "<div style=\"color:#8c8c8c;text-align:center;padding:60px"
                    + " 0;font-style:italic;\">（文档暂无正文内容）</div>";
        }
        Node document = parser.parse(markdownContent);
        return renderer.render(document);
    }
}
