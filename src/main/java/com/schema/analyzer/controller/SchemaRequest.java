package com.schema.analyzer.controller;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * Request object for schema analysis.
 */
@Data
public class SchemaRequest {
    
    @NotBlank(message = "SQL script cannot be empty")
    private String sqlScript;
}
