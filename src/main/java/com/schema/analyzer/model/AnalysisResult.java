package com.schema.analyzer.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contains the result of a schema normalization analysis.
 */
@Data
public class AnalysisResult {
    /**
     * The database schema that was analyzed.
     */
    private DatabaseSchema schema;
    
    /**
     * The highest normalization form achieved.
     */
    private NormalizationForm achievedForm;
    
    /**
     * Normalization issues found, grouped by normalization form.
     */
    private Map<NormalizationForm, List<NormalizationIssue>> issuesByForm = new LinkedHashMap<>();
    
    /**
     * Gets all issues found, across all normalization forms.
     */
    public List<NormalizationIssue> getAllIssues() {
        return issuesByForm.values().stream()
            .flatMap(List::stream)
            .toList();
    }
    
    /**
     * Checks if the schema has any normalization issues.
     */
    public boolean hasIssues() {
        return issuesByForm.values().stream()
            .anyMatch(issues -> !issues.isEmpty());
    }
}
