package com.schema.analyzer.service;

import com.schema.analyzer.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyzer for Second Normal Form (2NF).
 * 
 * A table is in 2NF if:
 * 1. It is in 1NF
 * 2. All non-key attributes are fully functionally dependent on the primary key
 *    (no partial dependencies where attributes depend on only part of a composite key)
 */
@Service
@Slf4j
public class SecondNormalFormAnalyzer implements NormalizationAnalyzer {

    private final FirstNormalFormAnalyzer firstNfAnalyzer;

    @Autowired
    public SecondNormalFormAnalyzer(FirstNormalFormAnalyzer firstNfAnalyzer) {
        this.firstNfAnalyzer = firstNfAnalyzer;
    }

    @Override
    public List<NormalizationIssue> analyze(DatabaseSchema schema) {
        List<NormalizationIssue> issues = new ArrayList<>();
        
        // First, check if schema is in 1NF
        List<NormalizationIssue> firstNfIssues = firstNfAnalyzer.analyze(schema);
        List<NormalizationIssue> criticalFirstNfIssues = firstNfIssues.stream()
            .filter(issue -> !issue.getDescription().contains("might contain structured data"))
            .collect(Collectors.toList());
            
        if (!criticalFirstNfIssues.isEmpty()) {
            // If not in 1NF, report only 1NF issues
            return firstNfIssues;
        }
        
        // Check each table for 2NF compliance
        for (Table table : schema.getTables()) {
            // Get primary key columns
            Set<Column> pkColumns = table.getPrimaryKeyColumns();
            
            // 2NF only applies to tables with composite primary keys
            if (pkColumns.size() > 1) {
                // Look for potential partial dependencies
                detectPartialDependencies(table, pkColumns, issues);
            }
            
            // Check foreign key relationships for partial dependencies
            detectForeignKeyPartialDependencies(table, issues);
        }
        
        return issues;
    }

    @Override
    public boolean isCompliant(DatabaseSchema schema) {
        return analyze(schema).isEmpty();
    }
    
    /**
     * Detects potential partial dependencies in a table.
     * A partial dependency exists when a non-key attribute depends on only part of a composite key.
     */
    private void detectPartialDependencies(Table table, Set<Column> pkColumns, List<NormalizationIssue> issues) {
        // Get foreign key constraints
        List<ForeignKeyConstraint> fkConstraints = table.getForeignKeyConstraints();
        
        // Get names of primary key columns for easier comparison
        Set<String> pkColumnNames = pkColumns.stream()
                .map(Column::getName)
                .collect(Collectors.toSet());
        
        // Check foreign key constraints for potential partial dependencies
        for (ForeignKeyConstraint fk : fkConstraints) {
            // Check if any FK column is part of the primary key
            boolean fkOverlapsPk = false;
            for (String fkCol : fk.getColumns()) {
                if (pkColumnNames.contains(fkCol)) {
                    fkOverlapsPk = true;
                    break;
                }
            }
            
            if (fkOverlapsPk) {
                // This is a potential sign of a partial dependency
                // Look for columns that might depend on this foreign key rather than the full primary key
                
                // Collect columns with dependency clues
                detectFkRelatedColumns(table, fk, pkColumnNames, issues);
            }
        }
        
        // Additional heuristic for detecting partial dependencies
        for (Column pkCol : pkColumns) {
            detectColumnRelatedDependencies(table, pkCol, pkColumnNames, issues);
        }
        
        // Check for columns matching parts of the composite key
        if (pkColumns.size() > 1) {
            detectCompositeKeyPartialDependencies(table, pkColumnNames, issues);
        }
    }
    
