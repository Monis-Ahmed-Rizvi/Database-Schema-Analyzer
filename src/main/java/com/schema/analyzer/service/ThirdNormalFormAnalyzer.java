package com.schema.analyzer.service;

import com.schema.analyzer.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyzer for Third Normal Form (3NF).
 * 
 * A table is in 3NF if:
 * 1. It is in 2NF
 * 2. All non-primary-key attributes are non-transitively dependent on the primary key
 *    (no transitive dependencies where A → B → C, meaning non-key attributes depend on other non-key attributes)
 */
@Service
@Slf4j
public class ThirdNormalFormAnalyzer implements NormalizationAnalyzer {

    private final SecondNormalFormAnalyzer secondNfAnalyzer;

    @Autowired
    public ThirdNormalFormAnalyzer(SecondNormalFormAnalyzer secondNfAnalyzer) {
        this.secondNfAnalyzer = secondNfAnalyzer;
    }

    @Override
    public List<NormalizationIssue> analyze(DatabaseSchema schema) {
        List<NormalizationIssue> issues = new ArrayList<>();
        
        // First, check if schema is in 2NF
        List<NormalizationIssue> secondNfIssues = secondNfAnalyzer.analyze(schema);
        if (!secondNfIssues.isEmpty()) {
            // If not in 2NF, report only 2NF issues (which include 1NF issues)
            return secondNfIssues;
        }
        
        // Check each table for 3NF compliance
        for (Table table : schema.getTables()) {
            // Get all non-primary key columns
            Set<Column> pkColumns = table.getPrimaryKeyColumns();
            Set<String> pkColumnNames = pkColumns.stream()
                    .map(Column::getName)
                    .collect(Collectors.toSet());
            
            List<Column> nonKeyColumns = table.getColumns().stream()
                    .filter(col -> !pkColumnNames.contains(col.getName()))
                    .collect(Collectors.toList());
            
            // Skip tables with no non-key columns
            if (nonKeyColumns.isEmpty()) {
                continue;
            }
            
            // Look for potential transitive dependencies
            detectTransitiveDependencies(table, pkColumns, nonKeyColumns, issues);
        }
        
        return issues;
    }

    @Override
    public boolean isCompliant(DatabaseSchema schema) {
        return analyze(schema).isEmpty();
    }
    
    /**
     * Detects potential transitive dependencies in a table.
     * A transitive dependency exists when a non-key attribute depends on another non-key attribute.
     */
    private void detectTransitiveDependencies(Table table, Set<Column> pkColumns, List<Column> nonKeyColumns, 
                                           List<NormalizationIssue> issues) {
        // Get names of primary key columns for easier comparison
        Set<String> pkColumnNames = pkColumns.stream()
                .map(Column::getName)
                .collect(Collectors.toSet());
        
        // Strategy 1: Look for candidates for functional dependencies based on column naming patterns
        Map<String, List<Column>> potentialFunctionalGroups = new HashMap<>();
        
        // Group columns that might be functionally dependent on each other
        for (Column col : nonKeyColumns) {
            String colName = col.getName().toLowerCase();
            
            // Process column name to extract potential entity identifiers
            // Examples: customer_id -> customer, product_code -> product, etc.
            String baseEntity = extractBaseEntity(colName);
            
            if (baseEntity != null && baseEntity.length() >= 2) {
                potentialFunctionalGroups
                    .computeIfAbsent(baseEntity, k -> new ArrayList<>())
                    .add(col);
            }
        }
        
        // For each group, check if there's a potential transitive dependency
        for (Map.Entry<String, List<Column>> entry : potentialFunctionalGroups.entrySet()) {
            List<Column> group = entry.getValue();
            
            // We need at least two columns to have a potential transitive dependency
            if (group.size() >= 2) {
                // Look for an ID or CODE column that might be a determinant
                Optional<Column> potentialDeterminant = group.stream()
                    .filter(col -> {
                        String name = col.getName().toLowerCase();
                        return name.endsWith("_id") || 
                               name.endsWith("_code") || 
                               name.endsWith("_key") ||
                               name.endsWith("_no");
                    })
                    .findFirst();
                
                if (potentialDeterminant.isPresent()) {
                    Column determinant = potentialDeterminant.get();
                    List<Column> dependents = group.stream()
                        .filter(col -> !col.getName().equals(determinant.getName()))
                        .collect(Collectors.toList());
                    
                    // If we have dependents, report a potential transitive dependency
                    if (!dependents.isEmpty()) {
                        issues.add(createTransitiveDependencyIssue(table, determinant, dependents));
                    }
                }
            }
        }
        
        // Strategy 2: Look for common patterns of transitive dependencies
        detectCommonTransitiveDependencies(table, nonKeyColumns, issues);
        
        // Strategy 3: Check for potential foreign keys that aren't defined as constraints
        detectImplicitForeignKeys(table, nonKeyColumns, issues);
    }
    
