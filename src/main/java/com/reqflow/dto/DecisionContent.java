package com.reqflow.dto;

import java.time.LocalDate;
import java.util.List;

public record DecisionContent(
        String title,
        String context,
        List<Option> options,
        String chosenOption,
        String rationale,
        List<String> assumptions,
        String confidence,
        String status,
        LocalDate reviewDate,
        AiAssistance aiAssistance) {
    public record Option(String name, String pros, String cons) {}

    public record AiAssistance(
            List<String> phases,
            String contribution,
            String humanJudgment,
            String handling,
            String verification) {}
}
