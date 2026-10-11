package com.reqflow.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import org.hibernate.annotations.DynamicUpdate;

@Data
@Entity
@DynamicUpdate
@Table(
        name = "req_wiki_document",
        indexes = {
            @Index(name = "idx_wiki_requirement_id", columnList = "requirement_id"),
            @Index(name = "idx_wiki_parent_id", columnList = "parent_id"),
            @Index(name = "idx_wiki_share_token", columnList = "share_token") // 新增索引加速检索
        })
public class WikiDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requirement_id")
    private Long requirementId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String tags;

    @Column(name = "document_type", length = 32)
    private String documentType;

    @Transient @JsonIgnore private boolean documentTypeProvided;

    public void setDocumentType(String value) {
        documentType = value;
        documentTypeProvided = true;
    }

    @Column(name = "client_request_id", length = 36)
    private String clientRequestId;

    @JsonIgnore
    @Column(name = "request_fingerprint", length = 64)
    private String requestFingerprint;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    // 核心安全字段：随机生成的分享令牌 (为空代表从未对外分享)
    @Column(name = "share_token", unique = true)
    private String shareToken;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Transient private String creatorNickname;

    @Transient private String requirementTitle;
}