    /**
     * Creates a normalization issue for a transitive dependency.
     */
    private NormalizationIssue createTransitiveDependencyIssue(Table table, Column determinant, List<Column> dependents) {
        List<String> dependentNames = dependents.stream()
                .map(Column::getName)
                .collect(Collectors.toList());
        
        return new NormalizationIssue(
            NormalizationForm.THIRD_NORMAL_FORM,
            table.getName(),
            String.join(", ", dependentNames),
            "Potential transitive dependency detected: These columns may depend on non-key attribute " + 
            determinant.getName() + " rather than directly on the primary key",
            "Consider creating a separate table for " + determinant.getName() + 
            " and its dependent columns",
            generateTransitiveDependencyFixSql(table, determinant, dependents)
        );
    }
    
    /**
     * Extracts a potential base entity from a column name.
     * For example, "customer_id" -> "customer", "product_price" -> "product"
     */
    private String extractBaseEntity(String columnName) {
        // Common suffixes that suggest a column identifies an entity
        List<String> identifierSuffixes = Arrays.asList("_id", "_code", "_key", "_no");
        
        for (String suffix : identifierSuffixes) {
            if (columnName.endsWith(suffix)) {
                return columnName.substring(0, columnName.length() - suffix.length());
            }
        }
        
        // Check for common attribute patterns
        List<String> attributePatterns = Arrays.asList("_name", "_description", "_address", 
                                                     "_city", "_state", "_zip", "_country",
                                                     "_date", "_time", "_price", "_cost",
                                                     "_quantity", "_amount", "_total");
        
        for (String pattern : attributePatterns) {
            if (columnName.endsWith(pattern)) {
                return columnName.substring(0, columnName.length() - pattern.length());
            }
        }
        
        // If we can't identify a pattern, assume the column might be part of a functional group
        // based on prefix up to the first underscore
        int firstUnderscore = columnName.indexOf('_');
        if (firstUnderscore > 0) {
            return columnName.substring(0, firstUnderscore);
        }
        
        return null;
    }
    
    /**
     * Detects common patterns of transitive dependencies.
     */
    private void detectCommonTransitiveDependencies(Table table, List<Column> nonKeyColumns, 
                                                List<NormalizationIssue> issues) {
        // Pattern 1: Look for code/id + name/description patterns
        // Examples: category_id + category_name, product_code + product_description
        Map<String, List<Column>> codeNamePairs = new HashMap<>();
        
        for (Column col : nonKeyColumns) {
            String colName = col.getName().toLowerCase();
            
            // Skip columns that are likely to be code/id columns
            if (colName.endsWith("_id") || colName.endsWith("_code") || 
                colName.endsWith("_key") || colName.endsWith("_no")) {
                
                // Extract base entity name
                String baseEntity = colName.substring(0, colName.lastIndexOf('_'));
                
                // Look for matching descriptive columns
                List<Column> matchingColumns = nonKeyColumns.stream()
                    .filter(c -> {
                        String cName = c.getName().toLowerCase();
                        return cName.startsWith(baseEntity + "_") && 
                               (cName.endsWith("_name") || cName.endsWith("_desc") || 
                                cName.endsWith("_description") || cName.endsWith("_title"));
                    })
                    .collect(Collectors.toList());
                
                if (!matchingColumns.isEmpty()) {
                    codeNamePairs.put(colName, matchingColumns);
                }
            }
        }
        
        // Create issues for each detected pattern
        for (Map.Entry<String, List<Column>> entry : codeNamePairs.entrySet()) {
            String determinantName = entry.getKey();
            List<Column> dependents = entry.getValue();
            
            Column determinant = nonKeyColumns.stream()
                .filter(c -> c.getName().equalsIgnoreCase(determinantName))
                .findFirst()
                .orElse(null);
            
            if (determinant != null) {
                issues.add(createTransitiveDependencyIssue(table, determinant, dependents));
            }
        }
        
        // Pattern 2: Look for address-related columns (common denormalization)
        detectAddressDenormalization(table, nonKeyColumns, issues);
        
        // Pattern 3: Look for price/tax calculation fields
        detectCalculatedFields(table, nonKeyColumns, issues);
    }
    
