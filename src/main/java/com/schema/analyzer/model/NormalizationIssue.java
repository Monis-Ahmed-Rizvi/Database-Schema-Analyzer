package com.schema.analyzer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a normalization issue found during analysis.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NormalizationIssue {
    /**
     * The normalization form that is violated.
     */
    private NormalizationForm violatedForm;
    
    /**
     * The table where the issue was found.
     */
    private String tableName;
    
    /**
     * The column(s) associated with the issue, if applicable.
     */
    private String columnName;
    
    /**
     * A description of the issue.
     */
    private String description;
    
    /**
     * A suggestion for resolving the issue.
     */
    private String suggestion;
    
    /**
     * SQL code that would fix the issue, if available.
     */
    private String fixSql;
    
    public NormalizationIssue(NormalizationForm violatedForm, String tableName, String description) {
        this.violatedForm = violatedForm;
        this.tableName = tableName;
        this.description = description;
    }
    
    public NormalizationIssue(NormalizationForm violatedForm, String tableName, 
                             String columnName, String description) {
        this.violatedForm = violatedForm;
        this.tableName = tableName;
        this.columnName = columnName;
        this.description = description;
    }
    
    public NormalizationIssue(NormalizationForm violatedForm, String tableName, 
                             String columnName, String description, String suggestion) {
        this.violatedForm = violatedForm;
        this.tableName = tableName;
        this.columnName = columnName;
        this.description = description;
        this.suggestion = suggestion;
    }
}