    /**
     * Detects partial dependencies related to a foreign key.
     */
    private void detectFkRelatedColumns(Table table, ForeignKeyConstraint fk, 
                                      Set<String> pkColumnNames, List<NormalizationIssue> issues) {
        // Heuristic: columns that have similar names to the FK table might depend on that FK
        String fkTableName = fk.getReferencedTable().toLowerCase();
        List<String> potentialDependentColumns = new ArrayList<>();
        
        for (Column col : table.getColumns()) {
            // Skip primary key columns
            if (pkColumnNames.contains(col.getName())) {
                continue;
            }
            
            // Skip the FK columns themselves
            if (fk.getColumns().contains(col.getName())) {
                continue;
            }
            
            String colName = col.getName().toLowerCase();
            
            // Check if column name contains FK table name (suggesting relationship)
            if (colName.contains(fkTableName) ||
                colName.startsWith(fkTableName.substring(0, Math.min(3, fkTableName.length()))) ||
                // Check for common patterns (e.g., order_id -> order_date)
                (fk.getColumns().size() == 1 && 
                 colName.startsWith(fk.getColumns().get(0).replaceAll("_id$", "") + "_"))) {
                potentialDependentColumns.add(col.getName());
            }
        }
        
        if (!potentialDependentColumns.isEmpty()) {
            issues.add(new NormalizationIssue(
                NormalizationForm.SECOND_NORMAL_FORM,
                table.getName(),
                String.join(", ", potentialDependentColumns),
                "Potential partial dependency detected: These columns may depend on " +
                String.join(", ", fk.getColumns()) + " (part of the primary key) rather than the full primary key",
                "Consider creating a separate table for these columns with " + 
                String.join(", ", fk.getColumns()) + " as the primary key",
                generatePartialDependencyFixSql(table, fk, potentialDependentColumns)
            ));
        }
    }
    
    /**
     * Detects columns that might depend on a specific PK column.
     */
    private void detectColumnRelatedDependencies(Table table, Column pkCol, 
                                              Set<String> pkColumnNames, List<NormalizationIssue> issues) {
        String pkName = pkCol.getName().toLowerCase();
        
        // If PK column name ends with "_id", use the prefix as a base
        String baseForSearch = pkName;
        if (pkName.endsWith("_id")) {
            baseForSearch = pkName.substring(0, pkName.length() - 3);
        }
        
        if (baseForSearch.length() >= 2) {  // Only if we have a meaningful base
            List<String> potentialDependentColumns = new ArrayList<>();
            
            for (Column col : table.getColumns()) {
                // Skip primary key columns
                if (pkColumnNames.contains(col.getName())) {
                    continue;
                }
                
                String colNameLower = col.getName().toLowerCase();
                
                // Check if column name contains the PK column base (suggesting relationship)
                if (colNameLower.contains(baseForSearch + "_") || 
                    colNameLower.startsWith(baseForSearch + "_")) {
                    potentialDependentColumns.add(col.getName());
                }
            }
            
            if (!potentialDependentColumns.isEmpty()) {
                issues.add(new NormalizationIssue(
                    NormalizationForm.SECOND_NORMAL_FORM,
                    table.getName(),
                    String.join(", ", potentialDependentColumns),
                    "Potential partial dependency detected: These columns may depend on " +
                    pkCol.getName() + " (part of the primary key) rather than the full primary key",
                    "Consider creating a separate table for these columns with " + 
                    pkCol.getName() + " as the primary key",
                    generatePartialDependencyFixSql(table, pkCol, potentialDependentColumns)
                ));
            }
        }
    }
    
    /**
     * Detects foreign key related partial dependencies in non-composite key tables.
     * This detects 2NF issues even when the table doesn't have a composite primary key.
     */
    private void detectForeignKeyPartialDependencies(Table table, List<NormalizationIssue> issues) {
        // Skip tables with composite keys (handled elsewhere)
        if (table.getPrimaryKeyColumns().size() > 1) {
            return;
        }
        
        // Get foreign key constraints
        List<ForeignKeyConstraint> fkConstraints = table.getForeignKeyConstraints();
        
        for (ForeignKeyConstraint fk : fkConstraints) {
            String fkTableName = fk.getReferencedTable().toLowerCase();
            List<String> potentialDependentColumns = new ArrayList<>();
            
            for (Column col : table.getColumns()) {
                // Skip primary key columns
                if (table.getPrimaryKeyColumns().stream()
                    .anyMatch(pk -> pk.getName().equals(col.getName()))) {
                    continue;
                }
                
                // Skip the FK columns themselves
                if (fk.getColumns().contains(col.getName())) {
                    continue;
                }
                
                String colName = col.getName().toLowerCase();
                
                // Check if column name contains FK table name (suggesting related data)
             // Check if column name contains FK table name (suggesting related data)
                if (colName.contains(fkTableName + "_") ||
                    colName.startsWith(fkTableName.substring(0, Math.min(3, fkTableName.length()))) ||
                    // Look for foreign key ID -> name patterns (e.g., product_id -> product_name)
                    (fk.getColumns().size() == 1 && 
                     colName.startsWith(fk.getColumns().get(0).replaceAll("_id$", "") + "_"))) {
                    potentialDependentColumns.add(col.getName());
                }
            }
            
            if (!potentialDependentColumns.isEmpty()) {
                issues.add(new NormalizationIssue(
                    NormalizationForm.SECOND_NORMAL_FORM,
                    table.getName(),
                    String.join(", ", potentialDependentColumns),
                    "Potential partial dependency detected: These columns may depend on " +
                    String.join(", ", fk.getColumns()) + " rather than the primary key",
                    "Consider creating a separate table for these columns with " + 
                    String.join(", ", fk.getColumns()) + " as the primary key",
                    generatePartialDependencyFixSql(table, fk, potentialDependentColumns)
                ));
            }
        }
    }
    
