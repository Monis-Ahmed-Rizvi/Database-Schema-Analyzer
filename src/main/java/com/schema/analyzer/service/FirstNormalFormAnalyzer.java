package com.schema.analyzer.service;

import com.schema.analyzer.model.*;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyzer for First Normal Form (1NF).
 * 
 * A table is in 1NF if:
 * 1. The table has a primary key
 * 2. All columns contain atomic values (no multi-valued attributes)
 * 3. No repeating groups of columns
 */
@Service
@Slf4j
public class FirstNormalFormAnalyzer implements NormalizationAnalyzer {

    @Override
    public List<NormalizationIssue> analyze(DatabaseSchema schema) {
        List<NormalizationIssue> issues = new ArrayList<>();
        
        for (Table table : schema.getTables()) {
            log.debug("Analyzing table {} for 1NF compliance", table.getName());
            
            // Check for primary key
            if (!table.hasPrimaryKey()) {
                log.debug("Table {} does not have a primary key", table.getName());
                issues.add(new NormalizationIssue(
                    NormalizationForm.FIRST_NORMAL_FORM,
                    table.getName(),
                    "Table does not have a primary key",
                    "Add a primary key to the table",
                    "ALTER TABLE " + table.getName() + " ADD COLUMN id INT AUTO_INCREMENT PRIMARY KEY;"
                ));
            } else {
                log.debug("Table {} has a primary key: {}", table.getName(), 
                    table.getPrimaryKeyColumns().stream().map(Column::getName).toList());
            }
            
            // Check for multi-valued attributes
            for (Column column : table.getColumns()) {
                if (column.isMultiValued()) {
                    log.debug("Column {} in table {} contains multi-valued attributes", 
                        column.getName(), table.getName());
                    issues.add(new NormalizationIssue(
                        NormalizationForm.FIRST_NORMAL_FORM,
                        table.getName(),
                        column.getName(),
                        "Column potentially contains multi-valued attributes",
                        "Create a separate table to store these values and establish a foreign key relationship",
                        generateSeparateTableSql(table.getName(), column)
                    ));
                } else if (column.mightContainStructuredData()) {
                    log.debug("Column {} in table {} might contain structured data", 
                        column.getName(), table.getName());
                    issues.add(new NormalizationIssue(
                        NormalizationForm.FIRST_NORMAL_FORM,
                        table.getName(),
                        column.getName(),
                        "Column might contain structured data (non-atomic values)",
                        "Consider splitting this data into separate columns or tables if it contains multiple values",
                        null
                    ));
                }
            }
            
            // Check for repeating groups
            detectRepeatingGroups(table, issues);
        }
        
        return issues;
    }

    @Override
    public boolean isCompliant(DatabaseSchema schema) {
        return analyze(schema).isEmpty();
    }
    
    /**
     * Detects potential repeating groups in a table based on column naming patterns.
     */
    private void detectRepeatingGroups(Table table, List<NormalizationIssue> issues) {
        List<String> columnNames = table.getColumns().stream().map(Column::getName).toList();
        
        // Get primary key column names to avoid flagging them
        Set<String> pkColumnNames = table.getPrimaryKeyColumns().stream()
            .map(Column::getName)
            .collect(Collectors.toSet());
        
        // Look for numbered columns (e.g., address1, address2, address3)
        for (String baseColumnName : findPotentialBaseColumnNames(columnNames, pkColumnNames)) {
            List<String> numberedColumns = findNumberedColumns(columnNames, baseColumnName);
            
            if (numberedColumns.size() >= 2) {
                // Check if these columns are part of primary key
                boolean allArePartOfPK = true;
                for (String col : numberedColumns) {
                    if (!pkColumnNames.contains(col)) {
                        allArePartOfPK = false;
                        break;
                    }
                }
                
                // Skip if all columns are part of the primary key
                if (allArePartOfPK) {
                    continue;
                }
                
                log.debug("Found repeating group in table {}: {} columns", 
                    table.getName(), baseColumnName);
                issues.add(new NormalizationIssue(
                    NormalizationForm.FIRST_NORMAL_FORM,
                    table.getName(),
                    String.join(", ", numberedColumns),
                    "Potential repeating group detected: " + baseColumnName + " columns",
                    "Create a separate table to store these values",
                    generateRepeatingGroupFixSql(table.getName(), baseColumnName, numberedColumns)
                ));
            }
        }
    }
    
