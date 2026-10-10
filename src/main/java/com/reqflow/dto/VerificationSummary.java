package com.reqflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record VerificationSummary(
        long definitionVersion,
        List<Criterion> criteria,
        Map<String, Long> distribution,
        List<Task> tasks,
        long doneUnverified,
        List<StageCount> stages) {
    public record Latest(Long id, String resultStatus, Instant createdAt) {}

    public record Criterion(
            String id,
            String description,
            String suggestedMethod,
            String targetValue,
            Latest latest) {}

    public record Task(
            Long id,
            Long stageId,
            String title,
            String status,
            String deliverable,
            String completionCriteria,
            Latest latest) {}

    public record StageCount(Long stageId, String title, long doneUnverified) {}
}
