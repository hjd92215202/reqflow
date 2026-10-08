package com.reqflow.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import java.time.LocalDate;
import lombok.Getter;

@Getter
public class StageUpdateRequest {
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private boolean startDateProvided;
    private boolean endDateProvided;

    @JsonSetter("title")
    public void setTitle(String title) {
        this.title = title;
    }

    @JsonSetter("startDate")
    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
        this.startDateProvided = true;
    }

    @JsonSetter("endDate")
    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
        this.endDateProvided = true;
    }

    @JsonSetter("status")
    public void setStatus(String status) {
        this.status = status;
    }
}
