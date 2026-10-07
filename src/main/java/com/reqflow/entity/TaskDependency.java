package com.reqflow.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(
        name = "req_task_dependency",
        uniqueConstraints =
                @UniqueConstraint(columnNames = {"stage_id", "predecessor_id", "successor_id"}))
public class TaskDependency {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stage_id", nullable = false)
    private Long stageId;

    @Column(name = "predecessor_id", nullable = false)
    private Long predecessorId;

    @Column(name = "successor_id", nullable = false)
    private Long successorId;

    @Column(nullable = false)
    private String type = "FINISH_TO_START";

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