    /**
     * Detects address-related columns that are commonly denormalized.
     */
    private void detectAddressDenormalization(Table table, List<Column> nonKeyColumns, 
                                           List<NormalizationIssue> issues) {
        // Look for tables with address-related fields
        List<Column> addressColumns = nonKeyColumns.stream()
            .filter(col -> {
                String name = col.getName().toLowerCase();
                return name.contains("address") || name.contains("street") || 
                       name.equals("city") || name.equals("state") || 
                       name.equals("zip") || name.equals("postal_code") || 
                       name.equals("country");
            })
            .collect(Collectors.toList());
        
        if (addressColumns.size() >= 3) {
            // Look for a potential determinant (usually address_id or similar)
            Optional<Column> determinant = addressColumns.stream()
                .filter(col -> col.getName().toLowerCase().endsWith("_id"))
                .findFirst();
            
            if (determinant.isPresent()) {
                List<Column> dependents = addressColumns.stream()
                    .filter(col -> !col.equals(determinant.get()))
                    .collect(Collectors.toList());
                
                issues.add(createTransitiveDependencyIssue(table, determinant.get(), dependents));
            } else {
                // If no clear determinant, suggest creating an address table anyway
                issues.add(new NormalizationIssue(
                    NormalizationForm.THIRD_NORMAL_FORM,
                    table.getName(),
                    addressColumns.stream().map(Column::getName).collect(Collectors.joining(", ")),
                    "Address information should be normalized into a separate table",
                    "Create an address table and reference it with a foreign key",
                    generateAddressTableSql(table, addressColumns)
                ));
            }
        }
    }
    
    /**
     * Detects calculated fields that might indicate transitive dependencies.
     */
    private void detectCalculatedFields(Table table, List<Column> nonKeyColumns, 
                                      List<NormalizationIssue> issues) {
        // Look for price-related columns
        List<Column> priceColumns = nonKeyColumns.stream()
            .filter(col -> {
                String name = col.getName().toLowerCase();
                return name.contains("price") || name.contains("cost") || 
                       name.contains("amount") || name.contains("total") || 
                       name.contains("tax") || name.contains("discount");
            })
            .collect(Collectors.toList());
        
        if (priceColumns.size() >= 2) {
            // Check if there might be calculated fields
            List<String> calculatedFieldIndicators = Arrays.asList(
                "total", "subtotal", "net", "gross", "final", "discounted"
            );
            
            List<Column> potentialCalculatedFields = priceColumns.stream()
                .filter(col -> {
                    String name = col.getName().toLowerCase();
                    return calculatedFieldIndicators.stream().anyMatch(name::contains);
                })
                .collect(Collectors.toList());
            
            if (!potentialCalculatedFields.isEmpty()) {
                // Suggest splitting these into different tables or computing them on demand
                issues.add(new NormalizationIssue(
                    NormalizationForm.THIRD_NORMAL_FORM,
                    table.getName(),
                    potentialCalculatedFields.stream().map(Column::getName).collect(Collectors.joining(", ")),
                    "Potentially calculated fields detected. These may be transitive dependencies.",
                    "Consider computing these values on demand rather than storing them, " +
                    "or ensure they are properly updated whenever their source values change.",
                    null
                ));
            }
        }
    }
    
    /**
     * Detects columns that might be implicit foreign keys (not declared as such)
     * which can lead to transitive dependencies.
     */
    private void detectImplicitForeignKeys(Table table, List<Column> nonKeyColumns, 
                                         List<NormalizationIssue> issues) {
        // Look for columns that end with _id but aren't declared as foreign keys
        List<Column> potentialFkColumns = nonKeyColumns.stream()
            .filter(col -> col.getName().toLowerCase().endsWith("_id"))
            .filter(col -> !isActualForeignKey(table, col.getName()))
            .collect(Collectors.toList());
        
        for (Column potentialFk : potentialFkColumns) {
            String baseEntity = potentialFk.getName().substring(0, potentialFk.getName().length() - 3);
            
            // Look for columns that might depend on this potential FK
            List<Column> dependents = nonKeyColumns.stream()
                .filter(col -> !col.equals(potentialFk))
                .filter(col -> {
                    String colName = col.getName().toLowerCase();
                    return colName.startsWith(baseEntity.toLowerCase() + "_");
                })
                .collect(Collectors.toList());
            
            if (!dependents.isEmpty()) {
                issues.add(createTransitiveDependencyIssue(table, potentialFk, dependents));
            }
        }
    }
    
    /**
     * Checks if a column is already defined as a foreign key.
     */
    private boolean isActualForeignKey(Table table, String columnName) {
        return table.getForeignKeyConstraints().stream()
            .flatMap(fk -> fk.getColumns().stream())
            .anyMatch(fkCol -> fkCol.equalsIgnoreCase(columnName));
    }
    
