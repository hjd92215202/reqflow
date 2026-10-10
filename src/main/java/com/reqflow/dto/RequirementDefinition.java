package com.reqflow.dto;

import java.util.List;

/** Structured content only; confirmation metadata is controlled by the server. */
public record RequirementDefinition(
        String problemStatement,
        String targetOutcome,
        List<String> constraints,
        List<String> assumptions,
        List<String> outOfScope,
        List<SuccessCriterion> successCriteria) {

    public static RequirementDefinition empty() {
        return new RequirementDefinition("", "", List.of(), List.of(), List.of(), List.of());
    }

    public record SuccessCriterion(
            String id, String description, String suggestedMethod, String targetValue) {}
}
