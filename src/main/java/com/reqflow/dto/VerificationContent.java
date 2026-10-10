package com.reqflow.dto;

import java.time.Instant;
import java.util.List;

public record VerificationContent(
        String criterionSnapshot,
        String method,
        String expectedResult,
        String actualResult,
        String resultStatus,
        String waiverReason,
        List<Evidence> evidence,
        Instant verifiedAt) {
    public record Evidence(String title, String url, String description) {}
}
