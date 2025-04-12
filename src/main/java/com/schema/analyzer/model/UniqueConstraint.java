package com.schema.analyzer.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a unique constraint on a table.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class UniqueConstraint extends Constraint {
    private List<String> columns = new ArrayList<>();
    
    public UniqueConstraint(String name, List<String> columns) {
        super(name, ConstraintType.UNIQUE);
        this.columns = columns;
    }
    
    public UniqueConstraint(List<String> columns) {
        super(ConstraintType.UNIQUE);
        this.columns = columns;
    }
}
