package com.schema.analyzer.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a primary key constraint on a table.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class PrimaryKeyConstraint extends Constraint {
    private List<String> columns = new ArrayList<>();
    
    public PrimaryKeyConstraint(String name, List<String> columns) {
        super(name, ConstraintType.PRIMARY_KEY);
        this.columns = columns;
    }
    
    public PrimaryKeyConstraint(List<String> columns) {
        super(ConstraintType.PRIMARY_KEY);
        this.columns = columns;
    }
}
