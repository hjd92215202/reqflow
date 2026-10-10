package com.reqflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.time.LocalDate;
import lombok.Getter;

@Getter
public class StageUpdateRequest {
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    @JsonIgnore private boolean startDateProvided;
    @JsonIgnore private boolean endDateProvided;
    private String goal;
    private String expectedOutput;
    private String exitCriteria;
    @JsonIgnore private boolean goalProvided;
    @JsonIgnore private boolean expectedOutputProvided;
    @JsonIgnore private boolean exitCriteriaProvided;

    @JsonSetter("goal")
    public void setGoal(String goal) {
        this.goal = goal;
        this.goalProvided = true;
    }

    @JsonSetter("expectedOutput")
    public void setExpectedOutput(String expectedOutput) {
        this.expectedOutput = expectedOutput;
        this.expectedOutputProvided = true;
    }

    @JsonSetter("exitCriteria")
    public void setExitCriteria(String exitCriteria) {
        this.exitCriteria = exitCriteria;
        this.exitCriteriaProvided = true;
    }

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
