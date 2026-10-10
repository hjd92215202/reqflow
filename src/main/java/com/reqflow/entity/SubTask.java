package com.reqflow.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Entity
@DynamicUpdate
@Table(
        name = "req_sub_task",
        indexes = {
            @Index(name = "idx_subtask_stage_id", columnList = "stage_id"),
            @Index(name = "idx_subtask_parent_id", columnList = "parent_id")
        })
public class SubTask {
    @com.fasterxml.jackson.annotation.JsonProperty(
            access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    @Column(name = "repair_verification_id")
    private Long repairVerificationId;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "repair_request_id", length = 36)
    private String repairRequestId;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stage_id", nullable = false)
    private Long stageId;

    @Column(name = "parent_id")
    private Long parentId; // 自关联父级ID

    @Column(nullable = false)
    private String title;

    private String assignee;

    private String status = "TODO"; // TODO, IN_PROGRESS, DONE

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(columnDefinition = "TEXT")
    private String note = "";

    @Column(columnDefinition = "TEXT")
    private String deliverable;

    @Column(name = "completion_criteria", columnDefinition = "TEXT")
    private String completionCriteria;

    // 原生利用 Hibernate 6 映射 PostgreSQL 的 JSONB 字段
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields")
    private Map<String, Object> customFields = new HashMap<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
