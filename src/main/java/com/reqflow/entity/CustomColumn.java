package com.reqflow.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "req_custom_column")
public class CustomColumn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requirement_id", nullable = false)
    private Long requirementId;

    @Column(name = "column_key", nullable = false)
    private String columnKey;

    @Column(name = "column_name", nullable = false)
    private String columnName;

    @Column(name = "column_type")
    private String columnType = "TEXT";
}