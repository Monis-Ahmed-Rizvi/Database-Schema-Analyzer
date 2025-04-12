package com.schema.analyzer.service;

import com.schema.analyzer.model.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class NormalizationAnalyzerTests {
    
    private static final Logger log = LoggerFactory.getLogger(NormalizationAnalyzerTests.class);

    @Autowired
    private NormalizationService normalizationService;

    @Test
    public void testFirstNormalFormAnalysis() {
        log.info("Running testFirstNormalFormAnalysis");
        // Example of a table not in 1NF (has repeating groups)
        String sql = "CREATE TABLE students (" +
                "    student_id INT PRIMARY KEY," +
                "    name VARCHAR(100)," +
                "    course1 VARCHAR(50)," +
                "    course2 VARCHAR(50)," +
                "    course3 VARCHAR(50)" +
                ");";

        AnalysisResult result = normalizationService.analyzeSchema(sql);
        
        // Should not be in 1NF
        assertNull(result.getAchievedForm());
        
        // Should have 1NF issues
        List<NormalizationIssue> firstNfIssues = result.getIssuesByForm().get(NormalizationForm.FIRST_NORMAL_FORM);
        assertNotNull(firstNfIssues);
        assertFalse(firstNfIssues.isEmpty());
        
        // Should detect repeating groups
        boolean foundRepeatingGroupIssue = firstNfIssues.stream()
                .anyMatch(issue -> issue.getDescription().contains("repeating group"));
                
        assertTrue(foundRepeatingGroupIssue, "Should detect repeating groups");
        log.info("testFirstNormalFormAnalysis passed");
    }

    @Test
    public void testSecondNormalFormAnalysis() {
        log.info("Running testSecondNormalFormAnalysis");
        // Example of a table in 1NF but not in 2NF (has partial dependencies)
        String sql = "CREATE TABLE order_items (" +
                "    order_id INT," +
                "    product_id INT," +
                "    quantity INT," +
                "    product_name VARCHAR(100)," +
                "    PRIMARY KEY (order_id, product_id)" +
                ");";

        AnalysisResult result = normalizationService.analyzeSchema(sql);
        log.debug("Achieved form: {}", result.getAchievedForm());
        
        // Should be in 1NF
        assertEquals(NormalizationForm.FIRST_NORMAL_FORM, result.getAchievedForm());
        
        // Should have 2NF issues
        List<NormalizationIssue> secondNfIssues = result.getIssuesByForm().get(NormalizationForm.SECOND_NORMAL_FORM);
        assertNotNull(secondNfIssues);
        assertFalse(secondNfIssues.isEmpty());
        
        // Should detect partial dependency on product_id
        boolean foundPartialDependencyIssue = secondNfIssues.stream()
                .anyMatch(issue -> issue.getDescription().contains("partial dependency") && 
                                 issue.getColumnName().contains("product_name"));
                
        assertTrue(foundPartialDependencyIssue, "Should detect partial dependency on product_id");
        log.info("testSecondNormalFormAnalysis passed");
    }

    @Test
    public void testThirdNormalFormAnalysis() {
        log.info("Running testThirdNormalFormAnalysis");
        // Example of a table in 2NF but not in 3NF (has transitive dependency)
        String sql = "CREATE TABLE employees (" +
                "    employee_id INT PRIMARY KEY," +
                "    department_id INT," +
                "    department_name VARCHAR(100)," +
                "    salary DECIMAL(10,2)" +
                ");";

        AnalysisResult result = normalizationService.analyzeSchema(sql);
        log.debug("Achieved form: {}", result.getAchievedForm());
        
        // Should be in 2NF
        assertEquals(NormalizationForm.SECOND_NORMAL_FORM, result.getAchievedForm());
        
        // Should have 3NF issues
        List<NormalizationIssue> thirdNfIssues = result.getIssuesByForm().get(NormalizationForm.THIRD_NORMAL_FORM);
        assertNotNull(thirdNfIssues);
        assertFalse(thirdNfIssues.isEmpty());
        
        // Should detect transitive dependency through department_id
        boolean foundTransitiveDependencyIssue = thirdNfIssues.stream()
                .anyMatch(issue -> issue.getDescription().contains("transitive dependency") && 
                                 issue.getColumnName().contains("department_name"));
                
        assertTrue(foundTransitiveDependencyIssue, "Should detect transitive dependency through department_id");
        log.info("testThirdNormalFormAnalysis passed");
    }
    
    @Test
    public void testFullyNormalizedSchema() {
        log.info("Running testFullyNormalizedSchema");
        // Example of a normalized schema - using inline PRIMARY KEY declarations for clarity
        String sql = 
            "CREATE TABLE departments (" +
            "    department_id INT PRIMARY KEY," +
            "    department_name VARCHAR(100)" +
            ");\n\n" +
            "CREATE TABLE employees (" +
            "    employee_id INT PRIMARY KEY," +
            "    name VARCHAR(100)," +
            "    department_id INT," +
            "    salary DECIMAL(10,2)," +
            "    FOREIGN KEY (department_id) REFERENCES departments(department_id)" +
            ");\n\n" +
            "CREATE TABLE orders (" +
            "    order_id INT PRIMARY KEY," +
            "    employee_id INT," +
            "    order_date DATE," +
            "    FOREIGN KEY (employee_id) REFERENCES employees(employee_id)" +
            ");\n\n" +
            "CREATE TABLE products (" +
            "    product_id INT PRIMARY KEY," +
            "    name VARCHAR(100)," +
            "    price DECIMAL(10,2)" +
            ");\n\n" +
            "CREATE TABLE order_items (" +
            "    order_id INT," +
            "    product_id INT," +
            "    quantity INT," +
            "    PRIMARY KEY (order_id, product_id)," +
            "    FOREIGN KEY (order_id) REFERENCES orders(order_id)," +
            "    FOREIGN KEY (product_id) REFERENCES products(product_id)" +
            ");";

        log.debug("Testing SQL: {}", sql);
        AnalysisResult result = normalizationService.analyzeSchema(sql);
        
        // Debug the result
        log.debug("Achieved form: {}", result.getAchievedForm());
        log.debug("1NF issues: {}", result.getIssuesByForm().get(NormalizationForm.FIRST_NORMAL_FORM).size());
        log.debug("2NF issues: {}", result.getIssuesByForm().get(NormalizationForm.SECOND_NORMAL_FORM).size());
        log.debug("3NF issues: {}", result.getIssuesByForm().get(NormalizationForm.THIRD_NORMAL_FORM).size());
        
        // Should be in 3NF
        assertEquals(NormalizationForm.THIRD_NORMAL_FORM, result.getAchievedForm());
        
        // Should not have normalization issues
        assertTrue(result.getIssuesByForm().get(NormalizationForm.FIRST_NORMAL_FORM).isEmpty());
        assertTrue(result.getIssuesByForm().get(NormalizationForm.SECOND_NORMAL_FORM).isEmpty());
        assertTrue(result.getIssuesByForm().get(NormalizationForm.THIRD_NORMAL_FORM).isEmpty());
        
        log.info("testFullyNormalizedSchema passed");
    }
}