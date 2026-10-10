package com.reqflow.dto;

public record VerificationCreateRequest(
        String clientRequestId,
        Long stageId,
        Long subTaskId,
        String successCriterionId,
        Long definitionVersion,
        String taskDeliverable,
        String taskCompletionCriteria,
        VerificationContent content) {}