    /**
     * Generates SQL to fix a transitive dependency by creating a separate table.
     */
    private String generateTransitiveDependencyFixSql(Table table, Column determinant, List<Column> dependents) {
        // Extract base entity name from the determinant column
        String determinantName = determinant.getName();
        String baseEntity = determinantName;
        
        if (determinantName.contains("_")) {
            baseEntity = determinantName.substring(0, determinantName.lastIndexOf('_'));
        }
        
        String newTableName = baseEntity;
        
        StringBuilder sql = new StringBuilder();
        sql.append("-- Create a new table to remove transitive dependency\n");
        sql.append("CREATE TABLE ").append(newTableName).append(" (\n");
        
        // Add the determinant as the primary key
        sql.append("    ").append(determinant.getName()).append(" ").append(determinant.getDataType());
        if (!determinant.isNullable()) {
            sql.append(" NOT NULL");
        }
        sql.append(" PRIMARY KEY,\n");
        
        // Add the dependent columns
        for (int i = 0; i < dependents.size(); i++) {
            Column col = dependents.get(i);
            
            sql.append("    ").append(col.getName()).append(" ").append(col.getDataType());
            if (!col.isNullable()) {
                sql.append(" NOT NULL");
            }
            
            if (i < dependents.size() - 1) {
                sql.append(",");
            }
            sql.append("\n");
        }
        
        sql.append(");\n\n");
        
        // Add instructions for data migration
        sql.append("-- Data migration instructions:\n");
        sql.append("-- INSERT INTO ").append(newTableName).append(" (")
           .append(determinant.getName()).append(", ")
           .append(dependents.stream().map(Column::getName).collect(Collectors.joining(", ")))
           .append(")\n");
        sql.append("-- SELECT DISTINCT ").append(determinant.getName()).append(", ")
           .append(dependents.stream().map(Column::getName).collect(Collectors.joining(", ")))
           .append(" FROM ").append(table.getName()).append(";\n\n");
        
        // Add foreign key to original table
        sql.append("-- Add foreign key to original table\n");
        sql.append("-- ALTER TABLE ").append(table.getName()).append(" ADD FOREIGN KEY (")
           .append(determinant.getName()).append(") REFERENCES ")
           .append(newTableName).append("(").append(determinant.getName()).append(");\n\n");
        
        // Drop the columns from the original table
        sql.append("-- After migration, drop the dependent columns from the original table\n");
        for (Column col : dependents) {
            sql.append("-- ALTER TABLE ").append(table.getName()).append(" DROP COLUMN ")
               .append(col.getName()).append(";\n");
        }
        
        return sql.toString();
    }
    
    /**
     * Generates SQL to create a separate address table.
     */
    private String generateAddressTableSql(Table table, List<Column> addressColumns) {
        String newTableName = table.getName() + "_address";
        
        StringBuilder sql = new StringBuilder();
        sql.append("-- Create a separate address table\n");
        sql.append("CREATE TABLE ").append(newTableName).append(" (\n");
        sql.append("    id INT AUTO_INCREMENT PRIMARY KEY,\n");
        sql.append("    ").append(table.getName()).append("_id INT NOT NULL,\n");
        
        // Add all address columns
        for (Column col : addressColumns) {
            sql.append("    ").append(col.getName()).append(" ").append(col.getDataType());
            if (!col.isNullable()) {
                sql.append(" NOT NULL");
            }
            sql.append(",\n");
        }
        
        // Add foreign key constraint
        sql.append("    FOREIGN KEY (").append(table.getName()).append("_id) REFERENCES ")
           .append(table.getName()).append("(id)\n");
        
        sql.append(");\n\n");
        
        // Add instructions for data migration
        sql.append("-- Data migration instructions:\n");
        sql.append("-- INSERT INTO ").append(newTableName).append(" (")
           .append(table.getName()).append("_id, ")
           .append(addressColumns.stream().map(Column::getName).collect(Collectors.joining(", ")))
           .append(")\n");
        sql.append("-- SELECT id, ")
           .append(addressColumns.stream().map(Column::getName).collect(Collectors.joining(", ")))
           .append(" FROM ").append(table.getName()).append(";\n\n");
        
        // Drop the columns from the original table
        sql.append("-- After migration, drop the address columns from the original table\n");
        for (Column col : addressColumns) {
            sql.append("-- ALTER TABLE ").append(table.getName()).append(" DROP COLUMN ")
               .append(col.getName()).append(";\n");
        }
        
        return sql.toString();
    }
}