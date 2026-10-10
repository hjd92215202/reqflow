package com.reqflow.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.reqflow.dto.RequirementDefinition;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Entity
@DynamicUpdate
@Table(name = "req_requirement")
public class Requirement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String description;
    private String status;
    private String priority;

    @Column(name = "start_date")
    private LocalDate startDate; // 新增需求开始时间

    @Column(name = "end_date")
    private LocalDate endDate; // 新增需求结束时间

    @Column(name = "creator_id")
    private Long creatorId;

    @Column(name = "project_id")
    private Long projectId;

    // Dedicated definition endpoints own these fields; legacy entity requests cannot edit them.
    @JsonIgnore
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition_json", nullable = false, columnDefinition = "jsonb")
    private RequirementDefinition definition = RequirementDefinition.empty();

    @JsonIgnore
    @Column(name = "definition_version", nullable = false)
    private long definitionVersion;

    @JsonIgnore
    @Column(name = "definition_confirmed_at")
    private Instant definitionConfirmedAt;

    @JsonIgnore
    @Column(name = "definition_confirmed_by")
    private Long definitionConfirmedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
