package com.schema.analyzer.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a relationship between tables (typically via foreign key).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Relationship {
    @JsonBackReference
    private Table sourceTable;
    
    @JsonBackReference
    private Table targetTable;
    
    private List<String> sourceColumns = new ArrayList<>();
    private List<String> targetColumns = new ArrayList<>();
}