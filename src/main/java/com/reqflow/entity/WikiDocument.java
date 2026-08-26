package com.reqflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "req_wiki_document", indexes = {
        @Index(name = "idx_wiki_requirement_id", columnList = "requirement_id"),
        @Index(name = "idx_wiki_parent_id", columnList = "parent_id")
})
public class WikiDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requirement_id")
    private Long requirementId; // 关联需求ID (为空表示全局公共经验)

    @Column(name = "parent_id")
    private Long parentId; // 父文档ID (支持树形层级)

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content; // 文档内容 (Markdown / HTML)

    private String tags; // 逗号分隔标签，如: "踩坑记录,技术方案"

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Transient
    private String creatorNickname; // 动态冗余展示创建人昵称

    @Transient
    private String requirementTitle; // 动态冗余展示关联需求名称
}