package com.schema.analyzer.model;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a database table with columns, constraints, and relationships.
 */
@Data
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "name")
public class Table {
    private String name;
    private List<Column> columns = new ArrayList<>();
    private List<Constraint> constraints = new ArrayList<>();
    
    @JsonManagedReference
    private List<Relationship> relationships = new ArrayList<>();
    
    public Table(String name) {
        this.name = name;
    }
    
    public void addColumn(Column column) {
        this.columns.add(column);
    }
    
    public void addConstraint(Constraint constraint) {
        this.constraints.add(constraint);
    }
    
    public void addRelationship(Relationship relationship) {
        this.relationships.add(relationship);
    }
    
    /**
     * Returns the columns that form the primary key of this table.
     */
    public Set<Column> getPrimaryKeyColumns() {
        Set<Column> pkColumns = new HashSet<>();
        for (Constraint constraint : constraints) {
            if (constraint.getType() == ConstraintType.PRIMARY_KEY) {
                PrimaryKeyConstraint pk = (PrimaryKeyConstraint) constraint;
                for (String colName : pk.getColumns()) {
                    columns.stream()
                        .filter(col -> col.getName().equalsIgnoreCase(colName))
                        .findFirst()
                        .ifPresent(pkColumns::add);
                }
            }
        }
        return pkColumns;
    }
    
    /**
     * Returns true if this table has a primary key.
     */
    public boolean hasPrimaryKey() {
        return constraints.stream()
            .anyMatch(c -> c.getType() == ConstraintType.PRIMARY_KEY);
    }
    
    /**
     * Returns all unique constraints defined on this table.
     */
    public List<UniqueConstraint> getUniqueConstraints() {
        return constraints.stream()
            .filter(c -> c.getType() == ConstraintType.UNIQUE)
            .map(c -> (UniqueConstraint) c)
            .collect(Collectors.toList());
    }
    
    /**
     * Returns all foreign key constraints defined on this table.
     */
    public List<ForeignKeyConstraint> getForeignKeyConstraints() {
        return constraints.stream()
            .filter(c -> c.getType() == ConstraintType.FOREIGN_KEY)
            .map(c -> (ForeignKeyConstraint) c)
            .collect(Collectors.toList());
    }
    
    /**
     * Finds a column by name (case-insensitive).
     */
    public Column findColumnByName(String name) {
        return columns.stream()
            .filter(c -> c.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }
}