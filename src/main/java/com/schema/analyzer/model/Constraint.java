package com.schema.analyzer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Base class for database constraints.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class Constraint {
    private String name;
    private ConstraintType type;
    
    public Constraint(ConstraintType type) {
        this.type = type;
    }
}
