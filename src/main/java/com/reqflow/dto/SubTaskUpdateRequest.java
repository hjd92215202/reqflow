package com.reqflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.time.LocalDate;
import java.util.Map;
import lombok.Getter;

/** Presence-aware updates keep legacy clients from clearing optional fields. */
@Getter
public class SubTaskUpdateRequest {
    private String title;
    private String status;
    private String assignee;
    private LocalDate startDate;
    private LocalDate endDate;
    private String note;
    private Map<String, Object> customFields;
    private String deliverable;
    private String completionCriteria;
    @JsonIgnore private boolean assigneeProvided;
    @JsonIgnore private boolean startDateProvided;
    @JsonIgnore private boolean endDateProvided;
    @JsonIgnore private boolean noteProvided;
    @JsonIgnore private boolean customFieldsProvided;
    @JsonIgnore private boolean deliverableProvided;
    @JsonIgnore private boolean completionCriteriaProvided;

    @JsonSetter("title")
    public void setTitle(String value) {
        title = value;
    }

    @JsonSetter("status")
    public void setStatus(String value) {
        status = value;
    }

    @JsonSetter("assignee")
    public void setAssignee(String value) {
        assignee = value;
        assigneeProvided = true;
    }

    @JsonSetter("startDate")
    public void setStartDate(LocalDate value) {
        startDate = value;
        startDateProvided = true;
    }

    @JsonSetter("endDate")
    public void setEndDate(LocalDate value) {
        endDate = value;
        endDateProvided = true;
    }

    @JsonSetter("note")
    public void setNote(String value) {
        note = value;
        noteProvided = true;
    }

    @JsonSetter("customFields")
    public void setCustomFields(Map<String, Object> value) {
        customFields = value;
        customFieldsProvided = true;
    }

    @JsonSetter("deliverable")
    public void setDeliverable(String value) {
        deliverable = value;
        deliverableProvided = true;
    }

    @JsonSetter("completionCriteria")
    public void setCompletionCriteria(String value) {
        completionCriteria = value;
        completionCriteriaProvided = true;
    }
}
