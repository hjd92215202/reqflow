package com.reqflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_todo", indexes = {
        @Index(name = "idx_todo_user_id", columnList = "user_id")
})
public class Todo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String title;

    private String status = "TODO"; // TODO (待办), DONE (已完成)

    private String priority = "MEDIUM"; // LOW (低), MEDIUM (中), HIGH (高)

    @Column(name = "due_date")
    private LocalDate dueDate; // 截止日期

    @Column(name = "sub_task_id")
    private Long subTaskId; // 关联的协同矩阵子任务ID (为空表示独立个人待办)

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}