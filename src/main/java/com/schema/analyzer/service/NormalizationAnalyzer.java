package com.schema.analyzer.service;

import com.schema.analyzer.model.DatabaseSchema;
import com.schema.analyzer.model.NormalizationIssue;

import java.util.List;

/**
 * Interface for normalization analyzers to implement.
 */
public interface NormalizationAnalyzer {
    
    /**
     * Analyzes a database schema for a specific normalization form.
     * 
     * @param schema The database schema to analyze
     * @return A list of normalization issues found
     */
    List<NormalizationIssue> analyze(DatabaseSchema schema);
    
    /**
     * Determines if the schema complies with this normalization form.
     * 
     * @param schema The database schema to check
     * @return true if the schema complies with this normalization form
     */
    boolean isCompliant(DatabaseSchema schema);
}