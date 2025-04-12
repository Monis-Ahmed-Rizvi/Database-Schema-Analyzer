package com.schema.analyzer.service;

import com.schema.analyzer.model.*;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.table.ForeignKeyIndex;
import net.sf.jsqlparser.statement.create.table.Index;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service to parse SQL statements and extract database schema information.
 */
@Service
@Slf4j
public class SQLParserService {

    /**
     * Parses SQL CREATE TABLE statements and builds a database schema model.
     * 
     * @param sqlScript The SQL script containing CREATE TABLE statements
     * @return A DatabaseSchema object representing the parsed schema
     * @throws JSQLParserException If the SQL cannot be parsed
     */
    public DatabaseSchema parseSchema(String sqlScript) throws JSQLParserException {
        DatabaseSchema schema = new DatabaseSchema("parsed_schema");
        
        log.debug("Parsing SQL script: {}", sqlScript);
        
        // Split the script into individual statements
        String[] statements = sqlScript.split(";");
        
        for (String stmt : statements) {
            stmt = stmt.trim();
            if (stmt.isEmpty()) {
                continue;
            }
            
            try {
                // Parse the SQL statement
                Statement statement = CCJSqlParserUtil.parse(stmt + ";");
                
                // Process CREATE TABLE statements
                if (statement instanceof CreateTable) {
                    CreateTable createTable = (CreateTable) statement;
                    log.debug("Parsing CREATE TABLE: {}", createTable.getTable().getName());
                    Table table = parseCreateTable(createTable);
                    schema.addTable(table);
                }
            } catch (JSQLParserException e) {
                log.warn("Failed to parse statement: {}", stmt, e);
                // Continue with next statement
            }
        }
        
        // Identify relationships between tables
        schema.identifyRelationships();
        log.debug("Completed parsing schema with {} tables", schema.getTables().size());
        
        return schema;
    }
    
    /**
     * Parses a CREATE TABLE statement and builds a Table model.
     */
    private Table parseCreateTable(CreateTable createTable) {
        String tableName = createTable.getTable().getName();
        Table table = new Table(tableName);
        
        // Parse columns
        if (createTable.getColumnDefinitions() != null) {
            List<String> primaryKeyColumns = new ArrayList<>();
            
            for (ColumnDefinition colDef : createTable.getColumnDefinitions()) {
                Column column = parseColumnDefinition(colDef);
                table.addColumn(column);
                log.debug("Added column to {}: {}", tableName, column.getName());
                
                // Check for inline PRIMARY KEY constraint
                if (colDef.getColumnSpecs() != null) {
                    for (int i = 0; i < colDef.getColumnSpecs().size() - 1; i++) {
                        if ("PRIMARY".equalsIgnoreCase(colDef.getColumnSpecs().get(i)) && 
                            "KEY".equalsIgnoreCase(colDef.getColumnSpecs().get(i + 1))) {
                            primaryKeyColumns.add(column.getName());
                            log.debug("Found inline PRIMARY KEY for column: {}", column.getName());
                            break;
                        }
                    }
                }
            }
            
            // If any columns were marked as PRIMARY KEY inline
            if (!primaryKeyColumns.isEmpty()) {
                PrimaryKeyConstraint pk = new PrimaryKeyConstraint("pk_" + tableName, primaryKeyColumns);
                table.addConstraint(pk);
                log.debug("Added inline PRIMARY KEY constraint for columns: {}", primaryKeyColumns);
            }
        }
        
        // Parse constraints (indexes and keys)
        if (createTable.getIndexes() != null) {
            log.debug("Table {} has {} indexes/constraints", tableName, createTable.getIndexes().size());
            for (Index index : createTable.getIndexes()) {
                Constraint constraint = parseIndex(index);
                if (constraint != null) {
                    table.addConstraint(constraint);
                    log.debug("Added constraint to {}: {} of type {}", 
                             tableName, constraint.getName(), constraint.getType());
                }
            }
        } else {
            log.debug("Table {} has no explicit indexes/constraints defined", tableName);
        }
        
        return table;
    }
    
    /**
     * Parses a column definition and builds a Column model.
     */
    private Column parseColumnDefinition(ColumnDefinition colDef) {
        String name = colDef.getColumnName();
        String dataType = colDef.getColDataType().getDataType();
        
        // Include specifications like length if available
        if (colDef.getColDataType().getArgumentsStringList() != null) {
            dataType += "(" + String.join(",", colDef.getColDataType().getArgumentsStringList()) + ")";
        }
        
        // Check if column is nullable
        boolean nullable = true;
        if (colDef.getColumnSpecs() != null) {
            for (int i = 0; i < colDef.getColumnSpecs().size() - 1; i++) {
                if ("NOT".equalsIgnoreCase(colDef.getColumnSpecs().get(i)) && 
                    "NULL".equalsIgnoreCase(colDef.getColumnSpecs().get(i + 1))) {
                    nullable = false;
                    break;
                }
            }
        }
        
        // Extract default value if present
        String defaultValue = null;
        if (colDef.getColumnSpecs() != null) {
            int defaultIdx = -1;
            for (int i = 0; i < colDef.getColumnSpecs().size(); i++) {
                if ("DEFAULT".equalsIgnoreCase(colDef.getColumnSpecs().get(i))) {
                    defaultIdx = i;
                    break;
                }
            }
            
            if (defaultIdx != -1 && defaultIdx + 1 < colDef.getColumnSpecs().size()) {
                defaultValue = colDef.getColumnSpecs().get(defaultIdx + 1);
            }
        }
        
        return new Column(name, dataType, nullable, defaultValue);
    }
    
    /**
     * Parses an index (constraint) and builds the appropriate Constraint model.
     */
    private Constraint parseIndex(Index index) {
        List<String> columns = index.getColumnsNames();
        log.debug("Parsing index of type {} for columns {}", index.getType(), columns);
        
        // Handle PRIMARY KEY
        if (index.getType().equalsIgnoreCase("PRIMARY KEY")) {
            return new PrimaryKeyConstraint(index.getName(), columns);
        }
        // Handle UNIQUE constraint
        else if (index.getType().equalsIgnoreCase("UNIQUE")) {
            return new UniqueConstraint(index.getName(), columns);
        }
        // Handle FOREIGN KEY
        else if (index instanceof ForeignKeyIndex) {
            ForeignKeyIndex fkIndex = (ForeignKeyIndex) index;
            String refTable = fkIndex.getTable().getName();
            
            // Get referenced columns
            List<String> refColumns = new ArrayList<>();
            if (fkIndex.getReferencedColumnNames() != null) {
                refColumns = fkIndex.getReferencedColumnNames();
            }
            
            log.debug("Found FK: {} referencing {}({})", 
                     columns, refTable, refColumns);
            
            return new ForeignKeyConstraint(index.getName(), columns, refTable, refColumns);
        }
        
        log.debug("Unknown index type: {}", index.getType());
        return null;
    }
    
    /**
     * Utility method to parse a comma-separated list of column names.
     */
    private List<String> parseColumnList(String columnList) {
        if (columnList == null || columnList.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        return Arrays.stream(columnList.split(","))
            .map(String::trim)
            .collect(Collectors.toList());
    }
}