    /**
     * Finds potential base column names by removing trailing numbers.
     * Excludes primary key columns to avoid false positives.
     */
    private List<String> findPotentialBaseColumnNames(List<String> columnNames, Set<String> pkColumnNames) {
        List<String> baseNames = new ArrayList<>();
        
        for (String name : columnNames) {
            // Skip very short column names and primary key columns
            if (name.length() <= 2 || pkColumnNames.contains(name)) {
                continue;
            }
            
            String baseName = name.replaceAll("\\d+$", "");
            // Only consider it a base name if removing numbers actually changed something
            // and the resulting base name is at least 3 characters long
            if (!baseName.equals(name) && baseName.length() >= 3 && !baseNames.contains(baseName)) {
                baseNames.add(baseName);
            }
        }
        
        return baseNames;
    }
    
    /**
     * Finds columns that appear to be numbered variations of a base name.
     */
    private List<String> findNumberedColumns(List<String> columnNames, String baseName) {
        List<String> numberedColumns = new ArrayList<>();
        
        for (String name : columnNames) {
            if (name.matches(baseName + "\\d+")) {
                numberedColumns.add(name);
            }
        }
        
        return numberedColumns;
    }
    
    /**
     * Generates SQL to fix multi-valued attributes by creating a separate table.
     */
    private String generateSeparateTableSql(String tableName, Column column) {
        String newTableName = tableName + "_" + column.getName();
        String idColumn = tableName + "_id";
        
        StringBuilder sql = new StringBuilder();
        sql.append("-- Create a new table for the multi-valued attribute\n");
        sql.append("CREATE TABLE ").append(newTableName).append(" (\n");
        sql.append("    id INT AUTO_INCREMENT PRIMARY KEY,\n");
        sql.append("    ").append(idColumn).append(" INT NOT NULL,\n");
        sql.append("    ").append(column.getName()).append("_value ");
        
        // Determine appropriate data type for the values
        if (column.getDataType().toUpperCase().contains("VARCHAR")) {
            sql.append("VARCHAR(255)");
        } else if (column.getDataType().toUpperCase().contains("INT")) {
            sql.append("INT");
        } else {
            sql.append("TEXT");
        }
        
        if (!column.isNullable()) {
            sql.append(" NOT NULL");
        }
        
        sql.append(",\n");
        sql.append("    FOREIGN KEY (").append(idColumn).append(") REFERENCES ")
           .append(tableName).append("(id)\n");
        sql.append(");\n\n");
        
        // Add instructions for data migration
        sql.append("-- Data migration would be required: \n");
        sql.append("-- INSERT INTO ").append(newTableName).append(" (")
           .append(idColumn).append(", ").append(column.getName()).append("_value)\n");
        sql.append("-- SELECT id, value FROM json_table_function(").append(column.getName()).append(") ...\n\n");
        
        // Drop the original column
        sql.append("-- After migration, drop the original column\n");
        sql.append("-- ALTER TABLE ").append(tableName).append(" DROP COLUMN ").append(column.getName()).append(";");
        
        return sql.toString();
    }
    
    /**
     * Generates SQL to fix repeating groups by creating a separate table.
     */
    private String generateRepeatingGroupFixSql(String tableName, String baseColumnName, List<String> numberedColumns) {
        String newTableName = tableName + "_" + baseColumnName;
        String idColumn = tableName + "_id";
        
        StringBuilder sql = new StringBuilder();
        sql.append("-- Create a new table for the repeating group\n");
        sql.append("CREATE TABLE ").append(newTableName).append(" (\n");
        sql.append("    id INT AUTO_INCREMENT PRIMARY KEY,\n");
        sql.append("    ").append(idColumn).append(" INT NOT NULL,\n");
        sql.append("    ").append(baseColumnName).append("_value VARCHAR(255),\n");
        sql.append("    FOREIGN KEY (").append(idColumn).append(") REFERENCES ")
           .append(tableName).append("(id)\n");
        sql.append(");\n\n");
        
        // Add instructions for data migration
        sql.append("-- Data migration instructions:\n");
        for (String columnName : numberedColumns) {
            sql.append("-- INSERT INTO ").append(newTableName).append(" (")
               .append(idColumn).append(", ").append(baseColumnName).append("_value)\n");
            sql.append("-- SELECT id, ").append(columnName).append(" FROM ")
               .append(tableName).append(" WHERE ").append(columnName).append(" IS NOT NULL;\n");
        }
        
        sql.append("\n-- After migration, drop the original columns\n");
        for (String columnName : numberedColumns) {
            sql.append("-- ALTER TABLE ").append(tableName).append(" DROP COLUMN ").append(columnName).append(";\n");
        }
        
        return sql.toString();
    }
}