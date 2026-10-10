package com.reqflow.dto;

import java.time.Instant;

public record VerificationResponse(
        Long id,
        Long requirementId,
        Long stageId,
        Long subTaskId,
        String stageTitle,
        String subTaskTitle,
        String successCriterionId,
        String criterionMethod,
        String criterionTarget,
        String taskDeliverable,
        String taskCompletionCriteria,
        VerificationContent content,
        Long verifiedBy,
        Instant createdAt,
        Instant invalidatedAt,
        String invalidationReason,
        Instant voidedAt,
        Long voidedBy,
        String voidReason,
        boolean valid) {}
