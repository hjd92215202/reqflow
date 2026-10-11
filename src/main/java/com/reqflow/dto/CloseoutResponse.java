package com.reqflow.dto;

import java.time.Instant;
import java.util.List;

public record CloseoutResponse(
        Long requirementId, Record record, Facts facts, String factsToken, boolean needsReview) {
    public record Record(
            String status,
            long version,
            CloseoutContent content,
            Instant completedAt,
            Long completedBy,
            Instant updatedAt,
            Long updatedBy) {}

    public record Issue(
            String key,
            String kind,
            String title,
            String status,
            Long stageId,
            Long subTaskId,
            Long decisionId,
            String criterionId) {}

    public record Wiki(Long id, String title, String documentType) {}

    public record Facts(
            String title,
            String description,
            RequirementDefinition definition,
            Instant definitionConfirmedAt,
            VerificationSummary verification,
            List<DecisionResponse> decisions,
            List<VerificationResponse> latestVerifications,
            List<Issue> issues,
            List<Wiki> wikis) {}
}
