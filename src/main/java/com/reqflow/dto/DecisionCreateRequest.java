package com.reqflow.dto;

public record DecisionCreateRequest(Long stageId, Long subTaskId, DecisionContent content) {}
