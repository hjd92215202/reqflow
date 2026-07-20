package com.reqflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "req_sub_task")
public class SubTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stage_id", nullable = false)
    private Long stageId; // 变更为关联阶段ID

    @Column(nullable = false)
    private String title;

    private String assignee;

    @Column(name = "is_completed")
    private Boolean isCompleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}