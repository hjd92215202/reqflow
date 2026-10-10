package com.reqflow.dto;

import java.time.Instant;

public record DefinitionResponse(
        RequirementDefinition definition,
        String state,
        long version,
        Instant confirmedAt,
        Long confirmedBy) {}
