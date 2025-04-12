package com.schema.analyzer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a column in a database table.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Column {
    private String name;
    private String dataType;
    private boolean nullable = true;
    private String defaultValue;
    
    public Column(String name, String dataType) {
        this.name = name;
        this.dataType = dataType;
    }
    
    public Column(String name, String dataType, boolean nullable) {
        this.name = name;
        this.dataType = dataType;
        this.nullable = nullable;
    }
    
    /**
     * Determines if this column potentially contains multi-valued attributes.
     * Multi-valued attributes violate 1NF.
     */
    public boolean isMultiValued() {
        String type = dataType.toUpperCase();
        return type.contains("SET") || 
               type.contains("ENUM") || 
               type.contains("JSON") || 
               type.contains("ARRAY");
    }
    
    /**
     * Determines if this column's type suggests it might store structured data.
     * Columns storing structured data violate 1NF.
     */
    public boolean mightContainStructuredData() {
        String type = dataType.toUpperCase();
        String nameLower = name.toLowerCase();
        
        // Skip columns that likely contain valid unstructured content
        if (type.contains("TEXT")) {
            // Common column names for text content that are usually atomic
            if (nameLower.contains("content") || 
                nameLower.contains("description") || 
                nameLower.contains("bio") || 
                nameLower.contains("comment") || 
                nameLower.contains("note") ||
                nameLower.contains("article")) {
                return false;
            }
        }
        
        // Skip columns that likely contain valid binary data
        if (type.contains("BLOB")) {
            // Common column names for binary content that are usually atomic
            if (nameLower.contains("image") || 
                nameLower.contains("photo") || 
                nameLower.contains("thumbnail") || 
                nameLower.contains("file") || 
                nameLower.contains("attachment")) {
                return false;
            }
        }
        
        // Default behavior for other potentially structured data types
        return type.contains("TEXT") || 
               type.contains("BLOB") || 
               type.contains("JSON") || 
               type.contains("VARCHAR") && type.contains("MAX");
    }
}