package com.schema.analyzer.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a database schema consisting of tables and their relationships.
 */
@Data
@NoArgsConstructor
public class DatabaseSchema {
    private String name;
    private List<Table> tables = new ArrayList<>();
    
    public DatabaseSchema(String name) {
        this.name = name;
    }
    
    public void addTable(Table table) {
        this.tables.add(table);
    }
    
    /**
     * Identifies relationships between tables based on foreign key constraints.
     */
    public void identifyRelationships() {
        // Build a map of table name to table object for easy lookup
        Map<String, Table> tableMap = new HashMap<>();
        for (Table table : tables) {
            tableMap.put(table.getName().toLowerCase(), table);
        }
        
        // Look for foreign key constraints in each table
        for (Table table : tables) {
            for (Constraint constraint : table.getConstraints()) {
                if (constraint.getType() == ConstraintType.FOREIGN_KEY) {
                    ForeignKeyConstraint fk = (ForeignKeyConstraint) constraint;
                    Table referencedTable = tableMap.get(fk.getReferencedTable().toLowerCase());
                    if (referencedTable != null) {
                        table.addRelationship(new Relationship(
                            table, 
                            referencedTable, 
                            fk.getColumns(), 
                            fk.getReferencedColumns()
                        ));
                    }
                }
            }
        }
    }
}