package com.reqflow.entity;

import com.reqflow.dto.DecisionContent;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@DynamicUpdate
@Table(name = "req_decision_record")
public class DecisionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requirement_id", nullable = false)
    private Long requirementId;

    @Column(name = "stage_id")
    private Long stageId;

    @Column(name = "sub_task_id")
    private Long subTaskId;

    @Column(name = "stage_title")
    private String stageTitle;

    @Column(name = "sub_task_title")
    private String subTaskTitle;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String context;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options_json", nullable = false)
    private List<DecisionContent.Option> options = List.of();

    @Column(name = "chosen_option", columnDefinition = "TEXT")
    private String chosenOption;

    @Column(columnDefinition = "TEXT")
    private String rationale;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "assumptions_json", nullable = false)
    private List<String> assumptions = List.of();

    @Column(length = 16)
    private String confidence;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "review_date")
    private LocalDate reviewDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_assistance_json")
    private DecisionContent.AiAssistance aiAssistance;

    @Column(name = "supersedes_decision_id")
    private Long supersedesDecisionId;

    @Column(name = "superseded_by_decision_id")
    private Long supersededByDecisionId;

    @Column(nullable = false)
    private long version;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
