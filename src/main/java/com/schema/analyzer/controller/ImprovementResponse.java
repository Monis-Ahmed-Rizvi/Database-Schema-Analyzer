package com.schema.analyzer.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response containing SQL statements for improving schema normalization.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImprovementResponse {
    private String sql;
}
