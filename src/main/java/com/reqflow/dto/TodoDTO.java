package com.reqflow.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TodoDTO {
    private Long id;             // 待办ID 或 矩阵子任务ID
    private Long userId;
    private String title;
    private String status;       // TODO, DONE
    private String priority;     // LOW, MEDIUM, HIGH
    private LocalDate dueDate;

    // 是否为项目需求派生的待办
    private Boolean isProjectTask = false;

    // 项目上下文关联信息
    private Long subTaskId;
    private Long stageId;
    private String stageTitle;
    private Long requirementId;
    private String requirementTitle;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}