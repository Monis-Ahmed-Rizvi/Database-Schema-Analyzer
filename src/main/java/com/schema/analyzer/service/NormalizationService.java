package com.schema.analyzer.service;

import com.schema.analyzer.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for performing normalization analysis on database schemas.
 */
@Service
@Slf4j
public class NormalizationService {

    private final SQLParserService sqlParserService;
    private final FirstNormalFormAnalyzer firstNfAnalyzer;
    private final SecondNormalFormAnalyzer secondNfAnalyzer;
    private final ThirdNormalFormAnalyzer thirdNfAnalyzer;
    
    @Autowired
    public NormalizationService(
            SQLParserService sqlParserService,
            FirstNormalFormAnalyzer firstNfAnalyzer,
            SecondNormalFormAnalyzer secondNfAnalyzer,
            ThirdNormalFormAnalyzer thirdNfAnalyzer) {
        this.sqlParserService = sqlParserService;
        this.firstNfAnalyzer = firstNfAnalyzer;
        this.secondNfAnalyzer = secondNfAnalyzer;
        this.thirdNfAnalyzer = thirdNfAnalyzer;
    }
    
    /**
     * Analyzes a SQL schema for normalization issues.
     * 
     * @param sqlScript The SQL CREATE TABLE statements to analyze
     * @return The analysis result containing the highest achieved normalization form and issues found
     */
    public AnalysisResult analyzeSchema(String sqlScript) {
        try {
            // Parse the SQL script into a database schema model
            log.debug("Analyzing schema: {}", sqlScript);
            DatabaseSchema schema = sqlParserService.parseSchema(sqlScript);
            
            // Analyze the schema against each normalization form
            List<NormalizationIssue> firstNfIssues = firstNfAnalyzer.analyze(schema);
            
            // Filter out warnings from 1NF for normalization form determination
            List<NormalizationIssue> criticalFirstNfIssues = firstNfIssues.stream()
                .filter(issue -> isCriticalIssue(issue))
                .collect(Collectors.toList());
            
            // Only check 2NF if 1NF passes critical issues
            List<NormalizationIssue> secondNfIssues = criticalFirstNfIssues.isEmpty() ? 
                secondNfAnalyzer.analyze(schema) : 
                new ArrayList<>();
            
            // Only check 3NF if 2NF passes
            List<NormalizationIssue> thirdNfIssues = secondNfIssues.isEmpty() && criticalFirstNfIssues.isEmpty() ? 
                thirdNfAnalyzer.analyze(schema) : 
                new ArrayList<>();
            
            log.debug("Analysis complete. 1NF issues: {}, 2NF issues: {}, 3NF issues: {}", 
                    firstNfIssues.size(), secondNfIssues.size(), thirdNfIssues.size());
            
            // Determine the highest achieved normalization form
            NormalizationForm achievedForm = determineAchievedForm(criticalFirstNfIssues, secondNfIssues, thirdNfIssues);
            log.debug("Achieved normalization form: {}", achievedForm);
            
            // Create a result object
            AnalysisResult result = new AnalysisResult();
            result.setAchievedForm(achievedForm);
            result.setSchema(schema);
            
            // Add the issues found and deduplicate them
            Map<NormalizationForm, List<NormalizationIssue>> issuesByForm = new LinkedHashMap<>();
            issuesByForm.put(NormalizationForm.FIRST_NORMAL_FORM, firstNfIssues);
            issuesByForm.put(NormalizationForm.SECOND_NORMAL_FORM, secondNfIssues);
            issuesByForm.put(NormalizationForm.THIRD_NORMAL_FORM, deduplicateIssues(thirdNfIssues));
            result.setIssuesByForm(issuesByForm);
            
            return result;
        } catch (Exception e) {
            log.error("Error analyzing schema", e);
            throw new SchemaAnalysisException("Failed to analyze schema: " + e.getMessage(), e);
        }
    }
    
    /**
     * Determines if an issue is critical for determining normalization form.
     * Some issues like potential TEXT fields are warnings and don't affect normalization status.
     */
    private boolean isCriticalIssue(NormalizationIssue issue) {
        // TEXT/BLOB warning check
        if (issue.getDescription() != null && 
            issue.getDescription().contains("might contain structured data")) {
            return false;
        }
        
        // Skip false positive repeating groups in composite primary keys
        if (issue.getDescription() != null && 
            issue.getDescription().contains("repeating group") &&
            issue.getColumnName() != null &&
            issue.getColumnName().contains("id") &&
            issue.getColumnName().split(",").length <= 2) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Removes duplicate issues that might have been created during analysis.
     */
    private List<NormalizationIssue> deduplicateIssues(List<NormalizationIssue> issues) {
        List<NormalizationIssue> uniqueIssues = new ArrayList<>();
        Set<String> seenIssues = new HashSet<>();
        
        for (NormalizationIssue issue : issues) {
            String key = issue.getTableName() + ":" + 
                         (issue.getColumnName() != null ? issue.getColumnName() : "") + ":" + 
                         issue.getDescription();
            
            if (!seenIssues.contains(key)) {
                seenIssues.add(key);
                uniqueIssues.add(issue);
            }
        }
        
        return uniqueIssues;
    }
    
    /**
     * Determines the highest normalization form achieved based on the issues found.
     */
    private NormalizationForm determineAchievedForm(
            List<NormalizationIssue> firstNfIssues,
            List<NormalizationIssue> secondNfIssues,
            List<NormalizationIssue> thirdNfIssues) {
        
        if (!firstNfIssues.isEmpty()) {
            return null; // Not even in 1NF
        } else if (!secondNfIssues.isEmpty()) {
            return NormalizationForm.FIRST_NORMAL_FORM;
        } else if (!thirdNfIssues.isEmpty()) {
            return NormalizationForm.SECOND_NORMAL_FORM;
        } else {
            return NormalizationForm.THIRD_NORMAL_FORM;
        }
    }
    
    /**
     * Generates SQL statements to fix normalization issues.
     * 
     * @param result The analysis result containing the issues to fix
     * @return SQL statements that would fix the normalization issues
     */
    public String generateImprovementSql(AnalysisResult result) {
        StringBuilder sql = new StringBuilder();
        
        sql.append("-- SQL Statements to Improve Schema Normalization\n");
        sql.append("-- Current Normalization Level: ");
        sql.append(result.getAchievedForm() != null ? result.getAchievedForm().getDisplay() : "Not normalized");
        sql.append("\n\n");
        
        // Add SQL for each issue that has a fix
        for (Map.Entry<NormalizationForm, List<NormalizationIssue>> entry : result.getIssuesByForm().entrySet()) {
            NormalizationForm form = entry.getKey();
            List<NormalizationIssue> issues = entry.getValue();
            
            if (!issues.isEmpty()) {
                sql.append("-- ").append(form.getDisplay()).append(" Issues\n");
                
                for (NormalizationIssue issue : issues) {
                    if (issue.getFixSql() != null && !issue.getFixSql().isEmpty()) {
                        sql.append("-- Issue: ").append(issue.getDescription()).append("\n");
                        sql.append(issue.getFixSql()).append("\n\n");
                    }
                }
            }
        }
        
        return sql.toString();
    }
}