    /**
     * Detects partial dependencies in tables with composite primary keys where column names
     * match parts of the composite key pattern.
     */
    private void detectCompositeKeyPartialDependencies(Table table, Set<String> pkColumnNames, 
                                                      List<NormalizationIssue> issues) {
        // For each non-PK column in the table
        for (Column col : table.getColumns()) {
            if (pkColumnNames.contains(col.getName())) {
                continue;
            }
            
            String colName = col.getName().toLowerCase();
            
            // Check for each primary key column if the non-PK column might depend on it
            for (String pkCol : pkColumnNames) {
                String pkBase = pkCol.replaceAll("_id$", "").toLowerCase();
                
                // If column name starts with the same prefix as the PK column, it might depend on it
                if (pkBase.length() >= 3 && 
                    (colName.startsWith(pkBase + "_") || colName.contains("_" + pkBase + "_"))) {
                    
                    issues.add(new NormalizationIssue(
                        NormalizationForm.SECOND_NORMAL_FORM,
                        table.getName(),
                        col.getName(),
                        "Potential partial dependency detected: This column may depend on " +
                        pkCol + " (part of the primary key) rather than the full primary key",
                        "Consider creating a separate table for this column with " + 
                        pkCol + " as the primary key",
                        generateSingleColumnPartialDependencyFixSql(table, pkCol, col.getName())
                    ));
                    
                    // Only report the first dependency found
                    break;
                }
            }
        }
    }
    
    /**
     * Generates SQL to fix a partial dependency by creating a separate table.
     */
    private String generatePartialDependencyFixSql(Table table, ForeignKeyConstraint fk, List<String> dependentColumns) {
        String newTableName = table.getName() + "_" + fk.getReferencedTable();
        
        StringBuilder sql = new StringBuilder();
        sql.append("-- Create a new table to remove partial dependency\n");
        sql.append("CREATE TABLE ").append(newTableName).append(" (\n");
        
        // Add the foreign key as the primary key in new table
        for (int i = 0; i < fk.getColumns().size(); i++) {
            String fkCol = fk.getColumns().get(i);
            Column column = table.findColumnByName(fkCol);
            
            sql.append("    ").append(fkCol).append(" ").append(column.getDataType());
            if (!column.isNullable()) {
                sql.append(" NOT NULL");
            }
            sql.append(",\n");
        }
        
        // Add the dependent columns
        for (String colName : dependentColumns) {
            Column column = table.findColumnByName(colName);
            
            sql.append("    ").append(colName).append(" ").append(column.getDataType());
            if (!column.isNullable()) {
                sql.append(" NOT NULL");
            }
            sql.append(",\n");
        }
        
        // Add primary key constraint
        sql.append("    PRIMARY KEY (").append(String.join(", ", fk.getColumns())).append("),\n");
        
        // Add foreign key constraint
        sql.append("    FOREIGN KEY (").append(String.join(", ", fk.getColumns())).append(") REFERENCES ")
           .append(fk.getReferencedTable()).append("(")
           .append(String.join(", ", fk.getReferencedColumns())).append(")\n");
        
        sql.append(");\n\n");
        
        // Add instructions for data migration
        sql.append("-- Data migration instructions:\n");
        sql.append("-- INSERT INTO ").append(newTableName).append(" (")
           .append(String.join(", ", fk.getColumns())).append(", ")
           .append(String.join(", ", dependentColumns)).append(")\n");
        sql.append("-- SELECT ").append(String.join(", ", fk.getColumns())).append(", ")
           .append(String.join(", ", dependentColumns))
           .append(" FROM ").append(table.getName()).append(";\n\n");
        
        // Drop the columns from the original table
        sql.append("-- After migration, drop the columns from the original table\n");
        for (String colName : dependentColumns) {
            sql.append("-- ALTER TABLE ").append(table.getName()).append(" DROP COLUMN ").append(colName).append(";\n");
        }
        
        return sql.toString();
    }
    
