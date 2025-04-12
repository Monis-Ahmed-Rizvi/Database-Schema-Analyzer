package com.schema.analyzer.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a foreign key constraint on a table.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ForeignKeyConstraint extends Constraint {
    private List<String> columns = new ArrayList<>();
    private String referencedTable;
    private List<String> referencedColumns = new ArrayList<>();
    
    public ForeignKeyConstraint(String name, List<String> columns, 
                                String referencedTable, List<String> referencedColumns) {
        super(name, ConstraintType.FOREIGN_KEY);
        this.columns = columns;
        this.referencedTable = referencedTable;
        this.referencedColumns = referencedColumns;
    }
    
    public ForeignKeyConstraint(List<String> columns, 
                               String referencedTable, List<String> referencedColumns) {
        super(ConstraintType.FOREIGN_KEY);
        this.columns = columns;
        this.referencedTable = referencedTable;
        this.referencedColumns = referencedColumns;
    }
}
