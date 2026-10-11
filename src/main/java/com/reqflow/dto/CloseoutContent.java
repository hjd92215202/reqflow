package com.reqflow.dto;

import java.util.List;

public record CloseoutContent(
        String outcome,
        String conclusion,
        List<Disposition> dispositions,
        String nextActions,
        String aiUse,
        String humanJudgment,
        Long wikiDocumentId) {
    public record Disposition(String key, String handling, String reason) {}

    public static CloseoutContent empty() {
        return new CloseoutContent(null, "", List.of(), null, null, null, null);
    }
}