    /**
     * Generates SQL to fix a partial dependency related to a specific primary key column.
     */
    private String generatePartialDependencyFixSql(Table table, Column pkColumn, List<String> dependentColumns) {
        String newTableName = table.getName() + "_" + pkColumn.getName().replaceAll("_id$", "");
        
        StringBuilder sql = new StringBuilder();
        sql.append("-- Create a new table to remove partial dependency\n");
        sql.append("CREATE TABLE ").append(newTableName).append(" (\n");
        
        // Add the primary key column
        sql.append("    ").append(pkColumn.getName()).append(" ").append(pkColumn.getDataType());
        if (!pkColumn.isNullable()) {
            sql.append(" NOT NULL");
        }
        sql.append(" PRIMARY KEY,\n");
        
        // Add the dependent columns
        for (int i = 0; i < dependentColumns.size(); i++) {
            String colName = dependentColumns.get(i);
            Column column = table.findColumnByName(colName);
            
            sql.append("    ").append(colName).append(" ").append(column.getDataType());
            if (!column.isNullable()) {
                sql.append(" NOT NULL");
            }
            
            if (i < dependentColumns.size() - 1) {
                sql.append(",");
            }
            sql.append("\n");
        }
        
        sql.append(");\n\n");
        
        // Add instructions for data migration
        sql.append("-- Data migration instructions:\n");
        sql.append("-- INSERT INTO ").append(newTableName).append(" (")
           .append(pkColumn.getName()).append(", ")
           .append(String.join(", ", dependentColumns)).append(")\n");
        sql.append("-- SELECT ").append(pkColumn.getName()).append(", ")
           .append(String.join(", ", dependentColumns))
           .append(" FROM ").append(table.getName()).append(";\n\n");
        
        // Drop the columns from the original table
        sql.append("-- After migration, drop the columns from the original table\n");
        for (String colName : dependentColumns) {
            sql.append("-- ALTER TABLE ").append(table.getName()).append(" DROP COLUMN ").append(colName).append(";\n");
        }
        
        return sql.toString();
    }
    
    /**
     * Generates SQL to fix a single column partial dependency.
     */
    private String generateSingleColumnPartialDependencyFixSql(Table table, String pkColumn, String dependentColumn) {
        String newTableName = table.getName() + "_" + pkColumn.replaceAll("_id$", "");
        
        StringBuilder sql = new StringBuilder();
        sql.append("-- Create a new table to remove partial dependency\n");
        sql.append("CREATE TABLE ").append(newTableName).append(" (\n");
        
        // Add the primary key column
        Column pkCol = table.findColumnByName(pkColumn);
        sql.append("    ").append(pkColumn).append(" ").append(pkCol.getDataType());
        if (!pkCol.isNullable()) {
            sql.append(" NOT NULL");
        }
        sql.append(" PRIMARY KEY,\n");
        
        // Add the dependent column
        Column depCol = table.findColumnByName(dependentColumn);
        sql.append("    ").append(dependentColumn).append(" ").append(depCol.getDataType());
        if (!depCol.isNullable()) {
            sql.append(" NOT NULL");
        }
        sql.append("\n");
        
        sql.append(");\n\n");
        
        // Add instructions for data migration
        sql.append("-- Data migration instructions:\n");
        sql.append("-- INSERT INTO ").append(newTableName).append(" (")
           .append(pkColumn).append(", ")
           .append(dependentColumn).append(")\n");
        sql.append("-- SELECT ").append(pkColumn).append(", ")
           .append(dependentColumn)
           .append(" FROM ").append(table.getName()).append(";\n\n");
        
        // Drop the column from the original table
        sql.append("-- After migration, drop the column from the original table\n");
        sql.append("-- ALTER TABLE ").append(table.getName()).append(" DROP COLUMN ").append(dependentColumn).append(";\n");
        
        return sql.toString();
    }
}
