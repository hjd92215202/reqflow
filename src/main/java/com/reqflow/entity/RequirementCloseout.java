package com.reqflow.entity;

import com.reqflow.dto.CloseoutContent;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "req_requirement_closeout")
public class RequirementCloseout {
    @Id
    @Column(name = "requirement_id")
    private Long requirementId;

    @Column(nullable = false)
    private String status = "DRAFT";

    @Column(nullable = false)
    private long version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_json", nullable = false, columnDefinition = "jsonb")
    private CloseoutContent content = CloseoutContent.empty();

    @Column(name = "wiki_document_id")
    private Long wikiDocumentId;

    @Column(name = "saved_facts_token")
    private String savedFactsToken;

    @Column(name = "completed_facts_token")
    private String completedFactsToken;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by")
    private Long completedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;
}
