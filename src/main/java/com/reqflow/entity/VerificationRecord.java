package com.reqflow.entity;

import com.reqflow.dto.VerificationContent;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Setter
@DynamicUpdate
@Table(name = "req_verification_record")
public class VerificationRecord {
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

    @Column(name = "success_criterion_id")
    private String successCriterionId;

    @Column(name = "criterion_snapshot", columnDefinition = "TEXT", nullable = false)
    private String criterionSnapshot;

    @Column(name = "criterion_method", columnDefinition = "TEXT")
    private String criterionMethod;

    @Column(name = "criterion_target", columnDefinition = "TEXT")
    private String criterionTarget;

    @Column(name = "task_deliverable", columnDefinition = "TEXT")
    private String taskDeliverable;

    @Column(name = "task_completion_criteria", columnDefinition = "TEXT")
    private String taskCompletionCriteria;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String method;

    @Column(name = "expected_result", columnDefinition = "TEXT")
    private String expectedResult;

    @Column(name = "actual_result", columnDefinition = "TEXT", nullable = false)
    private String actualResult;

    @Column(name = "result_status", length = 20, nullable = false)
    private String resultStatus;

    @Column(name = "waiver_reason", columnDefinition = "TEXT")
    private String waiverReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_json", nullable = false)
    private List<VerificationContent.Evidence> evidence;

    @Column(name = "verified_by", nullable = false)
    private Long verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Column(name = "invalidation_reason", columnDefinition = "TEXT")
    private String invalidationReason;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by")
    private Long voidedBy;

    @Column(name = "void_reason", columnDefinition = "TEXT")
    private String voidReason;

    @Column(name = "client_request_id", length = 36, nullable = false)
    private String clientRequestId;

    @Column(name = "request_fingerprint", length = 64, nullable = false)
    private String requestFingerprint;
}
