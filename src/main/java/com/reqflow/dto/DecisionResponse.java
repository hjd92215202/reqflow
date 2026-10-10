package com.reqflow.dto;

import java.time.LocalDateTime;

public record DecisionResponse(
        Long id,
        Long requirementId,
        Long stageId,
        Long subTaskId,
        String stageTitle,
        String subTaskTitle,
        DecisionContent content,
        long version,
        Long supersedesDecisionId,
        Long supersededByDecisionId,
        Long createdBy,
        Long updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
