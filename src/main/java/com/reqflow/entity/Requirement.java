package com.reqflow.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(name = "req_requirement")
public class Requirement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String description;
    private String status;
    private String priority;

    @Column(name = "start_date")
    private LocalDate startDate; // 新增需求开始时间

    @Column(name = "end_date")
    private LocalDate endDate; // 新增需求结束时间

    @Column(name = "creator_id")
    private Long creatorId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
